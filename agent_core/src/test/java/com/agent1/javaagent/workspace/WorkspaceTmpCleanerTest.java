package com.agent1.javaagent.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** [WorkspaceTmpCleaner]：tmp 代管条目清扫 / 配额 LRU / 快照统计。 */
class WorkspaceTmpCleanerTest {

    @TempDir
    Path workspace;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(workspace.resolve("tmp"));
    }

    private Path managedDir(String name, int files, int bytesPerFile) throws IOException {
        Path dir = workspace.resolve("tmp").resolve(name);
        Files.createDirectories(dir);
        for (int i = 0; i < files; i++) {
            Files.write(dir.resolve("f" + i), new byte[bytesPerFile]);
        }
        return dir;
    }

    private static void age(Path path, Duration age) throws IOException {
        Instant target = Instant.now().minus(age);
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path p : stream.toList()) {
                Files.setLastModifiedTime(p, FileTime.from(target));
            }
        }
    }

    @Test
    void isManagedCoversPackageAndWebviewPrefixes() {
        assertTrue(WorkspaceTmpCleaner.isManaged("html2pptx-render-abc"));
        assertTrue(WorkspaceTmpCleaner.isManaged("html2pptx-native-x"));
        assertTrue(WorkspaceTmpCleaner.isManaged("docx-render-1"));
        assertTrue(WorkspaceTmpCleaner.isManaged("pptx-render-1"));
        assertTrue(WorkspaceTmpCleaner.isManaged("webview_exec"));
        assertFalse(WorkspaceTmpCleaner.isManaged("my-own-dir"));
        assertFalse(WorkspaceTmpCleaner.isManaged("html2pptx"));
    }

    @Test
    void cleanRemovesExpiredManagedEntriesRecursively() throws IOException {
        Path oldRender = managedDir("html2pptx-render-old", 3, 1024);
        age(oldRender, Duration.ofHours(7));
        Path keep = managedDir("html2pptx-render-fresh", 2, 512);
        Path unmanaged = managedDir("user-notes", 1, 256);
        age(unmanaged, Duration.ofHours(48));

        WorkspaceTmpCleaner.CleanResult result = WorkspaceTmpCleaner.clean(
            workspace, Duration.ofHours(6), WorkspaceTmpCleaner.DEFAULT_QUOTA_BYTES);

        assertFalse(Files.exists(oldRender));
        assertTrue(Files.exists(keep));
        assertTrue(Files.exists(unmanaged));
        assertEquals(1, result.removedCount);
        assertEquals(3 * 1024, result.removedBytes);
        assertFalse(result.quotaTrimmed);
    }

    @Test
    void cleanKeepsTreeTouchedInsideWindow() throws IOException {
        // 目录整体 7h 前，但树内有文件 1h 前写过（AI 刚迭代过）→ 不删。
        Path dir = managedDir("html2pptx-native-x", 2, 100);
        age(dir, Duration.ofHours(7));
        Files.setLastModifiedTime(
            dir.resolve("f0"), FileTime.from(Instant.now().minus(Duration.ofHours(1))));

        WorkspaceTmpCleaner.CleanResult result = WorkspaceTmpCleaner.clean(
            workspace, Duration.ofHours(6), WorkspaceTmpCleaner.DEFAULT_QUOTA_BYTES);

        assertTrue(Files.exists(dir));
        assertEquals(0, result.removedCount);
    }

    @Test
    void cleanEnforcesQuotaByLru() throws IOException {
        Path older = managedDir("html2pptx-render-a", 1, 600);
        Path newer = managedDir("html2pptx-render-b", 1, 600);
        age(older, Duration.ofHours(1));
        age(newer, Duration.ofMinutes(5));

        // 配额 1000B：两条目共 1200B，最旧的 a 应被 LRU 掉，b 保留。
        WorkspaceTmpCleaner.CleanResult result = WorkspaceTmpCleaner.clean(
            workspace, Duration.ofHours(6), 1000);

        assertFalse(Files.exists(older));
        assertTrue(Files.exists(newer));
        assertEquals(1, result.removedCount);
        assertEquals(600, result.removedBytes);
        assertTrue(result.quotaTrimmed);
    }

    @Test
    void cleanWithoutTmpDirIsNoOp(@TempDir Path emptyWorkspace) {
        WorkspaceTmpCleaner.CleanResult result = WorkspaceTmpCleaner.clean(
            emptyWorkspace, Duration.ofHours(6), 1000);
        assertEquals(0, result.removedCount);
        assertEquals(0L, result.removedBytes);
        assertFalse(result.quotaTrimmed);
    }

    @Test
    void snapshotCountsFilesAndTmpShare() throws IOException {
        Files.write(workspace.resolve("deck.html"), new byte[100]);
        managedDir("html2pptx-render-x", 2, 50);
        Files.write(workspace.resolve("out.pptx"), new byte[10]);

        WorkspaceTmpCleaner.SnapshotStats stats = WorkspaceTmpCleaner.snapshot(workspace);

        assertEquals(4L, stats.fileCount);
        assertEquals(100L + 2 * 50L + 10L, stats.totalBytes);
        assertEquals(2 * 50L, stats.tmpBytes);

        var fields = WorkspaceTmpCleaner.snapshotEventFields("end", stats);
        assertEquals("end", fields.get("phase"));
        assertEquals(stats.totalBytes, fields.get("totalBytes"));
        assertEquals(stats.tmpBytes, fields.get("tmpBytes"));
    }

    @Test
    void snapshotMissingWorkspaceReturnsZeros(@TempDir Path unused) {
        WorkspaceTmpCleaner.SnapshotStats stats =
            WorkspaceTmpCleaner.snapshot(unused.resolve("nope"));
        assertEquals(0L, stats.fileCount);
        assertEquals(0L, stats.totalBytes);
        assertEquals(0L, stats.tmpBytes);
    }
}
