package com.agent1.javaagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Streamable HTTP 的 {@code tools/list}。只取名称和描述，供能力检索使用。
 * 真正调用走脚本 {@code $mcp}（Weizhi 引擎 {@code mcp.connect}）。
 */
public final class HttpMcpToolLister implements McpToolLister {

    private static final String PROTOCOL_VERSION = "2026-07-28";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient http;

    public HttpMcpToolLister() {
        this(new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build());
    }

    public HttpMcpToolLister(OkHttpClient http) {
        if (http == null) {
            throw new IllegalArgumentException("http required");
        }
        this.http = http;
    }

    @Override
    public List<McpListedTool> listTools(McpServerRecord server) throws IOException {
        JsonNode response = post(server, "tools/list", null, true);
        if (isFailure(response)) {
            if (isVersionRejected(response)) {
                response = post(server, "tools/list", null, false);
            }
        }
        if (isFailure(response) || response == null) {
            post(server, "initialize", initParams(), true);
            response = post(server, "tools/list", null, true);
            if (isFailure(response) && isVersionRejected(response)) {
                response = post(server, "tools/list", null, false);
            }
        }
        if (response == null || isFailure(response)) {
            throw new IOException(failureMessage(server, response));
        }
        return parseTools(response);
    }

    private JsonNode post(
        McpServerRecord server,
        String method,
        ObjectNode params,
        boolean sendVersionHeader
    ) throws IOException {
        ObjectNode rpc = MAPPER.createObjectNode();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", 1);
        rpc.put("method", method);
        if (params != null) {
            rpc.set("params", params);
        }
        Request.Builder builder = new Request.Builder()
            .url(server.url().trim())
            .header("Accept", "application/json, text/event-stream")
            .post(RequestBody.create(MAPPER.writeValueAsBytes(rpc), JSON));
        if (sendVersionHeader) {
            builder.header("MCP-Protocol-Version", PROTOCOL_VERSION);
        }
        for (Map.Entry<String, String> header : server.headers().entrySet()) {
            if (header.getKey() != null && !header.getKey().isBlank() && header.getValue() != null) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        try (Response response = http.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? "" : responseBody.string();
            if (body.length() > 200_000) {
                body = body.substring(0, 200_000);
            }
            String contentType = response.header("Content-Type", "");
            JsonNode parsed = contentType != null && contentType.contains("text/event-stream")
                ? parseSse(body)
                : parseJson(body);
            if (!response.isSuccessful() && (parsed == null || !parsed.has("error"))) {
                throw new IOException("HTTP " + response.code());
            }
            return parsed;
        }
    }

    private static ObjectNode initParams() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.putObject("capabilities");
        ObjectNode client = params.putObject("clientInfo");
        client.put("name", "agent1");
        client.put("version", "1.0");
        return params;
    }

    private static List<McpListedTool> parseTools(JsonNode response) {
        JsonNode tools = response.path("result").path("tools");
        List<McpListedTool> list = new ArrayList<>();
        if (!tools.isArray()) {
            return List.of();
        }
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText("").trim();
            if (name.isEmpty()) {
                continue;
            }
            JsonNode schema = tool.get("inputSchema");
            String schemaText = schema != null && schema.isObject() ? schema.toString() : "";
            list.add(new McpListedTool(name, tool.path("description").asText(""), schemaText));
        }
        return List.copyOf(list);
    }

    private static boolean isFailure(JsonNode response) {
        return response == null || response.has("error");
    }

    private static boolean isVersionRejected(JsonNode response) {
        if (response == null || !response.has("error")) {
            return false;
        }
        String message = response.path("error").path("message").asText("");
        return message.contains("Unsupported protocol version")
            || message.contains("UnsupportedProtocolVersion");
    }

    private static String failureMessage(McpServerRecord server, JsonNode response) {
        if (response != null && response.has("error")) {
            String message = response.path("error").path("message").asText("");
            if (!message.isBlank()) {
                return server.name() + ": " + clip(message);
            }
        }
        return server.name() + ": tools/list 失败";
    }

    private static JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonNode parseSse(String body) {
        if (body == null) {
            return null;
        }
        for (String line : body.split("\n")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring("data:".length()).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            JsonNode node = parseJson(payload);
            if (node != null && node.has("id") && (node.has("result") || node.has("error"))) {
                return node;
            }
        }
        return null;
    }

    private static String clip(String text) {
        String trimmed = text.trim().replace('\n', ' ');
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180);
    }
}
