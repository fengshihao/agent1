package com.agent1.javaagent.llm;

import com.agent1.javaagent.model.ToolCall;

public interface LlmStreamListener {
    void onTextDelta(String delta);

    default void onReasoningDelta(String delta) {
        // no-op
    }

    default void onToolCallDelta(ToolCall partialToolCall) {
        // no-op
    }

    /**
     * 流中途失败且客户端决定自动重试时，在丢弃当前流、重新开始前调用。
     * 实现应清空已累积的流式增量，否则重试后同一段内容会展示两遍。
     */
    default void onRetryAttempt() {
        // no-op
    }
}