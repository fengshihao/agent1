package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.mcp.McpSchemaBrief;
import com.agent1.javaagent.skill.AgentSkill;
import com.agent1.javaagent.skill.AgentSkillLoader;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 检索 agentRoot SQLite 能力索引（FTS5）。命中 Skill 时直接附上正文。 */
public final class CapabilitySearchTool implements AgentTool {

    public static final String TOOL_NAME = "find_caps";

    private static final int LOADED_SKILL_CHARS = 8_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;
    /** Host 装配时固定（android / desktop）；索引行上的 platforms 字段仍用于过滤。 */
    private final String hostPlatform;
    private final Path projectRoot;
    /** 仅能力检索 UI 需要按 kind 筛选；模型工具默认全量检索。 */
    private final boolean applyKindFilter;
    private final AgentSkillLoader skillLoader = new AgentSkillLoader();

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
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.hostPlatform = normalizeHostPlatform(hostPlatform);
        this.projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
        this.applyKindFilter = applyKindFilter;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            编程前用来找本地已有 API 和脚本（MCP、Skill、Caps、catalog_script），避免重复实现。
            query 用空格分隔关键词，一次写全；命中越多越靠前。
            无翻页：只返回前 limit 条（默认 8，可设 1–20）。不够就改 query 或提高 limit。
            同一轮不必并行多次；不够再下一轮再搜。
            默认检索全部类型（mcp、skill、catalog_script、caps 等）。前两条 MCP 命中带参数，其余给出名称和调用示例。命中 Skill 会直接带上正文。
            结果里的调用示例可以直接写进 run_js。
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
        int limit = parameters == null ? 8 : parameters.path("limit").asInt(8);

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, query, kinds, hostPlatform, limit);
        List<AgentSkill> loadedSkills = selectSkills(query, kinds, hits, limit);

        if (hits.isEmpty() && loadedSkills.isEmpty()) {
            return ToolExecutionResult.text(
                "未找到匹配「" + query + "」的能力条目。用更短的关键词再搜一次。"
            );
        }

        Set<String> loadedNames = new LinkedHashSet<>();
        for (AgentSkill skill : loadedSkills) {
            loadedNames.add(skill.name().toLowerCase(Locale.ROOT));
        }

        StringBuilder text = new StringBuilder();
        int shown = loadedSkills.size();
        for (CapabilityIndexStore.CapabilityHit hit : hits) {
            if (loadedNames.contains(skillName(hit))) {
                continue;
            }
            shown++;
        }
        text.append("find_caps: ").append(shown).append(" 条\n");
        ArrayNode details = MAPPER.createArrayNode();
        for (AgentSkill skill : loadedSkills) {
            appendLoadedSkill(text, details, skill);
        }
        int mcpDetails = 0;
        for (CapabilityIndexStore.CapabilityHit hit : hits) {
            if (loadedNames.contains(skillName(hit))) {
                continue;
            }
            boolean withParams = "mcp".equalsIgnoreCase(hit.kind()) && mcpDetails < 2;
            if (withParams) {
                mcpDetails++;
            }
            appendIndexHit(text, details, hit, withParams);
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set("hits", details);
        return new ToolExecutionResult(text.toString().trim(), root);
    }

    private List<AgentSkill> selectSkills(
        String query,
        List<String> kinds,
        List<CapabilityIndexStore.CapabilityHit> hits,
        int limit
    ) {
        if (kinds != null && !kinds.isEmpty() && !kinds.contains("skill")) {
            return List.of();
        }
        AgentSkillLoader.SkillLoadResult loaded = skillLoader.loadMerged(
            agentRoot,
            projectRoot == null ? agentRoot : projectRoot
        );
        Map<String, AgentSkill> byName = new LinkedHashMap<>();
        for (AgentSkill skill : loaded.skills()) {
            byName.put(skill.name().toLowerCase(Locale.ROOT), skill);
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        String exact = query.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
        if (byName.containsKey(exact)) {
            names.add(exact);
        } else {
            List<String> terms = CapabilityIndexStore.queryTerms(query).stream()
                .filter(term -> term.length() >= 2)
                .toList();
            if (!terms.isEmpty()) {
                for (AgentSkill skill : byName.values()) {
                    if (names.size() >= Math.min(limit, 3)) {
                        break;
                    }
                    String haystack = (skill.name() + " " + skill.description()).toLowerCase(Locale.ROOT);
                    if (matchesSkillQuery(haystack, terms)) {
                        names.add(skill.name().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        for (CapabilityIndexStore.CapabilityHit hit : hits) {
            String fromHit = skillName(hit);
            if (!fromHit.isEmpty() && byName.containsKey(fromHit)) {
                names.add(fromHit);
            }
        }

        List<AgentSkill> selected = new ArrayList<>();
        for (String name : names) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(byName.get(name));
        }
        return selected;
    }

    /**
     * 多词 query：命中任意 {@value #MIN_SKILL_TERM_MATCHES} 个词即可（避免「绘制流程图」「图片生成」
     * 等泛词把「时序图 + mermaid/svg」挡掉）。1～2 个词仍要求全中。
     */
    static final int MIN_SKILL_TERM_MATCHES = 2;

    static boolean matchesSkillQuery(String haystack, List<String> terms) {
        if (terms.isEmpty()) {
            return false;
        }
        int required = terms.size() <= MIN_SKILL_TERM_MATCHES ? terms.size() : MIN_SKILL_TERM_MATCHES;
        int matched = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                matched++;
            }
        }
        return matched >= required;
    }

    private static String skillName(CapabilityIndexStore.CapabilityHit hit) {
        if (hit == null) {
            return "";
        }
        String entry = hit.entry() == null ? "" : hit.entry().trim();
        if (entry.regionMatches(true, 0, "skill:", 0, "skill:".length())) {
            return entry.substring("skill:".length()).trim().toLowerCase(Locale.ROOT);
        }
        if ("skill".equalsIgnoreCase(hit.kind()) && hit.id() != null) {
            int dot = hit.id().indexOf('.');
            if (dot >= 0 && dot + 1 < hit.id().length()) {
                return hit.id().substring(dot + 1).trim().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    private static void appendLoadedSkill(StringBuilder text, ArrayNode details, AgentSkill skill) {
        text.append("- [skill] ").append(skill.name()).append(" loaded\n");
        text.append("  source: ").append(skill.sourceLabel()).append('\n');
        if (!skill.description().isEmpty()) {
            text.append("  description: ").append(truncate(skill.description(), 220)).append('\n');
        }
        String body = skill.content() == null ? "" : skill.content().trim();
        if (body.length() > LOADED_SKILL_CHARS) {
            body = body.substring(0, LOADED_SKILL_CHARS) + "\n…（正文已截断）";
        }
        text.append(body.isEmpty() ? "  （正文为空）\n" : body).append('\n');

        ObjectNode row = MAPPER.createObjectNode();
        row.put("id", "skill:" + skill.name());
        row.put("kind", "skill");
        row.put("title", skill.name());
        row.put("summary", skill.description());
        row.put("entry", "skill:" + skill.name());
        row.put("doc_path", "");
        row.put("platforms", "any");
        row.put("source", skill.sourceLabel());
        row.put("loaded", true);
        details.add(row);
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
        if ("catalog_script".equalsIgnoreCase(hit.kind())) {
            text.append("  from \"文件名.js\" 是脚本库，不是 workspace 文件。不要加 ./，也不要去 workspace 里找。\n");
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
