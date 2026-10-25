package com.agent1.android.productivity.ui.viewmodel

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MolanPreviewViewModelTest {

    @Test
    fun resolve_rejectsBlankSession() {
        val state = MolanPreviewViewModel.resolve(
            workspaceRoot = null,
            sessionId = "",
            relativePath = "out/report.md",
        )
        assertNull(state.file)
        assertTrue(state.error.orEmpty().contains("会话不存在"))
    }

    @Test
    fun resolve_rejectsNonMdExt() {
        val dir = Files.createTempDirectory("molan-preview-vm")
        val state = MolanPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "out/report.docx",
        )
        assertNull(state.file)
        assertTrue(state.error.orEmpty().contains("不支持的预览文件类型"))
    }

    @Test
    fun resolve_rejectsMissingFile() {
        val dir = Files.createTempDirectory("molan-preview-vm")
        val state = MolanPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "out/report.md",
        )
        assertNull(state.file)
        assertTrue(state.error.orEmpty().contains("文件不存在或已被移动"))
    }

    @Test
    fun resolve_rejectsPathEscape() {
        val dir = Files.createTempDirectory("molan-preview-vm")
        val state = MolanPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "../outside.md",
        )
        assertNull(state.file)
        assertNotNull(state.error)
    }

    @Test
    fun resolve_acceptsMdAndMarkdownExt() {
        val dir = Files.createTempDirectory("molan-preview-vm")
        val md = dir.resolve("out/report.md")
        Files.createDirectories(md.parent)
        md.toFile().writeText("# 报告")
        val mdMarkdown = dir.resolve("out/notes.markdown")
        mdMarkdown.toFile().writeText("# 笔记")

        val state = MolanPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "out/report.md",
        )
        assertEquals(md.toFile(), state.file)
        assertNull(state.error)

        val stateMarkdown = MolanPreviewViewModel.resolve(
            workspaceRoot = dir,
            sessionId = "s1",
            relativePath = "out/notes.markdown",
        )
        assertEquals(mdMarkdown.toFile(), stateMarkdown.file)
        assertNull(stateMarkdown.error)
    }
}
