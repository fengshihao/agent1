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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
