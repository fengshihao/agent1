package com.agent1.javaagent.tool;

import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {
    String name();

    String description();

    JsonNode parametersSchema();

    ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) throws Exception;
}
