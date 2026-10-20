package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 将 {@link AgentEvent} 编成前端可消费的 JSON（避免 payload 类型 getter 命名歧义）。 */
public final class UiEventCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ARGS_PREVIEW_LIMIT = 220;
    private static final int RESULT_PREVIEW_LIMIT = 280;

    private UiEventCodec() {
    }

    public static ObjectNode encode(AgentEvent event, String sessionId, String runId) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", event.getType().name());
        root.put("timestampMs", event.getTimestampMs());
        if (sessionId != null) {
            root.put("sessionId", sessionId);
        }
        if (runId != null) {
            root.put("runId", runId);
        }
        root.set("payload", encodePayload(event.getType(), event.getPayload()));
        return root;
    }

    public static ObjectNode encodeMessage(AgentMessage message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", message.getRole());
        node.put("content", message.getContent());
        node.put("reasoningContent", message.getReasoningContent());
        node.put("timestampMs", message.getTimestampMs());
        if (message.getToolCallId() != null) {
            node.put("toolCallId", message.getToolCallId());
        }
        node.put("error", message.isError());
        ArrayNode toolCalls = node.putArray("toolCalls");
        for (ToolCall call : message.getToolCalls()) {
            toolCalls.add(encodeToolCall(call));
        }
        return node;
    }

    private static ObjectNode encodePayload(AgentEventType type, Object payload) {
        ObjectNode node = MAPPER.createObjectNode();
        if (payload == null) {
            return node;
        }
        switch (type) {
            case MESSAGE_UPDATE -> {
                EventPayloads.MessageUpdate update = (EventPayloads.MessageUpdate) payload;
                node.put("delta", update.getDelta());
            }
            case REASONING_UPDATE -> {
                EventPayloads.ReasoningUpdate update = (EventPayloads.ReasoningUpdate) payload;
                node.put("delta", update.getDelta());
            }
            case MESSAGE_END, MESSAGE_START -> {
                EventPayloads.MessageEvent msg = (EventPayloads.MessageEvent) payload;
                node.set("message", encodeMessage(msg.getMessage()));
            }
            case MESSAGE_RESET -> {
                // empty
            }
            case USAGE -> {
                EventPayloads.Usage usage = (EventPayloads.Usage) payload;
                node.put("inputTokens", usage.getInputTokens());
                node.put("outputTokens", usage.getOutputTokens());
                if (usage.getCachedTokens() != null) {
                    node.put("cachedTokens", usage.getCachedTokens());
                }
            }
            case TOOL_EXECUTION_START -> {
                EventPayloads.ToolExecutionStart start = (EventPayloads.ToolExecutionStart) payload;
                node.set("toolCall", encodeToolCall(start.getToolCall()));
            }
            case TOOL_EXECUTION_END -> {
                EventPayloads.ToolExecutionEnd end = (EventPayloads.ToolExecutionEnd) payload;
                node.put("toolCallId", end.getToolCallId());
                node.put("error", end.isError());
                if (end.getErrorMessage() != null) {
                    node.put("errorMessage", end.getErrorMessage());
                }
                ToolExecutionResult result = end.getResult();
                if (result != null) {
                    node.put("resultText", preview(result.getText(), RESULT_PREVIEW_LIMIT));
                    node.put("stopRunWaitingUser", result.stopRunWaitingUser());
                    node.put("toolError", result.isError());
                }
            }
            case TURN_START -> {
                EventPayloads.TurnStart turn = (EventPayloads.TurnStart) payload;
                node.put("turnIndex", turn.getTurnIndex());
            }
            case AGENT_ERROR -> {
                EventPayloads.AgentError err = (EventPayloads.AgentError) payload;
                node.put("message", err.getMessage());
            }
            case AGENT_START, AGENT_END, TURN_END, TOOL_EXECUTION_UPDATE -> {
                node.put("rawType", type.name());
            }
            default -> node.put("rawType", type.name());
        }
        return node;
    }

    private static ObjectNode encodeToolCall(ToolCall call) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", call.getId());
        node.put("name", call.getName());
        node.put("argumentsJson", call.getArgumentsJson());
        node.put("argsPreview", preview(call.getArgumentsJson(), ARGS_PREVIEW_LIMIT));
        return node;
    }

    private static String preview(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "…";
    }
}
