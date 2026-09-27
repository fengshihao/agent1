package com.agent1.javaagent.coach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Coach 开关与阈值：默认来自 {@code agentRoot/agent.manifest.json} 的 {@code coach} 段，
 * 环境变量可覆盖（便于测试与部署，无需改文件）。
 *
 * <ul>
 *   <li>{@code AGENT1_COACH} — {@code 0}/{@code false} 关闭；未设则读 manifest</li>
 *   <li>{@code AGENT1_COACH_LARGE_WRITE_BYTES}</li>
 *   <li>{@code AGENT1_COACH_INLINE_LONG_LINES}</li>
 *   <li>{@code AGENT1_COACH_INLINE_LONG_BYTES}</li>
 * </ul>
 */
public final class AgentCoachConfig {

    public static final int DEFAULT_LARGE_WRITE_BYTES = 65_536;
    public static final int DEFAULT_INLINE_LONG_LINES = 80;
    public static final int DEFAULT_INLINE_LONG_BYTES = 8_192;
    public static final int DEFAULT_SCRIPT_FAIL_REPEAT = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean enabled;
    private final int largeWriteBytes;
    private final int inlineLongLines;
    private final int inlineLongBytes;
    private final int scriptFailRepeat;

    public AgentCoachConfig(
        boolean enabled,
        int largeWriteBytes,
        int inlineLongLines,
        int inlineLongBytes,
        int scriptFailRepeat
    ) {
        this.enabled = enabled;
        this.largeWriteBytes = positiveOrDefault(largeWriteBytes, DEFAULT_LARGE_WRITE_BYTES);
        this.inlineLongLines = positiveOrDefault(inlineLongLines, DEFAULT_INLINE_LONG_LINES);
        this.inlineLongBytes = positiveOrDefault(inlineLongBytes, DEFAULT_INLINE_LONG_BYTES);
        this.scriptFailRepeat = positiveOrDefault(scriptFailRepeat, DEFAULT_SCRIPT_FAIL_REPEAT);
    }

    public AgentCoachConfig(boolean enabled, int largeWriteBytes, int inlineLongLines, int inlineLongBytes) {
        this(enabled, largeWriteBytes, inlineLongLines, inlineLongBytes, DEFAULT_SCRIPT_FAIL_REPEAT);
    }

    public static AgentCoachConfig defaults() {
        return new AgentCoachConfig(
            true,
            DEFAULT_LARGE_WRITE_BYTES,
            DEFAULT_INLINE_LONG_LINES,
            DEFAULT_INLINE_LONG_BYTES,
            DEFAULT_SCRIPT_FAIL_REPEAT
        );
    }

    public static AgentCoachConfig load(Path agentRoot) {
        return fromManifest(readManifest(agentRoot)).withEnvOverrides();
    }

    static AgentCoachConfig fromManifest(JsonNode manifest) {
        JsonNode coach = manifest == null ? MAPPER.createObjectNode() : manifest.path("coach");
        boolean enabled = coach.path("enabled").asBoolean(true);
        JsonNode triggers = coach.path("triggers");
        int largeWrite = triggers.path("fileLargeWriteBytes").asInt(DEFAULT_LARGE_WRITE_BYTES);
        int inlineLines = triggers.path("scriptInlineLongLines").asInt(DEFAULT_INLINE_LONG_LINES);
        int inlineBytes = triggers.path("scriptInlineLongBytes").asInt(DEFAULT_INLINE_LONG_BYTES);
        int failRepeat = triggers.path("scriptFailRepeat").asInt(DEFAULT_SCRIPT_FAIL_REPEAT);
        return new AgentCoachConfig(enabled, largeWrite, inlineLines, inlineBytes, failRepeat);
    }

    AgentCoachConfig withEnvOverrides() {
        Boolean envEnabled = parseEnabledEnv(System.getenv("AGENT1_COACH"));
        boolean enabled = envEnabled != null ? envEnabled : this.enabled;
        int largeWrite = intEnv("AGENT1_COACH_LARGE_WRITE_BYTES", largeWriteBytes);
        int inlineLines = intEnv("AGENT1_COACH_INLINE_LONG_LINES", inlineLongLines);
        int inlineBytes = intEnv("AGENT1_COACH_INLINE_LONG_BYTES", inlineLongBytes);
        int failRepeat = intEnv("AGENT1_COACH_SCRIPT_FAIL_REPEAT", scriptFailRepeat);
        if (enabled == this.enabled
            && largeWrite == largeWriteBytes
            && inlineLines == inlineLongLines
            && inlineBytes == inlineLongBytes
            && failRepeat == scriptFailRepeat) {
            return this;
        }
        return new AgentCoachConfig(enabled, largeWrite, inlineLines, inlineBytes, failRepeat);
    }

    public boolean enabled() {
        return enabled;
    }

    public int largeWriteBytes() {
        return largeWriteBytes;
    }

    public int inlineLongLines() {
        return inlineLongLines;
    }

    public int inlineLongBytes() {
        return inlineLongBytes;
    }

    public int scriptFailRepeat() {
        return scriptFailRepeat;
    }

    public ProductivityCoach toCoach() {
        return new ProductivityCoach(this);
    }

    private static JsonNode readManifest(Path agentRoot) {
        if (agentRoot == null) {
            return MAPPER.createObjectNode();
        }
        Path file = agentRoot.toAbsolutePath().normalize().resolve("agent.manifest.json");
        if (!Files.isRegularFile(file)) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(Files.readString(file));
        } catch (IOException e) {
            return MAPPER.createObjectNode();
        }
    }

    private static Boolean parseEnabledEnv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        return null;
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
