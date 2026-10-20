package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.diagnostic.DiagnosticBundleWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 桌面 Web UI / CLI 诊断包（对齐 Android {@code DiagnosticExport}，无 logcat）。 */
public final class DesktopDiagnosticExport {

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private DesktopDiagnosticExport() {
    }

    public static Path exportZip(Path agentRoot, AgentRuntimeConfig config) throws IOException {
        String stamp = STAMP.format(Instant.now());
        Path zip = Files.createTempFile("agent1-diag-" + stamp + "-", ".zip");
        String runtime = runtimeNote(config);
        Path root = agentRoot.toAbsolutePath().normalize();
        List<DiagnosticBundleWriter.NamedDir> trees = new ArrayList<>();
        addTreeIfExists(trees, "agent1/sessions", root.resolve("sessions"));
        addTreeIfExists(trees, "agent1/logs", root.resolve("logs"));
        addTreeIfExists(trees, "agent1/shared", root.resolve("shared"));
        new DiagnosticBundleWriter().write(
            new DiagnosticBundleWriter.Request(
                zip,
                trees,
                List.of(
                    new DiagnosticBundleWriter.NamedText("README.txt", readme()),
                    new DiagnosticBundleWriter.NamedText("desktop.txt", desktopNote(runtime))
                )
            )
        );
        return zip;
    }

    private static void addTreeIfExists(List<DiagnosticBundleWriter.NamedDir> trees, String prefix, Path dir)
        throws IOException {
        if (Files.isDirectory(dir)) {
            trees.add(new DiagnosticBundleWriter.NamedDir(prefix, dir));
        }
    }

    private static String runtimeNote(AgentRuntimeConfig config) {
        return "model: " + config.getModel() + "\n"
            + "baseUrl: " + config.getBaseUrl() + "\n"
            + "limits: contextTurns=" + config.getMaxContextTurns()
            + " maxTurnsPerRun=" + config.getMaxTurnsPerRun()
            + " maxToolCallsPerRun=" + config.getMaxToolCallsPerRun() + "\n"
            + "webSearchConfigured: " + config.isWebSearchConfigured() + "\n";
    }

    private static String desktopNote(String runtimeNote) {
        return "time: " + Instant.now() + "\n"
            + "os: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n"
            + "java: " + System.getProperty("java.version") + "\n\n"
            + runtimeNote;
    }

    private static String readme() {
        return """
            Agent One 诊断包（桌面导出）
            把整个 zip 发给负责诊断的 Agent 即可。

            agent1/sessions/<sessionId>/transcript.jsonl  聊天记录
            agent1/logs/events.jsonl                     结构化事件
            desktop.txt                                  运行时摘要（不含 API Key）
            """.trim() + "\n";
    }
}
