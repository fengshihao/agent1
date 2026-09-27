package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 阶段 5 实现前占位：catalog pending 摘要（步骤 3.5）。 */
public final class CatalogSyncStatusTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "catalog_sync_status";
    }

    @Override
    public String description() {
        return "Report pending catalog items vs remote manifest (stub until sync is implemented).";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode());
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
            "未实现：catalog 同步尚未启用。"
                + "请用 list_catalog 查看本地条目数；安装流程见 docs/system/catalog-install.md。"
                + "CLI 将提供 agent1 sync check / sync apply（阶段 5）。"
        );
    }
}
