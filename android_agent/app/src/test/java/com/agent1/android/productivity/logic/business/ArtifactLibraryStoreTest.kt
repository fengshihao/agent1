package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactLibraryStoreTest {

    private fun sessionWorkspace(root: Path, sessionId: String): Path {
        // listArtifacts 的入参是 sessions 根（见 sessionsRoot()），会话布局为 <id>/workspace，
        // 这里不能再多套一层 sessions/
        val ws = root.resolve(sessionId).resolve("workspace")
        Files.createDirectories(ws)
        return ws
    }

    @Test
    fun isArtifactRelativePath_excludesImportsTmpAndHidden() {
        assertTrue(ArtifactLibraryStore.isArtifactRelativePath("out/report.docx"))
        assertTrue(ArtifactLibraryStore.isArtifactRelativePath("index.html"))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath("imports/a.png"))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath("imports"))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath("tmp/webview_exec/x.b64"))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath("tmp"))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath(".hidden/a.txt"))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath("out/.secret.txt"))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath(""))
        assertFalse(ArtifactLibraryStore.isArtifactRelativePath("/abs/path"))
    }

    @Test
    fun listArtifacts_scansAllSessionsAndSortsByModifiedTimeDesc() {
        val root = Files.createTempDirectory("art-scan")
        val ws1 = sessionWorkspace(root, "s1")
        val ws2 = sessionWorkspace(root, "s2")
        val older = ws1.resolve("report.docx")
        val newer = ws2.resolve("page.html")
        older.writeText("old")
        newer.writeText("<html>new</html>")
        val now = System.currentTimeMillis()
        Files.setLastModifiedTime(older, FileTime.fromMillis(now - 5_000))
        Files.setLastModifiedTime(newer, FileTime.fromMillis(now))

        val entries = ArtifactLibraryStore.listArtifacts(root)

        assertEquals(listOf("page.html", "report.docx"), entries.map { it.fileName })
        assertEquals(listOf("s2", "s1"), entries.map { it.sessionId })
        assertEquals("page.html", entries[0].workspaceRelativePath)
        assertEquals("<html>new</html>".length.toLong(), entries[0].sizeBytes)
    }

    @Test
    fun listArtifacts_skipsImportsTmpAndHiddenEntries() {
        val root = Files.createTempDirectory("art-skip")
        val ws = sessionWorkspace(root, "s1")
        Files.createDirectories(ws.resolve("imports"))
        Files.createDirectories(ws.resolve("tmp").resolve("webview_exec"))
        Files.createDirectories(ws.resolve(".hidden"))
        ws.resolve("imports").resolve("user.png").writeText("u")
        ws.resolve("tmp").resolve("webview_exec").resolve("x.b64").writeText("b64")
        ws.resolve(".hidden").resolve("a.txt").writeText("h")
        ws.resolve("chart.svg").writeText("<svg/>")

        val entries = ArtifactLibraryStore.listArtifacts(root)

        assertEquals(listOf("chart.svg"), entries.map { it.fileName })
    }

    @Test
    fun listArtifacts_ignoresMissingOrNonSessionDirs() {
        val root = Files.createTempDirectory("art-empty")
        assertTrue(ArtifactLibraryStore.listArtifacts(root).isEmpty())
        assertTrue(ArtifactLibraryStore.listArtifacts(root.resolve("nope")).isEmpty())
        Files.createDirectories(root.resolve("sessions").resolve("not-a-session"))
        assertTrue(ArtifactLibraryStore.listArtifacts(root).isEmpty())
    }

    @Test
    fun listArtifacts_includesNestedDirectories() {
        val root = Files.createTempDirectory("art-nested")
        val ws = sessionWorkspace(root, "s1")
        Files.createDirectories(ws.resolve("out").resolve("v2"))
        ws.resolve("out").resolve("v2").resolve("final.md").writeText("# done")

        val entries = ArtifactLibraryStore.listArtifacts(root)

        assertEquals(listOf("out/v2/final.md"), entries.map { it.workspaceRelativePath })
        assertEquals("s1/out/v2/final.md", entries[0].stableKey)
        assertEquals(ArtifactLibraryStore.ArtifactRef("s1", "out/v2/final.md"), entries[0].toRef())
    }
}