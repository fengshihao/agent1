package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 阶段 6 实现前占位：让模型可发现工具名（步骤 3.5）。 */
public final class PromoteRequestTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "promote_request";
    }

    @Override
    public String description() {
        return "Promote staged workspace assets to shared/local (not implemented yet; use read_agent_doc promotion.md).";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "note",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Optional note for audit when promotion is implemented.")
        );
        schema.set("properties", properties);
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        return ToolExecutionResult.text(
            "未实现：promote_request 将在阶段 6 提供。"
                + "请先把成果放到 workspace/staging/，并阅读 docs/system/promotion.md。"
                + "禁止用 write_file 写入 shared/local 或 catalog。"
        );
    }
}
