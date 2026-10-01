package com.agent1.javaagent.mcp;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 读写微智同一份 {@code baseDir/mcp_servers.json}（v2）和 {@code mcp_cache/}。 */
public final class McpServersFile {

    public static final String FILE_NAME = "mcp_servers.json";
    static final String CACHE_DIR = "mcp_cache";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpServersFile() {
    }

    public static Path configFile(Path baseDir) {
        return baseDir.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    public static List<McpServerRecord> load(Path baseDir) {
        Path file = configFile(baseDir);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            return List.of();
        }
        if (root == null) {
            return List.of();
        }
        JsonNode servers;
        if (root.isArray()) {
            servers = root;
        } else if (root.isObject() && root.path("servers").isArray()) {
            servers = root.path("servers");
        } else {
            return List.of();
        }
        List<McpServerRecord> list = new ArrayList<>();
        for (JsonNode node : servers) {
            McpServerRecord record = parseRecord(node);
            if (record != null) {
                list.add(record);
            }
        }
        return List.copyOf(list);
    }

    public static void save(Path baseDir, List<McpServerRecord> servers) {
        validate(servers);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 2);
        ArrayNode array = root.putArray("servers");
        for (McpServerRecord server : servers) {
            ObjectNode node = array.addObject();
            node.put("name", server.name());
            node.put("url", server.url().trim());
            if (!server.headers().isEmpty()) {
                ObjectNode headers = node.putObject("headers");
                for (Map.Entry<String, String> header : server.headers().entrySet()) {
                    headers.put(header.getKey(), header.getValue());
                }
            }
            node.put("enabled", server.enabled());
            if (!server.description().isBlank()) {
                node.put("description", server.description());
            }
            node.put("toolCount", server.toolCount());
            if (!server.lastListedAt().isBlank()) {
                node.put("lastListedAt", server.lastListedAt());
            }
        }
        try {
            atomicWrite(configFile(baseDir), MAPPER.writeValueAsString(root));
        } catch (IOException e) {
            throw new IllegalStateException("write mcp_servers.json failed", e);
        }
    }

    public static void validate(List<McpServerRecord> servers) {
        if (servers == null) {
            throw new IllegalArgumentException("servers required");
        }
        Set<String> names = new LinkedHashSet<>();
        for (McpServerRecord server : servers) {
            String name = server.name() == null ? "" : server.name().trim();
            if (!name.matches("[a-zA-Z0-9_-]+")) {
                throw new IllegalArgumentException("名称只能包含字母、数字、下划线和连字符");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("名称重复: " + name);
            }
            String url = server.url() == null ? "" : server.url().trim();
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new IllegalArgumentException("只支持 http:// 或 https://");
            }
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("URL 无效");
            }
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                throw new IllegalArgumentException("不要把账号密码写进 URL，请填 Authorization");
            }
        }
    }

    public static boolean cacheMatches(Path baseDir, McpServerRecord server) {
        String stored = readUrlStamp(baseDir, server.name());
        return server.url().trim().equals(stored) && Files.isRegularFile(cacheJson(baseDir, server.name()));
    }

    public static List<McpListedTool> readToolCache(Path baseDir, String serverName) {
        Path file = cacheJson(baseDir, serverName);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            if (root == null || !root.isArray()) {
                return null;
            }
            List<McpListedTool> tools = new ArrayList<>();
            for (JsonNode node : root) {
                String name = text(node, "name");
                if (name.isBlank()) {
                    continue;
                }
                tools.add(new McpListedTool(name, text(node, "description")));
            }
            return List.copyOf(tools);
        } catch (IOException e) {
            return null;
        }
    }

    /** 写成微智 {@code McpServerStore.saveToolCache} 能预载的 JSON 数组。 */
    public static void writeToolCache(Path baseDir, McpServerRecord server, List<McpListedTool> tools) {
        ArrayNode array = MAPPER.createArrayNode();
        for (McpListedTool tool : tools) {
            if (tool.name().isBlank()) {
                continue;
            }
            ObjectNode node = array.addObject();
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.putObject("inputSchema");
            node.put("readOnlyHint", false);
        }
        try {
            atomicWrite(cacheJson(baseDir, server.name()), MAPPER.writeValueAsString(array));
            atomicWrite(urlStamp(baseDir, server.name()), server.url().trim());
        } catch (IOException e) {
            throw new IllegalStateException("write mcp tool cache failed", e);
        }
    }

    private static McpServerRecord parseRecord(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String name = text(node, "name");
        String url = text(node, "url");
        if (!name.matches("[a-zA-Z0-9_-]+")
            || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        boolean enabled = !node.has("enabled") || node.path("enabled").asBoolean(true);
        int toolCount = node.path("toolCount").asInt(0);
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode headerNode = node.get("headers");
        if (headerNode != null && headerNode.isObject()) {
            headerNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    headers.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        return new McpServerRecord(
            name,
            url,
            headers,
            enabled,
            text(node, "description"),
            toolCount,
            text(node, "lastListedAt")
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static Path cacheJson(Path baseDir, String serverName) {
        return cacheDir(baseDir).resolve(safeName(serverName) + ".json");
    }

    private static Path urlStamp(Path baseDir, String serverName) {
        return cacheDir(baseDir).resolve(safeName(serverName) + ".url");
    }

    private static Path cacheDir(Path baseDir) {
        return baseDir.toAbsolutePath().normalize().resolve(CACHE_DIR);
    }

    private static String readUrlStamp(Path baseDir, String serverName) {
        Path file = urlStamp(baseDir, serverName);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return PathIo.readString(file).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** API 26–33 没有 {@code Files.writeString}，desugar 也不覆盖，必须走 {@link PathIo}。 */
    private static void atomicWrite(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        PathIo.writeString(tmp, content);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
