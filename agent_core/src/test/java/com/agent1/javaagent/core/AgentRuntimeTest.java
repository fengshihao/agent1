package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.model.ChatUsage;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.tool.agent.AskUserTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class AgentRuntimeTest {

    @Test
    void prompt_shouldEmitCoreEventsAndStoreMessages() {
        LlmClient fakeClient = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                streamListener.onTextDelta("你");
                streamListener.onTextDelta("好");
                return new AssistantResponse("你好", List.of());
            }
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .systemPrompt("You are helpful")
                .build(),
            fakeClient
        );

        List<AgentEventType> eventTypes = new ArrayList<>();
        AutoCloseable subscription = runtime.subscribe(event -> eventTypes.add(event.getType()));

        runtime.prompt("hello").join();
        runtime.waitForIdle();

        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        assertEquals(2, snapshot.getMessages().size());
        assertEquals(AgentMessage.ROLE_USER, snapshot.getMessages().get(0).getRole());
        assertEquals(AgentMessage.ROLE_ASSISTANT, snapshot.getMessages().get(1).getRole());
        assertEquals("你好", snapshot.getMessages().get(1).getContent());
        assertTrue(eventTypes.contains(AgentEventType.MESSAGE_UPDATE));
        assertEquals(AgentEventType.AGENT_START, eventTypes.get(0));
        assertEquals(AgentEventType.AGENT_END, eventTypes.get(eventTypes.size() - 1));

        try {
            subscription.close();
        } catch (Exception ignored) {
        }
        runtime.close();
    }

    @Test
    void continueRun_shouldValidateLastMessageRole() {
        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .messages(List.of(AgentMessage.assistant("done", List.of())))
                .build(),
            (request, tools, streamListener, cancellationToken) -> new AssistantResponse("ignored", List.of())
        );

        assertThrows(IllegalStateException.class, runtime::continueRun);
        runtime.close();
    }

    @Test
    void observeEvents_shouldReceiveStreamFromRxObservable() {
        LlmClient fakeClient = (request, tools, streamListener, cancellationToken) -> {
            streamListener.onTextDelta("A");
            return new AssistantResponse("A", List.of());
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model").build(),
            fakeClient
        );

        List<AgentEventType> rxEvents = new CopyOnWriteArrayList<>();
        var disposable = runtime.observeEvents()
            .map(event -> event.getType())
            .subscribe(rxEvents::add);

        runtime.prompt("hello").join();
        runtime.waitForIdle();

        assertTrue(rxEvents.contains(AgentEventType.AGENT_START));
        assertTrue(rxEvents.contains(AgentEventType.MESSAGE_UPDATE));
        assertTrue(rxEvents.contains(AgentEventType.AGENT_END));

        disposable.dispose();
        runtime.close();
    }

    @Test
    void maxTurnsReached_shouldAppendSummaryAndMarkPaused() {
        ToolCall call = new ToolCall("c1", "noop", "{}");
        ArrayDeque<AssistantResponse> queue = new ArrayDeque<>();
        queue.add(new AssistantResponse("", List.of(call)));
        queue.add(new AssistantResponse("进度摘要", List.of()));

        ObjectMapper mapper = new ObjectMapper();
        AgentTool noop = new AgentTool() {
            @Override
            public String name() {
                return "noop";
            }

            @Override
            public String description() {
                return "noop";
            }

            @Override
            public JsonNode parametersSchema() {
                ObjectNode schema = mapper.createObjectNode();
                schema.put("type", "object");
                return schema;
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                return ToolExecutionResult.text("ok");
            }
        };

        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            if (tools.isEmpty()) {
                streamListener.onTextDelta("进度摘要");
                return new AssistantResponse("进度摘要", List.of());
            }
            return queue.removeFirst();
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(noop))
                .maxTurnsPerRun(1)
                .build(),
            fake
        );

        runtime.prompt("go").join();
        runtime.waitForIdle();

        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        assertTrue(RunOutcome.isPaused(snapshot.getError()));
        assertTrue(
            snapshot.getMessages().stream()
                .anyMatch(m -> AgentMessage.ROLE_ASSISTANT.equals(m.getRole())
                    && "进度摘要".equals(m.getContent()))
        );
        runtime.close();
    }

    @Test
    void newRunClearsPausedErrorFromPreviousRun() {
        ToolCall noopCall = new ToolCall("t1", "noop", "{}");
        AgentTool noop = new AgentTool() {
            @Override
            public String name() {
                return "noop";
            }

            @Override
            public String description() {
                return "noop";
            }

            @Override
            public JsonNode parametersSchema() {
                return new ObjectMapper().createObjectNode().put("type", "object");
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                return ToolExecutionResult.text("ok");
            }
        };

        java.util.concurrent.atomic.AtomicInteger llmCalls = new java.util.concurrent.atomic.AtomicInteger();
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            if (tools.isEmpty() || llmCalls.getAndIncrement() > 0) {
                streamListener.onTextDelta("done");
                return new AssistantResponse("done", List.of());
            }
            return new AssistantResponse("", List.of(noopCall));
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(noop))
                .maxTurnsPerRun(1)
                .build(),
            fake
        );

        runtime.prompt("first").join();
        runtime.waitForIdle();
        assertTrue(RunOutcome.isPaused(runtime.getStateSnapshot().getError()));

        runtime.prompt("second").join();
        runtime.waitForIdle();
        assertTrue(runtime.getStateSnapshot().getError() == null
            || runtime.getStateSnapshot().getError().isBlank());
        runtime.close();
    }

    @Test
    void askUserTool_shouldStopRunWithWaitingUserError() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        ArrayNode questions = mapper.createArrayNode();
        ObjectNode q = mapper.createObjectNode();
        q.put("id", "origin");
        q.put("prompt", "出发地？");
        questions.add(q);
        params.set("questions", questions);
        String args = mapper.writeValueAsString(params);

        ToolCall askCall = new ToolCall("ask1", AskUserTool.TOOL_NAME, args);
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            if (!request.getMessages().isEmpty()
                && request.getMessages().get(request.getMessages().size() - 1).getRole().equals("tool")) {
                streamListener.onTextDelta("请补充信息");
                return new AssistantResponse("请补充信息", List.of());
            }
            return new AssistantResponse("", List.of(askCall));
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(new AskUserTool()))
                .maxTurnsPerRun(5)
                .build(),
            fake
        );

        runtime.prompt("规划广西").join();
        runtime.waitForIdle();

        assertTrue(RunOutcome.isWaitingUser(runtime.getStateSnapshot().getError()));
        runtime.close();
    }

    @Test
    void abort_shouldMarkCancelled() throws Exception {
        LlmClient slow = (request, tools, streamListener, cancellationToken) -> {
            while (!cancellationToken.isCancelled()) {
                Thread.sleep(5);
            }
            return new AssistantResponse("", List.of());
        };

        AgentRuntime runtime = new AgentRuntime(AgentOptions.builder("test-model").build(), slow);
        CompletableFuture<Void> task = runtime.prompt("wait");
        Thread.sleep(30);
        runtime.abort();
        task.join();
        runtime.waitForIdle();

        assertTrue(RunOutcome.isCancelled(runtime.getStateSnapshot().getError()));
        runtime.close();
    }

    @Test
    void prompt_llmFailure_persistsAssistantErrorMessage() {
        LlmClient failing = (request, tools, streamListener, cancellationToken) -> {
            throw new IllegalStateException("DashScope error [AllocationQuota.FreeTierOnly]: quota");
        };
        AgentRuntime runtime = new AgentRuntime(AgentOptions.builder("deepseek-v4-flash").build(), failing);
        runtime.prompt("hi").join();
        runtime.waitForIdle();
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        assertEquals(2, snapshot.getMessages().size());
        assertEquals(AgentMessage.ROLE_ASSISTANT, snapshot.getMessages().get(1).getRole());
        assertTrue(snapshot.getMessages().get(1).getContent().contains("AllocationQuota.FreeTierOnly"));
        runtime.close();
    }

    @Test
    void prompt_shouldEmitUsageWhenModelReturnsTokens() {
        LlmClient fakeClient = (request, tools, streamListener, cancellationToken) -> {
            streamListener.onTextDelta("ok");
            return new AssistantResponse("ok", List.of(), "stop", new ChatUsage(4, 2, 1L));
        };
        AgentRuntime runtime = new AgentRuntime(AgentOptions.builder("test-model").build(), fakeClient);
        List<AgentEventType> types = new ArrayList<>();
        runtime.subscribe(event -> types.add(event.getType()));
        runtime.prompt("hi").join();
        assertTrue(types.contains(AgentEventType.USAGE));
        runtime.close();
    }

    @Test
    void runCrashWithError_synthesizesToolResultSoNextRequestStaysPaired() {
        // 模拟 Android 主线程崩溃路径：工具元数据访问抛 Error（如 NoSuchMethodError），
        // 穿透 executeToolCall 后 run 失败，pending toolCall 必须补合成回执。
        AgentTool boom = new AgentTool() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public String description() {
                return "boom";
            }

            @Override
            public JsonNode parametersSchema() {
                throw new NoSuchMethodError("simulated Files.readString");
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                return ToolExecutionResult.text("ok");
            }
        };

        List<ChatRequest> requests = new CopyOnWriteArrayList<>();
        LlmClient fake = (request, tools, streamListener, cancellationToken) -> {
            requests.add(request);
            if (requests.size() == 1) {
                return new AssistantResponse("", List.of(new ToolCall("tc-crash", "boom", "{}")));
            }
            streamListener.onTextDelta("继续ok");
            return new AssistantResponse("继续ok", List.of());
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(boom))
                .maxTurnsPerRun(5)
                .build(),
            fake
        );

        runtime.prompt("go").join();
        runtime.waitForIdle();

        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        assertTrue(snapshot.getError() != null && snapshot.getError().contains("simulated"));

        // transcript 尾部：无回执的 tool_call 必须已补上配对的 toolResult
        List<AgentMessage> messages = snapshot.getMessages();
        boolean assistantHasCrashCall = messages.stream()
            .anyMatch(m -> m.getToolCalls().stream().anyMatch(tc -> "tc-crash".equals(tc.getId())));
        assertTrue(assistantHasCrashCall);
        boolean hasPairedResult = messages.stream()
            .anyMatch(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole())
                && "tc-crash".equals(m.getToolCallId())
                && m.isError());
        assertTrue(hasPairedResult, "run 崩溃后必须为 pending toolCall 补合成 toolResult");

        // 下一轮请求不再报 insufficient tool messages：每个 toolCall id 都有配对 toolResult
        runtime.prompt("继续").join();
        runtime.waitForIdle();
        assertEquals(2, requests.size());
        ChatRequest second = requests.get(1);
        List<String> callIds = new ArrayList<>();
        List<String> resultIds = new ArrayList<>();
        for (AgentMessage m : second.getMessages()) {
            for (ToolCall tc : m.getToolCalls()) {
                callIds.add(tc.getId());
            }
            if (AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()) && m.getToolCallId() != null) {
                resultIds.add(m.getToolCallId());
            }
        }
        assertTrue(resultIds.containsAll(callIds), "下一轮请求中 tool_call 与 tool_result 必须配对完整");

        // 会话仍可用：第二轮 run 正常完成并给出回复
        assertTrue(runtime.getStateSnapshot().getMessages().stream()
            .anyMatch(m -> "继续ok".equals(m.getContent())));
        runtime.close();
    }
}
