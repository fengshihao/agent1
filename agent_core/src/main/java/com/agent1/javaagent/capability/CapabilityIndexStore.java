package com.agent1.javaagent.capability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * agentRoot 下 SQLite 能力索引（FTS5 + 权重）。
 * Phase A：bundled seed 导入；catalog/skill/MCP 扫描见 Phase B。
 */
public final class CapabilityIndexStore {

    public static final int SCHEMA_VERSION = 1;

    private static final Pattern FTS_SPECIAL = Pattern.compile("[\"*:^()]");

    private CapabilityIndexStore() {
    }

    /** bootstrap 或索引缺失时从 seed 建库。 */
    public static void ensure(Path agentRoot) {
        Path dbPath = CapabilityDatabasePaths.databaseFile(agentRoot);
        createParentDirs(dbPath);
        if (needsRebuild(dbPath)) {
            rebuildFromBundledSeed(agentRoot);
        }
    }

    public static void rebuildFromBundledSeed(Path agentRoot) {
        List<CapabilityRecord> seed = CapabilitySeedLoader.loadBundledSeed();
        rebuild(agentRoot, seed);
    }

    public static void rebuild(Path agentRoot, List<CapabilityRecord> records) {
        Path dbPath = CapabilityDatabasePaths.databaseFile(agentRoot);
        try {
            createParentDirs(dbPath);
            if (Files.exists(dbPath)) {
                Files.delete(dbPath);
            }
        } catch (Exception e) {
            throw new IllegalStateException("reset capability db failed: " + dbPath, e);
        }
        try (Connection conn = open(dbPath)) {
            createSchema(conn);
            insertAll(conn, records);
            setMeta(conn, "schema_version", Integer.toString(SCHEMA_VERSION));
            setMeta(conn, "source", "bundled-seed");
        } catch (SQLException e) {
            throw new IllegalStateException("build capability index failed: " + dbPath, e);
        }
    }

    public static List<CapabilityHit> search(
        Path agentRoot,
        String query,
        List<String> kinds,
        String platform,
        int limit
    ) {
        ensure(agentRoot);
        Path dbPath = CapabilityDatabasePaths.databaseFile(agentRoot);
        int effectiveLimit = Math.min(Math.max(limit, 1), 20);
        String normalizedPlatform = normalizePlatform(platform);
        List<CapabilityHit> ftsHits = searchFts(dbPath, query, kinds, normalizedPlatform, effectiveLimit);
        if (!ftsHits.isEmpty()) {
            return ftsHits;
        }
        return searchLike(dbPath, query, kinds, normalizedPlatform, effectiveLimit);
    }

    private static boolean needsRebuild(Path dbPath) {
        if (!Files.isRegularFile(dbPath)) {
            return true;
        }
        try (Connection conn = open(dbPath)) {
            if (!tableExists(conn, "capability")) {
                return true;
            }
            String version = getMeta(conn, "schema_version");
            return !Integer.toString(SCHEMA_VERSION).equals(version);
        } catch (SQLException e) {
            return true;
        }
    }

    private static void createParentDirs(Path dbPath) {
        Path parent = dbPath.getParent();
        if (parent == null) {
            throw new IllegalStateException("capability db has no parent: " + dbPath);
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("create capabilities dir failed: " + parent, e);
        }
    }

    private static Connection open(Path dbPath) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private static void createSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(
                """
                CREATE TABLE meta (
                  key TEXT PRIMARY KEY,
                  value TEXT NOT NULL
                )
                """
            );
            st.execute(
                """
                CREATE TABLE capability (
                  id TEXT PRIMARY KEY,
                  kind TEXT NOT NULL,
                  title TEXT NOT NULL,
                  summary TEXT NOT NULL,
                  tags TEXT NOT NULL DEFAULT '',
                  platforms TEXT NOT NULL DEFAULT 'any',
                  entry TEXT NOT NULL DEFAULT '',
                  doc_path TEXT NOT NULL DEFAULT '',
                  doc_anchor TEXT NOT NULL DEFAULT '',
                  requires_json TEXT NOT NULL DEFAULT '',
                  source TEXT NOT NULL DEFAULT '',
                  weight REAL NOT NULL DEFAULT 1.0
                )
                """
            );
            st.execute(
                """
                CREATE VIRTUAL TABLE capability_fts USING fts5(
                  id UNINDEXED,
                  kind UNINDEXED,
                  platforms UNINDEXED,
                  title,
                  summary,
                  tags,
                  entry,
                  weight UNINDEXED,
                  tokenize='unicode61'
                )
                """
            );
        }
    }

    private static void insertAll(Connection conn, List<CapabilityRecord> records) throws SQLException {
        String insertCap =
            """
            INSERT INTO capability(id, kind, title, summary, tags, platforms, entry, doc_path, doc_anchor,
              requires_json, source, weight)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        String insertFts =
            """
            INSERT INTO capability_fts(id, kind, platforms, title, summary, tags, entry, weight)
            VALUES (?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement cap = conn.prepareStatement(insertCap);
            PreparedStatement fts = conn.prepareStatement(insertFts)) {
            for (CapabilityRecord r : records) {
                if (r.id().isEmpty() || r.kind().isEmpty()) {
                    continue;
                }
                cap.setString(1, r.id());
                cap.setString(2, r.kind());
                cap.setString(3, r.title());
                cap.setString(4, r.summary());
                cap.setString(5, r.tags());
                cap.setString(6, r.platforms());
                cap.setString(7, r.entry());
                cap.setString(8, r.docPath());
                cap.setString(9, r.docAnchor());
                cap.setString(10, r.requiresJson());
                cap.setString(11, r.source());
                cap.setDouble(12, r.weight());
                cap.addBatch();

                fts.setString(1, r.id());
                fts.setString(2, r.kind());
                fts.setString(3, r.platforms());
                fts.setString(4, r.title());
                fts.setString(5, r.summary());
                fts.setString(6, r.tags());
                fts.setString(7, r.entry());
                fts.setDouble(8, r.weight());
                fts.addBatch();
            }
            cap.executeBatch();
            fts.executeBatch();
        }
    }

    private static List<CapabilityHit> searchFts(
        Path dbPath,
        String query,
        List<String> kinds,
        String platform,
        int limit
    ) {
        String match = toFtsMatchQuery(query);
        if (match.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
            """
            SELECT c.id, c.kind, c.title, c.summary, c.entry, c.doc_path, c.platforms,
                   (bm25(capability_fts) * c.weight) AS score
            FROM capability_fts f
            JOIN capability c ON c.id = f.id
            WHERE capability_fts MATCH ?
            """
        );
        List<Object> params = new ArrayList<>();
        params.add(match);
        appendKindFilter(sql, params, kinds);
        appendPlatformFilter(sql, params, platform);
        sql.append(" ORDER BY score LIMIT ?");
        params.add(limit);

        return runSearch(dbPath, sql.toString(), params);
    }

    private static List<CapabilityHit> searchLike(
        Path dbPath,
        String query,
        List<String> kinds,
        String platform,
        int limit
    ) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
        StringBuilder sql = new StringBuilder(
            """
            SELECT c.id, c.kind, c.title, c.summary, c.entry, c.doc_path, c.platforms,
                   c.weight AS score
            FROM capability c
            WHERE (
              lower(c.title) LIKE ? OR lower(c.summary) LIKE ? OR lower(c.tags) LIKE ?
              OR lower(c.entry) LIKE ? OR lower(c.id) LIKE ?
            )
            """
        );
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            params.add(like);
        }
        appendKindFilter(sql, params, kinds);
        appendPlatformFilter(sql, params, platform);
        sql.append(" ORDER BY c.weight DESC, c.title LIMIT ?");
        params.add(limit);
        return runSearch(dbPath, sql.toString(), params);
    }

    private static void appendKindFilter(StringBuilder sql, List<Object> params, List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return;
        }
        sql.append(" AND c.kind IN (");
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            params.add(kinds.get(i).trim().toLowerCase(Locale.ROOT));
        }
        sql.append(')');
    }

    private static void appendPlatformFilter(StringBuilder sql, List<Object> params, String platform) {
        if (platform == null || platform.isBlank() || "any".equals(platform)) {
            return;
        }
        sql.append(" AND (c.platforms = 'any' OR c.platforms LIKE ? OR c.platforms LIKE ? OR c.platforms = ?)");
        params.add("%" + platform + "%");
        params.add(platform + ",%");
        params.add(platform);
    }

    private static List<CapabilityHit> runSearch(Path dbPath, String sql, List<Object> params) {
        List<CapabilityHit> out = new ArrayList<>();
        try (Connection conn = open(dbPath);
            PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(
                        new CapabilityHit(
                            rs.getString("id"),
                            rs.getString("kind"),
                            rs.getString("title"),
                            rs.getString("summary"),
                            rs.getString("entry"),
                            rs.getString("doc_path"),
                            rs.getString("platforms"),
                            rs.getDouble("score")
                        )
                    );
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("capability search failed: " + e.getMessage(), e);
        }
        return out;
    }

    static String toFtsMatchQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String cleaned = FTS_SPECIAL.matcher(query.trim()).replaceAll(" ");
        String[] parts = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('"').append(part).append('"');
        }
        return sb.toString();
    }

    private static String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "any";
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private static void setMeta(Connection conn, String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES (?,?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static String getMeta(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    private static boolean tableExists(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = ?"
        )) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public record CapabilityHit(
        String id,
        String kind,
        String title,
        String summary,
        String entry,
        String docPath,
        String platforms,
        double score
    ) {
    }
}
