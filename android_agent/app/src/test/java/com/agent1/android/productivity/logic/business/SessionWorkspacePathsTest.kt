package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionWorkspacePathsTest {

    @Test
    fun normalizeWorkspaceRelativePath_stripsWorkspacePrefix() {
        assertEquals(
            "out/report.docx",
            SessionWorkspacePaths.normalizeWorkspaceRelativePath("workspace/out/report.docx"),
        )
    }

    @Test
    fun resolveFile_findsFileAfterWorkspacePrefix() {
        val root = Files.createTempDirectory("ws-paths")
        val file = root.resolve("out/report.docx").toFile()
        file.parentFile?.mkdirs()
        file.writeText("hello")
        val resolved = SessionWorkspacePaths.resolveFile(root, "workspace/out/report.docx")
        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun canonicalWorkspaceRelative_stripsAbsolutePathUnderWorkspace() {
        val root = Files.createTempDirectory("ws-abs").toAbsolutePath()
        val file = root.resolve("out/report.docx")
        assertEquals(
            "out/report.docx",
            SessionWorkspacePaths.canonicalWorkspaceRelative(file.toString(), root),
        )
    }

    @Test
    fun resolveFile_findsFileFromAbsoluteWorkspacePath() {
        val root = Files.createTempDirectory("ws-abs-resolve")
        val file = root.resolve("notes.md").toFile()
        file.writeText("# hi")
        val resolved = SessionWorkspacePaths.resolveFile(root, file.absolutePath)
        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved?.absolutePath)
    }
}
