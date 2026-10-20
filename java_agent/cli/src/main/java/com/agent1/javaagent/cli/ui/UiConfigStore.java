package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code agentRoot/ui-config.json}：UI 保存的 Key / baseUrl / model。
 * 与环境变量合并时，文件中非空字段覆盖同名 env 值。
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
            String apiKey = textOrEmpty(root, "apiKey");
            String baseUrl = textOrEmpty(root, "baseUrl");
            String model = textOrEmpty(root, "model");
            return AgentRuntimeConfig.builder()
                .apiKey(apiKey.isEmpty() ? env.getApiKey() : apiKey)
                .baseUrl(baseUrl.isEmpty() ? env.getBaseUrl() : baseUrl)
                .model(model.isEmpty() ? env.getModel() : model)
                .maxContextTurns(env.getMaxContextTurns())
                .maxContextMessages(env.getMaxContextMessages())
                .maxTurnsPerRun(env.getMaxTurnsPerRun())
                .maxToolCallsPerRun(env.getMaxToolCallsPerRun())
                .webSearchApiKey(env.getWebSearchApiKey())
                .webSearchBaseUrl(env.getWebSearchBaseUrl())
                .build();
        } catch (IOException e) {
            return env;
        }
    }

    public static void save(Path agentRoot, String apiKey, String baseUrl, String model) throws IOException {
        Files.createDirectories(agentRoot);
        ObjectNode node = MAPPER.createObjectNode();
        if (apiKey != null && !apiKey.isBlank()) {
            node.put("apiKey", apiKey.trim());
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            node.put("baseUrl", baseUrl.trim());
        }
        if (model != null && !model.isBlank()) {
            node.put("model", model.trim());
        }
        // 保留已有 Key：若本次未传 apiKey 但文件里有，则保留
        Path path = configPath(agentRoot);
        if (Files.isRegularFile(path) && !node.has("apiKey")) {
            JsonNode existing = MAPPER.readTree(path.toFile());
            String existingKey = textOrEmpty(existing, "apiKey");
            if (!existingKey.isEmpty()) {
                node.put("apiKey", existingKey);
            }
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), node);
    }

    private static String textOrEmpty(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return "";
        }
        String value = root.get(field).asText("");
        return value == null ? "" : value.trim();
    }
}
