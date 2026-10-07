package com.agent1.javaagent.log;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 按行 {@code ts} 裁剪 {@code events.jsonl}，避免无限增长。 */
public final class EventJsonlRetention {

    private static final String ENV_RETENTION_DAYS = "AGENT1_EVENTS_LOG_RETENTION_DAYS";
    private static final int DEFAULT_RETENTION_DAYS = 2;
    private static final long PRUNE_INTERVAL_MS = 60L * 60L * 1000L;

    private static final ConcurrentHashMap<String, Long> LAST_PRUNE_MS = new ConcurrentHashMap<>();

    private EventJsonlRetention() {
    }

    public static Duration retentionDuration() {
        String fromEnv = System.getenv(ENV_RETENTION_DAYS);
        if (fromEnv != null && !fromEnv.isBlank()) {
            try {
                int days = Integer.parseInt(fromEnv.trim());
                if (days <= 0) {
                    return Duration.ZERO;
                }
                return Duration.ofDays(days);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Duration.ofDays(DEFAULT_RETENTION_DAYS);
    }

    /** 宿主启动时调用；不受节流限制。 */
    public static void pruneOnStartup(Path eventsPath, ObjectMapper mapper) {
        try {
            prune(eventsPath, mapper, retentionDuration());
        } catch (IOException e) {
            System.err.println("EventJsonlRetention: startup prune failed: " + e.getMessage());
        }
    }

    /** 写入前节流裁剪（高频 {@code model_text_delta} 时避免每次读全文件）。 */
    public static void maybePrune(Path eventsPath, ObjectMapper mapper) {
        Duration retention = retentionDuration();
        if (retention.isZero() || retention.isNegative()) {
            return;
        }
        String key = eventsPath.toAbsolutePath().normalize().toString();
        long now = System.currentTimeMillis();
        Long last = LAST_PRUNE_MS.get(key);
        if (last != null && now - last < PRUNE_INTERVAL_MS) {
            return;
        }
        LAST_PRUNE_MS.put(key, now);
        try {
            prune(eventsPath, mapper, retention);
        } catch (IOException e) {
            System.err.println("EventJsonlRetention: prune failed: " + e.getMessage());
        }
    }

    /**
     * 只保留 {@code ts} 在保留窗口内的行；无 {@code ts} 或解析失败的行保留。
     *
     * @return 删除的行数
     */
    public static int prune(Path eventsPath, ObjectMapper mapper, Duration retention) throws IOException {
        if (retention.isZero() || retention.isNegative()) {
            return 0;
        }
        if (!Files.isRegularFile(eventsPath)) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(retention);
        List<String> lines = Files.readAllLines(eventsPath, StandardCharsets.UTF_8);
        List<String> kept = new ArrayList<>(lines.size());
        int removed = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            if (keepLine(mapper, line, cutoff)) {
                kept.add(line);
            } else {
                removed++;
            }
        }
        if (removed == 0) {
            return 0;
        }
        Path parent = eventsPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = parent == null
            ? Path.of(eventsPath.getFileName() + ".prune.tmp")
            : parent.resolve(eventsPath.getFileName() + ".prune.tmp");
        String body = kept.isEmpty() ? "" : String.join("\n", kept) + "\n";
        PathIo.writeString(tmp, body, StandardCharsets.UTF_8);
        Files.move(tmp, eventsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return removed;
    }

    private static boolean keepLine(ObjectMapper mapper, String line, Instant cutoff) {
        try {
            JsonNode root = mapper.readTree(line);
            String ts = root.path("ts").asText("");
            if (ts.isBlank()) {
                return true;
            }
            Instant instant = Instant.parse(ts);
            return !instant.isBefore(cutoff);
        } catch (Exception e) {
            return true;
        }
    }
}
