package com.agent1.javaagent.cli.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

public final class JsonlLogger {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path logPath;

    public JsonlLogger() {
        this.logPath = resolveLogPath();
    }

    public Path getLogPath() {
        return logPath;
    }

    public synchronized void writeEvent(String eventType, String runId, Map<String, Object> fields) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("ts", Instant.now().toString());
        root.put("event_type", eventType);
        root.put("run_id", runId);
        if (fields != null) {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                root.set(entry.getKey(), MAPPER.valueToTree(entry.getValue()));
            }
        }
        try {
            Files.createDirectories(logPath.getParent());
            String line = MAPPER.writeValueAsString(root) + "\n";
            Files.writeString(
                logPath,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Logging should not interrupt CLI flow.
        }
    }

    private Path resolveLogPath() {
        String fromEnv = System.getenv("AGENT1_LOG_FILE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv).toAbsolutePath().normalize();
        }
        return Path.of(".").toAbsolutePath().normalize().resolve("logs").resolve("agent1.jsonl");
    }
}
