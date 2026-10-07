package com.agent1.javaagent.tool.workspace;

import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GlobTool {

    private static final int DEFAULT_LIMIT = 100;

    private final WorkspaceSandbox sandbox;

    public GlobTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Tool(
        name = "glob",
        description = "按 glob 查找文件，例如 **/*.js、src/**/*.ts。返回匹配路径，按修改时间排序。",
        readOnly = true,
        concurrencySafe = true
    )
    public String glob(
        @ToolParam(name = "pattern", description = "Glob 模式") String pattern,
        @ToolParam(name = "path", required = false, description = "起始目录") String path,
        @ToolParam(name = "limit", required = false, description = "最多返回数（默认 100）") Integer limit
    ) {
        Path base;
        try {
            base = path == null || path.isBlank() ? sandbox.getRoot() : sandbox.resolveRead(path);
        } catch (SecurityException e) {
            return "Error: " + e.getMessage();
        }
        if (!Files.exists(base)) {
            return "Error: path does not exist";
        }
        Pattern compiled;
        try {
            compiled = Pattern.compile(GlobToRegex.convert(pattern));
        } catch (RuntimeException e) {
            return "Error: invalid pattern: " + e.getMessage();
        }
        int lim = limit == null ? DEFAULT_LIMIT : Math.max(1, limit);

        List<Path> matched = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String rel = sandbox.relativize(file);
                if (compiled.matcher(rel).matches()) {
                    matched.add(file);
                }
            });
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
        matched.sort(Comparator.comparingLong(GlobTool::lastModified).reversed());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Path file : matched) {
            if (shown >= lim) {
                break;
            }
            sb.append(sandbox.relativize(file)).append("\n");
            shown++;
        }
        if (matched.size() > lim) {
            sb.append("<truncated: showing ").append(lim).append(" of ").append(matched.size()).append(">");
        }
        return sb.length() == 0 ? "No matches." : sb.toString();
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
