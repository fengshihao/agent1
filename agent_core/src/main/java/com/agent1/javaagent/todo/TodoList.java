package com.agent1.javaagent.todo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** 会话任务清单。每次 {@code todo_write} 整表替换，不按 id 合并。 */
public final class TodoList {

    public static final int MAX_ITEMS = 8;
    public static final int MAX_CONTENT_CHARS = 80;
    public static final int MAX_ID_CHARS = 32;

    public static final String PENDING = "pending";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";

    private final List<Item> items;
    private final boolean contentTruncated;

    private TodoList(List<Item> items, boolean contentTruncated) {
        this.items = List.copyOf(items);
        this.contentTruncated = contentTruncated;
    }

    public static TodoList empty() {
        return new TodoList(List.of(), false);
    }

    public List<Item> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean contentTruncated() {
        return contentTruncated;
    }

    /** 还有未完成项时才回注到系统提示。全部完成或已清空则不再占用提示词。 */
    public boolean hasOpenItem() {
        for (Item item : items) {
            if (!COMPLETED.equals(item.status())) {
                return true;
            }
        }
        return false;
    }

    public int openCount() {
        int count = 0;
        for (Item item : items) {
            if (!COMPLETED.equals(item.status())) {
                count++;
            }
        }
        return count;
    }

    public boolean sameItems(TodoList other) {
        return other != null && items.equals(other.items);
    }

    public String renderBody() {
        StringBuilder sb = new StringBuilder();
        for (Item item : items) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("- [").append(item.status()).append("] ")
                .append(item.id()).append(' ').append(item.content());
        }
        return sb.toString();
    }

    public String promptSection() {
        if (!hasOpenItem()) {
            return "";
        }
        return """
            ## 当前任务清单
            这是本会话还没做完的清单。状态没变就不要调用 todo_write。做完一项就整表更新，同时最多一项 in_progress。
            %s
            """.formatted(renderBody()).trim();
    }

    /**
     * 解析模型传入的 {@code todos} 数组。失败时 {@code error} 非空，清单为 null，不落盘。
     */
    public static ParseResult parse(JsonNode todosNode) {
        if (todosNode == null || !todosNode.isArray()) {
            return ParseResult.error("错误：todos 必须为数组");
        }
        if (todosNode.size() > MAX_ITEMS) {
            return ParseResult.error("错误：清单最多 " + MAX_ITEMS + " 项，请把步骤并进同一段工作");
        }
        List<Item> parsed = new ArrayList<>();
        boolean truncated = false;
        int inProgress = 0;
        for (JsonNode node : todosNode) {
            if (node == null || !node.isObject()) {
                return ParseResult.error("错误：todos 的每一项必须是对象");
            }
            String id = node.path("id").asText("").trim();
            String idError = validateId(id, parsed);
            if (idError != null) {
                return ParseResult.error(idError);
            }
            String status = node.path("status").asText("").trim();
            if (!PENDING.equals(status) && !IN_PROGRESS.equals(status) && !COMPLETED.equals(status)) {
                return ParseResult.error("错误：id=" + id + " 的 status 无效，只能是 pending、in_progress、completed");
            }
            if (IN_PROGRESS.equals(status)) {
                inProgress++;
                if (inProgress > 1) {
                    return ParseResult.error("错误：同时只能有一项 in_progress");
                }
            }
            String content = normalizeContent(node.path("content").asText(""));
            if (content.isEmpty()) {
                return ParseResult.error("错误：id=" + id + " 需要非空 content");
            }
            if (content.length() > MAX_CONTENT_CHARS) {
                content = content.substring(0, MAX_CONTENT_CHARS);
                truncated = true;
            }
            parsed.add(new Item(id, content, status));
        }
        return new ParseResult(new TodoList(parsed, truncated), null);
    }

    private static String validateId(String id, List<Item> parsed) {
        if (id.isEmpty()) {
            return "错误：每一项需要非空 id";
        }
        if (id.length() > MAX_ID_CHARS || containsWhitespace(id)) {
            return "错误：id 无效: " + id;
        }
        for (Item existing : parsed) {
            if (existing.id().equals(id)) {
                return "错误：id 重复: " + id;
            }
        }
        return null;
    }

    private static boolean containsWhitespace(String id) {
        for (int i = 0; i < id.length(); i++) {
            if (Character.isWhitespace(id.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeContent(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || c == ' ') {
                pendingSpace = sb.length() > 0;
                continue;
            }
            if (pendingSpace) {
                sb.append(' ');
                pendingSpace = false;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    public record Item(String id, String content, String status) {
    }

    public record ParseResult(TodoList list, String error) {
        static ParseResult error(String error) {
            return new ParseResult(null, error);
        }

        public boolean ok() {
            return error == null && list != null;
        }
    }
}
