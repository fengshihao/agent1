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
}
