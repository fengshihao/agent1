package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 阶段 5 实现前占位（步骤 3.5）。 */
public final class CatalogInstallTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "catalog_install";
    }

    @Override
    public String description() {
        return "Install catalog items by id from remote manifest (stub; wraps future sync apply).";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "ids",
            MAPPER.createObjectNode()
                .put("type", "array")
                .put("description", "Catalog item ids to install when sync is available.")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("ids"));
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
            "未实现：catalog_install / sync apply 将在阶段 5 提供。"
                + "请勿用 write_file 向 shared/catalog 拷贝文件。"
                + "请先 read_agent_doc docs/system/catalog-install.md。"
        );
    }
}
