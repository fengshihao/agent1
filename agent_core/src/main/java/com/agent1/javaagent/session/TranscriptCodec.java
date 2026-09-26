package com.agent1.javaagent.session;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/** transcript.jsonl 单行 JSON 与 {@link AgentMessage} 互转。 */
final class TranscriptCodec {

    private final ObjectMapper mapper;

    TranscriptCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String toLine(String runId, AgentMessage message) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("role", message.getRole());
            root.put("content", message.getContent());
            root.put("createdAt", message.getTimestampMs());
            root.put("runId", runId == null ? "" : runId);
            if (message.getToolCallId() != null) {
                root.put("toolCallId", message.getToolCallId());
            }
            root.put("error", message.isError());
            if (!message.getToolCalls().isEmpty()) {
                ArrayNode arr = root.putArray("toolCalls");
                for (ToolCall tc : message.getToolCalls()) {
                    ObjectNode n = arr.addObject();
                    n.put("id", tc.getId());
                    n.put("name", tc.getName());
                    n.put("argumentsJson", tc.getArgumentsJson());
                }
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("transcript encode failed", e);
        }
    }

    AgentMessage fromLine(String line) {
        try {
            JsonNode root = mapper.readTree(line);
            String role = root.path("role").asText("");
            String content = root.path("content").asText("");
            long createdAt = root.path("createdAt").asLong(System.currentTimeMillis());
            String toolCallId = root.hasNonNull("toolCallId") ? root.get("toolCallId").asText() : null;
            boolean error = root.path("error").asBoolean(false);
            List<ToolCall> toolCalls = new ArrayList<>();
            if (root.has("toolCalls") && root.get("toolCalls").isArray()) {
                for (JsonNode n : root.get("toolCalls")) {
                    toolCalls.add(new ToolCall(
                        n.path("id").asText(""),
                        n.path("name").asText(""),
                        n.path("argumentsJson").asText("{}")
                    ));
                }
            }
            return new AgentMessage(role, content, createdAt, toolCallId, error, toolCalls);
        } catch (Exception e) {
            throw new IllegalStateException("transcript decode failed: " + line, e);
        }
    }
}
