package com.agent1.javaagent.config;

import java.util.Properties;

/** 从环境变量（及可选 Properties 覆盖）加载 {@link AgentRuntimeConfig}。 */
public final class EnvAgentRuntimeConfigLoader {

    private EnvAgentRuntimeConfigLoader() {
    }

    public static AgentRuntimeConfig load() {
        return load(null);
    }

    public static AgentRuntimeConfig load(Properties overrides) {
        return load(overrides, System::getenv);
    }

    /** 测试注入空 env 隔离宿主环境变量（如开发者 shell 常驻 TAVILY_API_KEY）。 */
    static AgentRuntimeConfig load(Properties overrides, java.util.function.UnaryOperator<String> envLookup) {
        return build(overrides, envLookup);
    }

    private static AgentRuntimeConfig build(Properties overrides, java.util.function.UnaryOperator<String> envLookup) {
        AgentRuntimeConfig.Builder b = AgentRuntimeConfig.builder();
        b.apiKey(firstNonBlank(
            prop(overrides, "apiKey"),
            env(envLookup, "QWEN_API_KEY"),
            env(envLookup, "DASHSCOPE_API_KEY"),
            env(envLookup, "OPENAI_API_KEY"),
            env(envLookup, "ALIBABA_API_KEY")));
        b.baseUrl(firstNonBlank(
            prop(overrides, "baseUrl"),
            env(envLookup, "QWEN_BASE_URL"),
            env(envLookup, "ALIBABA_BASE_URL"),
            env(envLookup, "OPENAI_BASE_URL")));
        b.model(firstNonBlank(
            prop(overrides, "model"),
            env(envLookup, "QWEN_MODEL"),
            env(envLookup, "OPENAI_MODEL")));
        b.maxContextTurns(parseIntOrDefault(
            firstNonBlank(prop(overrides, "maxContextTurns"), env(envLookup, "AGENT1_MAX_CONTEXT_TURNS")),
            AgentRuntimeDefaults.DEFAULT_MAX_CONTEXT_TURNS));
        b.maxContextMessages(parseIntOrZero(
            firstNonBlank(prop(overrides, "maxContextMessages"), env(envLookup, "AGENT1_MAX_CONTEXT_MESSAGES"))));
        b.maxTurnsPerRun(parseIntOrZero(
            firstNonBlank(prop(overrides, "maxTurnsPerRun"), env(envLookup, "AGENT1_MAX_TURNS_PER_RUN"))));
        b.maxToolCallsPerRun(parseIntOrZero(
            firstNonBlank(prop(overrides, "maxToolCallsPerRun"), env(envLookup, "AGENT1_MAX_TOOL_CALLS_PER_RUN"))));
        b.webSearchApiKey(firstNonBlank(
            prop(overrides, "webSearchApiKey"),
            env(envLookup, "TAVILY_API_KEY")));
        b.webSearchBaseUrl(firstNonBlank(
            prop(overrides, "webSearchBaseUrl"),
            env(envLookup, "TAVILY_BASE_URL")));
        return b.build();
    }

    private static String prop(Properties p, String key) {
        if (p == null) {
            return null;
        }
        String v = p.getProperty(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String env(java.util.function.UnaryOperator<String> envLookup, String name) {
        String v = envLookup.apply(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static int parseIntOrDefault(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseIntOrZero(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
