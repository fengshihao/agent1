package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.script.FakeScriptEngineFactory;
import com.agent1.javaagent.script.MutableScriptToolBridge;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.DelegatingAgentTool;
import com.agent1.javaagent.tool.WorkspaceToolProvider;
import java.util.List;
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
            assertTrue(toolNames.get().contains("execute_script"));
            assertTrue(host.runtime().getStateSnapshot().getSystemPrompt().contains("execute_script"));
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
            "grep",
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
            assertTrue(toolNames.get().contains("grep"));
            assertTrue(toolNames.get().contains("execute_script"));
            assertTrue(bridge.exposedNames().contains("grep"));
            assertTrue(bridge.exposedNames().contains("read_file"));
            assertFalse(bridge.exposedNames().contains("execute_script"));
            assertEquals("hit", bridge.call("grep", java.util.Map.of()));
            assertTrue(host.runtime().getStateSnapshot().getSystemPrompt().contains("$tools"));
        }
    }
}
