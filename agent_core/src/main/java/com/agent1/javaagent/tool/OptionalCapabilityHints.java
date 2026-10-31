package com.agent1.javaagent.tool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 正文会要求高德 MCP、web_search、image_search，但这些能力未配置时宿主不注册。
 * 检索落空或调用失败时，用这段文案让模型跳过，而不是换参数重试。
 */
public final class OptionalCapabilityHints {

    public static final String IMAGE_SEARCH_CARD_ID = "tool.image_search";

    private static final Pattern MCP_REF = Pattern.compile("\\$mcp\\.([A-Za-z0-9_-]+)");
    private static final Pattern UNKNOWN_MCP =
        Pattern.compile("unknown MCP server:\\s*([A-Za-z0-9_-]+)");

    private OptionalCapabilityHints() {
    }

    public static boolean looksLikeUnavailable(String toolName, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains("not in js exposed whitelist")
            || text.contains("unknown MCP server")
            || text.contains("weizhi mcp client is not available")) {
            return true;
        }
        if (!isWebSearchTool(toolName) && !textMentionsMissingWebTool(text)) {
            return false;
        }
        return text.contains("Tool not found") || text.contains("unsupported:");
    }

    public static String failureAdvice(String toolName, String text) {
        boolean web = isWebSearchTool(toolName) || textMentionsMissingWebTool(text);
        String mcp = unknownMcpServer(text);
        StringBuilder advice = new StringBuilder();
        advice.append("这次失败是因为当前环境没有接上该工具，不是参数写错。不要重试，不要换关键词再 find_caps。");
        if (web) {
            advice.append("未配置 Web 搜索 Key，不要调用 image_search / web_search，也不要编造图片 URL；配图步骤跳过。");
        }
        if (mcp != null) {
            advice.append("未接入 MCP「").append(mcp).append("」，不要调用 $mcp.").append(mcp)
                .append("。坐标用大致经纬度，里程估算并在页面注明。");
        }
        if (!web && mcp == null) {
            advice.append("跳过依赖该工具的步骤，用已有信息把任务做完。");
        } else {
            advice.append("其余步骤继续做完。");
        }
        return advice.toString();
    }

    /** Skill 正文点名了未启用能力时，加在 load_skill 结果前面。没有缺口则返回空串。 */
    public static String skillPreface(String body, boolean webSearchConfigured, Set<String> enabledMcpServers) {
        if (body == null || body.isBlank()) {
            return "";
        }
        Set<String> enabled = enabledMcpServers == null ? Set.of() : enabledMcpServers;
        List<String> missingMcp = missingMcpServers(body, enabled);
        boolean missingWeb = !webSearchConfigured && bodyMentionsWebSearch(body);
        if (missingMcp.isEmpty() && !missingWeb) {
            return "";
        }
        StringBuilder note = new StringBuilder();
        note.append("[host] 本环境没接上剧本里的部分工具，按剧本调用会失败。这些步骤跳过，用已有信息把任务做完：\n");
        if (missingWeb) {
            note.append("- 未配置 Web 搜索：不要调用 image_search / web_search，也不要编造图片 URL。配图可省略。\n");
        }
        if (!missingMcp.isEmpty()) {
            note.append("- 未接入 MCP「").append(String.join("、", missingMcp))
                .append("」：不要调用对应的 $mcp。坐标用大致经纬度，里程估算并在页面注明。\n");
        }
        return note.toString();
    }

    public static boolean queryLooksLikeGaode(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("gaode")
            || query.contains("高德")
            || q.contains("maps_geo")
            || q.contains("maps_direction")
            || q.contains("maps_text_search");
    }

    public static boolean queryLooksLikeWebSearch(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("image_search")
            || q.contains("web_search")
            || q.contains("tavily")
            || query.contains("搜图");
    }

    public static String webSearchMissMessage() {
        return "未启用 image_search / web_search（未配置 Web 搜索 Key）。"
            + "不要调用，也不要换关键词再搜。跳过配图和联网检索，不要编造图片 URL，用已有信息继续。";
    }

    public static String gaodeMissMessage() {
        return "未启用 MCP「gaode」（高德）。当前环境没有这项能力。"
            + "不要换关键词再搜，不要调用 $mcp.gaode。"
            + "坐标用大致经纬度，里程估算并在页面注明，然后继续做完其余步骤。";
    }

    public static String gaodeFootnote() {
        return "未启用 MCP「gaode」（高德）。不要调用 $mcp.gaode。"
            + "坐标用大致经纬度，里程估算并在页面注明。";
    }

    public static boolean hitIsGaode(String id, String title, String entry, String summary) {
        String blob = ((id == null ? "" : id) + "\n"
            + (title == null ? "" : title) + "\n"
            + (entry == null ? "" : entry) + "\n"
            + (summary == null ? "" : summary)).toLowerCase(Locale.ROOT);
        return blob.contains("gaode") || blob.contains("高德");
    }

    public static String webSearchFootnote() {
        return "未启用 image_search / web_search（未配置 Web 搜索 Key）。"
            + "不要调用，跳过配图；不要编造图片 URL。";
    }

    private static boolean isWebSearchTool(String toolName) {
        return "image_search".equals(toolName) || "web_search".equals(toolName);
    }

    private static boolean textMentionsMissingWebTool(String text) {
        return text.contains("$tools.image_search")
            || text.contains("$tools.web_search")
            || text.contains("Tool not found: image_search")
            || text.contains("Tool not found: web_search");
    }

    private static boolean bodyMentionsWebSearch(String body) {
        return body.contains("image_search") || body.contains("web_search");
    }

    private static String unknownMcpServer(String text) {
        Matcher matcher = UNKNOWN_MCP.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private static List<String> missingMcpServers(String text, Set<String> enabled) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        Matcher matcher = MCP_REF.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!enabled.contains(name)) {
                missing.add(name);
            }
        }
        return new ArrayList<>(missing);
    }
}
