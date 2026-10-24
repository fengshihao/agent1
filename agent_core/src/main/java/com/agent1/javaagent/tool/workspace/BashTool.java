package com.agent1.javaagent.tool.workspace;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.workspace.WorkspaceSandbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 白名单 bash（无 shell）：{@link ProcessBuilder} 直执行 + {@link WorkspaceSandbox} 路径校验。
 */
public final class BashTool {

    /** 命令成功但无 stdout 时返回给模型的文案（避免字面 “empty output” 被当成失败）。 */
    static final String NO_STDOUT_SUCCESS = "已成功执行（无标准输出）";

    private static final long TIMEOUT_MS = 10_000;
    private static final int MAX_OUTPUT_BYTES = 50_000;

    static final Set<String> ALLOWED_COMMANDS = new TreeSet<>(Arrays.asList(
            "ls", "mkdir", "rm", "mv", "cp",
            "cat", "head", "tail", "wc", "find", "pwd", "touch", "echo", "date",
            "which", "file", "base64", "sha256sum"
    ));

    private static final String ALLOWED_LIST = String.join(", ", ALLOWED_COMMANDS);

    static final Set<String> READ_COMMANDS = new TreeSet<>(Arrays.asList(
            "ls", "cat", "head", "tail", "wc", "find", "pwd", "which",
            "file", "base64", "sha256sum", "date", "echo"));

    static final Set<String> WRITE_COMMANDS = new TreeSet<>(Arrays.asList(
            "mkdir", "rm", "mv", "touch"));

    private final WorkspaceSandbox sandbox;

    public BashTool(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Tool(name = "bash",
            description = "在工作区执行命令（白名单、无交互 shell）。"
                    + "仅支持: base64, cat, cp, date, echo, file, find, head, ls, mkdir, mv, "
                    + "pwd, rm, sha256sum, tail, touch, wc, which。"
                    + "可用 ; 或 && 串联多条、| 管道、< > >> 重定向（路径受沙箱约束）。"
                    + "which 仅对白名单内命令报路径；无 python/node 等解释器。"
                    + "读写分离：只读命令可访问只读区；写命令与重定向目标仅限工作区内。",
            readOnly = false, concurrencySafe = false)
    public String bash(
            @ToolParam(name = "command", description = "要执行的命令，如 'ls -la'") String command,
            CancellationToken cancellationToken) {
        if (command == null || command.trim().isEmpty()) {
            return "Error: empty command";
        }
        String trimmed = command.trim();

        String injectErr = checkInjection(trimmed);
        if (injectErr != null) {
            return "Error: " + injectErr;
        }
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return "Error: newline separators not allowed";
        }

        try {
            List<BashCommandChain.Segment> segments = BashCommandChain.parse(trimmed);
            if (segments.isEmpty()) {
                return "Error: empty command";
            }
            return executeChain(cancellationToken, segments);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }

    private String executeChain(CancellationToken cancellationToken, List<BashCommandChain.Segment> segments) {
        StringBuilder combined = new StringBuilder();
        boolean lastOk = true;
        for (int s = 0; s < segments.size(); s++) {
            BashCommandChain.Segment segment = segments.get(s);
            if (s > 0) {
                BashCommandChain.Join join = segments.get(s - 1).joinToNext;
                if (join == BashCommandChain.Join.AND && !lastOk) {
                    break;
                }
            }
            String out;
            try {
                out = executePipe(cancellationToken, segment.pipe);
            } catch (SecurityException e) {
                out = "Error: " + e.getMessage();
            }
            lastOk = !out.startsWith("Error:");
            if (combined.length() > 0) {
                combined.append("\n---\n");
            }
            combined.append(out);
            if (!lastOk && s + 1 < segments.size()
                && segments.get(s).joinToNext == BashCommandChain.Join.AND) {
                break;
            }
        }
        return combined.toString();
    }

    private String executePipe(CancellationToken cancellationToken, List<BashCommandChain.Stage> stages)
        throws SecurityException {
        if (stages.isEmpty()) {
            return "Error: empty command";
        }
        for (BashCommandChain.Stage stage : stages) {
            validateStage(stage);
        }
        if (stages.size() == 1) {
            return runStage(cancellationToken, stages.get(0), null, null);
        }
        Process[] processes = new Process[stages.size()];
        Thread[] transfers = new Thread[stages.size() - 1];
        try {
            for (int i = 0; i < stages.size(); i++) {
                BashCommandChain.Stage stage = stages.get(i);
                ProcessBuilder pb = processBuilderForStage(stage, i == 0, i == stages.size() - 1);
                if (i > 0) {
                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                }
                if (i < stages.size() - 1) {
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                }
                processes[i] = pb.start();
                if (i > 0) {
                    final InputStream in = processes[i - 1].getInputStream(); // NOPMD CloseResource — pump() 内 try-with-resources 负责关闭
                    final OutputStream out = processes[i].getOutputStream(); // NOPMD CloseResource — pump() 内 try-with-resources 负责关闭
                    transfers[i - 1] = new Thread(() -> pump(in, out), "bash-pipe");
                    transfers[i - 1].setDaemon(true);
                    transfers[i - 1].start();
                }
            }
            Process last = processes[processes.length - 1];
            String output = readProcessOutput(cancellationToken, last);
            int exit = waitForAll(cancellationToken, processes);
            for (Thread t : transfers) {
                if (t != null) {
                    try {
                        t.join(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (exit != 0) {
                if (output.isEmpty() || output.startsWith("Error:")) {
                    return "Error: command exited with code " + exit;
                }
                return "Error: command exited with code " + exit + "\n" + output;
            }
            return output.isEmpty() ? NO_STDOUT_SUCCESS : output;
        } catch (IOException | InterruptedException e) {
            for (Process p : processes) {
                if (p != null) {
                    p.destroyForcibly();
                }
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return "Error: command interrupted";
            }
            return "Error: " + e.getMessage();
        }
    }

    private void validateStage(BashCommandChain.Stage stage) {
        String[] tokens = stage.argv;
        String cmdName = tokens[0];
        if (!ALLOWED_COMMANDS.contains(cmdName)) {
            throw new SecurityException("command '" + cmdName + "' not allowed. Supported: " + ALLOWED_LIST);
        }
        validatePathArgs(tokens);
        validateRedirects(stage);
    }

    private void validateRedirects(BashCommandChain.Stage stage) {
        if (stage.stdin.mode != BashCommandChain.RedirectMode.NONE) {
            sandbox.resolveRead(stage.stdin.path);
        }
        if (stage.stdout.mode != BashCommandChain.RedirectMode.NONE) {
            sandbox.resolveWrite(stage.stdout.path);
        }
    }

    private ProcessBuilder processBuilderForStage(
        BashCommandChain.Stage stage,
        boolean first,
        boolean last
    ) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(stage.argv);
        pb.directory(sandbox.getRoot().toFile());
        pb.redirectErrorStream(true);
        if (first && stage.stdin.mode != BashCommandChain.RedirectMode.NONE) {
            pb.redirectInput(sandbox.resolveRead(stage.stdin.path).toFile());
        }
        if (last && stage.stdout.mode != BashCommandChain.RedirectMode.NONE) {
            BashCommandChain.Redirect out = stage.stdout;
            java.nio.file.Path dest = sandbox.resolveWrite(out.path);
            java.nio.file.Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (out.mode == BashCommandChain.RedirectMode.APPEND) {
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(dest.toFile()));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.to(dest.toFile()));
            }
        }
        return pb;
    }

    private String runStage(
        CancellationToken cancellationToken,
        BashCommandChain.Stage stage,
        Void ignored1,
        Void ignored2
    ) {
        if ("which".equals(stage.argv[0])) {
            return executeWhich(cancellationToken, stage.argv);
        }
        return execute(cancellationToken, stage.argv, stage);
    }

    private static void pump(InputStream in, OutputStream out) {
        try (in; out) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (IOException ignored) {
            // 管道另一端已关闭
        }
    }

    private int waitForAll(CancellationToken cancellationToken, Process[] processes)
        throws InterruptedException {
        int lastExit = 0;
        for (Process p : processes) {
            if (p == null) {
                continue;
            }
            while (true) {
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    p.destroyForcibly();
                    return -1;
                }
                if (p.waitFor(200, TimeUnit.MILLISECONDS)) {
                    lastExit = p.exitValue();
                    break;
                }
            }
        }
        return lastExit;
    }

    private String readProcessOutput(CancellationToken cancellationToken, Process proc) throws IOException {
        FutureTask<String> outputTask = new FutureTask<>(() -> readOutput(proc.getInputStream()));
        Thread drainThread = new Thread(outputTask, "bash-drain");
        drainThread.setDaemon(true);
        drainThread.start();
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            outputTask.cancel(true);
            return "错误：执行已取消";
        }
        try {
            return outputTask.get(30_000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            return "Error: failed to read command output";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: command interrupted";
        }
    }

    /**
     * 不把主机 PATH 里的 node/python 等路径暴露给模型：只有 {@link #ALLOWED_COMMANDS} 里的名字才查系统 which。
     */
    private String executeWhich(CancellationToken cancellationToken, String[] tokens) {
        List<String> targets = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            if (!tokens[i].startsWith("-")) {
                targets.add(tokens[i]);
            }
        }
        if (targets.isEmpty()) {
            return execute(cancellationToken, tokens);
        }
        StringBuilder out = new StringBuilder();
        for (String name : targets) {
            if (!ALLOWED_COMMANDS.contains(name)) {
                continue;
            }
            String line = execute(cancellationToken, new String[] {"which", name});
            if (line.startsWith("Error:") || NO_STDOUT_SUCCESS.equals(line)) {
                continue;
            }
            if (out.length() > 0 && !out.toString().endsWith("\n")) {
                out.append('\n');
            }
            out.append(line.trim());
            if (!line.endsWith("\n")) {
                out.append('\n');
            }
        }
        return out.length() == 0 ? NO_STDOUT_SUCCESS : out.toString().trim() + "\n";
    }

    private static String checkInjection(String cmd) {
        if (cmd.contains("$(") || cmd.contains("`")) {
            return "command substitution not allowed";
        }
        return null;
    }

    private void validatePathArgs(String[] tokens) {
        String cmd = tokens[0];
        if ("cp".equals(cmd)) {
            validateCpArgs(tokens);
            return;
        }
        boolean write = !READ_COMMANDS.contains(cmd);
        for (int i = 1; i < tokens.length; i++) {
            String arg = tokens[i];
            if (arg.startsWith("-")) {
                continue;
            }
            if (looksLikePath(arg)) {
                if (write) {
                    sandbox.resolveWrite(arg);
                } else {
                    sandbox.resolveRead(arg);
                }
            }
        }
    }

    private void validateCpArgs(String[] tokens) {
        for (int i = 1; i < tokens.length; i++) {
            if ("-t".equals(tokens[i]) || "--target-directory".equals(tokens[i])) {
                throw new SecurityException("cp option '" + tokens[i]
                        + "' not supported; use: cp <src...> <dst>");
            }
        }
        List<Integer> operandIdx = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            if (!tokens[i].startsWith("-")) {
                operandIdx.add(i);
            }
        }
        for (int k = 0; k < operandIdx.size(); k++) {
            String arg = tokens[operandIdx.get(k)];
            if (!looksLikePath(arg)) {
                continue;
            }
            if (k == operandIdx.size() - 1) {
                sandbox.resolveWrite(arg);
            } else {
                sandbox.resolveRead(arg);
            }
        }
    }

    private static boolean looksLikePath(String arg) {
        if (arg.isEmpty()) {
            return false;
        }
        if (arg.indexOf('*') >= 0 || arg.indexOf('?') >= 0
                || arg.indexOf('[') >= 0 || arg.indexOf('{') >= 0) {
            return false;
        }
        return arg.indexOf('/') >= 0 || arg.indexOf('\\') >= 0
                || arg.indexOf('.') >= 0 || arg.startsWith("~");
    }

    static String[] tokenize(String cmd) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < cmd.length()) {
            char c = cmd.charAt(i);
            if (Character.isWhitespace(c)) {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
                i++;
                while (i < cmd.length() && Character.isWhitespace(cmd.charAt(i))) {
                    i++;
                }
            } else if (c == '"') {
                i++;
                while (i < cmd.length() && cmd.charAt(i) != '"') {
                    if (cmd.charAt(i) == '\\' && i + 1 < cmd.length()) {
                        i++;
                        if (i < cmd.length()) {
                            sb.append(cmd.charAt(i));
                        }
                    } else {
                        sb.append(cmd.charAt(i));
                    }
                    i++;
                }
                if (i < cmd.length()) {
                    i++;
                }
            } else if (c == '\'') {
                i++;
                while (i < cmd.length() && cmd.charAt(i) != '\'') {
                    sb.append(cmd.charAt(i));
                    i++;
                }
                if (i < cmd.length()) {
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens.toArray(new String[0]);
    }

    private String execute(CancellationToken cancellationToken, String[] tokens) {
        return execute(cancellationToken, tokens, null);
    }

    private String execute(
        CancellationToken cancellationToken,
        String[] tokens,
        BashCommandChain.Stage stage
    ) {
        try {
            ProcessBuilder pb;
            if (stage != null) {
                pb = processBuilderForStage(stage, true, true);
            } else {
                pb = new ProcessBuilder(tokens);
                pb.directory(sandbox.getRoot().toFile());
                pb.redirectErrorStream(true);
            }
            Process proc = pb.start();

            FutureTask<String> outputTask = new FutureTask<>(() -> readOutput(proc.getInputStream()));
            Thread drainThread = new Thread(outputTask, "bash-drain");
            drainThread.setDaemon(true);
            drainThread.start();

            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            boolean finished = false;
            while (System.currentTimeMillis() < deadline) {
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    proc.destroyForcibly();
                    outputTask.cancel(true);
                    return "错误：执行已取消";
                }
                if (proc.waitFor(200, TimeUnit.MILLISECONDS)) {
                    finished = true;
                    break;
                }
            }
            if (!finished) {
                proc.destroyForcibly();
                outputTask.cancel(true);
                return "Error: command timed out after " + TIMEOUT_MS + "ms";
            }

            String output;
            try {
                output = outputTask.get(2000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                return "Error: failed to read command output";
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                String msg = cause == null ? e.getMessage() : cause.getMessage();
                return "Error: read failed: " + (msg == null ? "unknown" : msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Error: command interrupted";
            }
            int exitCode = proc.exitValue();
            if (exitCode != 0) {
                if (output.isEmpty()) {
                    return "Error: command exited with code " + exitCode;
                }
                return "Error: command exited with code " + exitCode + "\n" + output;
            }
            return output.isEmpty() ? NO_STDOUT_SUCCESS : output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: command interrupted";
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    private static String readOutput(InputStream in) {
        StringBuilder sb = new StringBuilder();
        boolean truncated = false;
        int byteCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                if (byteCount + n > MAX_OUTPUT_BYTES) {
                    sb.append(buf, 0, MAX_OUTPUT_BYTES - byteCount);
                    truncated = true;
                    while (reader.read(buf) >= 0) { // NOPMD EmptyWhileStmt — 排空剩余输出，防止子进程阻塞在满管道上
                    }
                    break;
                }
                sb.append(buf, 0, n);
                byteCount += n;
            }
        } catch (IOException e) {
            if (sb.length() == 0) {
                return "Error: read failed: " + e.getMessage();
            }
        }
        String out = sb.toString();
        if (truncated) {
            out += "\n<truncated: output exceeded " + MAX_OUTPUT_BYTES + " bytes>";
        }
        return out;
    }
}
