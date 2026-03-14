package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
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
import org.junit.jupiter.api.Test;

class ToolLoopIntegrationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void prompt_shouldExecuteToolCallsThenContinue() {
        ToolCall call = new ToolCall("call_1", "echo", "{\"text\":\"ping\"}");
        ArrayDeque<AssistantResponse> responses = new ArrayDeque<>();
        responses.add(new AssistantResponse("", List.of(call)));
        responses.add(new AssistantResponse("done", List.of()));

        LlmClient fakeClient = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                AssistantResponse response = responses.removeFirst();
                if (!response.getContent().isBlank()) {
                    streamListener.onTextDelta(response.getContent());
                }
                return response;
            }
        };

        AgentTool echoTool = new AgentTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "echo text";
            }

            @Override
            public JsonNode parametersSchema() {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("type", "object");
                return node;
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                return ToolExecutionResult.text("pong:" + parameters.path("text").asText());
            }
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(echoTool))
                .build(),
            fakeClient
        );
        List<AgentEventType> events = new ArrayList<>();
        runtime.subscribe(event -> events.add(event.getType()));

        runtime.prompt("run").join();
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();

        assertEquals(4, snapshot.getMessages().size());
        assertEquals(AgentMessage.ROLE_USER, snapshot.getMessages().get(0).getRole());
        assertEquals(AgentMessage.ROLE_ASSISTANT, snapshot.getMessages().get(1).getRole());
        assertEquals(AgentMessage.ROLE_TOOL_RESULT, snapshot.getMessages().get(2).getRole());
        assertEquals("pong:ping", snapshot.getMessages().get(2).getContent());
        assertEquals(AgentMessage.ROLE_ASSISTANT, snapshot.getMessages().get(3).getRole());
        assertTrue(events.contains(AgentEventType.TOOL_EXECUTION_START));
        assertTrue(events.contains(AgentEventType.TOOL_EXECUTION_END));

        runtime.close();
    }
}
