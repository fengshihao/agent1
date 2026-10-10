package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileImportTest {

    @Test
    fun copyLocalFileToImports_landsInImportsWithUniqueName() {
        val workspace = Files.createTempDirectory("ws-import")
        val sourceDir = Files.createTempDirectory("src-import")
        val source = sourceDir.resolve("chart.png")
        source.writeText("png-bytes")

        val result = WorkspaceFileImport.copyLocalFileToImports(workspace, source, "chart.png")

        assertNotNull(result)
        assertEquals("imports/chart.png", result?.workspaceRelativePath)
        assertEquals("chart.png", result?.displayName)
        assertTrue(Files.isRegularFile(workspace.resolve("imports").resolve("chart.png")))
    }

    @Test
    fun copyLocalFileToImports_avoidsOverwriteAndKeepsBothCopies() {
        val workspace = Files.createTempDirectory("ws-import-2")
        val sourceDir = Files.createTempDirectory("src-import-2")
        val first = sourceDir.resolve("report.docx").apply { writeText("v1") }
        val second = sourceDir.resolve("other.docx").apply { writeText("v2") }
        WorkspaceFileImport.copyLocalFileToImports(workspace, first, "report.docx")

        val result = WorkspaceFileImport.copyLocalFileToImports(workspace, second, "report.docx")

        assertNotNull(result)
        assertEquals("imports/report-2.docx", result?.workspaceRelativePath)
        assertEquals("v1", workspace.resolve("imports").resolve("report.docx").toFile().readText())
        assertEquals("v2", workspace.resolve("imports").resolve("report-2.docx").toFile().readText())
    }

    @Test
    fun copyLocalFileToImports_sanitizesDisplayName() {
        val workspace = Files.createTempDirectory("ws-import-3")
        val sourceDir = Files.createTempDirectory("src-import-3")
        val source = sourceDir.resolve("orig.md").apply { writeText("# hi") }

        val result = WorkspaceFileImport.copyLocalFileToImports(workspace, source, "报告/最终:版.md")

        assertNotNull(result)
        assertTrue(result?.workspaceRelativePath.orEmpty().startsWith("imports/"))
        assertTrue(Files.isRegularFile(workspace.resolve(result?.workspaceRelativePath.orEmpty())))
    }

    @Test
    fun copyLocalFileToImports_blankDisplayNameFallsBackToSourceName() {
        val workspace = Files.createTempDirectory("ws-import-4")
        val sourceDir = Files.createTempDirectory("src-import-4")
        val source = sourceDir.resolve("fallback.txt").apply { writeText("x") }

        val result = WorkspaceFileImport.copyLocalFileToImports(workspace, source, "")

        assertNotNull(result)
        assertTrue(result?.workspaceRelativePath.orEmpty().endsWith("fallback.txt"))
    }

    @Test
    fun copyLocalFileToImports_missingSourceReturnsNull() {
        val workspace = Files.createTempDirectory("ws-import-5")
        val sourceDir = Files.createTempDirectory("src-import-5")
        val missing = sourceDir.resolve("nope.txt")

        val result = WorkspaceFileImport.copyLocalFileToImports(workspace, missing, "nope.txt")

        assertNull(result)
    }
}