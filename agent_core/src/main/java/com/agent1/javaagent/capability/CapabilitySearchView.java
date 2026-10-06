package com.agent1.javaagent.capability;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.mcp.McpServerRecord;
import com.agent1.javaagent.mcp.McpServersFile;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.agent.CapabilitySearchTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 给人看的能力检索：调用 {@link CapabilitySearchTool}，参数和模型正文一致。
 * 另外标出当前平台被滤掉的条目，以及未启用、因此不会进索引的 MCP。
 */
public final class CapabilitySearchView {

    public static final int DEFAULT_LIMIT = 8;
    public static final int MAX_LIMIT = 20;

    /** 与索引 kind 枚举一致。空列表表示不按 kind 过滤。 */
    public static final List<String> KINDS = List.of(
        "caps",
        "builtin",
        "catalog_script",
        "catalog_lib",
        "native",
        "skill",
        "mcp",
        "bridge_tool",
        "agent_tool"
    );

    /** 出现在模型结果里。 */
    public static final String VISIBLE = "visible";
    /** 已写入索引，但摘要说明当前列不出或没有工具。 */
    public static final String LISTED_BUT_UNUSABLE = "listed_but_unusable";
    /** 索引里有，当前宿主平台过滤后模型搜不到。 */
    public static final String PLATFORM_HIDDEN = "platform_hidden";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CapabilitySearchView() {
    }

    public static Result search(
        Path agentRoot,
        String hostPlatform,
        Path projectRoot,
        String query,
        List<String> kinds,
        int limit
    ) {
        String platform = hostPlatform == null || hostPlatform.isBlank()
            ? "desktop"
            : hostPlatform.trim().toLowerCase(Locale.ROOT);
        List<String> normalizedKinds = normalizeKinds(kinds);
        int requested = limit > 0 ? limit : DEFAULT_LIMIT;
        int effectiveLimit = Math.min(MAX_LIMIT, Math.max(1, requested));
        ToolExecutionResult model = execute(agentRoot, platform, projectRoot, query, normalizedKinds, effectiveLimit);
        String modelText = model.getText() == null ? "" : model.getText();
        if (query == null || query.isBlank()) {
            return new Result(modelText, List.of(), List.of(), List.of(), platform, effectiveLimit);
        }

        List<HitView> visible = readHits(model.getDetails(), true);
        Set<String> visibleIds = new LinkedHashSet<>();
        for (HitView hit : visible) {
            if (!hit.id().isEmpty()) {
                visibleIds.add(hit.id());
            }
        }

        ToolExecutionResult unfiltered = execute(
            agentRoot,
            "any",
            projectRoot,
            query,
            normalizedKinds,
            MAX_LIMIT
        );
        List<HitView> hidden = new ArrayList<>();
        for (HitView hit : readHits(unfiltered.getDetails(), false)) {
            if (visibleIds.contains(hit.id()) || availableOn(hit.platforms(), platform)) {
                continue;
            }
            hidden.add(hit.withAvailability(PLATFORM_HIDDEN));
        }
        return new Result(
            modelText,
            List.copyOf(visible),
            List.copyOf(hidden),
            disabledMcp(agentRoot, query, normalizedKinds),
            platform,
            effectiveLimit
        );
    }

    private static ToolExecutionResult execute(
        Path agentRoot,
        String platform,
        Path projectRoot,
        String query,
        List<String> kinds,
        int limit
    ) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("query", query == null ? "" : query);
        if (!kinds.isEmpty()) {
            ArrayNode array = params.putArray("kinds");
            for (String kind : kinds) {
                array.add(kind);
            }
        }
        params.put("limit", limit);
        try {
            return new CapabilitySearchTool(agentRoot, platform, projectRoot, true)
                .execute("capability-view", params, new CancellationToken(), update -> {
                });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("capability_search failed: " + e.getMessage(), e);
        }
    }

    private static List<HitView> readHits(JsonNode details, boolean classifyListing) {
        if (details == null || !details.path("hits").isArray()) {
            return List.of();
        }
        List<HitView> hits = new ArrayList<>();
        for (JsonNode row : details.path("hits")) {
            String summary = row.path("summary").asText("");
            String availability = classifyListing && listingFailure(summary) ? LISTED_BUT_UNUSABLE : VISIBLE;
            hits.add(new HitView(
                row.path("id").asText(""),
                row.path("kind").asText(""),
                row.path("title").asText(""),
                summary,
                row.path("entry").asText(""),
                row.path("doc_path").asText(""),
                row.path("platforms").asText(""),
                row.path("source").asText(""),
                row.path("loaded").asBoolean(false),
                row.path("score").asDouble(0),
                availability
            ));
        }
        return hits;
    }

    static boolean listingFailure(String summary) {
        if (summary == null || summary.isBlank()) {
            return false;
        }
        return summary.contains("暂时列不出工具") || summary.contains("当前没有工具");
    }

    static boolean availableOn(String platforms, String hostPlatform) {
        if (hostPlatform == null || hostPlatform.isBlank() || "any".equals(hostPlatform)) {
            return true;
        }
        if (platforms == null || platforms.isBlank()) {
            return true;
        }
        for (String part : platforms.split(",")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty() || "any".equals(token) || token.equals(hostPlatform)) {
                return true;
            }
        }
        return false;
    }

    private static List<DisabledMcp> disabledMcp(Path agentRoot, String query, List<String> kinds) {
        if (!kinds.isEmpty() && !kinds.contains("mcp")) {
            return List.of();
        }
        List<String> terms = CapabilityIndexStore.queryTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<DisabledMcp> out = new ArrayList<>();
        for (McpServerRecord server : McpServersFile.load(agentRoot)) {
            if (server.enabled()) {
                continue;
            }
            String haystack = (server.name() + " " + server.url() + " " + server.description())
                .toLowerCase(Locale.ROOT);
            boolean allTerms = true;
            for (String term : terms) {
                if (!haystack.contains(term)) {
                    allTerms = false;
                    break;
                }
            }
            if (allTerms) {
                out.add(new DisabledMcp(server.name(), server.url(), server.description()));
            }
        }
        return List.copyOf(out);
    }

    private static List<String> normalizeKinds(List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String kind : kinds) {
            if (kind == null) {
                continue;
            }
            String normalized = kind.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                out.add(normalized);
            }
        }
        return List.copyOf(out);
    }

    public record HitView(
        String id,
        String kind,
        String title,
        String summary,
        String entry,
        String docPath,
        String platforms,
        String source,
        boolean loaded,
        double score,
        String availability
    ) {
        HitView withAvailability(String next) {
            return new HitView(id, kind, title, summary, entry, docPath, platforms, source, loaded, score, next);
        }
    }

    public record DisabledMcp(String name, String url, String description) {
    }

    public record Result(
        String modelText,
        List<HitView> visible,
        List<HitView> hiddenByPlatform,
        List<DisabledMcp> disabledMcp,
        String hostPlatform,
        int limit
    ) {
    }
}
