package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.catalog.sync.CatalogSyncDiff;
import com.agent1.javaagent.catalog.sync.CatalogSyncService;
import com.agent1.javaagent.log.AgentAuditEvents;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** catalog pending 摘要（阶段 5.5，封装 sync check）。 */
public final class CatalogSyncStatusTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;

    public CatalogSyncStatusTool(Path agentRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "catalog_sync_status";
    }

    @Override
    public String description() {
        return "Fetch remote catalog manifest and report pending installs (sync check).";
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
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        try {
            CatalogSyncService.SyncCheckResult result = new CatalogSyncService(agentRoot).check();
            StringBuilder out = new StringBuilder();
            out.append("manifest: ").append(result.manifestUrl()).append('\n');
            out.append("catalogId: ").append(result.catalogId()).append('\n');
            out.append("pending: ").append(result.pending().size()).append('\n');
            for (var entry : result.pendingByKind().entrySet()) {
                out.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
            }
            for (CatalogSyncDiff.PendingItem item : result.pending()) {
                out.append("- ")
                    .append(item.item().id())
                    .append(" (")
                    .append(item.reason().name().toLowerCase())
                    .append(")\n");
            }
            out.append("CLI: agent1 sync apply [--ids id1,id2]");
            AgentAuditEvents.catalogSyncChecked(agentRoot, null, result, "catalog_sync_status");
            return ToolExecutionResult.text(out.toString().trim());
        } catch (IllegalStateException e) {
            return ToolExecutionResult.text("catalog 未配置: " + e.getMessage());
        } catch (Exception e) {
            return ToolExecutionResult.text("catalog_sync_status 失败: " + e.getMessage());
        }
    }
}
