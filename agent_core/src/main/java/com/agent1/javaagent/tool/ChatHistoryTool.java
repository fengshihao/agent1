package com.agent1.javaagent.tool;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 在本会话完整 transcript 上按关键词检索（见 doc/基础能力/10）。
 */
public final class ChatHistoryTool implements AgentTool {

    static final int DEFAULT_LIMIT = 20;
    static final int SNIPPET_MAX_CHARS = 300;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Supplier<List<AgentMessage>> transcriptSupplier;

    public ChatHistoryTool(Supplier<List<AgentMessage>> transcriptSupplier) {
        this.transcriptSupplier = transcriptSupplier;
    }

    @Override
    public String name() {
        return "chat_history";
    }

    @Override
    public String description() {
        return "Search the full session transcript by keyword (substring, case-insensitive). "
            + "Returns message index, role, and a short snippet.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "query",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Keyword or phrase to search for.")
        );
        properties.set(
            "limit",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Maximum number of matches to return. Default is 20.")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("query"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        String query = parameters.path("query").asText("").trim();
        if (query.isEmpty()) {
            return ToolExecutionResult.text("错误：query 不能为空");
        }
        int limit = parameters.path("limit").asInt(DEFAULT_LIMIT);
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }

        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        List<AgentMessage> transcript = transcriptSupplier.get();
        if (transcript == null) {
            transcript = List.of();
        }

        String queryLower = query.toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < transcript.size() && lines.size() < limit; i++) {
            AgentMessage message = transcript.get(i);
            String described = describe(message);
            if (!containsIgnoreCase(described, queryLower)) {
                continue;
            }
            String snippet = snippetAroundQuery(described, queryLower, SNIPPET_MAX_CHARS);
            lines.add("#" + i + " role=" + message.getRole() + " snippet=" + snippet);
        }

        if (lines.isEmpty()) {
            return ToolExecutionResult.text("未找到匹配「" + query + "」的消息。");
        }
        return ToolExecutionResult.text(String.join("\n", lines));
    }

    static String describe(AgentMessage message) {
        StringBuilder sb = new StringBuilder();
        sb.append("role=").append(message.getRole());
        if (!message.getContent().isEmpty()) {
            sb.append(" content=").append(message.getContent());
        }
        if (message.getToolCallId() != null && !message.getToolCallId().isEmpty()) {
            sb.append(" toolCallId=").append(message.getToolCallId());
        }
        for (ToolCall toolCall : message.getToolCalls()) {
            sb.append(" tool=").append(toolCall.getName());
        }
        return sb.toString();
    }

    private static boolean containsIgnoreCase(String haystack, String queryLower) {
        return haystack.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    static String snippetAroundQuery(String text, String queryLower, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int idx = text.toLowerCase(Locale.ROOT).indexOf(queryLower);
        if (idx < 0) {
            return text.substring(0, maxChars);
        }
        int half = maxChars / 2;
        int start = Math.max(0, idx - half);
        int end = Math.min(text.length(), start + maxChars);
        start = Math.max(0, end - maxChars);
        String slice = text.substring(start, end);
        if (start > 0) {
            slice = "…" + slice;
        }
        if (end < text.length()) {
            slice = slice + "…";
        }
        return slice;
    }
}
