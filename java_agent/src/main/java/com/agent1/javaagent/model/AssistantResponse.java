package com.agent1.javaagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssistantResponse {
    private final String content;
    private final List<ToolCall> toolCalls;

    public AssistantResponse(String content, List<ToolCall> toolCalls) {
        this.content = content == null ? "" : content;
        this.toolCalls = Collections.unmodifiableList(new ArrayList<>(toolCalls == null ? List.of() : toolCalls));
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }
}
