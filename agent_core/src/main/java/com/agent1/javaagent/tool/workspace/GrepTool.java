package com.agent1.javaagent.tool.workspace;

import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class GrepTool {

    private static final int DEFAULT_LIMIT = 100;

    private final WorkspaceSandbox sandbox;

    public GrepTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Tool(
        name = "grep",
        description = "用正则搜索文件内容。output_mode：content（默认）、files、count。",
        readOnly = true,
        concurrencySafe = true
    )
    public String grep(
        @ToolParam(name = "pattern", description = "正则表达式") String pattern,
        @ToolParam(name = "path", required = false, description = "搜索目录（默认工作区根）") String path,
        @ToolParam(name = "include", required = false, description = "文件名 glob，如 '*.md'") String include,
        @ToolParam(
            name = "output_mode",
            required = false,
            description = "content | files | count"
        ) String outputMode
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
        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return "Error: invalid regex: " + e.getMessage();
        }
        String mode = outputMode == null || outputMode.isBlank() ? "content" : outputMode;
        Pattern includeRegex = include == null || include.isBlank()
            ? null
            : Pattern.compile(GlobToRegex.convert(include));

        StringBuilder sb = new StringBuilder();
        int[] matchCount = new int[] {0};
        Set<String> matchedFiles = new LinkedHashSet<>();

        try (Stream<Path> walk = Files.walk(base)) {
            var it = walk.filter(Files::isRegularFile).iterator();
            while (it.hasNext()) {
                Path file = it.next();
                Path fileName = file.getFileName();
                if (includeRegex != null
                    && (fileName == null || !includeRegex.matcher(fileName.toString()).matches())) {
                    continue;
                }
                if (isBinary(file)) {
                    continue;
                }
                int fileMatches = searchFile(file, regex, mode, sb, matchCount, DEFAULT_LIMIT);
                if (fileMatches > 0) {
                    matchedFiles.add(sandbox.relativize(file));
                }
                if (matchCount[0] >= DEFAULT_LIMIT) {
                    break;
                }
            }
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }

        if ("files".equals(mode)) {
            if (matchedFiles.isEmpty()) {
                return "No matches.";
            }
            StringBuilder files = new StringBuilder();
            for (String file : matchedFiles) {
                files.append(file).append("\n");
            }
            return files.toString();
        }
        if ("count".equals(mode)) {
            return "Total matches: " + matchCount[0];
        }
        if (sb.length() == 0) {
            return "No matches.";
        }
        if (matchCount[0] >= DEFAULT_LIMIT) {
            sb.append("\n<truncated: showing ").append(DEFAULT_LIMIT).append(" matches>");
        }
        return sb.toString();
    }

    private int searchFile(
        Path file,
        Pattern regex,
        String mode,
        StringBuilder sb,
        int[] matchCount,
        int limit
    ) {
        int fileMatches = 0;
        String rel = sandbox.relativize(file);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineno = 0;
            while ((line = reader.readLine()) != null) {
                lineno++;
                if (regex.matcher(line).find()) {
                    fileMatches++;
                    matchCount[0]++;
                    if (matchCount[0] <= limit && "content".equals(mode)) {
                        sb.append(rel).append(":").append(lineno).append(":").append(line).append("\n");
                    }
                }
            }
        } catch (IOException ignored) {
            return fileMatches;
        }
        return fileMatches;
    }

    private boolean isBinary(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            if (n <= 0) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                if (buf[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
