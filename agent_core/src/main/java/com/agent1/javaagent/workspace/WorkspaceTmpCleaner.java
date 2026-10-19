package com.agent1.javaagent.workspace;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * workspace tmp 生命周期管理（22 号规划 T1/T2/T3）：
 * {@code tmp/} 下宿主代管的中间产物（weizhi 包组包目录、webview_exec 回执）由宿主
 * 统一回收——QuickJS fs 没有 rmdir/readdirSync，JS 侧删不了目录树，工具自清只能
 * 覆盖单文件中间件（shots/input 已在包内 finally 删），组包目录只能宿主清。
 *
 * 策略：run 收尾后台清「年龄 ≥ minAge 的代管条目」（默认 6h，保护进行中的使用）；
 * 清后代管条目总量仍超配额（默认 200MB）时按 mtime LRU 继续删到限内。
 * 快照统计（文件数/总字节/tmp 字节）供 {@code workspace_snapshot} 台账事件与
 * 产物库占用展示；确定性纯 IO，桌面 CLI 与 Android 同一实现。
 */
public final class WorkspaceTmpCleaner {

    /** tmp/ 下宿主代管的前缀（weizhi 包组包/中间件 + webview_exec 回执）。 */
    public static final List<String> MANAGED_PREFIXES =
        List.of("html2pptx-", "docx-", "pptx-render-", "webview_exec");

    public static final Duration DEFAULT_MIN_AGE = Duration.ofHours(6);
    public static final long DEFAULT_QUOTA_BYTES = 200L * 1024 * 1024;

    private WorkspaceTmpCleaner() {
    }

    /** 一次清扫的结果（audit 事件与测试断言用）。 */
    public static final class CleanResult {
        public final int removedCount;
        public final long removedBytes;
        public final boolean quotaTrimmed;

        CleanResult(int removedCount, long removedBytes, boolean quotaTrimmed) {
            this.removedCount = removedCount;
            this.removedBytes = removedBytes;
            this.quotaTrimmed = quotaTrimmed;
        }
    }

    /** workspace 聚合快照：总文件数/总字节与 tmp 部分（台账事件用，不含文件名）。 */
    public static final class SnapshotStats {
        public final long fileCount;
        public final long totalBytes;
        public final long tmpBytes;

        SnapshotStats(long fileCount, long totalBytes, long tmpBytes) {
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.tmpBytes = tmpBytes;
        }
    }

    /** 单个代管条目的体积与树内最新 mtime（目录取树内最新，防删到正在写的）。 */
    private record EntryStat(Path path, long bytes, FileTime latestMtime) {
        Instant latest() {
            return latestMtime.toInstant();
        }
    }

    public static boolean isManaged(String name) {
        for (String prefix : MANAGED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清扫 {@code <workspace>/tmp}：先删年龄达标的代管条目，再按配额 LRU 收尾。
     * IO 失败跳过该条目继续（清扫不因单个坏条目失败）。
     */
    public static CleanResult clean(Path workspace, Duration minAge, long quotaBytes) {
        Path tmp = workspace.resolve("tmp");
        if (!Files.isDirectory(tmp)) {
            return new CleanResult(0, 0L, false);
        }
        Instant now = Instant.now();
        Instant cutoff = now.minus(minAge);
        List<EntryStat> survivors = new ArrayList<>();
        int removedCount = 0;
        long removedBytes = 0L;
        for (Path child : listChildren(tmp)) {
            Path name = child.getFileName();
            if (name == null || !isManaged(name.toString())) {
                continue;
            }
            EntryStat stat = statTree(child);
            if (stat == null) {
                continue;
            }
            // 树内最新修改仍在窗口内 = 可能还在被本会话使用，保留。
            if (!stat.latest().isBefore(cutoff)) {
                survivors.add(stat);
                continue;
            }
            long size = deleteTree(child);
            if (size >= 0) {
                removedCount++;
                removedBytes += size;
            } else {
                survivors.add(stat);
            }
        }
        boolean quotaTrimmed = false;
        survivors.sort(Comparator.comparing(EntryStat::latest));
        long retained = survivors.stream().mapToLong(EntryStat::bytes).sum();
        for (EntryStat stat : survivors) {
            if (retained <= quotaBytes) {
                break;
            }
            long size = deleteTree(stat.path());
            if (size >= 0) {
                quotaTrimmed = true;
                removedCount++;
                removedBytes += size;
                retained -= stat.bytes();
            }
        }
        return new CleanResult(removedCount, removedBytes, quotaTrimmed);
    }

    /** 递归统计 workspace：总文件数/总字节与 tmp 子树字节；IO 失败按已统计部分返回。 */
    public static SnapshotStats snapshot(Path workspace) {
        if (!Files.isDirectory(workspace)) {
            return new SnapshotStats(0, 0L, 0L);
        }
        long[] fileCount = {0};
        long[] totalBytes = {0};
        walkQuietly(workspace, (file, attrs) -> {
            fileCount[0]++;
            totalBytes[0] += attrs.size();
        });
        long tmpBytes = 0;
        Path tmp = workspace.resolve("tmp");
        if (Files.isDirectory(tmp)) {
            long[] tmpAccum = {0};
            walkQuietly(tmp, (file, attrs) -> tmpAccum[0] += attrs.size());
            tmpBytes = tmpAccum[0];
        }
        return new SnapshotStats(fileCount[0], totalBytes[0], tmpBytes);
    }

    private interface FileVisitor {
        void accept(Path file, BasicFileAttributes attrs);
    }

    private static void walkQuietly(Path root, FileVisitor visitor) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    visitor.accept(file, Files.readAttributes(file, BasicFileAttributes.class));
                } catch (IOException ignored) {
                    // 单文件属性读失败跳过
                }
            });
        } catch (IOException ignored) {
            // 快照统计尽力而为
        }
    }

    private static List<Path> listChildren(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 目录树体积 + 树内最新 mtime；失败返回 null。 */
    private static EntryStat statTree(Path root) {
        long[] bytes = {0};
        FileTime[] latest = {null};
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : stream.toList()) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    if (attrs.isRegularFile()) {
                        bytes[0] += attrs.size();
                    }
                    FileTime mtime = attrs.lastModifiedTime();
                    if (latest[0] == null || mtime.compareTo(latest[0]) > 0) {
                        latest[0] = mtime;
                    }
                } catch (IOException ignored) {
                    // 单条目失败按已读部分继续
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (latest[0] == null) {
            latest[0] = FileTime.from(Instant.EPOCH);
        }
        return new EntryStat(root, bytes[0], latest[0]);
    }

    /** 递归删除（子先父后）；返回删除字节数，失败返回 -1（调用方保留重试机会）。 */
    private static long deleteTree(Path root) {
        EntryStat stat = statTree(root);
        if (stat == null) {
            return -1;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // 单个删失败：后续父目录删会失败，整体由返回值表达
                }
            });
        } catch (IOException e) {
            return -1;
        }
        return Files.exists(root) ? -1 : stat.bytes();
    }

    /** 台账事件字段（保持 JSONL 里 Map 形状稳定，便于消费方解析）。 */
    public static Map<String, Object> snapshotEventFields(String phase, SnapshotStats stats) {
        return Map.of(
            "phase", phase,
            "fileCount", stats.fileCount,
            "totalBytes", stats.totalBytes,
            "tmpBytes", stats.tmpBytes
        );
    }
}
