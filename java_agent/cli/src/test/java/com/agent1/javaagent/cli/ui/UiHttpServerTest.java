package com.agent1.javaagent.cli.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.session.ProductivityAgentHost;
import com.agent1.javaagent.tool.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiHttpServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void healthSessionsTranscriptAndStreamingMessage() throws Exception {
        LlmClient fake = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                streamListener.onTextDelta("hello-ui");
                return new AssistantResponse("hello-ui", List.of());
            }

            @Override
            public void close() {
            }
        };

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").model("test-model").build();
        DesktopUiGateway gateway = new DesktopUiGateway(
            temp,
            cfg -> new ProductivityAgentHost(temp, cfg, fake),
            config
        );

        int port = freePort();
        try (gateway; UiHttpServer server = new UiHttpServer(gateway, port)) {
            server.start();
            String base = "http://127.0.0.1:" + port;

            JsonNode health = getJson(base + "/api/health");
            assertTrue(health.path("ok").asBoolean());

            JsonNode created = postJson(base + "/api/sessions", "{}");
            String sessionId = created.path("sessionId").asText();
            assertTrue(sessionId.length() > 4);

            CountDownLatch sawDelta = new CountDownLatch(1);
            AtomicBoolean sawAgentEnd = new AtomicBoolean();
            Thread sse = new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(base + "/api/events").toURL()
                        .openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3_000);
                    conn.setReadTimeout(15_000);
                    try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                    )) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) {
                                continue;
                            }
                            JsonNode event = MAPPER.readTree(line.substring(6));
                            String type = event.path("type").asText();
                            if ("MESSAGE_UPDATE".equals(type)
                                && event.path("payload").path("delta").asText().contains("hello")) {
                                sawDelta.countDown();
                            }
                            if ("AGENT_END".equals(type)) {
                                sawAgentEnd.set(true);
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // test asserts latch
                }
            }, "ui-sse-test");
            sse.setDaemon(true);
            sse.start();

            // 给 SSE 一点时间挂上
            Thread.sleep(200);

            HttpURLConnection send = (HttpURLConnection) URI.create(
                base + "/api/sessions/" + sessionId + "/messages"
            ).toURL().openConnection();
            send.setRequestMethod("POST");
            send.setDoOutput(true);
            send.setRequestProperty("Content-Type", "application/json");
            send.getOutputStream().write("{\"text\":\"ping\"}".getBytes(StandardCharsets.UTF_8));
            assertEquals(202, send.getResponseCode());
            send.disconnect();

            assertTrue(sawDelta.await(10, TimeUnit.SECONDS), "expected MESSAGE_UPDATE via SSE");

            // Run 结束后再读 transcript
            for (int i = 0; i < 50; i++) {
                if (!gateway.isRunInProgress()) {
                    break;
                }
                Thread.sleep(100);
            }

            JsonNode transcript = getJson(base + "/api/sessions/" + sessionId + "/transcript");
            assertEquals(sessionId, transcript.path("sessionId").asText());
            assertTrue(transcript.path("messages").size() >= 2);
            assertEquals("ping", transcript.path("messages").get(0).path("content").asText());
            assertEquals("hello-ui", transcript.path("messages").get(1).path("content").asText());

            JsonNode cfg = getJson(base + "/api/config");
            assertEquals("test-model", cfg.path("model").asText());
            assertTrue(cfg.path("hasApiKey").asBoolean());
        }
    }

    @Test
    void systemPromptSnapshotIncludesPromptAndTools() throws Exception {
        LlmClient fake = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                return new AssistantResponse("", List.of());
            }

            @Override
            public void close() {
            }
        };
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").model("test-model").build();
        DesktopUiGateway gateway = new DesktopUiGateway(
            temp,
            cfg -> new ProductivityAgentHost(temp, cfg, fake),
            config
        );
        int port = freePort();
        try (gateway; UiHttpServer server = new UiHttpServer(gateway, port)) {
            server.start();
            String base = "http://127.0.0.1:" + port;
            JsonNode created = postJson(base + "/api/sessions", "{}");
            String sessionId = created.path("sessionId").asText();

            JsonNode body = getJson(base + "/api/sessions/" + sessionId + "/system-prompt");
            assertEquals(sessionId, body.path("sessionId").asText());
            assertTrue(body.path("prompt").asText().length() > 20, "expected system prompt text");
            assertTrue(body.path("tools").isArray());
            assertTrue(body.path("tools").size() > 0, "expected registered tools");
            JsonNode first = body.path("tools").get(0);
            assertTrue(first.path("name").asText().length() > 0);
            assertTrue(first.has("description"));
            assertTrue(first.has("parametersSchema"));
        }
    }

    @Test
    void htmlPreviewShellAndMeta() throws Exception {
        LlmClient fake = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                return new AssistantResponse("", List.of());
            }

            @Override
            public void close() {
            }
        };
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").model("test-model").build();
        DesktopUiGateway gateway = new DesktopUiGateway(
            temp,
            cfg -> new ProductivityAgentHost(temp, cfg, fake),
            config
        );
        int port = freePort();
        try (gateway; UiHttpServer server = new UiHttpServer(gateway, port)) {
            server.start();
            String base = "http://127.0.0.1:" + port;
            JsonNode created = postJson(base + "/api/sessions", "{}");
            String sessionId = created.path("sessionId").asText();
            Path workspace = new FileSessionStore(temp).workspaceDir(sessionId);
            Files.createDirectories(workspace.resolve("out"));
            Files.writeString(
                workspace.resolve("out/deck.html"),
                "<html><section class=\"slide\"></section>",
                StandardCharsets.UTF_8
            );

            JsonNode meta = getJson(base + "/api/sessions/" + sessionId + "/preview-meta?path=out%2Fdeck.html");
            assertTrue(meta.path("isDeck").asBoolean());

            HttpURLConnection conn = (HttpURLConnection) URI.create(
                base + "/api/sessions/" + sessionId + "/preview/out/deck.html"
            ).toURL().openConnection();
            conn.setRequestMethod("GET");
            String html;
            try (var in = conn.getInputStream()) {
                html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
            assertTrue(html.contains("preview-target"));
            assertTrue(html.contains("agent1-inspect"));

            Files.writeString(
                workspace.resolve("cat.svg"),
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"8\" height=\"8\"/>",
                StandardCharsets.UTF_8
            );
            HttpURLConnection svgConn = (HttpURLConnection) URI.create(
                base + "/api/sessions/" + sessionId + "/files/cat.svg"
            ).toURL().openConnection();
            svgConn.setRequestMethod("GET");
            try (var in = svgConn.getInputStream()) {
                assertEquals("image/svg+xml", svgConn.getContentType());
                assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("<svg"));
            } finally {
                svgConn.disconnect();
            }
        }
    }

    @Test
    void resolvePortReadsFlag() {
        assertEquals(9999, ProductivityUiCommand.resolvePort(new String[]{"--port", "9999"}));
    }

    @Test
    void servesStaticFromConfiguredWebUiRoot(@TempDir Path webUi) throws Exception {
        Files.writeString(webUi.resolve("index.html"), "<html>disk-ui</html>", StandardCharsets.UTF_8);
        LlmClient fake = new LlmClient() {
            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                return new AssistantResponse("", List.of());
            }

            @Override
            public void close() {
            }
        };
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").model("test-model").build();
        DesktopUiGateway gateway = new DesktopUiGateway(
            temp,
            cfg -> new ProductivityAgentHost(temp, cfg, fake),
            config
        );
        int port = freePort();
        try (gateway; UiHttpServer server = new UiHttpServer(gateway, port, webUi)) {
            assertTrue(server.servesWebUiFromDisk());
            server.start();
            HttpURLConnection conn = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/").toURL()
                .openConnection();
            conn.setConnectTimeout(3_000);
            conn.setReadTimeout(5_000);
            String html;
            try (var in = conn.getInputStream()) {
                html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
            assertTrue(html.contains("disk-ui"));
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static JsonNode getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3_000);
        conn.setReadTimeout(5_000);
        try (var in = conn.getInputStream()) {
            return MAPPER.readTree(in);
        } finally {
            conn.disconnect();
        }
    }

    private static JsonNode postJson(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(3_000);
        conn.setReadTimeout(5_000);
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        try (var in = conn.getInputStream()) {
            return MAPPER.readTree(in);
        } finally {
            conn.disconnect();
        }
    }
}
