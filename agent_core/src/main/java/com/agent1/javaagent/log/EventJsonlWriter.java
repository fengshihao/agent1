package com.agent1.javaagent.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

/** 追加写入 {@code events.jsonl}；写失败只打 stderr，不抛异常。 */
public final class EventJsonlWriter {

    public static final int TOOL_ARGS_MAX = 220;
    public static final int TOOL_RESULT_MAX = 280;

    private final Path eventsPath;
    private final ObjectMapper mapper;

    public EventJsonlWriter(Path eventsPath) {
        this(eventsPath, new ObjectMapper());
    }

    public EventJsonlWriter(Path eventsPath, ObjectMapper mapper) {
        this.eventsPath = eventsPath.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public Path getEventsPath() {
        return eventsPath;
    }

    public synchronized void write(RunLogContext context, String type, Map<String, Object> payload) {
        try {
            Map<String, Object> fields = payload == null ? Map.of() : payload;
            ObjectNode root = mapper.createObjectNode();
            root.put("ts", Instant.now().toString());
            root.put("sessionId", context.getSessionId());
            root.put("runId", context.getRunId());
            root.put("parentRunId", context.getParentRunId());
            root.put("agentId", context.getAgentId());
            root.put("seq", context.nextSeq());
            root.put("type", type);
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                Object value = sanitizeField(type, entry.getKey(), entry.getValue());
                root.set(entry.getKey(), mapper.valueToTree(value));
            }
            Files.createDirectories(eventsPath.getParent());
            String line = mapper.writeValueAsString(root) + "\n";
            Files.writeString(
                eventsPath,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("EventJsonlWriter: failed to write type=" + type + ": " + e.getMessage());
        }
    }

    public void write(RunLogContext context, String type) {
        write(context, type, Map.of());
    }

    static Object sanitizeField(String type, String key, Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        if ("tool_call".equals(type) && "tool_args".equals(key)) {
            return truncate(text, TOOL_ARGS_MAX);
        }
        if ("tool_result".equals(type) && ("result".equals(key) || "error_message".equals(key))) {
            return truncate(text, TOOL_RESULT_MAX);
        }
        return value;
    }

    static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...(truncated)";
    }
}
