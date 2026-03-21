package com.agent1.javaagent.cli.tools;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Long-term memory tool backed by SQLite with a fixed schema.
 */
public final class MemoryTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final String TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS memories (
            id TEXT PRIMARY KEY,
            created_at TEXT NOT NULL,
            type TEXT NOT NULL CHECK (type IN ('data','code','txt','calen')),
            keywords TEXT NOT NULL,
            summary TEXT NOT NULL,
            content TEXT NOT NULL
        )
        """;

    private final Path dbPath;
    private final String jdbcUrl;

    public MemoryTool(Path dbPath) {
        this.dbPath = dbPath.toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + this.dbPath;
        initDatabase();
    }

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public String description() {
        return "Search/read/write long-term memory in local SQLite.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        ObjectNode actionNode = MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "memory action: search/read/write");
        actionNode.set("enum", MAPPER.createArrayNode().add("search").add("read").add("write"));
        properties.set("action", actionNode);
        properties.set("id", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "memory id; required for read, optional for write"));
        properties.set("query", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "search text for id/keywords/content"));
        properties.set("type", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "memory type: data|code|txt|calen"));
        properties.set("keywords", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "comma-separated keywords"));
        properties.set("summary", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "required short summary, less than 100 chars"));
        properties.set("content", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "memory content for write"));
        properties.set("limit", MAPPER.createObjectNode()
            .put("type", "integer")
            .put("description", "search limit (default 10, max 20)"));
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("action"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        String action = parameters.path("action").asText("").trim().toLowerCase();
        return switch (action) {
            case "search" -> search(parameters);
            case "read" -> read(parameters);
            case "write" -> write(parameters);
            default -> ToolExecutionResult.text("错误：未知 action，支持 search/read/write");
        };
    }

    private ToolExecutionResult search(JsonNode parameters) {
        String query = parameters.path("query").asText("").trim();
        String type = parameters.path("type").asText("").trim();
        int limit = parameters.path("limit").asInt(DEFAULT_LIMIT);
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        limit = Math.min(limit, MAX_LIMIT);
        QueryClause clause = buildQueryClause(query);
        String sql = "SELECT id, created_at, type, keywords, summary FROM memories"
            + " WHERE " + clause.whereSql
            + " AND (? = '' OR type = ?)"
            + " ORDER BY datetime(created_at) DESC"
            + " LIMIT ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String value : clause.args) {
                ps.setString(idx++, value);
                ps.setString(idx++, value);
                ps.setString(idx++, value);
                ps.setString(idx++, value);
            }
            ps.setString(idx++, type);
            ps.setString(idx++, type);
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> lines = new ArrayList<>();
                while (rs.next()) {
                    lines.add("- id=" + rs.getString("id")
                        + " | created_at=" + rs.getString("created_at")
                        + " | type=" + rs.getString("type")
                        + " | keywords=" + rs.getString("keywords")
                        + " | summary=" + rs.getString("summary"));
                }
                if (lines.isEmpty()) {
                    return ToolExecutionResult.text("未找到匹配记忆。");
                }
                return ToolExecutionResult.text(
                    "搜索结果（最多 " + limit + " 条，建议先根据 summary 判断再 read）:\n" + String.join("\n", lines)
                );
            }
        } catch (Exception e) {
            return ToolExecutionResult.text("错误：memory.search 执行失败: " + e.getMessage());
        }
    }

    private ToolExecutionResult read(JsonNode parameters) {
        String id = parameters.path("id").asText("").trim();
        if (id.isEmpty()) {
            return ToolExecutionResult.text("错误：read 需要 id");
        }
        String sql = "SELECT id, created_at, type, keywords, summary, content FROM memories WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ToolExecutionResult.text("未找到该记忆: " + id);
                }
                return ToolExecutionResult.text(
                    "id: " + rs.getString("id") + "\n"
                        + "created_at: " + rs.getString("created_at") + "\n"
                        + "type: " + rs.getString("type") + "\n"
                        + "keywords: " + rs.getString("keywords") + "\n"
                        + "summary: " + rs.getString("summary") + "\n"
                        + "content:\n" + rs.getString("content")
                );
            }
        } catch (Exception e) {
            return ToolExecutionResult.text("错误：memory.read 执行失败: " + e.getMessage());
        }
    }

    private ToolExecutionResult write(JsonNode parameters) {
        String type = parameters.path("type").asText("").trim();
        String keywords = parameters.path("keywords").asText("").trim();
        String summary = parameters.path("summary").asText("").trim();
        String content = parameters.path("content").asText("");
        if (!isValidType(type)) {
            return ToolExecutionResult.text("错误：write 需要合法 type（data/code/txt/calen）");
        }
        if (summary.isEmpty()) {
            return ToolExecutionResult.text("错误：write 需要 summary（<100字）");
        }
        if (summary.codePointCount(0, summary.length()) >= 100) {
            return ToolExecutionResult.text("错误：summary 需小于100字");
        }
        if (content.isBlank()) {
            return ToolExecutionResult.text("错误：write 需要 content");
        }
        String id = parameters.path("id").asText("").trim();
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        String sql = "INSERT INTO memories(id, created_at, type, keywords, summary, content) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, type);
            ps.setString(4, keywords);
            ps.setString(5, summary);
            ps.setString(6, content);
            ps.executeUpdate();
            return ToolExecutionResult.text("写入成功: id=" + id);
        } catch (Exception e) {
            return ToolExecutionResult.text("错误：memory.write 执行失败: " + e.getMessage());
        }
    }

    private QueryClause buildQueryClause(String query) {
        if (query == null || query.isBlank()) {
            return new QueryClause("1=1", List.of());
        }
        String normalized = normalizeQuery(query);
        String[] orParts = normalized.split("\\|\\|");
        List<String> orSql = new ArrayList<>();
        List<String> args = new ArrayList<>();
        for (String orPart : orParts) {
            String trimmedOr = orPart.trim();
            if (trimmedOr.isEmpty()) {
                continue;
            }
            String[] andParts = trimmedOr.split("&");
            List<String> andSql = new ArrayList<>();
            for (String raw : andParts) {
                String token = raw.trim();
                if (token.isEmpty()) {
                    continue;
                }
                boolean negate = token.startsWith("!");
                if (negate) {
                    token = token.substring(1).trim();
                }
                if (token.isEmpty()) {
                    continue;
                }
                String like = wildcardToSqlLike(token);
                String expr = "(id LIKE ? ESCAPE '\\' OR keywords LIKE ? ESCAPE '\\' OR summary LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\')";
                andSql.add(negate ? "NOT " + expr : expr);
                args.add(like);
            }
            if (!andSql.isEmpty()) {
                orSql.add("(" + String.join(" AND ", andSql) + ")");
            }
        }
        if (orSql.isEmpty()) {
            return new QueryClause("1=1", List.of());
        }
        return new QueryClause("(" + String.join(" OR ", orSql) + ")", args);
    }

    /**
     * If user does not provide boolean operators, treat whitespace as OR to avoid accidental phrase-only search.
     */
    private String normalizeQuery(String query) {
        String q = query.trim();
        if (q.contains("||") || q.contains("&") || q.contains("!")) {
            return q;
        }
        String[] parts = q.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part.trim());
            }
        }
        if (tokens.isEmpty()) {
            return "";
        }
        return String.join(" || ", tokens);
    }

    private String wildcardToSqlLike(String raw) {
        String escaped = raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
            .replace("*", "%")
            .replace("?", "_");
        boolean hasWildcard = escaped.contains("%") || escaped.contains("_");
        return hasWildcard ? escaped : "%" + escaped + "%";
    }

    private boolean isValidType(String type) {
        return "data".equals(type) || "code".equals(type) || "txt".equals(type) || "calen".equals(type);
    }

    private void initDatabase() {
        try {
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement create = conn.prepareStatement(TABLE_SQL)) {
                create.executeUpdate();
            }
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE memories ADD COLUMN summary TEXT NOT NULL DEFAULT ''");
            } catch (Exception ignored) {
                // Column already exists in upgraded databases.
            }
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement idxType = conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_memories_type ON memories(type)");
                 PreparedStatement idxCreated = conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_memories_created_at ON memories(created_at)")) {
                idxType.executeUpdate();
                idxCreated.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化 memory 数据库失败: " + e.getMessage(), e);
        }
    }

    private record QueryClause(String whereSql, List<String> args) {
    }
}
