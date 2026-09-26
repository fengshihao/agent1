package com.agent1.javaagent.modelcatalog;

import java.util.List;
import java.util.Objects;

/**
 * DashScope / Model Studio 模型公开规格摘要（供 CLI 与安卓展示，非实时定价）。
 * 来源：<a href="https://help.aliyun.com/en/model-studio/qwen3-7-flash">qwen3.7-flash</a> 等官方页，2026-07 快照。
 */
public final class QwenModelInfo {

    private final String modelId;
    private final String displayName;
    private final String tier;
    private final long contextWindowTokens;
    private final long maxInputTokens;
    private final long maxOutputTokens;
    private final Long maxThinkingChainTokens;
    private final List<String> inputModalities;
    private final List<String> outputModalities;
    private final boolean functionCalling;
    private final String thinkingMode;
    private final String docUrl;

    public QwenModelInfo(
        String modelId,
        String displayName,
        String tier,
        long contextWindowTokens,
        long maxInputTokens,
        long maxOutputTokens,
        Long maxThinkingChainTokens,
        List<String> inputModalities,
        List<String> outputModalities,
        boolean functionCalling,
        String thinkingMode,
        String docUrl
    ) {
        this.modelId = Objects.requireNonNull(modelId);
        this.displayName = displayName == null ? modelId : displayName;
        this.tier = tier == null ? "" : tier;
        this.contextWindowTokens = contextWindowTokens;
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.maxThinkingChainTokens = maxThinkingChainTokens;
        this.inputModalities = List.copyOf(inputModalities);
        this.outputModalities = List.copyOf(outputModalities);
        this.functionCalling = functionCalling;
        this.thinkingMode = thinkingMode == null ? "" : thinkingMode;
        this.docUrl = docUrl == null ? "" : docUrl;
    }

    public String getModelId() {
        return modelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTier() {
        return tier;
    }

    public long getContextWindowTokens() {
        return contextWindowTokens;
    }

    public long getMaxInputTokens() {
        return maxInputTokens;
    }

    public long getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public Long getMaxThinkingChainTokens() {
        return maxThinkingChainTokens;
    }

    public List<String> getInputModalities() {
        return inputModalities;
    }

    public List<String> getOutputModalities() {
        return outputModalities;
    }

    public boolean isFunctionCalling() {
        return functionCalling;
    }

    public String getThinkingMode() {
        return thinkingMode;
    }

    public String getDocUrl() {
        return docUrl;
    }
}
