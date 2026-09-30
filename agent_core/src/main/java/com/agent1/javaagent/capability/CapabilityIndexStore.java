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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * agentRoot 下 SQLite 能力索引（FTS5 + 权重）。
 * Phase A：bundled seed 导入；catalog/skill/MCP 扫描见 Phase B。
 */
public final class CapabilityIndexStore {

    /** v4：doc 条目 entry 改为 read_file 路径；read_agent_doc 已合并进工作区工具。 */
    public static final int SCHEMA_VERSION = 4;

    /**
     * FTS5 bm25 列权（仅 indexed 列，顺序与 {@code capability_fts} 一致：title, summary, tags, entry）。
     * title（名称）最高，tags 次之，summary/entry 正文最低。
     */
    static final double BM25_WEIGHT_TITLE = 10.0;
    static final double BM25_WEIGHT_SUMMARY = 1.0;
    static final double BM25_WEIGHT_TAGS = 4.0;
    static final double BM25_WEIGHT_ENTRY = 1.0;

    /** LIKE 回退时的字段档位分（与 bm25 优先级一致，数值越大越靠前）。 */
    static final double LIKE_SCORE_TITLE = 1_000.0;
    static final double LIKE_SCORE_TAGS = 400.0;
    static final double LIKE_SCORE_SUMMARY = 100.0;
    static final double LIKE_SCORE_ENTRY = 80.0;
    static final double LIKE_SCORE_ID = 50.0;

    private static final Pattern FTS_SPECIAL = Pattern.compile("[\"*:^()]");

    private CapabilityIndexStore() {
    }

    /**
     * Android 不能加载 sqlite-jdbc 的桌面 .so（16KB 页设备上会在 dlopen 时直接杀掉进程，且写不出 Java 崩溃栈）。
     */
    static boolean useInMemoryIndex() {
        String vm = System.getProperty("java.vm.name", "");
        return vm.contains("Dalvik") || vm.contains("Android");
    }

    /** bootstrap 或索引缺失时从 seed 建库。Android 上不打开 JDBC。 */
    public static void ensure(Path agentRoot) {
        if (useInMemoryIndex()) {
            return;
        }
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
        if (useInMemoryIndex()) {
            return searchRecords(
                CapabilitySeedLoader.loadBundledSeed(),
                query,
                kinds,
                normalizedPlatform,
                effectiveLimit
            );
        }
        List<CapabilityHit> ftsHits = searchFts(dbPath, query, kinds, normalizedPlatform, effectiveLimit);
        if (!ftsHits.isEmpty()) {
            return ftsHits;
        }
        return searchLike(dbPath, query, kinds, normalizedPlatform, effectiveLimit);
    }

    static List<String> queryTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String cleaned = FTS_SPECIAL.matcher(query.trim()).replaceAll(" ");
        String[] parts = cleaned.split("\\s+");
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                terms.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return terms;
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
        String bm25 =
            "bm25(capability_fts, "
                + BM25_WEIGHT_TITLE
                + ", "
                + BM25_WEIGHT_SUMMARY
                + ", "
                + BM25_WEIGHT_TAGS
                + ", "
                + BM25_WEIGHT_ENTRY
                + ")";
        StringBuilder sql = new StringBuilder(
            """
            SELECT c.id, c.kind, c.title, c.summary, c.tags, c.entry, c.doc_path, c.platforms,
                   ("""
                + bm25
                + " * c.weight) AS score\n"
                + """
            FROM capability_fts f
            JOIN capability c ON c.id = f.id
            WHERE capability_fts MATCH ?
            """
        );
        List<Object> params = new ArrayList<>();
        params.add(match);
        appendKindFilter(sql, params, kinds);
        appendPlatformFilter(sql, params, platform);
        int candidateLimit = Math.min(Math.max(limit * 8, limit), 80);
        sql.append(" ORDER BY score ASC LIMIT ?");
        params.add(candidateLimit);

        List<CapabilityHit> hits = runSearch(dbPath, sql.toString(), params);
        return finalizeRanking(hits, query, limit);
    }

    static List<CapabilityHit> searchLike(
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
            SELECT c.id, c.kind, c.title, c.summary, c.tags, c.entry, c.doc_path, c.platforms,
                   (
                     (CASE WHEN lower(c.title) LIKE ? THEN %1$f ELSE 0 END)
                     + (CASE WHEN lower(c.tags) LIKE ? THEN %2$f ELSE 0 END)
                     + (CASE WHEN lower(c.summary) LIKE ? THEN %3$f ELSE 0 END)
                     + (CASE WHEN lower(c.entry) LIKE ? THEN %4$f ELSE 0 END)
                     + (CASE WHEN lower(c.id) LIKE ? THEN %5$f ELSE 0 END)
                   ) * c.weight AS score
            FROM capability c
            WHERE (
              lower(c.title) LIKE ? OR lower(c.summary) LIKE ? OR lower(c.tags) LIKE ?
              OR lower(c.entry) LIKE ? OR lower(c.id) LIKE ?
            )
            """
                .formatted(
                    LIKE_SCORE_TITLE,
                    LIKE_SCORE_TAGS,
                    LIKE_SCORE_SUMMARY,
                    LIKE_SCORE_ENTRY,
                    LIKE_SCORE_ID
                )
        );
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            params.add(like);
        }
        for (int i = 0; i < 5; i++) {
            params.add(like);
        }
        appendKindFilter(sql, params, kinds);
        appendPlatformFilter(sql, params, platform);
        int candidateLimit = Math.min(Math.max(limit * 8, limit), 80);
        sql.append(" ORDER BY score DESC, c.title LIMIT ?");
        params.add(candidateLimit);
        List<CapabilityHit> hits = runSearch(dbPath, sql.toString(), params);
        return finalizeRanking(hits, query, limit);
    }

    private static List<CapabilityHit> finalizeRanking(List<CapabilityHit> hits, String query, int limit) {
        if (hits.isEmpty()) {
            return hits;
        }
        List<String> terms = queryTerms(query);
        List<CapabilityHit> sorted = new ArrayList<>(hits);
        sorted.sort(fieldPriorityComparator(terms));
        if (sorted.size() <= limit) {
            return sorted;
        }
        return List.copyOf(sorted.subList(0, limit));
    }

    private static Comparator<CapabilityHit> fieldPriorityComparator(List<String> terms) {
        return (a, b) -> {
            int tierA = fieldMatchTier(a, terms);
            int tierB = fieldMatchTier(b, terms);
            if (tierA != tierB) {
                return Integer.compare(tierA, tierB);
            }
            int scoreCmp = compareScoresWithinTier(a.score(), b.score());
            if (scoreCmp != 0) {
                return scoreCmp;
            }
            return a.title().compareToIgnoreCase(b.title());
        };
    }

    /** 0=title, 1=tags, 2=summary, 3=entry, 4=id；多词取最优档。 */
    static int fieldMatchTier(CapabilityHit hit, List<String> terms) {
        if (terms.isEmpty()) {
            return 5;
        }
        int best = 5;
        for (String term : terms) {
            best = Math.min(best, fieldMatchTier(hit, term));
        }
        return best;
    }

    private static int fieldMatchTier(CapabilityHit hit, String term) {
        if (containsIgnoreCase(hit.title(), term)) {
            return 0;
        }
        if (containsIgnoreCase(hit.tags(), term)) {
            return 1;
        }
        if (containsIgnoreCase(hit.summary(), term)) {
            return 2;
        }
        if (containsIgnoreCase(hit.entry(), term)) {
            return 3;
        }
        if (containsIgnoreCase(hit.id(), term)) {
            return 4;
        }
        return 5;
    }

    /** bm25 越小越好；LIKE 档位分为正，越大越好。 */
    private static int compareScoresWithinTier(double scoreA, double scoreB) {
        if (scoreA >= 0 && scoreB >= 0) {
            return Double.compare(scoreB, scoreA);
        }
        return Double.compare(scoreA, scoreB);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle);
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
                            rs.getString("tags"),
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

    static List<CapabilityHit> searchRecords(
        List<CapabilityRecord> records,
        String query,
        List<String> kinds,
        String platform,
        int limit
    ) {
        List<String> terms = queryTerms(query);
        if (terms.isEmpty() || records == null || records.isEmpty()) {
            return List.of();
        }
        List<CapabilityHit> hits = new ArrayList<>();
        for (CapabilityRecord record : records) {
            if (!kindAllowed(record.kind(), kinds) || !platformAllowed(record.platforms(), platform)) {
                continue;
            }
            double score = scoreRecord(record, terms);
            if (score <= 0) {
                continue;
            }
            hits.add(
                new CapabilityHit(
                    record.id(),
                    record.kind(),
                    record.title(),
                    record.summary(),
                    record.tags(),
                    record.entry(),
                    record.docPath(),
                    record.platforms(),
                    score * record.weight()
                )
            );
        }
        hits.sort(Comparator.comparingDouble(CapabilityHit::score).reversed());
        if (hits.size() <= limit) {
            return hits;
        }
        return new ArrayList<>(hits.subList(0, limit));
    }

    private static boolean kindAllowed(String kind, List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return true;
        }
        String normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        for (String candidate : kinds) {
            if (candidate != null && normalized.equals(candidate.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean platformAllowed(String platforms, String platform) {
        if (platform == null || platform.isBlank() || "any".equals(platform)) {
            return true;
        }
        String value = platforms == null ? "" : platforms.toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "any".equals(value)) {
            return true;
        }
        for (String part : value.split("[,\\s]+")) {
            if (platform.equals(part)) {
                return true;
            }
        }
        return value.contains(platform);
    }

    private static double scoreRecord(CapabilityRecord record, List<String> terms) {
        double score = 0;
        for (String term : terms) {
            if (containsIgnoreCase(record.title(), term)) {
                score += LIKE_SCORE_TITLE;
            } else if (containsIgnoreCase(record.tags(), term)) {
                score += LIKE_SCORE_TAGS;
            } else if (containsIgnoreCase(record.summary(), term)) {
                score += LIKE_SCORE_SUMMARY;
            } else if (containsIgnoreCase(record.entry(), term) || containsIgnoreCase(record.id(), term)) {
                score += LIKE_SCORE_ENTRY;
            }
        }
        return score;
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
        String tags,
        String entry,
        String docPath,
        String platforms,
        double score
    ) {
    }
}
