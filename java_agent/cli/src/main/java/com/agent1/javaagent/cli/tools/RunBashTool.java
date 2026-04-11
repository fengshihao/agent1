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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class RunBashTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "run_bash";
    }

    @Override
    public String description() {
        return "Execute a shell command and return stdout/stderr.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("command", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Bash command to execute"));
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("command"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        String command = parameters.path("command").asText("").trim();
        if (command.isEmpty()) {
            return ToolExecutionResult.text("错误：命令为空");
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        List<String> shellCommand = pickShellCommand(command);
        ProcessBuilder pb = new ProcessBuilder(shellCommand);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolExecutionResult.text("错误：命令执行超时（60秒）");
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
            return ToolExecutionResult.text("错误：命令执行被中断");
        } catch (IOException e) {
            return ToolExecutionResult.text("错误：" + e);
        }
    }

    private List<String> pickShellCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String bash = findExecutableInPath("bash");
            if (bash != null) {
                return List.of(bash, "-lc", command);
            }
            String powershell = firstNonNull(findExecutableInPath("powershell"), findExecutableInPath("pwsh"));
            if (powershell != null) {
                return List.of(powershell, "-NoProfile", "-Command", command);
            }
            String cmd = firstNonNull(System.getenv("ComSpec"), "cmd.exe");
            return List.of(cmd, "/d", "/s", "/c", command);
        }

        if (os.contains("mac")) {
            String zsh = findExecutableInPath("zsh");
            if (zsh != null) {
                return List.of(zsh, "-lc", command);
            }
            String bash = findExecutableInPath("bash");
            if (bash != null) {
                return List.of(bash, "-lc", command);
            }
            return List.of("/bin/sh", "-lc", command);
        }

        String bash = findExecutableInPath("bash");
        if (bash != null) {
            return List.of(bash, "-lc", command);
        }
        String sh = firstNonNull(findExecutableInPath("sh"), "/bin/sh");
        return List.of(sh, "-lc", command);
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
            // Windows executable suffix fallback
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
