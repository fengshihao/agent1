package com.agent1.android.productivity.ui.viewmodel

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlPreviewViewModelTest {

    @Test
    fun resolve_rejectsBlankSession() {
        val state = HtmlPreviewViewModel.resolve(
            workspaceRoot = null,
            sessionId = "",
            relativePath = "out/index.html",
        )
        assertNull(state.file)
        assertTrue(state.error.orEmpty().contains("会话不存在"))
    }

    @Test
    fun resolve_rejectsNonPreviewableExt() {
        val dir = Files.createTempDirectory("preview-vm")
        val state = HtmlPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "out/report.docx",
        )
        assertNull(state.file)
        assertTrue(state.error.orEmpty().contains("不支持的预览文件类型"))
    }

    @Test
    fun resolve_rejectsMissingFile() {
        val dir = Files.createTempDirectory("preview-vm")
        val state = HtmlPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "out/index.html",
        )
        assertNull(state.file)
        assertTrue(state.error.orEmpty().contains("文件不存在或已被移动"))
    }

    @Test
    fun resolve_rejectsPathEscape() {
        val dir = Files.createTempDirectory("preview-vm")
        val state = HtmlPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "../outside.html",
        )
        assertNull(state.file)
        assertNotNull(state.error)
    }

    @Test
    fun resolve_returnsFileForExistingHtml() {
        val dir = Files.createTempDirectory("preview-vm")
        val html = dir.resolve("out/index.html")
        Files.createDirectories(html.parent)
        html.toFile().writeText("<html><body>hi</body></html>")
        val state = HtmlPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "out/index.html",
        )
        assertEquals(html.toFile(), state.file)
        assertNull(state.error)
    }
}