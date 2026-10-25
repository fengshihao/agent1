package com.agent1.android.productivity.ui.viewmodel

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPreviewViewModelTest {

    @Test
    fun resolve_rejectsBlankSession() {
        val dir = Files.createTempDirectory("docx-preview-vm")
        val state = DocxPreviewViewModel.resolve(
            workspaceRoot = null,
            agentRoot = dir,
            sessionId = "",
            relativePath = "out/report.docx",
        )
        assertNull(state.file)
        assertTrue(state.error.orEmpty().contains("会话不存在"))
    }

    @Test
    fun resolve_returnsFileForExistingDocx() {
        val dir = Files.createTempDirectory("docx-preview-vm")
        val docx = dir.resolve("out/report.docx")
        Files.createDirectories(docx.parent)
        docx.toFile().writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
        val state = DocxPreviewViewModel.resolve(
            workspaceRoot = dir,
            agentRoot = dir,
            sessionId = "s1",
            relativePath = "out/report.docx",
        )
        assertEquals(docx.toFile(), state.file)
        assertNull(state.error)
        assertTrue(state.shellUrl.contains("shell.html"))
    }
}
