package com.agent1.javaagent.log.query;

/** 失败的工具调用摘要。 */
public record FailedToolCall(
    String sessionId,
    String runId,
    String toolCallId,
    String toolName,
    String errorMessage
) {
}
