package com.agent1.javaagent.llm.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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

    @Test
    void safeThrowableSummary_nullThrowable_returnsNull() throws Exception {
        Method m = OpenAiCompatibleClient.class.getDeclaredMethod("safeThrowableSummary", Throwable.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, new Object[] { null }));
    }

    @Test
    void describeFailure_nullThrowable_nullResponse_returnsUnknown() throws Exception {
        Method m = OpenAiCompatibleClient.class.getDeclaredMethod(
            "describeFailure",
            Throwable.class,
            okhttp3.Response.class
        );
        m.setAccessible(true);
        assertEquals("unknown (no Throwable, no Response)", m.invoke(null, null, null));
    }

    @Test
    void describeFailure_nullThrowable_withHttpResponse_usesStatusLine() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(418).setBody("teapot"));
            server.start();
            OkHttpClient http = new OkHttpClient();
            try (Response response = http
                .newCall(new Request.Builder().url(server.url("/")).build())
                .execute()) {
                Method m = OpenAiCompatibleClient.class.getDeclaredMethod(
                    "describeFailure",
                    Throwable.class,
                    okhttp3.Response.class
                );
                m.setAccessible(true);
                String reason = (String) m.invoke(null, null, response);
                assertTrue(reason.contains("418"), reason);
            }
        }
    }

    @Test
    void streamChat_httpError_surfacesSseFailedWithoutThrowingOnDispatcherThread() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(
                new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":\"nope\"}")
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

            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> client.streamChat(
                    new ChatRequest("m", List.of(AgentMessage.user("hi"))),
                    List.of(),
                    s -> {},
                    new CancellationToken()
                )
            );
            assertTrue(ex.getMessage().contains("SSE failed"), ex.getMessage());
        }
    }
}
