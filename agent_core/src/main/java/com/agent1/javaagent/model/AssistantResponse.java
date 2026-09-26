package com.agent1.javaagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssistantResponse {
    public static final String FINISH_LENGTH = "length";

    private final String content;
    private final String reasoningContent;
    private final List<ToolCall> toolCalls;
    private final String finishReason;
    private final ChatUsage usage;

    public AssistantResponse(String content, List<ToolCall> toolCalls) {
        this(content, "", toolCalls, null, null);
    }

    public AssistantResponse(String content, List<ToolCall> toolCalls, String finishReason, ChatUsage usage) {
        this(content, "", toolCalls, finishReason, usage);
    }

    public AssistantResponse(
        String content,
        String reasoningContent,
        List<ToolCall> toolCalls,
        String finishReason,
        ChatUsage usage
    ) {
        this.content = content == null ? "" : content;
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
        this.toolCalls = Collections.unmodifiableList(new ArrayList<>(toolCalls == null ? List.of() : toolCalls));
        this.finishReason = finishReason;
        this.usage = usage;
    }

    public String getContent() {
        return content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public ChatUsage getUsage() {
        return usage;
    }

    /** {@code finish_reason=length} 且带 tool_calls：参数多半不完整，不应执行。 */
    public boolean isTruncatedToolCall() {
        return FINISH_LENGTH.equalsIgnoreCase(finishReason) && !toolCalls.isEmpty();
    }
}
