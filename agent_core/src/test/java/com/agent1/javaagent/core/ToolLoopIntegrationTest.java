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
import java.time.Duration;
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

    @Test
    void prompt_shouldTimeoutLongRunningToolAndContinue() {
        ToolCall call = new ToolCall("call_timeout", "slow_tool", "{}");
        ArrayDeque<AssistantResponse> responses = new ArrayDeque<>();
        responses.add(new AssistantResponse("", List.of(call)));
        responses.add(new AssistantResponse("handled timeout", List.of()));

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

        AgentTool slowTool = new AgentTool() {
            @Override
            public String name() {
                return "slow_tool";
            }

            @Override
            public String description() {
                return "simulate long running tool";
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
            ) throws Exception {
                Thread.sleep(1_200);
                return ToolExecutionResult.text("unexpected");
            }
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(slowTool))
                .defaultToolTimeout(Duration.ofMillis(200))
                .build(),
            fakeClient
        );

        runtime.prompt("run timeout case").join();
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();

        assertEquals(4, snapshot.getMessages().size());
        assertEquals(AgentMessage.ROLE_TOOL_RESULT, snapshot.getMessages().get(2).getRole());
        assertTrue(snapshot.getMessages().get(2).isError());
        assertTrue(snapshot.getMessages().get(2).getContent().contains("工具执行超时"));
        assertEquals("handled timeout", snapshot.getMessages().get(3).getContent());

        runtime.close();
    }

    @Test
    void prompt_shouldSkipTruncatedToolCallsAndContinue() {
        ToolCall call = new ToolCall("call_trunc", "echo", "{\"text\":\"huge");
        ArrayDeque<AssistantResponse> responses = new ArrayDeque<>();
        responses.add(new AssistantResponse("", List.of(call), AssistantResponse.FINISH_LENGTH, null));
        responses.add(new AssistantResponse("recovered", List.of()));

        int[] executed = {0};
        LlmClient fakeClient = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                return responses.removeFirst();
            }
        };

        AgentTool echoTool = new AgentTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "echo";
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
                executed[0] += 1;
                return ToolExecutionResult.text("should-not-run");
            }
        };

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model").tools(List.of(echoTool)).build(),
            fakeClient
        );
        runtime.prompt("write big").join();
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();

        assertEquals(0, executed[0]);
        assertEquals(4, snapshot.getMessages().size());
        assertEquals(AgentMessage.ROLE_TOOL_RESULT, snapshot.getMessages().get(2).getRole());
        assertTrue(snapshot.getMessages().get(2).isError());
        assertTrue(snapshot.getMessages().get(2).getContent().contains("max_tokens"));
        assertEquals("recovered", snapshot.getMessages().get(3).getContent());
        runtime.close();
    }

    @Test
    void prompt_shouldTruncateOldToolResultsInNextModelRequest() {
        String oldResult = "z".repeat(281);
        List<String> seenToolResults = new ArrayList<>();
        ArrayDeque<AssistantResponse> responses = new ArrayDeque<>();
        responses.add(new AssistantResponse("first", List.of()));
        responses.add(new AssistantResponse("second", List.of()));

        LlmClient fakeClient = (request, tools, streamListener, cancellationToken) -> {
            for (AgentMessage message : request.getMessages()) {
                if (AgentMessage.ROLE_TOOL_RESULT.equals(message.getRole())) {
                    seenToolResults.add(message.getContent());
                }
            }
            return responses.removeFirst();
        };

        AgentRuntime runtime = new AgentRuntime(AgentOptions.builder("test-model").build(), fakeClient);
        runtime.replaceMessages(List.of(
            AgentMessage.user("u1"),
            AgentMessage.assistant("", List.of(new ToolCall("c1", "read_file", "{}"))),
            AgentMessage.toolResult("c1", oldResult, false)
        ));
        runtime.prompt("u2").join();

        assertEquals(1, seenToolResults.size());
        assertTrue(seenToolResults.get(0).contains("已省略"), seenToolResults.get(0));
        runtime.close();
    }
}
