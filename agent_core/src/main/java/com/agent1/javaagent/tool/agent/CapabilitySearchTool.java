package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 检索 agentRoot SQLite 能力索引（FTS5）；细节仍 read_agent_doc / skill。 */
public final class CapabilitySearchTool implements AgentTool {

    public static final String TOOL_NAME = "capability_search";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;

    public CapabilitySearchTool(Path agentRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            Search the local capability index (SQLite FTS) before writing JS or calling tools.
            Returns short summaries with entry hints (execute_script, android.*, doc paths, $tools.*).
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("required", MAPPER.createArrayNode().add("query"));
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("query", MAPPER.createObjectNode().put("type", "string"));
        properties.set(
            "kinds",
            MAPPER.createObjectNode()
                .put("type", "array")
                .set("items", MAPPER.createObjectNode().put("type", "string"))
        );
        properties.set(
            "platform",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "android | desktop | any")
        );
        properties.set(
            "limit",
            MAPPER.createObjectNode().put("type", "integer").put("description", "1-20, default 8")
        );
        schema.set("properties", properties);
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
        String query = parameters == null ? "" : parameters.path("query").asText("").trim();
        if (query.isEmpty()) {
            return ToolExecutionResult.text("错误：query 不能为空");
        }
        List<String> kinds = parseKinds(parameters == null ? null : parameters.get("kinds"));
        String platform = parameters == null ? "any" : parameters.path("platform").asText("any");
        int limit = parameters == null ? 8 : parameters.path("limit").asInt(8);

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, query, kinds, platform, limit);

        if (hits.isEmpty()) {
            return ToolExecutionResult.text(
                "未找到匹配「" + query + "」的能力条目。可换关键词、放宽 kinds/platform，或 read_agent_doc 读 docs/system。"
            );
        }

        StringBuilder text = new StringBuilder();
        text.append("capability_search: ").append(hits.size()).append(" 条\n");
        ArrayNode details = MAPPER.createArrayNode();
        for (CapabilityIndexStore.CapabilityHit hit : hits) {
            text.append("- [").append(hit.kind()).append("] ").append(hit.title()).append('\n');
            text.append("  id: ").append(hit.id()).append('\n');
            if (!hit.entry().isEmpty()) {
                text.append("  entry: ").append(hit.entry()).append('\n');
            }
            if (!hit.docPath().isEmpty()) {
                text.append("  doc: ").append(hit.docPath()).append('\n');
            }
            text.append("  ").append(truncate(hit.summary(), 220)).append('\n');

            ObjectNode row = MAPPER.createObjectNode();
            row.put("id", hit.id());
            row.put("kind", hit.kind());
            row.put("title", hit.title());
            row.put("summary", hit.summary());
            row.put("entry", hit.entry());
            row.put("doc_path", hit.docPath());
            row.put("platforms", hit.platforms());
            row.put("score", hit.score());
            details.add(row);
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set("hits", details);
        return new ToolExecutionResult(text.toString().trim(), root);
    }

    private static List<String> parseKinds(JsonNode kindsNode) {
        if (kindsNode == null || !kindsNode.isArray()) {
            return List.of();
        }
        List<String> kinds = new ArrayList<>();
        for (JsonNode n : kindsNode) {
            String k = n.asText("").trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty()) {
                kinds.add(k);
            }
        }
        return kinds;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }
}
