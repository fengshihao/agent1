package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceAccessibleFileDeleteTest {

    @Test
    fun deleteRelativeFiles_removesUnderWorkspace() {
        val workspace = Files.createTempDirectory("ws-del")
        val file = workspace.resolve("imports").resolve("x.txt")
        Files.createDirectories(file.parent)
        file.writeText("x")
        WorkspaceAccessibleFileDelete.deleteRelativeFiles(workspace, listOf("imports/x.txt"))
        assertFalse(Files.exists(file))
    }
}
