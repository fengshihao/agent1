package com.agent1.javaagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AgentMessage {
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL_RESULT = "toolResult";

    private final String role;
    private final String content;
    private final String reasoningContent;
    private final long timestampMs;
    private final String toolCallId;
    private final boolean error;
    private final List<ToolCall> toolCalls;

    public AgentMessage(
        String role,
        String content,
        long timestampMs,
        String toolCallId,
        boolean error,
        List<ToolCall> toolCalls
    ) {
        this(role, content, "", timestampMs, toolCallId, error, toolCalls);
    }

    public AgentMessage(
        String role,
        String content,
        String reasoningContent,
        long timestampMs,
        String toolCallId,
        boolean error,
        List<ToolCall> toolCalls
    ) {
        this.role = Objects.requireNonNull(role, "role");
        this.content = content == null ? "" : content;
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
        this.timestampMs = timestampMs;
        this.toolCallId = toolCallId;
        this.error = error;
        this.toolCalls = Collections.unmodifiableList(new ArrayList<>(toolCalls == null ? List.of() : toolCalls));
    }

    public static AgentMessage user(String content) {
        return new AgentMessage(ROLE_USER, content, System.currentTimeMillis(), null, false, List.of());
    }

    public static AgentMessage assistant(String content, List<ToolCall> toolCalls) {
        return assistant(content, "", toolCalls);
    }

    public static AgentMessage assistant(String content, String reasoningContent, List<ToolCall> toolCalls) {
        return new AgentMessage(
            ROLE_ASSISTANT,
            content,
            reasoningContent,
            System.currentTimeMillis(),
            null,
            false,
            toolCalls
        );
    }

    public static AgentMessage toolResult(String toolCallId, String content, boolean isError) {
        return new AgentMessage(ROLE_TOOL_RESULT, content, System.currentTimeMillis(), toolCallId, isError, List.of());
    }

    public static AgentMessage system(String content) {
        return new AgentMessage(ROLE_SYSTEM, content, System.currentTimeMillis(), null, false, List.of());
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    /** GLM / 兼容 OpenAI 接口中的 {@code reasoning_content}，与可见 {@link #content} 分离。 */
    public String getReasoningContent() {
        return reasoningContent;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public boolean isError() {
        return error;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public AgentMessage withContent(String newContent) {
        return new AgentMessage(role, newContent, reasoningContent, timestampMs, toolCallId, error, toolCalls);
    }

    public AgentMessage withReasoningContent(String newReasoningContent) {
        return new AgentMessage(role, content, newReasoningContent, timestampMs, toolCallId, error, toolCalls);
    }
}
