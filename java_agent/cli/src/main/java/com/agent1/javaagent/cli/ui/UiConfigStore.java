package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.config.AgentRuntimeDefaults;
import com.agent1.javaagent.modelcatalog.ModelProviderIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code agentRoot/ui-config.json}：UI 保存的 Key / baseUrl / model / 限额等。
 * 与环境变量合并时，文件中非空字段覆盖同名 env 值；限额字段 {@code > 0} 才覆盖。
 */
public final class UiConfigStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FILE_NAME = "ui-config.json";

    private UiConfigStore() {
    }

    public static Path configPath(Path agentRoot) {
        return agentRoot.resolve(FILE_NAME);
    }

    /** env 为基底，ui-config 非空字段覆盖。 */
    public static AgentRuntimeConfig mergeWithEnvironment(Path agentRoot) {
        AgentRuntimeConfig env = AgentRuntimeConfig.fromEnvironment();
        Path path = configPath(agentRoot);
        if (!Files.isRegularFile(path)) {
            return env;
        }
        try {
            JsonNode root = MAPPER.readTree(path.toFile());
            return applyOverlay(env, root);
        } catch (IOException e) {
            return env;
        }
    }

    public static UiConfigSnapshot readSnapshot(Path agentRoot) {
        return snapshotFor(agentRoot, mergeWithEnvironment(agentRoot));
    }

    /** 以当前生效配置为基底，合并 ui-config.json 中的服务商与已存限额字段（用于 API 展示）。 */
    public static UiConfigSnapshot snapshotFor(Path agentRoot, AgentRuntimeConfig effective) {
        Path path = configPath(agentRoot);
        JsonNode root = null;
        if (Files.isRegularFile(path)) {
            try {
                root = MAPPER.readTree(path.toFile());
            } catch (IOException ignored) {
                root = null;
            }
        }
        String providerId = textOrEmpty(root, "providerId");
        if (providerId.isEmpty()) {
            providerId = ModelProviderIds.inferProviderId(effective.getBaseUrl());
        }
        return new UiConfigSnapshot(
            effective,
            providerId,
            storedLimit(root, "maxContextTurns"),
            storedLimit(root, "maxContextMessages"),
            storedLimit(root, "maxTurnsPerRun"),
            storedLimit(root, "maxToolCallsPerRun")
        );
    }

    public static void save(
        Path agentRoot,
        String apiKey,
        String baseUrl,
        String model,
        String providerId,
        int maxContextTurns,
        int maxContextMessages,
        int maxTurnsPerRun,
        int maxToolCallsPerRun
    ) throws IOException {
        Files.createDirectories(agentRoot);
        ObjectNode node = MAPPER.createObjectNode();
        Path path = configPath(agentRoot);
        if (Files.isRegularFile(path)) {
            node = (ObjectNode) MAPPER.readTree(path.toFile());
        }
        if (apiKey != null && !apiKey.isBlank()) {
            node.put("apiKey", apiKey.trim());
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            node.put("baseUrl", baseUrl.trim());
        }
        if (model != null && !model.isBlank()) {
            node.put("model", model.trim());
        }
        if (providerId != null && !providerId.isBlank()) {
            node.put("providerId", providerId.trim());
        }
        putLimit(node, "maxContextTurns", maxContextTurns);
        putLimit(node, "maxContextMessages", maxContextMessages);
        putLimit(node, "maxTurnsPerRun", maxTurnsPerRun);
        putLimit(node, "maxToolCallsPerRun", maxToolCallsPerRun);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), node);
    }

    /** 兼容旧调用：仅 Key / URL / model。 */
    public static void save(Path agentRoot, String apiKey, String baseUrl, String model) throws IOException {
        save(agentRoot, apiKey, baseUrl, model, null, 0, 0, 0, 0);
    }

    private static AgentRuntimeConfig applyOverlay(AgentRuntimeConfig env, JsonNode root) {
        String apiKey = textOrEmpty(root, "apiKey");
        String baseUrl = textOrEmpty(root, "baseUrl");
        String model = textOrEmpty(root, "model");
        return AgentRuntimeConfig.builder()
            .apiKey(apiKey.isEmpty() ? env.getApiKey() : apiKey)
            .baseUrl(baseUrl.isEmpty() ? env.getBaseUrl() : baseUrl)
            .model(model.isEmpty() ? env.getModel() : model)
            .maxContextTurns(mergeLimit(root, "maxContextTurns", env.getMaxContextTurns()))
            .maxContextMessages(mergeLimit(root, "maxContextMessages", env.getMaxContextMessages()))
            .maxTurnsPerRun(mergeLimit(root, "maxTurnsPerRun", env.getMaxTurnsPerRun()))
            .maxToolCallsPerRun(mergeLimit(root, "maxToolCallsPerRun", env.getMaxToolCallsPerRun()))
            .webSearchApiKey(env.getWebSearchApiKey())
            .webSearchBaseUrl(env.getWebSearchBaseUrl())
            .build();
    }

    private static int mergeLimit(JsonNode root, String field, int envValue) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return envValue;
        }
        int stored = root.get(field).asInt(0);
        return stored > 0 ? stored : envValue;
    }

    private static int storedLimit(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return 0;
        }
        return root.get(field).asInt(0);
    }

    private static void putLimit(ObjectNode node, String field, int value) {
        if (value > 0) {
            node.put(field, value);
        }
    }

    public record UiConfigSnapshot(
        AgentRuntimeConfig effective,
        String providerId,
        int storedMaxContextTurns,
        int storedMaxContextMessages,
        int storedMaxTurnsPerRun,
        int storedMaxToolCallsPerRun
    ) {
        public int displayMaxContextTurns() {
            return effective.getMaxContextTurns() > 0
                ? effective.getMaxContextTurns()
                : AgentRuntimeDefaults.DEFAULT_MAX_CONTEXT_TURNS;
        }

        public int displayMaxTurnsPerRun() {
            return effective.getMaxTurnsPerRun();
        }

        public int displayMaxToolCallsPerRun() {
            return effective.getMaxToolCallsPerRun();
        }
    }

    private static String textOrEmpty(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return "";
        }
        String value = root.get(field).asText("");
        return value == null ? "" : value.trim();
    }
}
