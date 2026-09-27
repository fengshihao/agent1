package com.agent1.javaagent.llm.scripted;

/** Mock LLM 分支：上一轮 {@code toolResult} 消息的摘要（供 Predicate 使用）。 */
public record ToolResultView(String content, boolean error) {

    public boolean contentContains(String needle) {
        if (needle == null || needle.isEmpty() || content == null) {
            return false;
        }
        return content.contains(needle);
    }

    /** 工具失败：runtime 标记、JSON {@code ok:false}、或 Agent1 工具常见「错误：」文案。 */
    public boolean looksLikeFailure() {
        if (error) {
            return true;
        }
        if (content == null || content.isBlank()) {
            return false;
        }
        if (content.contains("\"ok\":false") || content.contains("\"ok\": false")) {
            return true;
        }
        return content.startsWith("错误") || content.contains("错误：");
    }
}
