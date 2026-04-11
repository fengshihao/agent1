package com.agent1.javaagent.cli.tools;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class RunPythonTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "run_python";
    }

    @Override
    public String description() {
        return "Run Python code by script content or file path.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("script", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Inline Python script content"));
        properties.set("file_path", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Path to a Python file"));
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
        String script = parameters.path("script").asText(null);
        String filePath = parameters.path("file_path").asText(null);

        boolean hasScript = script != null && !script.isBlank();
        boolean hasFilePath = filePath != null && !filePath.isBlank();

        if (hasScript && hasFilePath) {
            return ToolExecutionResult.text("错误：不能同时指定 script 和 file_path");
        }
        if (!hasScript && !hasFilePath) {
            return ToolExecutionResult.text("错误：必须提供 script（脚本内容）或 file_path（文件路径）");
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        if (hasFilePath) {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return ToolExecutionResult.text("错误：文件不存在: " + filePath);
            }
            if (!Files.isRegularFile(path)) {
                return ToolExecutionResult.text("错误：不是文件: " + filePath);
            }
            return runProcess(path.getParent(), buildPythonCommand(path.toString()));
        }

        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("java_agent_", ".py");
            Files.writeString(tmpFile, script, StandardCharsets.UTF_8);
            return runProcess(null, buildPythonCommand(tmpFile.toString()));
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：" + e);
        } finally {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    private ToolExecutionResult runProcess(Path cwd, List<String> cmd) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolExecutionResult.text("错误：Python 脚本执行超时（30秒）");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.exitValue();

            StringBuilder out = new StringBuilder();
            if (!stdout.isBlank()) {
                out.append(stdout.strip());
            }
            if (!stderr.isBlank()) {
                if (out.length() > 0) {
                    out.append("\n\n");
                }
                out.append("[stderr]\n").append(stderr.strip());
            }
            if (out.length() == 0) {
                out.append("(退出码: ").append(code).append(")");
            }
            return ToolExecutionResult.text(out.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolExecutionResult.text("错误：Python 脚本执行被中断");
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：" + e);
        }
    }

    private List<String> buildPythonCommand(String scriptPath) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String py = findExecutableInPath("py");
            if (py != null) {
                return List.of(py, "-3", scriptPath);
            }
            String python = firstNonNull(findExecutableInPath("python"), findExecutableInPath("python3"));
            if (python != null) {
                return List.of(python, scriptPath);
            }
            return List.of("python", scriptPath);
        }
        String python3 = firstNonNull(findExecutableInPath("python3"), findExecutableInPath("python"));
        if (python3 != null) {
            return List.of(python3, scriptPath);
        }
        return List.of("python3", scriptPath);
    }

    private String findExecutableInPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        String[] dirs = pathEnv.split(File.pathSeparator);
        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
            Path exeCandidate = Path.of(dir, name + ".exe");
            if (Files.isRegularFile(exeCandidate) && Files.isExecutable(exeCandidate)) {
                return exeCandidate.toString();
            }
        }
        return null;
    }

    private String firstNonNull(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
