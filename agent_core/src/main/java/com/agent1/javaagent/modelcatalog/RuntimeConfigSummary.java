package com.agent1.javaagent.modelcatalog;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.config.AgentRuntimeDefaults;
import java.util.Objects;
import java.util.Optional;

/** 供界面展示的当前运行时配置（不含密钥明文）。 */
public final class RuntimeConfigSummary {

    private final String modelId;
    private final String baseUrl;
    private final boolean apiKeyConfigured;
    private final String configurationError;
    private final int maxContextTurns;
    private final int maxContextMessages;
    private final int maxTurnsPerRun;
    private final int maxToolCallsPerRun;
    private final Optional<QwenModelInfo> catalogMatch;

    private RuntimeConfigSummary(
        String modelId,
        String baseUrl,
        boolean apiKeyConfigured,
        String configurationError,
        int maxContextTurns,
        int maxContextMessages,
        int maxTurnsPerRun,
        int maxToolCallsPerRun,
        Optional<QwenModelInfo> catalogMatch
    ) {
        this.modelId = modelId;
        this.baseUrl = baseUrl;
        this.apiKeyConfigured = apiKeyConfigured;
        this.configurationError = configurationError;
        this.maxContextTurns = maxContextTurns;
        this.maxContextMessages = maxContextMessages;
        this.maxTurnsPerRun = maxTurnsPerRun;
        this.maxToolCallsPerRun = maxToolCallsPerRun;
        this.catalogMatch = catalogMatch;
    }

    public static RuntimeConfigSummary from(AgentRuntimeConfig config) {
        Objects.requireNonNull(config, "config");
        String model = config.getModel();
        if (model == null || model.isBlank()) {
            model = AgentRuntimeDefaults.DEFAULT_MODEL;
        }
        return new RuntimeConfigSummary(
            model,
            config.getBaseUrl(),
            config.isModelConfigured(),
            config.configurationError(),
            config.getMaxContextTurns(),
            config.getMaxContextMessages(),
            config.getMaxTurnsPerRun(),
            config.getMaxToolCallsPerRun(),
            QwenModelCatalog.findByModelId(model)
        );
    }

    public String getModelId() {
        return modelId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isApiKeyConfigured() {
        return apiKeyConfigured;
    }

    public String getConfigurationError() {
        return configurationError;
    }

    public int getMaxContextTurns() {
        return maxContextTurns;
    }

    public int getMaxContextMessages() {
        return maxContextMessages;
    }

    public int getMaxTurnsPerRun() {
        return maxTurnsPerRun;
    }

    public int getMaxToolCallsPerRun() {
        return maxToolCallsPerRun;
    }

    public Optional<QwenModelInfo> getCatalogMatch() {
        return catalogMatch;
    }
}
