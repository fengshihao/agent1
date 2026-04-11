package com.agent1.javaagent.llm;

import com.agent1.javaagent.model.ToolCall;

public interface LlmStreamListener {
    void onTextDelta(String delta);

    default void onToolCallDelta(ToolCall partialToolCall) {
        // no-op
    }
}
