package com.agent1.javaagent.memory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reads SQLite schema metadata via the {@code sqlite3} CLI for injection into the system prompt.
 */
public final class MemorySqliteCatalog {

    private static final int MAX_SECTION_CHARS = 8000;
    private static final int TIMEOUT_SEC = 5;

    private static final String HEADER = "=== 记忆库结构（自动生成）===\n"
        + "以下为当前库中用户表的 CREATE 语句摘要；表内数据请用 run_bash 调用 sqlite3 自行查询（建议 LIMIT）。\n\n";

    private MemorySqliteCatalog() {
    }

    /**
     * Returns a Markdown-ish block describing user tables; safe to append to system prompt.
     */
    public static String buildSection(Path dbPath) {
        if (dbPath == null) {
            return "";
        }
        Path abs = dbPath.toAbsolutePath().normalize();
        if (!Files.exists(abs)) {
            return "=== 记忆库结构（自动生成）===\n（数据库文件尚未创建；首次用 sqlite3 写入后会显示表结构。）\n";
        }
        String sql =
            "SELECT sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name;";
        List<String> command = List.of("sqlite3", abs.toString(), sql);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return HEADER + "（读取 schema 超时，已跳过。）\n";
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            int code = process.exitValue();
            if (code != 0) {
                return HEADER + "（sqlite3 退出码 " + code + "）\n" + out + "\n";
            }
            if (out.isBlank()) {
                return HEADER + "（尚无用户表；可自行 CREATE TABLE。）\n";
            }
            String body = out;
            if (body.length() > MAX_SECTION_CHARS) {
                body = body.substring(0, MAX_SECTION_CHARS) + "\n\n…（已截断）\n";
            }
            return HEADER + "```sql\n" + body + "\n```\n";
        } catch (Exception e) {
            return HEADER + "（无法执行 sqlite3：" + e.getMessage() + "）\n";
        }
    }
}
