package com.agent1.javaagent.usage;

import java.util.List;

/** 全部会话累计的模型用量（读 logs/events.jsonl 的 usage 行）。 */
public record UsageOverview(
    long inputTokens,
    long outputTokens,
    long cachedTokens,
    int usageEvents,
    List<ModelUsageSlice> byModel
) {
    public UsageOverview {
        byModel = byModel == null ? List.of() : List.copyOf(byModel);
    }

    public static UsageOverview empty() {
        return new UsageOverview(0, 0, 0, 0, List.of());
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    public boolean hasUsage() {
        return usageEvents > 0;
    }

    /** 缓存 token 占输入 token 的百分比；无输入时为 null。 */
    public Integer cacheHitPercent() {
        if (inputTokens <= 0) {
            return null;
        }
        return (int) Math.min(100, Math.max(0, (cachedTokens * 100.0) / inputTokens));
    }
}
