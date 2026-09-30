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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** 在 workspace 与 agent 只读 docs 下 glob 文件路径。 */
public final class GlobTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RESULTS = 200;

    private final WorkspaceSandbox sandbox;

    public GlobTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return """
            Find files by glob (e.g. **/*.md) under session workspace or read-only agent docs \
            (base path docs/system or docs/capabilities). Default base is workspace ".".
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("pattern", MAPPER.createObjectNode().put("type", "string"));
        properties.set(
            "path",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Base directory. Default \".\".")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("pattern"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        String glob = parameters.path("pattern").asText("").trim();
        if (glob.isEmpty()) {
            return ToolExecutionResult.text("错误：pattern 不能为空");
        }
        String rawPath = parameters.path("path").asText(".").trim();
        if (rawPath.isEmpty()) {
            rawPath = ".";
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        final Path base;
        try {
            base = sandbox.resolveRead(rawPath);
        } catch (SecurityException e) {
            return ToolExecutionResult.text("错误：" + e.getMessage());
        }
        if (!Files.isDirectory(base)) {
            return ToolExecutionResult.text("错误：base 不是目录");
        }

        String syntax = glob.startsWith("glob:") ? glob : "glob:" + glob;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(syntax);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base, 12)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                if (matches.size() >= MAX_RESULTS || cancellationToken.isCancelled()) {
                    return;
                }
                Path rel = base.relativize(path);
                if (matcher.matches(rel) || matcher.matches(path.getFileName())) {
                    matches.add(sandbox.displayPath(path));
                }
            });
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：glob 失败: " + e.getMessage());
        }

        matches.sort(Comparator.naturalOrder());
        if (matches.isEmpty()) {
            return ToolExecutionResult.text("无匹配: pattern=" + glob);
        }
        StringBuilder text = new StringBuilder();
        text.append("glob: ").append(matches.size()).append(" 个文件\n");
        for (String m : matches) {
            text.append(m).append('\n');
        }
        return ToolExecutionResult.text(text.toString().trim());
    }
}
