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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class ListDirTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;

    public ListDirTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String name() {
        return "list_dir";
    }

    @Override
    public String description() {
        return "List entries in a session workspace directory.";
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
                .put("description", "Directory path relative to session workspace. Default is \".\".")
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
        String rawPath = parameters.path("path").asText(".").trim();
        if (rawPath.isEmpty()) {
            rawPath = ".";
        }

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
            return ToolExecutionResult.text("错误：目录不存在: " + displayPath);
        }
        if (!Files.isDirectory(resolvedPath)) {
            return ToolExecutionResult.text("错误：不是目录: " + displayPath);
        }

        try (Stream<Path> stream = Files.list(resolvedPath)) {
            StringBuilder out = new StringBuilder();
            out.append("DIR: ").append(displayPath).append("\n");
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .forEach(entry -> {
                    String name = entry.getFileName().toString();
                    if (Files.isDirectory(entry)) {
                        out.append(name).append("/\n");
                    } else {
                        out.append(name).append("\n");
                    }
                });
            return ToolExecutionResult.text(out.toString().trim());
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：列出目录失败: " + e.getMessage());
        }
    }
}
