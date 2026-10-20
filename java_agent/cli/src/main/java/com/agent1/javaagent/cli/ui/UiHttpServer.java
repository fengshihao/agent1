package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.session.SessionMeta;
import com.agent1.javaagent.capability.CapabilitySearchView;
import com.agent1.javaagent.usage.ModelUsageSlice;
import com.agent1.javaagent.usage.UsageOverview;
import com.agent1.javaagent.workspace.ArtifactLibrary;
import com.agent1.javaagent.workspace.SessionAttachmentLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** 本机 Web UI：静态资源 + REST + SSE。仅绑定 127.0.0.1。 */
public final class UiHttpServer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATIC_PREFIX = "web-ui/";

    private final DesktopUiGateway gateway;
    private final HttpServer server;
    private final int port;
    private final CopyOnWriteArrayList<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final Consumer<AgentEvent> eventFanOut;

    public UiHttpServer(DesktopUiGateway gateway, int port) throws IOException {
        this.gateway = gateway;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "desktop-ui-http");
            t.setDaemon(true);
            return t;
        }));
        this.eventFanOut = this::broadcastEvent;
        gateway.addEventListener(eventFanOut);

        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/usage", this::handleUsage);
        server.createContext("/api/artifacts", this::handleArtifacts);
        server.createContext("/api/capabilities", this::handleCapabilities);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/run/abort", this::handleAbort);
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/", this::handleStatic);
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port + "/";
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        gateway.removeEventListener(eventFanOut);
        for (SseClient client : sseClients) {
            client.closeQuietly();
        }
        sseClients.clear();
        server.stop(0);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("ok", true);
        body.put("runInProgress", gateway.isRunInProgress());
        sendJson(exchange, 200, body);
    }

    private void handleArtifacts(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        ArrayNode items = MAPPER.createArrayNode();
        for (ArtifactLibrary.ArtifactEntry entry : gateway.listArtifacts()) {
            ObjectNode node = items.addObject();
            node.put("sessionId", entry.sessionId());
            node.put("workspaceRelativePath", entry.workspaceRelativePath());
            node.put("fileName", entry.fileName());
            node.put("sizeBytes", entry.sizeBytes());
            node.put("lastModifiedMillis", entry.lastModifiedMillis());
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.set("artifacts", items);
        sendJson(exchange, 200, body);
    }

    private void handleCapabilities(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String q = "";
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "q".equals(kv[0])) {
                    q = urlDecode(kv[1]);
                }
            }
        }
        CapabilitySearchView.Result result = gateway.searchCapabilities(q, 20);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("text", result.modelText());
        sendJson(exchange, 200, body);
    }

    private void handleUsage(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        UsageOverview usage = gateway.usageOverview();
        sendJson(exchange, 200, encodeUsage(usage));
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            AgentRuntimeConfig cfg = gateway.configuration();
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", cfg.getModel());
            body.put("baseUrl", cfg.getBaseUrl());
            body.put("hasApiKey", cfg.isModelConfigured());
            body.put("apiKeyHint", maskKey(cfg.getApiKey()));
            String err = cfg.configurationError();
            if (err != null) {
                body.put("configurationError", err);
            }
            sendJson(exchange, 200, body);
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            JsonNode req = readJson(exchange);
            String apiKey = textField(req, "apiKey");
            String baseUrl = textField(req, "baseUrl");
            String model = textField(req, "model");
            try {
                AgentRuntimeConfig next = gateway.updateConfig(apiKey, baseUrl, model);
                ObjectNode body = MAPPER.createObjectNode();
                body.put("model", next.getModel());
                body.put("baseUrl", next.getBaseUrl());
                body.put("hasApiKey", next.isModelConfigured());
                body.put("apiKeyHint", maskKey(next.getApiKey()));
                sendJson(exchange, 200, body);
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
            return;
        }
        sendMethodNotAllowed(exchange);
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ("/api/sessions".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                ObjectNode body = MAPPER.createObjectNode();
                ArrayNode sessions = body.putArray("sessions");
                for (SessionMeta meta : gateway.listSessions()) {
                    sessions.add(sessionNode(meta));
                }
                String active = gateway.getActiveSessionId();
                if (active != null) {
                    body.put("activeSessionId", active);
                } else {
                    body.putNull("activeSessionId");
                }
                body.put("runInProgress", gateway.isRunInProgress());
                sendJson(exchange, 200, body);
                return;
            }
            if ("POST".equalsIgnoreCase(method)) {
                SessionMeta created = gateway.createSession();
                sendJson(exchange, 200, sessionNode(created));
                return;
            }
            sendMethodNotAllowed(exchange);
            return;
        }

        // /api/sessions/{id}/use | /transcript | /messages | DELETE /api/sessions/{id}
        String rest = path.substring("/api/sessions/".length());
        String[] parts = rest.split("/");
        if (parts.length == 0 || parts[0].isBlank()) {
            sendError(exchange, 404, "not found");
            return;
        }
        String sessionId = urlDecode(parts[0]);

        if (parts.length == 1 && "DELETE".equalsIgnoreCase(method)) {
            gateway.deleteSession(sessionId);
            sendJson(exchange, 200, MAPPER.createObjectNode().put("ok", true));
            return;
        }
        if (parts.length == 2 && "use".equals(parts[1]) && "POST".equalsIgnoreCase(method)) {
            gateway.switchSession(sessionId);
            sendJson(exchange, 200, MAPPER.createObjectNode().put("ok", true).put("activeSessionId", sessionId));
            return;
        }
        if (parts.length == 2 && "transcript".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            List<AgentMessage> messages = gateway.loadTranscript(sessionId);
            ObjectNode body = MAPPER.createObjectNode();
            body.put("sessionId", sessionId);
            ArrayNode arr = body.putArray("messages");
            for (AgentMessage message : messages) {
                arr.add(UiEventCodec.encodeMessage(message));
            }
            sendJson(exchange, 200, body);
            return;
        }
        if (parts.length == 2 && "attachments".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            ArrayNode arr = MAPPER.createArrayNode();
            for (DesktopAttachments.ListedFile file : gateway.listAttachments(sessionId)) {
                ObjectNode node = arr.addObject();
                node.put("workspaceRelativePath", file.workspaceRelativePath());
                node.put("fileName", file.fileName());
                node.put("sizeBytes", file.sizeBytes());
            }
            sendJson(exchange, 200, MAPPER.createObjectNode().set("files", arr));
            return;
        }
        if (parts.length == 2 && "attachments".equals(parts[1]) && "POST".equalsIgnoreCase(method)) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] raw = readRawBody(exchange, SessionAttachmentLimits.MAX_FILE_BYTES + 1024);
            var partsMp = UiMultipart.parse(raw, contentType);
            UiMultipart.Part filePart = partsMp.stream()
                .filter(p -> "file".equals(p.name()) && p.body().length > 0)
                .findFirst()
                .orElse(null);
            if (filePart == null) {
                sendError(exchange, 400, "multipart field 'file' required");
                return;
            }
            try {
                DesktopAttachments.ListedFile saved = gateway.importAttachment(
                    sessionId,
                    filePart.filename(),
                    filePart.body()
                );
                ObjectNode node = MAPPER.createObjectNode();
                node.put("workspaceRelativePath", saved.workspaceRelativePath());
                node.put("fileName", saved.fileName());
                node.put("sizeBytes", saved.sizeBytes());
                sendJson(exchange, 200, node);
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
            return;
        }
        if (parts.length == 3 && "attachments".equals(parts[1]) && "DELETE".equalsIgnoreCase(method)) {
            String rel = urlDecode(parts[2]);
            gateway.deleteAttachment(sessionId, rel);
            sendJson(exchange, 200, MAPPER.createObjectNode().put("ok", true));
            return;
        }
        if (parts.length == 3 && "artifacts".equals(parts[1]) && "import".equals(parts[2])
            && "POST".equalsIgnoreCase(method)) {
            JsonNode req = readJson(exchange);
            String sourceSession = textField(req, "sourceSessionId");
            String workspacePath = textField(req, "workspaceRelativePath");
            if (sourceSession == null || workspacePath == null) {
                sendError(exchange, 400, "sourceSessionId and workspaceRelativePath required");
                return;
            }
            try {
                DesktopAttachments.ListedFile saved = gateway.importArtifactIntoSession(
                    sessionId,
                    sourceSession,
                    workspacePath
                );
                ObjectNode node = MAPPER.createObjectNode();
                node.put("workspaceRelativePath", saved.workspaceRelativePath());
                sendJson(exchange, 200, node);
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
            return;
        }
        if (parts.length >= 3 && "files".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            String relPath = urlDecode(String.join("/", Arrays.copyOfRange(parts, 2, parts.length)));
            try {
                Path file = gateway.resolveWorkspaceFile(sessionId, relPath);
                byte[] bytes = Files.readAllBytes(file);
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                String ct = "application/octet-stream";
                if (name.endsWith(".html") || name.endsWith(".htm")) {
                    ct = "text/html; charset=utf-8";
                } else if (name.endsWith(".css")) {
                    ct = "text/css; charset=utf-8";
                } else if (name.endsWith(".js")) {
                    ct = "application/javascript; charset=utf-8";
                } else if (name.endsWith(".svg")) {
                    ct = "image/svg+xml";
                } else if (name.endsWith(".png")) {
                    ct = "image/png";
                } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    ct = "image/jpeg";
                }
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", ct);
                headers.set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (Exception e) {
                sendError(exchange, 404, e.getMessage());
            }
            return;
        }
        if (parts.length == 2 && "messages".equals(parts[1]) && "POST".equalsIgnoreCase(method)) {
            JsonNode req = readJson(exchange);
            String text = textField(req, "text");
            if (text == null || text.isBlank()) {
                sendError(exchange, 400, "text required");
                return;
            }
            if (!gateway.configuration().isModelConfigured()) {
                sendError(exchange, 400, "未配置 API Key，请先在设置中填写");
                return;
            }
            if (gateway.isRunInProgress()) {
                sendError(exchange, 409, "已有进行中的 Run，请先停止或等待结束");
                return;
            }
            gateway.submitUserMessage(sessionId, text.trim());
            ObjectNode body = MAPPER.createObjectNode();
            body.put("ok", true);
            body.put("sessionId", sessionId);
            sendJson(exchange, 202, body);
            return;
        }
        sendError(exchange, 404, "not found");
    }

    private void handleAbort(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        gateway.abortActiveRun();
        sendJson(exchange, 200, MAPPER.createObjectNode().put("ok", true));
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        SseClient client = new SseClient(out);
        sseClients.add(client);
        try {
            client.writeComment("connected");
            // block until client disconnects
            while (!client.closed) {
                try {
                    Thread.sleep(15_000);
                    client.writeComment("keepalive");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    break;
                }
            }
        } finally {
            sseClients.remove(client);
            client.closeQuietly();
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
            && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/")) {
            sendError(exchange, 404, "not found");
            return;
        }
        String resource = resolveStaticResource(path);
        byte[] bytes = readClasspath(STATIC_PREFIX + resource);
        if (bytes == null && !"index.html".equals(resource)) {
            bytes = readClasspath(STATIC_PREFIX + "index.html");
            resource = "index.html";
        }
        if (bytes == null) {
            bytes = FALLBACK_HTML.getBytes(StandardCharsets.UTF_8);
            resource = "index.html";
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType(resource));
        headers.set("Cache-Control", resource.endsWith(".html") ? "no-cache" : "public, max-age=3600");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void broadcastEvent(AgentEvent event) {
        // 勿在此调用 gateway.execute（list/getActive…）：事件回调已在 agent 线程上，会死锁
        String sessionId = gateway.activeRunSessionId();
        String runId = gateway.activeRunId();
        String json = UiEventCodec.encode(event, sessionId, runId).toString();
        for (SseClient client : sseClients) {
            try {
                client.writeEvent(json);
            } catch (IOException e) {
                client.closeQuietly();
                sseClients.remove(client);
            }
        }
    }

    private static String resolveStaticResource(String path) {
        String cleaned = path == null || path.isBlank() || "/".equals(path) ? "index.html" : path;
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.contains("..")) {
            return "index.html";
        }
        return cleaned;
    }

    private static byte[] readClasspath(String name) throws IOException {
        ClassLoader cl = UiHttpServer.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(name)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private static String contentType(String resource) {
        String lower = resource.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }

    private static ObjectNode encodeUsage(UsageOverview usage) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("inputTokens", usage.inputTokens());
        body.put("outputTokens", usage.outputTokens());
        body.put("cachedTokens", usage.cachedTokens());
        body.put("usageEvents", usage.usageEvents());
        body.put("totalTokens", usage.totalTokens());
        body.put("hasUsage", usage.hasUsage());
        Integer hit = usage.cacheHitPercent();
        if (hit != null) {
            body.put("cacheHitPercent", hit);
        }
        ArrayNode models = body.putArray("byModel");
        for (ModelUsageSlice slice : usage.byModel()) {
            ObjectNode node = models.addObject();
            node.put("modelId", slice.modelId());
            node.put("inputTokens", slice.inputTokens());
            node.put("outputTokens", slice.outputTokens());
            node.put("cachedTokens", slice.cachedTokens());
        }
        return body;
    }

    private static ObjectNode sessionNode(SessionMeta meta) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("sessionId", meta.getSessionId());
        node.put("title", meta.getTitle());
        node.put("createdAt", meta.getCreatedAt());
        node.put("updatedAt", meta.getUpdatedAt());
        return node;
    }

    private static String maskKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "…" + apiKey.substring(apiKey.length() - 4);
    }

    private static String textField(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    private static byte[] readRawBody(HttpExchange exchange, long maxBytes) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(chunk)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("request body too large");
                }
                buf.write(chunk, 0, read);
            }
            return buf.toByteArray();
        }
    }

    private static JsonNode readJson(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            byte[] bytes = buf.toByteArray();
            if (bytes.length == 0) {
                return MAPPER.createObjectNode();
            }
            return MAPPER.readTree(bytes);
        }
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, ObjectNode body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("error", message == null ? "error" : message);
        sendJson(exchange, status, body);
    }

    private static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "method not allowed");
    }

    private static final class SseClient {
        private final OutputStream out;
        private final Object lock = new Object();
        private volatile boolean closed;

        SseClient(OutputStream out) {
            this.out = out;
        }

        void writeEvent(String json) throws IOException {
            synchronized (lock) {
                if (closed) {
                    throw new IOException("closed");
                }
                out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        void writeComment(String comment) throws IOException {
            synchronized (lock) {
                if (closed) {
                    throw new IOException("closed");
                }
                out.write((": " + comment + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        void closeQuietly() {
            closed = true;
            try {
                out.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    /** 未打包前端时的最小占位页。 */
    private static final String FALLBACK_HTML = """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head><meta charset="utf-8"/><title>Agent1 UI</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#F4EFE6;color:#1C1914;padding:2rem;}
          a{color:#D4773B}
        </style></head>
        <body>
          <h1>Agent1 Web UI</h1>
          <p>前端资源未打包。请在仓库执行 <code>cd java_agent/web_ui && npm ci && npm run build</code>，
          或设置 Gradle 构建 web-ui 后再启动。</p>
          <p>API 探活：<a href="/api/health">/api/health</a></p>
        </body></html>
        """;
}
