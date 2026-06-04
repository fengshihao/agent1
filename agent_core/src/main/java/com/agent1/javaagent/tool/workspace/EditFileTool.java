package com.agent1.javaagent.tool.workspace;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EditFileTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;

    public EditFileTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "Replace old_string with new_string in a workspace text file.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "path",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "File path relative to session workspace.")
        );
        properties.set(
            "old_string",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Exact substring to replace.")
        );
        properties.set(
            "new_string",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Replacement text.")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("path").add("old_string").add("new_string"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        String rawPath = parameters.path("path").asText("").trim();
        if (rawPath.isEmpty()) {
            return ToolExecutionResult.text("错误：path 不能为空");
        }
        if (!parameters.has("old_string") || parameters.get("old_string").isNull()) {
            return ToolExecutionResult.text("错误：old_string 不能为空");
        }
        if (!parameters.has("new_string") || parameters.get("new_string").isNull()) {
            return ToolExecutionResult.text("错误：new_string 不能为空");
        }
        String oldString = parameters.path("old_string").asText("");
        String newString = parameters.path("new_string").asText("");

        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        final Path resolvedPath;
        final String displayPath;
        try {
            resolvedPath = sandbox.resolve(rawPath);
            displayPath = sandbox.relativize(resolvedPath);
        } catch (SecurityException e) {
            return ToolExecutionResult.text("错误：路径超出工作区范围: " + rawPath);
        }

        if (!Files.exists(resolvedPath)) {
            return ToolExecutionResult.text("错误：文件不存在: " + displayPath);
        }
        if (!Files.isRegularFile(resolvedPath)) {
            return ToolExecutionResult.text("错误：不是普通文件: " + displayPath);
        }

        try {
            String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
            if (!content.contains(oldString)) {
                return ToolExecutionResult.text("错误：未找到 old_string: " + displayPath);
            }
            String updated = content.replace(oldString, newString);
            Files.writeString(resolvedPath, updated, StandardCharsets.UTF_8);
            return ToolExecutionResult.text("已编辑: " + displayPath);
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：编辑文件失败: " + e.getMessage());
        }
    }
}
