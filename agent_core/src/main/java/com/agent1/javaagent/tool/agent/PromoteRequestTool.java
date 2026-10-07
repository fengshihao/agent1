package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.promote.PromotionService;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** staging → shared/local（阶段 6.2）。 */
public final class PromoteRequestTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;
    private final Path workspaceRoot;

    public PromoteRequestTool(Path agentRoot, Path workspaceRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "promote_request";
    }

    @Override
    public String description() {
        return """
            把 workspace/staging 晋升到 shared/local。
            Skill：staging/skills/<name>/SKILL.md，frontmatter 含 name、description。
            脚本：staging/scripts/<name>.js。先 find_caps skill-creator，再调用本工具。
            """.trim();
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
                .put("description", "Optional audit note.")
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
        String note = parameters == null ? "" : parameters.path("note").asText("");
        try {
            PromotionService.PromotionResult result = new PromotionService(agentRoot, workspaceRoot).promote(note);
            if (result.ok()) {
                StringBuilder out = new StringBuilder();
                out.append("promotion_completed\n");
                out.append(result.message()).append('\n');
                for (String item : result.promoted()) {
                    out.append("  - ").append(item).append('\n');
                }
                out.append("capabilities 已更新 docs/capabilities/local.*.md；脚本已同步到 shared/catalog/scripts 供 import");
                return ToolExecutionResult.text(out.toString().trim());
            }
            if (!result.rejections().isEmpty()) {
                StringBuilder out = new StringBuilder("promotion_rejected\n");
                for (String r : result.rejections()) {
                    out.append("  - ").append(r).append('\n');
                }
                return ToolExecutionResult.text(out.toString().trim());
            }
            return ToolExecutionResult.text(result.message());
        } catch (Exception e) {
            return ToolExecutionResult.text("promote_request 失败: " + e.getMessage());
        }
    }
}
