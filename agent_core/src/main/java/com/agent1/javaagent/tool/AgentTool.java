package com.agent1.javaagent.tool;

import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {
    String name();

    String description();

    JsonNode parametersSchema();

    /**
     * 本工具执行的建议超时（毫秒）。默认回退到运行时 fallback；工具可基于参数细化
     * （如 skill 按 action 区分）。返回值必须 ≥ 1_000ms 由调用方保证。
     */
    default long suggestedTimeoutMs(JsonNode parameters, long fallbackMs) {
        return fallbackMs;
    }

    ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) throws Exception;
}
