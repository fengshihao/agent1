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
import com.agent1.javaagent.tool.AgentTool;
import java.util.ArrayList;
import java.util.List;
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
}
