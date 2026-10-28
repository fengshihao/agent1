package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.mcp.McpServerRecord;
import com.agent1.javaagent.modelcatalog.ModelProviderIds;
import com.agent1.javaagent.modelcatalog.RemoteModelOption;
import java.util.HashMap;
import java.util.Map;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.session.SessionMeta;
import com.agent1.javaagent.capability.CapabilitySearchView;
import com.agent1.javaagent.log.query.FailedToolCall;
import com.agent1.javaagent.usage.ModelUsageSlice;
import com.agent1.javaagent.usage.UsageOverview;
import com.agent1.javaagent.html.inspect.DeckHtmlDetector;
import com.agent1.javaagent.html.inspect.HtmlInspectParser;
import com.agent1.javaagent.html.inspect.InspectFeedbackComposer;
import com.agent1.javaagent.html.inspect.InspectedElement;
import com.agent1.javaagent.workspace.ArtifactLibrary;
import com.agent1.javaagent.workspace.SessionAttachmentLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
    private final Path webUiRoot;
    private final CopyOnWriteArrayList<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final Consumer<AgentEvent> eventFanOut;

    public UiHttpServer(DesktopUiGateway gateway, int port) throws IOException {
        this(gateway, port, resolveWebUiStaticRoot());
    }

    UiHttpServer(DesktopUiGateway gateway, int port, Path webUiRoot) throws IOException {
        this.gateway = gateway;
        this.port = port;
        this.webUiRoot = webUiRoot;
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
        server.createContext("/api/models", this::handleModels);
        server.createContext("/api/tools", this::handleTools);
        server.createContext("/api/diagnostics/export", this::handleDiagnosticExport);
        server.createContext("/api/mcp/servers", this::handleMcpServers);
        server.createContext("/api/usage", this::handleUsage);
        server.createContext("/api/logs/failed", this::handleLogsFailed);
        server.createContext("/api/about", this::handleAbout);
        server.createContext("/api/artifacts", this::handleArtifacts);
        server.createContext("/api/capabilities", this::handleCapabilities);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/run/abort", this::handleAbort);
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/api/html-inspect", this::handleHtmlInspect);
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
        String queryString = exchange.getRequestURI().getQuery();
        String q = "";
        List<String> kinds = List.of();
        int limit = CapabilitySearchView.DEFAULT_LIMIT;
        if (queryString != null) {
            for (String pair : queryString.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                String key = kv[0];
                String value = urlDecode(kv[1]);
                if ("q".equals(key)) {
                    q = value;
                } else if ("kinds".equals(key) && !value.isBlank()) {
                    kinds = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                } else if ("limit".equals(key)) {
                    try {
                        limit = Integer.parseInt(value.trim());
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
            }
        }
        CapabilitySearchView.Result result = gateway.searchCapabilities(q, kinds, limit);
        sendJson(exchange, 200, encodeCapabilityResult(result));
    }

    private ObjectNode encodeCapabilityResult(CapabilitySearchView.Result result) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("modelText", result.modelText());
        body.put("hostPlatform", result.hostPlatform());
        body.put("limit", result.limit());
        body.set("visible", encodeCapabilityHits(result.visible()));
        body.set("hiddenByPlatform", encodeCapabilityHits(result.hiddenByPlatform()));
        ArrayNode disabled = MAPPER.createArrayNode();
        for (CapabilitySearchView.DisabledMcp server : result.disabledMcp()) {
            ObjectNode node = disabled.addObject();
            node.put("name", server.name());
            node.put("url", server.url());
            node.put("description", server.description());
        }
        body.set("disabledMcp", disabled);
        return body;
    }

    private ArrayNode encodeCapabilityHits(List<CapabilitySearchView.HitView> hits) {
        ArrayNode array = MAPPER.createArrayNode();
        for (CapabilitySearchView.HitView hit : hits) {
            ObjectNode node = array.addObject();
            node.put("id", hit.id());
            node.put("kind", hit.kind());
            node.put("title", hit.title());
            node.put("summary", hit.summary());
            node.put("entry", hit.entry());
            node.put("docPath", hit.docPath());
            node.put("platforms", hit.platforms());
            node.put("source", hit.source());
            node.put("loaded", hit.loaded());
            node.put("score", hit.score());
            node.put("availability", hit.availability());
        }
        return array;
    }

    private void handleUsage(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        UsageOverview usage = gateway.usageOverview();
        sendJson(exchange, 200, encodeUsage(usage));
    }

    private void handleAbout(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        sendJson(exchange, 200, DesktopAboutInfo.toJson(MAPPER));
    }

    private void handleLogsFailed(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        String query = exchange.getRequestURI().getRawQuery();
        String sessionId = null;
        if (query != null) {
            for (String part : query.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && "sessionId".equals(kv[0])) {
                    sessionId = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        }
        ArrayNode items = MAPPER.createArrayNode();
        for (FailedToolCall item : gateway.listFailedToolCalls(sessionId)) {
            ObjectNode node = items.addObject();
            node.put("sessionId", item.sessionId());
            node.put("runId", item.runId());
            node.put("toolCallId", item.toolCallId());
            node.put("toolName", item.toolName());
            node.put("errorMessage", item.errorMessage());
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.set("items", items);
        sendJson(exchange, 200, body);
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, encodeConfig(gateway.configSnapshot()));
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            JsonNode req = readJson(exchange);
            String apiKey = textField(req, "apiKey");
            String baseUrl = textField(req, "baseUrl");
            String model = textField(req, "model");
            String providerId = textField(req, "providerId");
            int maxContextTurns = intField(req, "maxContextTurns");
            int maxContextMessages = intField(req, "maxContextMessages");
            int maxTurnsPerRun = intField(req, "maxTurnsPerRun");
            int maxToolCallsPerRun = intField(req, "maxToolCallsPerRun");
            String webSearchApiKey = textField(req, "webSearchApiKey");
            String webSearchBaseUrl = textField(req, "webSearchBaseUrl");
            try {
                gateway.updateConfig(
                    apiKey,
                    baseUrl,
                    model,
                    providerId,
                    maxContextTurns,
                    maxContextMessages,
                    maxTurnsPerRun,
                    maxToolCallsPerRun,
                    webSearchApiKey,
                    webSearchBaseUrl
                );
                sendJson(exchange, 200, encodeConfig(gateway.configSnapshot()));
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
            return;
        }
        sendMethodNotAllowed(exchange);
    }

    private void handleModels(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String providerId = "";
        boolean refresh = false;
        if (query != null) {
            for (String part : query.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                String key = urlDecode(kv[0]);
                String value = urlDecode(kv[1]);
                if ("provider".equals(key)) {
                    providerId = value;
                } else if ("refresh".equals(key) && "1".equals(value)) {
                    refresh = true;
                }
            }
        }
        List<RemoteModelOption> models = gateway.listModels(providerId, refresh);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("source", refresh ? "remote_or_fallback" : "fallback");
        ArrayNode arr = body.putArray("models");
        for (RemoteModelOption option : models) {
            ObjectNode node = arr.addObject();
            node.put("modelId", option.modelId());
            node.put("title", option.title());
            node.put("subtitle", option.subtitle());
        }
        sendJson(exchange, 200, body);
    }

    private void handleTools(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        ArrayNode arr = MAPPER.createArrayNode();
        for (var tool : gateway.registeredTools()) {
            ObjectNode node = arr.addObject();
            node.put("name", tool.name());
            node.put("description", tool.description());
        }
        sendJson(exchange, 200, MAPPER.createObjectNode().set("tools", arr));
    }

    private void handleDiagnosticExport(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Path zip = gateway.exportDiagnosticZip();
        byte[] bytes = Files.readAllBytes(zip);
        String fileName = zip.getFileName().toString();
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/zip");
        headers.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    private void handleMcpServers(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            ArrayNode arr = MAPPER.createArrayNode();
            for (McpServerRecord server : gateway.listMcpServers()) {
                arr.add(encodeMcpServer(server));
            }
            sendJson(exchange, 200, MAPPER.createObjectNode().set("servers", arr));
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            JsonNode req = readJson(exchange);
            JsonNode serversNode = req.path("servers");
            if (!serversNode.isArray()) {
                sendError(exchange, 400, "servers array required");
                return;
            }
            List<McpServerRecord> servers = new java.util.ArrayList<>();
            for (JsonNode node : serversNode) {
                McpServerRecord record = decodeMcpServer(node);
                if (record != null) {
                    servers.add(record);
                }
            }
            try {
                DesktopMcpService.SaveResult result = gateway.saveMcpServers(servers);
                ObjectNode body = MAPPER.createObjectNode();
                body.put("toolCount", result.toolCount());
                ArrayNode warnings = body.putArray("warnings");
                for (String warning : result.warnings()) {
                    warnings.add(warning);
                }
                sendJson(exchange, 200, body);
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
            return;
        }
        sendMethodNotAllowed(exchange);
    }

    private static ObjectNode encodeMcpServer(McpServerRecord server) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", server.name());
        node.put("url", server.url());
        node.put("enabled", server.enabled());
        node.put("description", server.description());
        node.put("toolCount", server.toolCount());
        node.put("lastListedAt", server.lastListedAt());
        ObjectNode headers = node.putObject("headers");
        for (Map.Entry<String, String> entry : server.headers().entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        return node;
    }

    private static McpServerRecord decodeMcpServer(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String name = node.path("name").asText("").trim();
        String url = node.path("url").asText("").trim();
        if (name.isEmpty() || url.isEmpty()) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        JsonNode headersNode = node.path("headers");
        if (headersNode.isObject()) {
            headersNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull()) {
                    headers.put(entry.getKey(), entry.getValue().asText(""));
                }
            });
        }
        return new McpServerRecord(
            name,
            url,
            headers,
            node.path("enabled").asBoolean(true),
            node.path("description").asText(""),
            node.path("toolCount").asInt(0),
            node.path("lastListedAt").asText("")
        );
    }

    private ObjectNode encodeConfig(UiConfigStore.UiConfigSnapshot snap) {
        AgentRuntimeConfig cfg = snap.effective();
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", cfg.getModel());
        body.put("baseUrl", cfg.getBaseUrl());
        body.put("hasApiKey", cfg.isModelConfigured());
        body.put("apiKeyHint", maskKey(cfg.getApiKey()));
        body.put("providerId", snap.providerId());
        body.put("maxContextTurns", snap.displayMaxContextTurns());
        body.put("maxContextMessages", cfg.getMaxContextMessages());
        body.put("maxTurnsPerRun", snap.displayMaxTurnsPerRun());
        body.put("maxToolCallsPerRun", snap.displayMaxToolCallsPerRun());
        body.put("hasWebSearch", cfg.isWebSearchConfigured());
        body.put("webSearchBaseUrl", cfg.getWebSearchBaseUrl());
        String err = cfg.configurationError();
        if (err != null) {
            body.put("configurationError", err);
        }
        return body;
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

        if (parts.length == 1 && "rollover".equals(sessionId) && "POST".equalsIgnoreCase(method)) {
            JsonNode req = readJsonOptional(exchange);
            boolean summarize = req == null || req.path("summarize").asBoolean(true);
            try {
                DesktopUiGateway.SessionRolloverResult result = gateway.rolloverSession(summarize);
                ObjectNode body = MAPPER.createObjectNode();
                body.set("session", sessionNode(result.session()));
                if (result.previousSessionId() != null) {
                    body.put("previousSessionId", result.previousSessionId());
                }
                if (result.summaryPath() != null) {
                    body.put("summaryPath", result.summaryPath());
                }
                sendJson(exchange, 200, body);
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
            return;
        }

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
        if (parts.length == 2 && "todos".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("sessionId", sessionId);
            ArrayNode arr = body.putArray("todos");
            for (DesktopUiGateway.SessionTodoItem item : gateway.loadSessionTodos(sessionId)) {
                ObjectNode node = arr.addObject();
                node.put("id", item.id());
                node.put("content", item.content());
                node.put("status", item.status());
            }
            sendJson(exchange, 200, body);
            return;
        }
        if (parts.length == 2 && "brief".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            String text = gateway.briefTranscript(sessionId);
            ObjectNode body = MAPPER.createObjectNode();
            body.put("sessionId", sessionId);
            body.put("text", text);
            sendJson(exchange, 200, body);
            return;
        }
        if (parts.length == 2 && "system-prompt".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            try {
                DesktopUiGateway.SystemPromptSnapshot snap = gateway.systemPromptSnapshot(sessionId);
                ObjectNode body = MAPPER.createObjectNode();
                body.put("sessionId", sessionId);
                body.put("prompt", snap.prompt() == null ? "" : snap.prompt());
                ArrayNode tools = body.putArray("tools");
                for (var tool : snap.tools()) {
                    ObjectNode node = tools.addObject();
                    node.put("name", tool.name());
                    node.put("description", tool.description());
                    node.put("parametersSchema", tool.parametersSchema());
                }
                sendJson(exchange, 200, body);
            } catch (Exception e) {
                sendError(exchange, 400, e.getMessage());
            }
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
        if (parts.length == 2 && "preview-meta".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            String relPath = queryParam(exchange.getRequestURI().getRawQuery(), "path");
            if (relPath == null || relPath.isBlank() || !isPreviewableHtml(relPath)) {
                sendError(exchange, 400, "path required (.html)");
                return;
            }
            try {
                Path file = gateway.resolveWorkspaceFile(sessionId, relPath);
                ObjectNode body = MAPPER.createObjectNode();
                body.put("sessionId", sessionId);
                body.put("path", relPath);
                body.put("isDeck", DeckHtmlDetector.scanFile(file));
                sendJson(exchange, 200, body);
            } catch (Exception e) {
                sendError(exchange, 404, e.getMessage());
            }
            return;
        }
        if (parts.length >= 3 && "preview".equals(parts[1]) && "GET".equalsIgnoreCase(method)) {
            String relPath = urlDecode(String.join("/", Arrays.copyOfRange(parts, 2, parts.length)));
            if (!isPreviewableHtml(relPath)) {
                sendError(exchange, 400, "unsupported preview type");
                return;
            }
            try {
                gateway.resolveWorkspaceFile(sessionId, relPath);
                String html = HtmlPreviewShell.render(sessionId, relPath);
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "text/html; charset=utf-8");
                headers.set("Cache-Control", "no-cache");
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (Exception e) {
                sendError(exchange, 404, e.getMessage());
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
                } else if (name.endsWith(".gif")) {
                    ct = "image/gif";
                } else if (name.endsWith(".webp")) {
                    ct = "image/webp";
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

    private void handleHtmlInspect(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!"/api/html-inspect/compose".equals(path)) {
            sendError(exchange, 404, "not found");
            return;
        }
        JsonNode req = readJson(exchange);
        String relativePath = textField(req, "relativePath");
        String problem = textField(req, "problem");
        String elementJson = textField(req, "elementJson");
        if (relativePath == null || relativePath.isBlank() || elementJson == null || elementJson.isBlank()) {
            sendError(exchange, 400, "relativePath and elementJson required");
            return;
        }
        InspectedElement element = HtmlInspectParser.parse(elementJson);
        if (element == null) {
            sendError(exchange, 400, "invalid elementJson");
            return;
        }
        String text = InspectFeedbackComposer.compose(relativePath, element, problem == null ? "" : problem);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("text", text);
        sendJson(exchange, 200, body);
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
        OutputStream out = exchange.getResponseBody(); // NOPMD(CloseResource) SseClient.closeQuietly 在 finally 关闭 out，PMD 不跨类追踪
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
        byte[] bytes = readStaticAsset(resource);
        if (bytes == null && !"index.html".equals(resource)) {
            bytes = readStaticAsset("index.html");
            resource = "index.html";
        }
        if (bytes == null) {
            bytes = FALLBACK_HTML.getBytes(StandardCharsets.UTF_8);
            resource = "index.html";
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType(resource));
        boolean devStatic = webUiRoot != null;
        String cacheControl = devStatic || resource.endsWith(".html")
            ? "no-cache"
            : "public, max-age=3600";
        headers.set("Cache-Control", cacheControl);
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

    /**
     * 开发时优先从磁盘读 {@code web-ui/}（每次请求读文件），避免改前端后必须重启 JVM / 重打 fat-jar。
     * {@code AGENT1_WEB_UI_ROOT} 显式指定目录；{@code AGENT1_WEB_UI_DEV=0} 强制只用 classpath。
     */
    static Path resolveWebUiStaticRoot() {
        if ("0".equals(System.getenv("AGENT1_WEB_UI_DEV"))) {
            return null;
        }
        String env = System.getenv("AGENT1_WEB_UI_ROOT");
        if (env != null && !env.isBlank()) {
            Path configured = Path.of(env.trim()).toAbsolutePath().normalize();
            if (isWebUiDir(configured)) {
                return configured;
            }
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path base : List.of(cwd, cwd.getParent())) {
            if (base == null) {
                continue;
            }
            Path candidate = base.resolve("java_agent/cli/src/main/resources/web-ui").normalize();
            if (isWebUiDir(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isWebUiDir(Path dir) {
        return dir != null && Files.isRegularFile(dir.resolve("index.html"));
    }

    private byte[] readStaticAsset(String resource) throws IOException {
        if (webUiRoot != null) {
            Path file = webUiRoot.resolve(resource).normalize();
            if (!file.startsWith(webUiRoot)) {
                return null;
            }
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
            return null;
        }
        return readClasspath(STATIC_PREFIX + resource);
    }

    boolean servesWebUiFromDisk() {
        return webUiRoot != null;
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

    private static int intField(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return 0;
        }
        return node.get(field).asInt(0);
    }

    private static JsonNode readJsonOptional(HttpExchange exchange) throws IOException {
        return readJson(exchange);
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

    private static String queryParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(urlDecode(kv[0]))) {
                return urlDecode(kv[1]);
            }
        }
        return null;
    }

    private static boolean isPreviewableHtml(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".htm");
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
