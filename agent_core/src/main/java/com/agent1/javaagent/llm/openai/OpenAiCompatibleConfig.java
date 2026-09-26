package com.agent1.javaagent.llm.openai;

import java.time.Duration;
import java.util.Objects;

public final class OpenAiCompatibleConfig {
    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final Double temperature;

    public OpenAiCompatibleConfig(String apiKey, String baseUrl, Duration timeout, Double temperature) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        this.temperature = temperature;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Double getTemperature() {
        return temperature;
    }
}
