package com.agent1.javaagent.model;

import java.util.concurrent.atomic.AtomicLong;

/** 规范化 LLM / transcript 中的 tool call id，避免空值或 JSON null 退化为重复 id。 */
public final class ToolCallIds {

    private static final AtomicLong SYNTHETIC_SEQ = new AtomicLong();

    private ToolCallIds() {
    }

    /**
     * @param rawId 来自 API 或 transcript；{@code null}、空白、字面量 {@code "null"} 会替换为唯一 synthetic id
     */
    public static String normalize(String rawId) {
        if (rawId == null) {
            return synthetic();
        }
        String trimmed = rawId.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return synthetic();
        }
        return trimmed;
    }

    private static String synthetic() {
        return "tool_call_" + SYNTHETIC_SEQ.incrementAndGet() + "_" + System.nanoTime();
    }
}
