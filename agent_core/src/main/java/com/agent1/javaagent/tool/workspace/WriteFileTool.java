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

public final class WriteFileTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;

    public WriteFileTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Write text content to a file in session workspace (creates or overwrites).";
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
            "content",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Full file content to write.")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("path").add("content"));
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
        if (!parameters.has("content") || parameters.get("content").isNull()) {
            return ToolExecutionResult.text("错误：content 不能为空");
        }
        String content = parameters.path("content").asText("");

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

        try {
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolvedPath, content, StandardCharsets.UTF_8);
            return ToolExecutionResult.text("已写入: " + displayPath + " (" + content.length() + " chars)");
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：写入文件失败: " + e.getMessage());
        }
    }
}
