package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.catalog.sync.CatalogSyncService;
import com.agent1.javaagent.log.AgentAuditEvents;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

/** 封装 sync apply（阶段 5.5）。 */
public final class CatalogInstallTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;

    public CatalogInstallTool(Path agentRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "catalog_install";
    }

    @Override
    public String description() {
        return "Install catalog items by id from remote manifest (sync apply).";
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
                .put("description", "Catalog item ids to install; omit to apply all pending.")
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
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        List<String> ids = readIds(parameters);
        try {
            CatalogSyncService.SyncApplyResult result = new CatalogSyncService(agentRoot).apply(ids);
            StringBuilder out = new StringBuilder();
            out.append("applied: ").append(result.appliedIds().size()).append('\n');
            for (String id : result.appliedIds()) {
                out.append("  ok ").append(id).append('\n');
            }
            for (String error : result.errors()) {
                out.append("  fail ").append(error).append('\n');
            }
            if (result.appliedIds().isEmpty() && result.errors().isEmpty()) {
                out.append("(无 pending 条目；可先 catalog_sync_status)");
            }
            if (!result.appliedIds().isEmpty() || !result.errors().isEmpty()) {
                AgentAuditEvents.catalogSyncCompleted(agentRoot, null, result, "catalog_install");
            }
            return ToolExecutionResult.text(out.toString().trim());
        } catch (IllegalStateException e) {
            return ToolExecutionResult.text("catalog 未配置: " + e.getMessage());
        } catch (Exception e) {
            return ToolExecutionResult.text("catalog_install 失败: " + e.getMessage());
        }
    }

    private static List<String> readIds(JsonNode parameters) {
        JsonNode idsNode = parameters == null ? null : parameters.get("ids");
        if (idsNode == null || !idsNode.isArray()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode node : idsNode) {
            if (node.isTextual() && !node.asText().isBlank()) {
                ids.add(node.asText().trim());
            }
        }
        return ids;
    }
}
