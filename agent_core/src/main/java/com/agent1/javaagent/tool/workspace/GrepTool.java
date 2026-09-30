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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/** 在 workspace 与 agent 只读 docs 下按行 grep（与 Weizhi 外层工具名对齐）。 */
public final class GrepTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_MATCHES = 80;
    private static final int MAX_FILE_BYTES = 512 * 1024;

    private final WorkspaceSandbox sandbox;

    public GrepTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return """
            Search file contents by regex under session workspace or read-only agent docs \
            (paths docs/system/... or docs/capabilities/...). Default search root is workspace ".".
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
                .put("description", "File or directory relative to workspace or docs/. Default \".\".")
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
        String patternText = parameters.path("pattern").asText("").trim();
        if (patternText.isEmpty()) {
            return ToolExecutionResult.text("错误：pattern 不能为空");
        }
        final Pattern pattern;
        try {
            pattern = Pattern.compile(patternText);
        } catch (PatternSyntaxException e) {
            return ToolExecutionResult.text("错误：无效正则: " + e.getMessage());
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

        List<String> linesOut = new ArrayList<>();
        try {
            if (Files.isRegularFile(base)) {
                grepFile(base, pattern, linesOut);
            } else if (Files.isDirectory(base)) {
                grepTree(base, pattern, linesOut, cancellationToken);
            } else {
                return ToolExecutionResult.text("错误：路径不存在或不是文件/目录");
            }
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：grep 失败: " + e.getMessage());
        }

        if (linesOut.isEmpty()) {
            return ToolExecutionResult.text("无匹配: pattern=" + patternText);
        }
        StringBuilder text = new StringBuilder();
        text.append("grep: ").append(linesOut.size()).append(" 行匹配\n");
        for (String line : linesOut) {
            text.append(line).append('\n');
        }
        return ToolExecutionResult.text(text.toString().trim());
    }

    private void grepTree(Path base, Pattern pattern, List<String> out, CancellationToken token)
        throws IOException {
        try (Stream<Path> walk = Files.walk(base, 8, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                if (out.size() >= MAX_MATCHES || token.isCancelled()) {
                    return;
                }
                try {
                    grepFile(path, pattern, out);
                } catch (IOException ignored) {
                    // skip unreadable
                }
            });
        }
    }

    private void grepFile(Path file, Pattern pattern, List<String> out) throws IOException {
        if (out.size() >= MAX_MATCHES) {
            return;
        }
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String display = sandbox.displayPath(file);
        for (int i = 0; i < lines.size(); i++) {
            if (out.size() >= MAX_MATCHES) {
                break;
            }
            if (pattern.matcher(lines.get(i)).find()) {
                out.add(display + ":" + (i + 1) + ":" + lines.get(i));
            }
        }
    }
}
