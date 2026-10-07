package com.agent1.javaagent.llm.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.llm.LlmCancelledException;
import com.agent1.javaagent.llm.LlmHttpException;
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
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

            okhttp3.mockwebserver.RecordedRequest recorded = server.takeRequest();
            JsonNode sent = MAPPER.readTree(recorded.getBody().readUtf8());
            assertTrue(sent.path("stream_options").path("include_usage").asBoolean());
        }
    }

    @Test
    void streamChat_nullIdDeltaDoesNotCollapseToolCalls() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            String sseBody = ""
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\",\"function\":{\"name\":\"read_file\",\"arguments\":\"\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"call_b\",\"function\":{\"name\":\"list_dir\",\"arguments\":\"\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":null,\"function\":{\"arguments\":\"{\\\"path\\\":\\\"a\\\"}\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":null,\"function\":{\"name\":null,\"arguments\":\"{}\"}}]}}]}\n\n"
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
            AssistantResponse response = client.streamChat(
                new ChatRequest("gpt-4o-mini", List.of(AgentMessage.user("hello"))),
                List.of(),
                delta -> { },
                new CancellationToken()
            );

            assertEquals(2, response.getToolCalls().size());
            assertEquals("call_a", response.getToolCalls().get(0).getId());
            assertEquals("read_file", response.getToolCalls().get(0).getName());
            assertEquals("{\"path\":\"a\"}", response.getToolCalls().get(0).getArgumentsJson());
            assertEquals("call_b", response.getToolCalls().get(1).getId());
            assertEquals("list_dir", response.getToolCalls().get(1).getName());
            assertEquals("{}", response.getToolCalls().get(1).getArgumentsJson());
        }
    }

    @Test
    void streamChat_parsesReasoningContent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            String sseBody = ""
                + "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"think\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"ing\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                + "data: [DONE]\n\n";
            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
            );
            server.start();

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", server.url("/v1").toString(), Duration.ofSeconds(5), null)
            );
            List<String> reasoningDeltas = new ArrayList<>();
            AssistantResponse response = client.streamChat(
                new ChatRequest("glm-5.3", List.of(AgentMessage.user("hi"))),
                List.of(),
                new com.agent1.javaagent.llm.LlmStreamListener() {
                    @Override
                    public void onTextDelta(String delta) {
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        reasoningDeltas.add(delta);
                    }
                },
                new CancellationToken()
            );
            assertEquals("thinking", response.getReasoningContent());
            assertEquals("Hi", response.getContent());
            assertEquals(List.of("think", "ing"), reasoningDeltas);
        }
    }

    @Test
    void buildPayload_codingEndpointEnablesPreservedThinking() throws Exception {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
            new OpenAiCompatibleConfig(
                "k",
                "https://open.bigmodel.cn/api/coding/paas/v4",
                Duration.ofSeconds(5),
                null
            )
        );
        ObjectNode payload = client.buildPayload(
            new ChatRequest("glm-5.3", List.of(AgentMessage.user("hi"))),
            List.of()
        );
        assertEquals("enabled", payload.path("thinking").path("type").asText());
        assertFalse(payload.path("thinking").path("clear_thinking").asBoolean(true));
    }

    @Test
    void streamChat_http403_surfacesDashScopeQuotaMessage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(
                new MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        "{\"error\":{\"message\":\"Free quota exhausted\","
                            + "\"code\":\"AllocationQuota.FreeTierOnly\"}}"
                    )
            );
            server.start();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", server.url("/v1").toString(), Duration.ofSeconds(5), null)
            );
            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> client.streamChat(
                    new ChatRequest("deepseek-v4-flash", List.of(AgentMessage.user("hi"))),
                    List.of(),
                    s -> {},
                    new CancellationToken()
                )
            );
            assertTrue(ex.getMessage().contains("AllocationQuota.FreeTierOnly"), ex.getMessage());
            assertTrue(ex.getMessage().contains("Free quota exhausted"), ex.getMessage());
        }
    }

    @Test
    void streamChat_parsesUsageAndFinishReason() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            String sseBody = ""
                + "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":3,\"prompt_tokens_details\":{\"cached_tokens\":2}}}\n\n"
                + "data: [DONE]\n\n";
            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
            );
            server.start();

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", server.url("/v1").toString(), Duration.ofSeconds(5), null)
            );
            AssistantResponse response = client.streamChat(
                new ChatRequest("m", List.of(AgentMessage.user("hi"))),
                List.of(),
                s -> {},
                new CancellationToken()
            );
            assertEquals("hi", response.getContent());
            assertEquals("stop", response.getFinishReason());
            assertEquals(11, response.getUsage().getInputTokens());
            assertEquals(3, response.getUsage().getOutputTokens());
            assertEquals(2L, response.getUsage().getCachedTokens());
        }
    }

    @Test
    void streamChat_http200GatewayError_doesNotReturnEmptyReply() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            String sseBody = "data: {\"error_code\":\"InvalidApiKey\",\"message\":\"invalid\"}\n\n";
            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
            );
            server.start();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", server.url("/v1").toString(), Duration.ofSeconds(5), null)
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
            assertTrue(ex.getMessage().contains("InvalidApiKey"), ex.getMessage());
        }
    }

    @Test
    void streamChat_retries429ThenSucceeds() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(429).setBody("slow down"));
            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n")
            );
            server.start();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", server.url("/v1").toString(), Duration.ofSeconds(5), null),
                MAPPER,
                2,
                1L
            );
            AssistantResponse response = client.streamChat(
                new ChatRequest("m", List.of(AgentMessage.user("hi"))),
                List.of(),
                s -> {},
                new CancellationToken()
            );
            assertEquals("ok", response.getContent());
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void streamChat_midStreamFailure_resetsListenerBeforeRetry() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            String firstBody = ""
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                + "data: {\"error_code\":\"Throttling.RequestsThrottled\",\"message\":\"slow down\"}\n\n";
            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(firstBody)
            );
            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n")
            );
            server.start();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", server.url("/v1").toString(), Duration.ofSeconds(5), null),
                MAPPER,
                2,
                1L
            );
            List<String> events = new ArrayList<>();
            AssistantResponse response = client.streamChat(
                new ChatRequest("m", List.of(AgentMessage.user("hi"))),
                List.of(),
                new com.agent1.javaagent.llm.LlmStreamListener() {
                    @Override
                    public void onTextDelta(String delta) {
                        events.add("delta:" + delta);
                    }

                    @Override
                    public void onRetryAttempt() {
                        events.add("reset");
                    }
                },
                new CancellationToken()
            );
            assertEquals("ok", response.getContent());
            assertEquals(List.of("delta:Hel", "reset", "delta:ok"), events);
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void streamChat_cancel_throwsLlmCancelled() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBodyDelay(2, TimeUnit.SECONDS)
                    .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n")
            );
            server.start();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", server.url("/v1").toString(), Duration.ofSeconds(5), null)
            );
            CancellationToken token = new CancellationToken();
            Thread canceller = new Thread(() -> {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                token.cancel();
            });
            canceller.start();
            assertThrows(
                LlmCancelledException.class,
                () -> client.streamChat(
                    new ChatRequest("m", List.of(AgentMessage.user("hi"))),
                    List.of(),
                    s -> {},
                    token
                )
            );
            canceller.join();
        }
    }

    @Test
    void shouldRetry_429And5xxOnly() {
        assertTrue(OpenAiCompatibleClient.shouldRetry(new LlmHttpException(429, "SSE failed: HTTP 429")));
        assertTrue(OpenAiCompatibleClient.shouldRetry(new LlmHttpException(503, "SSE failed: HTTP 503")));
        assertFalse(OpenAiCompatibleClient.shouldRetry(new LlmHttpException(401, "SSE failed: HTTP 401")));
        assertFalse(OpenAiCompatibleClient.shouldRetry(new LlmCancelledException()));
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

    @Test
    void toOpenAiMessages_toolCallIncludesFunctionObject() throws Exception {
        Method m = OpenAiCompatibleClient.class.getDeclaredMethod("toOpenAiMessages", List.class);
        m.setAccessible(true);
        List<AgentMessage> messages = List.of(
            AgentMessage.user("hi"),
            AgentMessage.assistant("", List.of(new ToolCall("call_1", "list_dir", "{}"))),
            AgentMessage.toolResult("call_1", "ok", false)
        );
        @SuppressWarnings("unchecked")
        com.fasterxml.jackson.databind.node.ArrayNode array =
            (com.fasterxml.jackson.databind.node.ArrayNode) m.invoke(new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig("k", "http://localhost/v1", Duration.ofSeconds(5), 0.2)
            ), messages);
        JsonNode assistant = array.get(1);
        assertTrue(assistant.get("tool_calls").get(0).has("function"));
        assertEquals("list_dir", assistant.get("tool_calls").get(0).get("function").get("name").asText());
    }
}
