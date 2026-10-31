package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.mcp.McpSchemaBrief;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.OptionalCapabilityHints;
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

/** 检索 agentRoot SQLite 能力索引（FTS5）。Skill 由系统提示词目录 + {@code load_skill} 提供，不进本工具结果。 */
public final class CapabilitySearchTool implements AgentTool {

    public static final String TOOL_NAME = "find_caps";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;
    /** Host 装配时固定（android / desktop）；索引行上的 platforms 字段仍用于过滤。 */
    private final String hostPlatform;
    private final Path projectRoot;
    /** 仅能力检索 UI 需要按 kind 筛选；模型工具默认全量检索。 */
    private final boolean applyKindFilter;
    /** 未配置 Tavily Key 时不把 image_search 卡片交给模型。 */
    private final boolean webSearchConfigured;

    /** 单测等场景：不按平台过滤（等同 any）。 */
    public CapabilitySearchTool(Path agentRoot) {
        this(agentRoot, "any", null, false);
    }

    public CapabilitySearchTool(Path agentRoot, String hostPlatform) {
        this(agentRoot, hostPlatform, null, false);
    }

    public CapabilitySearchTool(Path agentRoot, String hostPlatform, Path projectRoot) {
        this(agentRoot, hostPlatform, projectRoot, false);
    }

    public CapabilitySearchTool(
        Path agentRoot,
        String hostPlatform,
        Path projectRoot,
        boolean applyKindFilter
    ) {
        this(agentRoot, hostPlatform, projectRoot, applyKindFilter, true);
    }

    public CapabilitySearchTool(
        Path agentRoot,
        String hostPlatform,
        Path projectRoot,
        boolean applyKindFilter,
        boolean webSearchConfigured
    ) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.hostPlatform = normalizeHostPlatform(hostPlatform);
        this.projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
        this.applyKindFilter = applyKindFilter;
        this.webSearchConfigured = webSearchConfigured;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            编程前用来找本地已有 API 和官方 Package（MCP、Caps、weizhi_package 等），避免重复实现。
            Skill 不在此检索：对照系统提示词里的官方 Skill 目录，用 load_skill 读剧本。
            query 用空格分隔关键词，一次写全；命中越多越靠前。
            无翻页：只返回前 limit 条（默认 8，可设 1–20）。不够就改 query 或提高 limit。
            同一轮不必并行多次；不够再下一轮再搜。
            默认检索全部类型（mcp、weizhi_package、caps、web_lib 等，不含 skill）。前两条 MCP 命中带参数，其余给出名称和调用示例。
            结果里的调用示例可以直接写进 run_js。
            流程图、思维导图、统计图和页面样式的稳定 CDN 在 web_lib 条目里。script 与 link 只用条目中的 URL，不要自造地址或猜全局变量。
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("required", MAPPER.createArrayNode().add("query"));
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "query",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put(
                    "description",
                    "本任务相关关键词，空格分隔，一次写全，例如「地理编码 地址 坐标」。"
                )
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
        List<String> kinds = applyKindFilter
            ? parseKinds(parameters == null ? null : parameters.get("kinds"))
            : List.of();
        int limit = Math.min(Math.max(parameters == null ? 8 : parameters.path("limit").asInt(8), 1), 20);

        List<CapabilityIndexStore.CapabilityHit> hits = new ArrayList<>();
        boolean removedWebSearch = false;
        boolean gaodeHit = false;
        boolean gaodeAsked = OptionalCapabilityHints.queryLooksLikeGaode(query);
        for (CapabilityIndexStore.CapabilityHit hit : filterOutSkills(
            CapabilityIndexStore.search(agentRoot, query, kinds, hostPlatform, 20)
        )) {
            if (gaodeAsked && OptionalCapabilityHints.hitIsGaode(
                hit.id(), hit.title(), hit.entry(), hit.summary()
            )) {
                gaodeHit = true;
            }
            if (!webSearchConfigured && OptionalCapabilityHints.IMAGE_SEARCH_CARD_ID.equals(hit.id())) {
                removedWebSearch = true;
                continue;
            }
            if (hits.size() >= limit) {
                break;
            }
            hits.add(hit);
        }

        if (hits.isEmpty()) {
            if (gaodeAsked && !gaodeHit) {
                return ToolExecutionResult.text(OptionalCapabilityHints.gaodeMissMessage());
            }
            if (removedWebSearch || (!webSearchConfigured && OptionalCapabilityHints.queryLooksLikeWebSearch(query))) {
                return ToolExecutionResult.text(OptionalCapabilityHints.webSearchMissMessage());
            }
            return ToolExecutionResult.text(
                "未找到匹配「" + query + "」的能力条目。用更短的关键词再搜一次。"
            );
        }

        StringBuilder text = new StringBuilder();
        text.append("find_caps: ").append(hits.size()).append(" 条\n");
        ArrayNode details = MAPPER.createArrayNode();
        int mcpDetails = 0;
        for (CapabilityIndexStore.CapabilityHit hit : hits) {
            boolean withParams = "mcp".equalsIgnoreCase(hit.kind()) && mcpDetails < 2;
            if (withParams) {
                mcpDetails++;
            }
            appendIndexHit(text, details, hit, withParams);
        }
        if (removedWebSearch) {
            text.append(OptionalCapabilityHints.webSearchFootnote()).append('\n');
        }
        if (gaodeAsked && !gaodeHit) {
            text.append(OptionalCapabilityHints.gaodeFootnote()).append('\n');
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set("hits", details);
        return new ToolExecutionResult(text.toString().trim(), root);
    }

    static List<CapabilityIndexStore.CapabilityHit> filterOutSkills(
        List<CapabilityIndexStore.CapabilityHit> hits
    ) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<CapabilityIndexStore.CapabilityHit> out = new ArrayList<>();
        for (CapabilityIndexStore.CapabilityHit hit : hits) {
            if (hit != null && !"skill".equalsIgnoreCase(hit.kind())) {
                out.add(hit);
            }
        }
        return out;
    }

    private static void appendIndexHit(
        StringBuilder text,
        ArrayNode details,
        CapabilityIndexStore.CapabilityHit hit,
        boolean withParams
    ) {
        text.append("- [").append(hit.kind()).append("] ").append(hit.title()).append('\n');
        text.append("  id: ").append(hit.id()).append('\n');
        text.append("  ").append(truncate(hit.summary(), 220)).append('\n');
        appendEntry(text, hit.entry());
        if ("weizhi_package".equalsIgnoreCase(hit.kind()) || "catalog_script".equalsIgnoreCase(hit.kind())) {
            text.append("  import from \"weizhi:<name>\" 是官方 Package，不是 workspace 文件。不要加 ./，也不要去 workspace 里找同名 .js。\n");
        }
        if (withParams) {
            String params = McpSchemaBrief.format(hit.requiresJson());
            if (!params.isEmpty()) {
                text.append("  参数:\n").append(params).append('\n');
            }
        }

        ObjectNode row = MAPPER.createObjectNode();
        row.put("id", hit.id());
        row.put("kind", hit.kind());
        row.put("title", hit.title());
        row.put("summary", hit.summary());
        row.put("entry", hit.entry());
        row.put("doc_path", hit.docPath());
        row.put("platforms", hit.platforms());
        row.put("score", hit.score());
        row.put("loaded", false);
        details.add(row);
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

    private static String normalizeHostPlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "desktop";
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private static void appendEntry(StringBuilder text, String entry) {
        if (entry == null || entry.isEmpty()) {
            return;
        }
        if (!entry.contains("\n")) {
            text.append("  entry: ").append(entry).append('\n');
            return;
        }
        text.append("  调用:\n");
        for (String line : entry.split("\n", -1)) {
            text.append("  ").append(line).append('\n');
        }
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
