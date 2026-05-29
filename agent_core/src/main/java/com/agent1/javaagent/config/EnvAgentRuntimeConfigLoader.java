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
        AgentRuntimeConfig.Builder b = AgentRuntimeConfig.builder();
        b.apiKey(firstNonBlank(
            prop(overrides, "apiKey"),
            env("DASHSCOPE_API_KEY"),
            env("OPENAI_API_KEY"),
            env("ALIBABA_API_KEY")));
        b.baseUrl(firstNonBlank(
            prop(overrides, "baseUrl"),
            env("ALIBABA_BASE_URL"),
            env("OPENAI_BASE_URL")));
        b.model(firstNonBlank(prop(overrides, "model"), env("OPENAI_MODEL")));
        b.maxContextTurns(parseIntOrDefault(
            firstNonBlank(prop(overrides, "maxContextTurns"), env("AGENT1_MAX_CONTEXT_TURNS")),
            AgentRuntimeDefaults.DEFAULT_MAX_CONTEXT_TURNS));
        b.maxContextMessages(parseIntOrZero(
            firstNonBlank(prop(overrides, "maxContextMessages"), env("AGENT1_MAX_CONTEXT_MESSAGES"))));
        b.maxTurnsPerRun(parseIntOrZero(
            firstNonBlank(prop(overrides, "maxTurnsPerRun"), env("AGENT1_MAX_TURNS_PER_RUN"))));
        b.maxToolCallsPerRun(parseIntOrZero(
            firstNonBlank(prop(overrides, "maxToolCallsPerRun"), env("AGENT1_MAX_TOOL_CALLS_PER_RUN"))));
        return b.build();
    }

    private static String prop(Properties p, String key) {
        if (p == null) {
            return null;
        }
        String v = p.getProperty(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String env(String name) {
        String v = System.getenv(name);
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
