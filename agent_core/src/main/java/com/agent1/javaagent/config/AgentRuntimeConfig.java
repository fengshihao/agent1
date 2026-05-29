package com.agent1.javaagent.config;

import com.agent1.javaagent.core.AgentOptions;
import com.agent1.javaagent.llm.openai.OpenAiCompatibleConfig;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * 模型连接与 Run 限额（13-配置）。密钥仅保存在本对象内，勿写入日志或 transcript。
 */
public final class AgentRuntimeConfig {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxContextTurns;
    private final int maxContextMessages;
    private final int maxTurnsPerRun;
    private final int maxToolCallsPerRun;

    private AgentRuntimeConfig(Builder builder) {
        this.apiKey = builder.apiKey == null ? "" : builder.apiKey.trim();
        this.baseUrl = blankToDefault(builder.baseUrl, AgentRuntimeDefaults.DEFAULT_BASE_URL);
        this.model = blankToDefault(builder.model, AgentRuntimeDefaults.DEFAULT_MODEL);
        this.maxContextTurns = builder.maxContextTurns;
        this.maxContextMessages = builder.maxContextMessages;
        this.maxTurnsPerRun = positiveOrDefault(
            builder.maxTurnsPerRun, AgentRuntimeDefaults.DEFAULT_MAX_TURNS_PER_RUN);
        this.maxToolCallsPerRun = positiveOrDefault(
            builder.maxToolCallsPerRun, AgentRuntimeDefaults.DEFAULT_MAX_TOOL_CALLS_PER_RUN);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 环境变量 + 默认值（CLI / 桌面）。 */
    public static AgentRuntimeConfig fromEnvironment() {
        return EnvAgentRuntimeConfigLoader.load();
    }

    /** 属性文件条目覆盖环境变量中对应项（Android 本地配置可复用）。 */
    public static AgentRuntimeConfig fromProperties(Properties properties) {
        return EnvAgentRuntimeConfigLoader.load(properties);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    /** 保留最近 N 个用户轮次；{@code <= 0} 表示不按轮次裁剪。 */
    public int getMaxContextTurns() {
        return maxContextTurns;
    }

    /** 消息条数上限（在轮次裁剪之后）；{@code <= 0} 表示不限制。 */
    public int getMaxContextMessages() {
        return maxContextMessages;
    }

    public int getMaxTurnsPerRun() {
        return maxTurnsPerRun;
    }

    public int getMaxToolCallsPerRun() {
        return maxToolCallsPerRun;
    }

    /** 是否具备发起模型请求的条件。 */
    public boolean isModelConfigured() {
        return !apiKey.isEmpty();
    }

    /** 缺配置时的用户可读原因；已配置则返回 null。 */
    public String configurationError() {
        if (apiKey.isEmpty()) {
            return "未配置 API Key（请设置 DASHSCOPE_API_KEY 或 OPENAI_API_KEY）";
        }
        return null;
    }

    public OpenAiCompatibleConfig toOpenAiCompatibleConfig(Duration timeout, Double temperature) {
        return new OpenAiCompatibleConfig(apiKey, baseUrl, timeout, temperature);
    }

    public AgentOptions.Builder toAgentOptionsBuilder(String systemPrompt) {
        return AgentOptions.builder(model)
            .systemPrompt(systemPrompt)
            .maxContextTurns(maxContextTurns)
            .maxContextMessages(maxContextMessages)
            .maxTurnsPerRun(maxTurnsPerRun)
            .maxToolCallsPerRun(maxToolCallsPerRun);
    }

    private static String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    public static final class Builder {
        private String apiKey = "";
        private String baseUrl;
        private String model;
        private int maxContextTurns = AgentRuntimeDefaults.DEFAULT_MAX_CONTEXT_TURNS;
        private int maxContextMessages;
        private int maxTurnsPerRun;
        private int maxToolCallsPerRun;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxContextTurns(int maxContextTurns) {
            this.maxContextTurns = maxContextTurns;
            return this;
        }

        public Builder maxContextMessages(int maxContextMessages) {
            this.maxContextMessages = maxContextMessages;
            return this;
        }

        public Builder maxTurnsPerRun(int maxTurnsPerRun) {
            this.maxTurnsPerRun = maxTurnsPerRun;
            return this;
        }

        public Builder maxToolCallsPerRun(int maxToolCallsPerRun) {
            this.maxToolCallsPerRun = maxToolCallsPerRun;
            return this;
        }

        public AgentRuntimeConfig build() {
            return new AgentRuntimeConfig(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentRuntimeConfig that)) {
            return false;
        }
        return maxContextTurns == that.maxContextTurns
            && maxContextMessages == that.maxContextMessages
            && maxTurnsPerRun == that.maxTurnsPerRun
            && maxToolCallsPerRun == that.maxToolCallsPerRun
            && Objects.equals(apiKey, that.apiKey)
            && Objects.equals(baseUrl, that.baseUrl)
            && Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            apiKey, baseUrl, model, maxContextTurns, maxContextMessages, maxTurnsPerRun, maxToolCallsPerRun);
    }
}
