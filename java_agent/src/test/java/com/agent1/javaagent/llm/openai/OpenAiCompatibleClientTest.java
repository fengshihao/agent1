package com.agent1.javaagent.llm.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void streamChat_shouldParseTextAndToolCalls() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            String sseBody = ""
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"text\\\":\\\"\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"ping\\\"}\"}}]}}]}\n\n"
                + "data: [DONE]\n\n";

            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
            );
            server.start();

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig(
                    "test-key",
                    server.url("/v1").toString(),
                    Duration.ofSeconds(5),
                    0.2
                )
            );

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
                    ObjectNode schema = MAPPER.createObjectNode();
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
                    return ToolExecutionResult.text("unused");
                }
            };

            List<String> deltas = new ArrayList<>();
            AssistantResponse response = client.streamChat(
                new ChatRequest("gpt-4o-mini", List.of(AgentMessage.user("hello"))),
                List.of(echoTool),
                deltas::add,
                new CancellationToken()
            );

            assertEquals("Hello", response.getContent());
            assertFalse(response.getToolCalls().isEmpty());
            assertEquals("call_1", response.getToolCalls().get(0).getId());
            assertEquals("echo", response.getToolCalls().get(0).getName());
            assertEquals("{\"text\":\"ping\"}", response.getToolCalls().get(0).getArgumentsJson());
            assertEquals(List.of("Hel", "lo"), deltas);
        }
    }
}
