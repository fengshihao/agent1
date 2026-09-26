package com.agent1.javaagent.log.query;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** {@code events.jsonl} 中的一行（只读视图）。 */
public final class EventLogEntry {

    private final int lineNumber;
    private final String ts;
    private final String sessionId;
    private final String runId;
    private final String parentRunId;
    private final String agentId;
    private final int seq;
    private final String type;
    private final JsonNode root;

    public EventLogEntry(int lineNumber, JsonNode root) {
        this.lineNumber = lineNumber;
        this.root = Objects.requireNonNull(root, "root");
        this.ts = text(root, "ts");
        this.sessionId = text(root, "sessionId");
        this.runId = text(root, "runId");
        this.parentRunId = text(root, "parentRunId");
        this.agentId = text(root, "agentId");
        this.seq = root.path("seq").asInt(0);
        this.type = text(root, "type");
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String ts() {
        return ts;
    }

    public String sessionId() {
        return sessionId;
    }

    public String runId() {
        return runId;
    }

    public String parentRunId() {
        return parentRunId;
    }

    public String agentId() {
        return agentId;
    }

    public int seq() {
        return seq;
    }

    public String type() {
        return type;
    }

    public JsonNode root() {
        return root;
    }

    public String fieldText(String key) {
        return text(root, key);
    }

    public boolean fieldBool(String key) {
        JsonNode node = root.get(key);
        return node != null && node.asBoolean(false);
    }

    public long fieldLong(String key) {
        JsonNode node = root.get(key);
        return node == null ? 0L : node.asLong(0L);
    }

    private static String text(JsonNode root, String key) {
        JsonNode node = root.get(key);
        return node == null || node.isNull() ? "" : node.asText("");
    }
}
