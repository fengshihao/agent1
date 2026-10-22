package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.core.RunOutcome;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunRecord;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.script.FakeScriptEngineFactory;
import com.agent1.javaagent.script.MutableScriptToolBridge;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.DelegatingAgentTool;
import com.agent1.javaagent.tool.agent.AskUserTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.agent1.javaagent.tool.WorkspaceToolProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ProductivityAgentHostTest {

    @TempDir
    Path temp;

    @Test
    void runUserMessagePersistsTranscriptAndRunRecord() {
        LlmClient fake = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                streamListener.onTextDelta("OK");
                return new AssistantResponse("OK", List.of());
            }

            @Override
            public void close() {
            }
        };

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            String runId = host.runUserMessage("hello");

            FileSessionStore store = new FileSessionStore(temp);
            List<AgentMessage> transcript = store.loadTranscript(host.getActiveSessionId());
            assertEquals(2, transcript.size());
            assertEquals("hello", transcript.get(0).getContent());
            assertEquals("OK", transcript.get(1).getContent());

            FileRunStore runs = new FileRunStore(store);
            assertEquals(RunState.SUCCEEDED, runs.read(host.getActiveSessionId(), runId).getState());
        }
    }

    @Test
    void interruptedRunningRecordDoesNotBlockNextMessage() {
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            streamListener.onTextDelta("继续完成");
            return new AssistantResponse("继续完成", List.of());
        };
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            String sessionId = host.getActiveSessionId();
            FileSessionStore store = new FileSessionStore(temp);
            FileRunStore runs = new FileRunStore(store);
            String started = "2026-10-11T07:04:55.547623Z";
            runs.write(new RunRecord(
                "stale-run",
                sessionId,
                RunState.RUNNING,
                started,
                started,
                null,
                1,
                9
            ));

            String runId = host.runUserMessage("继续");

            assertEquals(RunState.FAILED, runs.read(sessionId, "stale-run").getState());
            assertEquals(
                "上次运行因进程中断未结束",
                runs.read(sessionId, "stale-run").getLastError().orElse("")
            );
            assertEquals(RunState.SUCCEEDED, runs.read(sessionId, runId).getState());
            List<AgentMessage> transcript = store.loadTranscript(sessionId);
            assertEquals("继续", transcript.get(0).getContent());
            assertEquals("继续完成", transcript.get(1).getContent());
        }
    }

    @Test
    void switchSessionReloadsTranscript() {
        LlmClient fake = (request, tools, streamListener, cancellationToken) ->
            new AssistantResponse("x", List.of());

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            SessionMeta a = host.createSession();
            host.runUserMessage("only-a");

            SessionMeta b = host.createSession();
            host.runUserMessage("only-b");

            host.switchSession(a.getSessionId());
            assertEquals(2, host.runtime().getStateSnapshot().getMessages().size());
            assertEquals("only-a", host.runtime().getStateSnapshot().getMessages().get(0).getContent());

            host.switchSession(b.getSessionId());
            assertEquals("only-b", host.runtime().getStateSnapshot().getMessages().get(0).getContent());
            assertTrue(host.runtime().getStateSnapshot().getSystemPrompt().contains("工作区"));
        }
    }

    @Test
    void loadTranscriptSwitchesAndReturnsMessages() {
        LlmClient fake = (request, tools, streamListener, cancellationToken) ->
            new AssistantResponse("ok", List.of());

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            SessionMeta a = host.createSession();
            host.runUserMessage("alpha");
            SessionMeta b = host.createSession();
            host.runUserMessage("beta");

            List<AgentMessage> fromA = host.loadTranscript(a.getSessionId());
            assertEquals(a.getSessionId(), host.getActiveSessionId());
            assertEquals("alpha", fromA.get(0).getContent());
            assertEquals("ok", fromA.get(1).getContent());

            List<AgentMessage> fromB = host.loadTranscript(b.getSessionId());
            assertEquals(b.getSessionId(), host.getActiveSessionId());
            assertEquals("beta", fromB.get(0).getContent());
        }
    }

    @Test
    void askUserEndsRunInWaitingUserState() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        ArrayNode questions = mapper.createArrayNode();
        ObjectNode q = mapper.createObjectNode();
        q.put("id", "origin");
        q.put("prompt", "从哪出发？");
        questions.add(q);
        params.set("questions", questions);
        String args = mapper.writeValueAsString(params);
        ToolCall askCall = new ToolCall("a1", AskUserTool.TOOL_NAME, args);

        LlmClient fake = (request, tools, streamListener, cancellationToken) ->
            new AssistantResponse("", List.of(askCall));

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            String runId = host.runUserMessage("广西三日游");
            FileRunStore runs = new FileRunStore(new FileSessionStore(temp));
            assertEquals(RunState.WAITING_USER, runs.read(host.getActiveSessionId(), runId).getState());
        }
    }

    @Test
    void registersCoreToolsWithoutRetiredCatalogInstall() {
        java.util.concurrent.atomic.AtomicReference<List<String>> toolNames =
            new java.util.concurrent.atomic.AtomicReference<>();
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            toolNames.set(tools.stream().map(AgentTool::name).collect(Collectors.toList()));
            return new AssistantResponse("x", List.of());
        };
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            host.runUserMessage("ping");
            assertFalse(toolNames.get().contains("read_agent_doc"));
            assertFalse(toolNames.get().contains("list_catalog"));
            assertFalse(toolNames.get().contains("promote_request"));
            assertFalse(toolNames.get().contains("catalog_install"));
            assertFalse(toolNames.get().contains("catalog_sync_status"));
            assertFalse(toolNames.get().contains("list_sessions"));
            assertFalse(toolNames.get().contains("skill"));
            assertTrue(toolNames.get().contains("ask_user"));
            assertTrue(toolNames.get().contains("todo_write"));
            assertTrue(toolNames.get().contains("find_caps"));
            assertFalse(toolNames.get().contains("media_resize"));
            assertFalse(toolNames.get().contains("media_probe"));
            assertFalse(toolNames.get().contains("media_extract_frame"));
            assertTrue(toolNames.get().contains("images_download"));
            assertFalse(toolNames.get().contains("image_search"));
            assertFalse(toolNames.get().contains("web_search"));
            assertFalse(host.runtime().getStateSnapshot().getSystemPrompt().contains("web_search"));
        }
    }

    @Test
    void registersWebSearchWhenTavilyKeyPresent() {
        java.util.concurrent.atomic.AtomicReference<List<String>> toolNames =
            new java.util.concurrent.atomic.AtomicReference<>();
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            toolNames.set(tools.stream().map(AgentTool::name).collect(Collectors.toList()));
            return new AssistantResponse("x", List.of());
        };
        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
            .apiKey("test-key")
            .webSearchApiKey("tvly-test")
            .build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            host.runUserMessage("ping");
            assertTrue(toolNames.get().contains("web_search"));
            assertTrue(toolNames.get().contains("image_search"));
            assertTrue(toolNames.get().contains("images_download"));
            assertTrue(host.runtime().getStateSnapshot().getSystemPrompt().contains("web_search"));
        }
    }

    @Test
    void registersExecuteScriptWhenEngineFactoryPresent() {
        java.util.concurrent.atomic.AtomicReference<List<String>> toolNames =
            new java.util.concurrent.atomic.AtomicReference<>();
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            toolNames.set(tools.stream().map(AgentTool::name).collect(Collectors.toList()));
            return new AssistantResponse("x", List.of());
        };

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        FakeScriptEngineFactory factory = new FakeScriptEngineFactory("\"x\"");
        try (ProductivityAgentHost host = new ProductivityAgentHost(
            temp,
            config,
            fake,
            factory,
            30_000,
            "沙盒契约片段"
        )) {
            host.createSession();
            host.runUserMessage("ping");
            assertTrue(toolNames.get().contains("run_js"));
            assertTrue(host.runtime().getStateSnapshot().getSystemPrompt().contains("run_js"));
            assertTrue(host.runtime().getStateSnapshot().getSystemPrompt().contains("沙盒契约片段"));
        }
    }

    @Test
    void registersExtraToolsAndExposesThemToScripts() {
        java.util.concurrent.atomic.AtomicReference<List<String>> toolNames =
            new java.util.concurrent.atomic.AtomicReference<>();
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            toolNames.set(tools.stream().map(AgentTool::name).collect(Collectors.toList()));
            return new AssistantResponse("x", List.of());
        };
        MutableScriptToolBridge bridge = new MutableScriptToolBridge();
        WorkspaceToolProvider extra = sandbox -> List.of(new DelegatingAgentTool(
            "extra_search",
            "search",
            null,
            (params, token) -> "hit"
        ));
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(
            temp,
            config,
            fake,
            new FakeScriptEngineFactory("\"x\""),
            30_000,
            "",
            bridge,
            extra
        )) {
            host.createSession();
            host.runUserMessage("ping");
            assertTrue(toolNames.get().contains("extra_search"));
            assertTrue(toolNames.get().contains("run_js"));
            assertTrue(toolNames.get().contains("read_url"));
            assertTrue(bridge.exposedNames().contains("extra_search"));
            assertTrue(bridge.exposedNames().contains("read_file"));
            assertTrue(bridge.exposedNames().contains("todo_write"));
            assertTrue(bridge.exposedNames().contains("read_url"));
            assertFalse(bridge.exposedNames().contains("run_js"));
            assertEquals("hit", bridge.call("extra_search", java.util.Map.of()));
            assertTrue(host.runtime().getStateSnapshot().getSystemPrompt().contains("$tools"));
        }
    }

    @Test
    void injectsOpenTodosIntoSystemPrompt() throws Exception {
        LlmClient fake = (request, tools, streamListener, cancellationToken) ->
            new AssistantResponse("ok", List.of());
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            java.nio.file.Files.writeString(
                temp.resolve("sessions").resolve(host.getActiveSessionId()).resolve("todos.json"),
                "{\"todos\":[{\"id\":\"1\",\"content\":\"写出大纲\",\"status\":\"in_progress\"}]}"
            );
            host.runUserMessage("继续");
            String prompt = host.activeSystemPrompt();
            assertTrue(prompt.contains("当前任务清单"));
            assertTrue(prompt.contains("写出大纲"));
        }
    }

    @Test
    void completedTodosAreNotInjected() throws Exception {
        LlmClient fake = (request, tools, streamListener, cancellationToken) ->
            new AssistantResponse("ok", List.of());
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            java.nio.file.Files.writeString(
                temp.resolve("sessions").resolve(host.getActiveSessionId()).resolve("todos.json"),
                "{\"todos\":[{\"id\":\"1\",\"content\":\"写出大纲\",\"status\":\"completed\"}]}"
            );
            host.runUserMessage("继续");
            assertFalse(host.activeSystemPrompt().contains("当前任务清单"));
        }
    }

    @Test
    void toolBudgetStop_persistsToolResultAndContinueSucceeds() {
        List<ChatRequest> requests = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            requests.add(request);
            int index = calls.getAndIncrement();
            if (index == 0) {
                return new AssistantResponse("", List.of(new ToolCall("c0", "missing_tool", "{}")));
            }
            if (index == 1) {
                return new AssistantResponse("", List.of(new ToolCall("c1", "missing_tool", "{}")));
            }
            streamListener.onTextDelta("补完了");
            return new AssistantResponse("补完了", List.of());
        };
        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
            .apiKey("test-key")
            .maxTurnsPerRun(5)
            .maxToolCallsPerRun(1)
            .build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            String sessionId = host.getActiveSessionId();
            String runId = host.runUserMessage("配图");

            FileRunStore runs = new FileRunStore(new FileSessionStore(temp));
            assertEquals(RunState.PAUSED, runs.read(sessionId, runId).getState());
            assertTrue(RunOutcome.isToolBudget(runs.read(sessionId, runId).getLastError().orElse("")));

            List<AgentMessage> transcript = new FileSessionStore(temp).loadTranscript(sessionId);
            int skipped = -1;
            int notice = -1;
            for (int i = 0; i < transcript.size(); i++) {
                AgentMessage message = transcript.get(i);
                if ("c1".equals(message.getToolCallId())) {
                    skipped = i;
                }
                if (message.getContent() != null && message.getContent().contains(RunOutcome.TOOL_BUDGET_PREFIX)) {
                    notice = i;
                }
            }
            assertTrue(skipped >= 0, "未执行的工具调用必须先写入回执");
            assertTrue(notice > skipped, "回执必须排在暂停说明之前");

            host.runUserMessage("继续");
            ChatRequest continued = requests.get(requests.size() - 1);
            assertTrue(continued.getMessages().stream()
                .anyMatch(m -> "c1".equals(m.getToolCallId())));
            assertTrue(new FileSessionStore(temp).loadTranscript(sessionId).stream()
                .anyMatch(m -> "补完了".equals(m.getContent())));
        }
    }
}
