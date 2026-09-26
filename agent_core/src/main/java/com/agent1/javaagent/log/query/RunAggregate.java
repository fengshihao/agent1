package com.agent1.javaagent.log.query;

/** 按 Run 汇总（次数、耗时、token）。 */
public record RunAggregate(
    String sessionId,
    String runId,
    String parentRunId,
    String agentId,
    int eventCount,
    int failedToolCount,
    long inputTokens,
    long outputTokens,
    long durationMs,
    String terminalType
) {
    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
