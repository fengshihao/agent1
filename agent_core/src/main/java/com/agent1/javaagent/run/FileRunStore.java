package com.agent1.javaagent.run;

import com.agent1.javaagent.session.FileSessionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** {@code sessions/<sessionId>/runs/<runId>.json}，原子替换写入。 */
public final class FileRunStore {

    private final FileSessionStore sessionStore;
    private final ObjectMapper mapper;

    public FileRunStore(FileSessionStore sessionStore) {
        this(sessionStore, new ObjectMapper());
    }

    public FileRunStore(FileSessionStore sessionStore, ObjectMapper mapper) {
        this.sessionStore = sessionStore;
        this.mapper = mapper;
    }

    public Path runFile(String sessionId, String runId) {
        return sessionStore.sessionDir(sessionId).resolve("runs").resolve(runId + ".json");
    }

    public void write(RunRecord record) {
        Path file = runFile(record.getSessionId(), record.getRunId());
        Path dir = file.getParent();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("create runs dir failed: " + dir, e);
        }
        Path tmp = dir.resolve(record.getRunId() + ".json.tmp");
        try {
            Files.writeString(
                tmp,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(record)),
                StandardCharsets.UTF_8
            );
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("write run record failed: " + file, e);
        }
    }

    public RunRecord read(String sessionId, String runId) {
        return readOptional(sessionId, runId)
            .orElseThrow(() -> new IllegalArgumentException("run not found: " + sessionId + "/" + runId));
    }

    public Optional<RunRecord> readOptional(String sessionId, String runId) {
        Path file = runFile(sessionId, runId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            return Optional.of(fromJson(root));
        } catch (IOException e) {
            throw new IllegalStateException("read run record failed: " + file, e);
        }
    }

    private ObjectNode toJson(RunRecord record) {
        ObjectNode root = mapper.createObjectNode();
        root.put("runId", record.getRunId());
        root.put("sessionId", record.getSessionId());
        root.put("state", record.getState().wireValue());
        root.put("startedAt", record.getStartedAt());
        root.put("updatedAt", record.getUpdatedAt());
        record.getLastError().ifPresent(err -> root.put("lastError", err));
        return root;
    }

    private RunRecord fromJson(JsonNode root) {
        String lastError = root.hasNonNull("lastError") ? root.get("lastError").asText() : null;
        return new RunRecord(
            root.path("runId").asText(""),
            root.path("sessionId").asText(""),
            RunState.fromWire(root.path("state").asText("")),
            root.path("startedAt").asText(""),
            root.path("updatedAt").asText(""),
            lastError
        );
    }
}
