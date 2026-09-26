package com.agent1.javaagent.log;

import com.agent1.javaagent.core.AgentStateSnapshot;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventListener;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.RunState;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 将 {@link AgentEventType} 映射为 doc/基础能力/04 中的 JSONL {@code type} 并落盘。 */
public final class AgentEventJsonlBridge implements AgentEventListener {

    private final RunLogContext context;
    private final EventJsonlWriter writer;
    private volatile boolean runFailed;
    private volatile boolean deferRunTerminal;
    private final Map<String, Long> toolStartEpochMs = new ConcurrentHashMap<>();

    public AgentEventJsonlBridge(RunLogContext context, EventJsonlWriter writer) {
        this.context = context;
        this.writer = writer;
    }

    public AgentEventJsonlBridge(RunLogContext context, Path eventsPath) {
        this(context, new EventJsonlWriter(eventsPath));
    }

    public AgentEventJsonlBridge(RunLogContext context) {
        this(context, AgentDataPaths.eventsJsonl());
    }

    /** 为 true 时 {@code run_completed} / {@code run_failed} 由宿主在 Run 结束时显式写入。 */
    public void setDeferRunTerminal(boolean deferRunTerminal) {
        this.deferRunTerminal = deferRunTerminal;
    }

    @Override
    public void onEvent(AgentEvent event) {
        AgentEventType type = event.getType();
        Object payload = event.getPayload();
        switch (type) {
            case AGENT_START -> onAgentStart((AgentStateSnapshot) payload);
            case MESSAGE_START -> onMessageStart((EventPayloads.MessageEvent) payload);
            case MESSAGE_UPDATE -> onMessageUpdate((EventPayloads.MessageUpdate) payload);
            case USAGE -> onUsage((EventPayloads.Usage) payload);
            case TOOL_EXECUTION_START -> onToolStart((EventPayloads.ToolExecutionStart) payload);
            case TOOL_EXECUTION_END -> onToolEnd((EventPayloads.ToolExecutionEnd) payload);
            case AGENT_ERROR -> onAgentError((EventPayloads.AgentError) payload);
            case AGENT_END -> onAgentEnd((EventPayloads.AgentEnd) payload);
            default -> {
                // TURN_* / MESSAGE_END / TOOL_EXECUTION_UPDATE 不落 events.jsonl
            }
        }
    }

    private void onAgentStart(AgentStateSnapshot snapshot) {
        runFailed = false;
        toolStartEpochMs.clear();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("model", snapshot.getModel());
        fields.put("message_count", snapshot.getMessages().size());
        writer.write(context, "run_started", fields);
    }

    private void onMessageStart(EventPayloads.MessageEvent payload) {
        Map<String, Object> fields = new LinkedHashMap<>();
        AgentMessage message = payload.getMessage();
        if (message != null && AgentMessage.ROLE_ASSISTANT.equals(message.getRole())) {
            fields.put("streaming", true);
        }
        writer.write(context, "model_request", fields);
    }

    private void onMessageUpdate(EventPayloads.MessageUpdate payload) {
        writer.write(context, "model_text_delta", Map.of("delta", payload.getDelta()));
    }

    private void onUsage(EventPayloads.Usage payload) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("input_tokens", payload.getInputTokens());
        fields.put("output_tokens", payload.getOutputTokens());
        if (payload.getCachedTokens() != null) {
            fields.put("cached_tokens", payload.getCachedTokens());
        }
        writer.write(context, "usage", fields);
    }

    private void onToolStart(EventPayloads.ToolExecutionStart payload) {
        var toolCall = payload.getToolCall();
        toolStartEpochMs.put(toolCall.getId(), System.currentTimeMillis());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool_name", toolCall.getName());
        fields.put("tool_args", toolCall.getArgumentsJson());
        fields.put("tool_call_id", toolCall.getId());
        writer.write(context, "tool_call", fields);
    }

    private void onToolEnd(EventPayloads.ToolExecutionEnd payload) {
        Long started = toolStartEpochMs.remove(payload.getToolCallId());
        long durationMs = started == null ? 0L : Math.max(0L, System.currentTimeMillis() - started);
        String resultText = payload.getResult() == null ? "" : payload.getResult().getText();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool_call_id", payload.getToolCallId());
        fields.put("result", resultText);
        fields.put("is_error", payload.isError());
        fields.put("duration_ms", durationMs);
        if (payload.isError() && payload.getErrorMessage() != null) {
            fields.put("error_message", payload.getErrorMessage());
        }
        writer.write(context, "tool_result", fields);
    }

    private void onAgentError(EventPayloads.AgentError payload) {
        runFailed = true;
        if (!deferRunTerminal) {
            writer.write(context, "run_failed", Map.of("error", payload.getMessage()));
        }
    }

    private void onAgentEnd(EventPayloads.AgentEnd payload) {
        if (deferRunTerminal || runFailed) {
            return;
        }
        writer.write(context, "run_completed", Map.of("status", "ok", "message_count", payload.getMessages().size()));
    }

    /** 生产力宿主在 Run 终态确定后调用（需 {@link #setDeferRunTerminal(boolean)}）。 */
    public void writeRunTerminal(RunState state, String reason, int messageCount) {
        runFailed = true;
        switch (state) {
            case SUCCEEDED -> writer.write(
                context,
                "run_completed",
                Map.of("status", "ok", "message_count", messageCount)
            );
            case PAUSED -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                if (reason != null && !reason.isBlank()) {
                    fields.put("reason", reason);
                }
                fields.put("message_count", messageCount);
                writer.write(context, "run_paused", fields);
            }
            case CANCELLED -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                if (reason != null && !reason.isBlank()) {
                    fields.put("reason", reason);
                }
                writer.write(context, "run_cancelled", fields);
            }
            case FAILED -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("error", reason == null || reason.isBlank() ? "unknown" : reason);
                fields.put("message_count", messageCount);
                writer.write(context, "run_failed", fields);
            }
            default -> {
            }
        }
    }

    /** 供宿主在取消 Run 时显式写入终态。 */
    public void writeRunCancelled(String reason) {
        runFailed = true;
        Map<String, Object> fields = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            fields.put("reason", reason);
        }
        writer.write(context, "run_cancelled", fields);
    }

    /** 供宿主在暂停 Run 时显式写入。 */
    public void writeRunPaused(String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            fields.put("reason", reason);
        }
        writer.write(context, "run_paused", fields);
    }

    /** 宿主手动补写 usage（runtime 已会从模型响应发 {@link AgentEventType#USAGE}）。 */
    public void writeUsage(long inputTokens, long outputTokens) {
        writer.write(
            context,
            "usage",
            Map.of("input_tokens", inputTokens, "output_tokens", outputTokens)
        );
    }
}
