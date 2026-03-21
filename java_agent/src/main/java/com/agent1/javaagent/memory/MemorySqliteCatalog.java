package com.agent1.javaagent.memory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads SQLite schema metadata via JDBC for injection into system prompt.
 */
public final class MemorySqliteCatalog {
    private static final int MAX_SECTION_CHARS = 6000;
    private static final String HEADER = "=== 记忆库结构（自动生成）===\n";

    private MemorySqliteCatalog() {
    }

    public static String buildSection(Path dbPath) {
        if (dbPath == null) {
            return "";
        }
        Path abs = dbPath.toAbsolutePath().normalize();
        if (!Files.exists(abs)) {
            return HEADER + "（数据库文件尚未创建。）\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + abs);
             Statement st = conn.createStatement();
             ResultSet tables = st.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            List<String> all = new ArrayList<>();
            while (tables.next()) {
                String table = tables.getString("name");
                all.add("- table: " + table);
                try (Statement colSt = conn.createStatement();
                     ResultSet cols = colSt.executeQuery("PRAGMA table_info('" + table.replace("'", "''") + "')")) {
                    while (cols.next()) {
                        all.add("  - " + cols.getString("name")
                            + " | " + cols.getString("type")
                            + " | notnull=" + cols.getInt("notnull")
                            + " | pk=" + cols.getInt("pk"));
                    }
                }
            }
            if (all.isEmpty()) {
                return HEADER + "（当前尚无用户表。）\n";
            }
            String body = String.join("\n", all);
            if (body.length() > MAX_SECTION_CHARS) {
                body = body.substring(0, MAX_SECTION_CHARS) + "\n…（已截断）";
            }
            sb.append(body).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return HEADER + "（读取失败: " + e.getMessage() + "）\n";
        }
    }
}
