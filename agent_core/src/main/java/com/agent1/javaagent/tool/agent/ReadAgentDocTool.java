package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.agent.AgentReadScope;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 只读读取 agentRoot 下 docs/system 与 docs/capabilities（REQ-012）。 */
public final class ReadAgentDocTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_OFFSET = 1;
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final Path agentRoot;

    public ReadAgentDocTool(Path agentRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "read_agent_doc";
    }

    @Override
    public String description() {
        return "Read a text file under agentRoot docs/system or docs/capabilities (read-only).";
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
                .put("description", "Path relative to agentRoot, e.g. docs/system/catalog-install.md")
        );
        properties.set(
            "offset",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "1-based start line. Default is 1.")
        );
        properties.set(
            "limit",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Number of lines to read. Default is 200, max is 500.")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("path"));
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
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        int offset = Math.max(parameters.path("offset").asInt(DEFAULT_OFFSET), 1);
        int limit = parameters.path("limit").asInt(DEFAULT_LIMIT);
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        limit = Math.min(limit, MAX_LIMIT);

        final Path resolvedPath;
        try {
            resolvedPath = AgentReadScope.resolveDocPath(agentRoot, rawPath);
        } catch (SecurityException e) {
            return ToolExecutionResult.text("错误：" + e.getMessage());
        }

        if (!Files.exists(resolvedPath)) {
            return ToolExecutionResult.text("错误：文件不存在: " + rawPath);
        }
        if (!Files.isRegularFile(resolvedPath)) {
            return ToolExecutionResult.text("错误：不是普通文件: " + rawPath);
        }

        try {
            List<String> lines = Files.readAllLines(resolvedPath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return ToolExecutionResult.text("PATH: " + rawPath + "\nFile is empty.");
            }
            int startIndex = Math.min(offset - 1, lines.size());
            int endIndex = Math.min(startIndex + limit, lines.size());
            StringBuilder out = new StringBuilder();
            out.append("PATH: ").append(rawPath).append("\n");
            out.append("RANGE: ").append(startIndex + 1).append("-").append(endIndex).append(" / ").append(lines.size()).append("\n");
            for (int i = startIndex; i < endIndex; i++) {
                out.append(i + 1).append("|").append(lines.get(i)).append("\n");
            }
            if (endIndex < lines.size()) {
                out.append("... ").append(lines.size() - endIndex).append(" more lines not shown");
            }
            return ToolExecutionResult.text(out.toString().trim());
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：读取文件失败: " + e.getMessage());
        }
    }
}
