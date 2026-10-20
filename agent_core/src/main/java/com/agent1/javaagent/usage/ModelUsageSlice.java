package com.agent1.javaagent.usage;

/** 某一模型在 events.jsonl 里累计的用量。modelId 为空表示未能对上 run_started。 */
public record ModelUsageSlice(
    String modelId,
    long inputTokens,
    long outputTokens,
    long cachedTokens
) {
}
