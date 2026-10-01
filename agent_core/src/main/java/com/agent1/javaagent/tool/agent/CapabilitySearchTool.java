package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
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

    public static final String TOOL_NAME = "capability_search";

    private static final int LOADED_SKILL_CHARS = 8_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;
    /** Host 装配时固定（android / desktop）；索引行上的 platforms 字段仍用于过滤。 */
    private final String hostPlatform;
    private final Path projectRoot;
    private final AgentSkillLoader skillLoader = new AgentSkillLoader();

    /** 单测等场景：不按平台过滤（等同 any）。 */
    public CapabilitySearchTool(Path agentRoot) {
        this(agentRoot, "any", null);
    }

    public CapabilitySearchTool(Path agentRoot, String hostPlatform) {
        this(agentRoot, hostPlatform, null);
    }

    public CapabilitySearchTool(Path agentRoot, String hostPlatform, Path projectRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.hostPlatform = normalizeHostPlatform(hostPlatform);
        this.projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            Search the local capability index (SQLite FTS).
            Skill hits include the loaded SKILL.md body. Doc hits include doc_path for read_file.
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
        int limit = parameters == null ? 8 : parameters.path("limit").asInt(8);

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, query, kinds, hostPlatform, limit);
        List<AgentSkill> loadedSkills = selectSkills(query, kinds, hits, limit);

        if (hits.isEmpty() && loadedSkills.isEmpty()) {
            return ToolExecutionResult.text(
                "未找到匹配「" + query + "」的能力条目。可换关键词、放宽 kinds，或用 read_file/grep 读 docs/system。"
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
        text.append("capability_search: ").append(shown).append(" 条\n");
        ArrayNode details = MAPPER.createArrayNode();
        for (AgentSkill skill : loadedSkills) {
            appendLoadedSkill(text, details, skill);
        }
        for (CapabilityIndexStore.CapabilityHit hit : hits) {
            if (loadedNames.contains(skillName(hit))) {
                continue;
            }
            appendIndexHit(text, details, hit);
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
                    boolean allTerms = true;
                    for (String term : terms) {
                        if (!haystack.contains(term)) {
                            allTerms = false;
                            break;
                        }
                    }
                    if (allTerms) {
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
        CapabilityIndexStore.CapabilityHit hit
    ) {
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
