package com.agent1.javaagent.capability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * agentRoot 下 SQLite 能力索引（FTS5 + 权重）。
 * Phase A：bundled seed 导入。MCP 条目由 {@code McpCapabilitySync} 按 kind 替换，不进模型工具参数。
 */
public final class CapabilityIndexStore {

    /** v4：doc 条目 entry 改为 read_file 路径；read_agent_doc 已合并进工作区工具。 */
    public static final int SCHEMA_VERSION = 4;

    /**
     * 种子内容代次。修改 {@code search-index.seed.jsonl} 时递增，已有 capabilities.db 会在下次检索前重建。
     */
    public static final int SEED_REVISION = 4;

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

    private static volatile CapabilityDatabaseOpener databaseOpener;

    private CapabilityIndexStore() {
    }

    /**
     * Android 在进程启动时注入系统 {@code SQLiteDatabase}。
     * 未注入时使用 sqlite-jdbc（仅桌面）。
     */
    public static void setDatabaseOpener(CapabilityDatabaseOpener opener) {
        databaseOpener = opener;
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
        try (CapabilityDatabase db = open(dbPath)) {
            createSchema(db);
            insertAll(db, records);
            setMeta(db, "schema_version", Integer.toString(SCHEMA_VERSION));
            setMeta(db, "seed_revision", Integer.toString(SEED_REVISION));
            setMeta(db, "source", "bundled-seed");
        } catch (SQLException e) {
            throw new IllegalStateException("build capability index failed: " + dbPath, e);
        }
    }

    /**
     * 删掉某一 kind 的旧行再写入新行。种子里的其他 kind 保留。
     * 调用方在 MCP 配置变化或缓存仍有效时刷新 {@code mcp} 条目。
     */
    public static void replaceKind(Path agentRoot, String kind, List<CapabilityRecord> records) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind required");
        }
        ensure(agentRoot);
        Path dbPath = CapabilityDatabasePaths.databaseFile(agentRoot);
        try (CapabilityDatabase db = open(dbPath)) {
            db.execute("DELETE FROM capability WHERE kind = ?", List.of(kind));
            if (tableExists(db, "capability_fts")) {
                db.execute("DELETE FROM capability_fts WHERE kind = ?", List.of(kind));
            }
            if (records != null && !records.isEmpty()) {
                insertAll(db, records);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("replace capability kind failed: " + kind, e);
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
        List<CapabilityHit> ftsHits = List.of();
        try {
            ftsHits = searchFts(dbPath, query, kinds, normalizedPlatform, effectiveLimit);
        } catch (IllegalStateException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (!message.contains("no such table") && !message.contains("fts5")) {
                throw e;
            }
        }
        if (!ftsHits.isEmpty()) {
            return ftsHits;
        }
        return searchLike(dbPath, query, kinds, normalizedPlatform, effectiveLimit);
    }

    public static List<String> queryTerms(String query) {
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
        try (CapabilityDatabase db = open(dbPath)) {
            if (!tableExists(db, "capability")) {
                return true;
            }
            String version = getMeta(db, "schema_version");
            if (!Integer.toString(SCHEMA_VERSION).equals(version)) {
                return true;
            }
            String seedRevision = getMeta(db, "seed_revision");
            return !Integer.toString(SEED_REVISION).equals(seedRevision);
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

    private static CapabilityDatabase open(Path dbPath) throws SQLException {
        CapabilityDatabaseOpener opener = databaseOpener;
        if (opener != null) {
            return opener.open(dbPath);
        }
        return new JdbcCapabilityDatabase(dbPath);
    }

    private static void createSchema(CapabilityDatabase db) throws SQLException {
        db.execute(
            """
            CREATE TABLE meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            )
            """
        );
        db.execute(
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
        try {
            db.execute(
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
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (!message.contains("fts5")) {
                throw e;
            }
        }
    }

    private static void insertAll(CapabilityDatabase db, List<CapabilityRecord> records) throws SQLException {
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
        boolean fts = tableExists(db, "capability_fts");
        for (CapabilityRecord r : records) {
            if (r.id().isEmpty() || r.kind().isEmpty()) {
                continue;
            }
            db.execute(
                insertCap,
                List.of(
                    r.id(),
                    r.kind(),
                    r.title(),
                    r.summary(),
                    r.tags(),
                    r.platforms(),
                    r.entry(),
                    r.docPath(),
                    r.docAnchor(),
                    r.requiresJson(),
                    r.source(),
                    r.weight()
                )
            );
            if (fts) {
                db.execute(
                    insertFts,
                    List.of(
                        r.id(),
                        r.kind(),
                        r.platforms(),
                        r.title(),
                        r.summary(),
                        r.tags(),
                        r.entry(),
                        r.weight()
                    )
                );
            }
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
        try (CapabilityDatabase db = open(dbPath);
            CapabilityRowCursor rs = db.query(sql, params)) {
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

    private static void setMeta(CapabilityDatabase db, String key, String value) throws SQLException {
        db.execute("INSERT OR REPLACE INTO meta(key,value) VALUES (?,?)", List.of(key, value));
    }

    private static String getMeta(CapabilityDatabase db, String key) throws SQLException {
        try (CapabilityRowCursor rows = db.query("SELECT value FROM meta WHERE key = ?", List.of(key))) {
            return rows.next() ? rows.getString("value") : "";
        }
    }

    private static boolean tableExists(CapabilityDatabase db, String name) throws SQLException {
        try (CapabilityRowCursor rows = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
            List.of(name)
        )) {
            return rows.next();
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
