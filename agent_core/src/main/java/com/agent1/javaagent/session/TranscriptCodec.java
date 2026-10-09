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
            if (!message.getReasoningContent().isBlank()) {
                root.put("reasoning", message.getReasoningContent());
            }
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
            String reasoning = root.path("reasoning").asText("");
            long createdAt = root.path("createdAt").asLong(System.currentTimeMillis());
            String toolCallId = root.hasNonNull("toolCallId") ? root.get("toolCallId").asText() : null;
            // decode 不改写 id（两侧对称保留原值）：运行时 PartialToolCall 已保证 id 非空，
            // 这里再各自 normalize 会生成不同 id，把 assistant.toolCalls 与 toolResult.toolCallId
            // 错配，导致之后每轮请求都被服务端以 insufficient tool messages 拒绝。
            // 仅当 toolResult 行缺失 toolCallId 时归一为 ""，与 assistant 侧 null→"" 对称，
            // 保证坏数据两侧仍然配对一致。
            if (toolCallId == null && AgentMessage.ROLE_TOOL_RESULT.equals(role)) {
                toolCallId = "";
            }
            boolean error = root.path("error").asBoolean(false);
            List<ToolCall> toolCalls = new ArrayList<>();
            if (root.has("toolCalls") && root.get("toolCalls").isArray()) {
                for (JsonNode n : root.get("toolCalls")) {
                    String rawId = n.hasNonNull("id") ? n.get("id").asText() : null;
                    toolCalls.add(new ToolCall(
                        rawId == null ? "" : rawId,
                        n.path("name").asText(""),
                        n.path("argumentsJson").asText("{}")
                    ));
                }
            }
            return new AgentMessage(role, content, reasoning, createdAt, toolCallId, error, toolCalls);
        } catch (Exception e) {
            throw new IllegalStateException("transcript decode failed: " + line, e);
        }
    }
}
