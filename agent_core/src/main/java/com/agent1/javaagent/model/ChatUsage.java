package com.agent1.javaagent.model;

/** 一次模型调用的 token 用量（11-模型 / 04-事件日志）。 */
public final class ChatUsage {
    private final long inputTokens;
    private final long outputTokens;
    private final Long cachedTokens;

    public ChatUsage(long inputTokens, long outputTokens, Long cachedTokens) {
        this.inputTokens = Math.max(0L, inputTokens);
        this.outputTokens = Math.max(0L, outputTokens);
        this.cachedTokens = cachedTokens != null && cachedTokens < 0 ? 0L : cachedTokens;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public Long getCachedTokens() {
        return cachedTokens;
    }
}
