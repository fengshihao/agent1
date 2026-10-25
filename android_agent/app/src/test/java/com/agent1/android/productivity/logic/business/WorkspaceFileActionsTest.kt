package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileActionsTest {

    @Test
    fun isPreviewableInApp_acceptsHtmlAndDocx() {
        assertTrue(WorkspaceFileActions.isPreviewableInApp("out/index.html"))
        assertTrue(WorkspaceFileActions.isPreviewableInApp("demo.htm"))
        assertTrue(WorkspaceFileActions.isPreviewableInApp("INDEX.HTML"))
        assertTrue(WorkspaceFileActions.isPreviewableInApp("out/report.docx"))
        assertTrue(WorkspaceFileActions.isDocxPreviewableInApp("out/report.docx"))
        assertFalse(WorkspaceFileActions.isHtmlPreviewableInApp("out/report.docx"))
        assertFalse(WorkspaceFileActions.isPreviewableInApp("cat.png"))
        // md / markdown 现走墨览（molan）内置预览（md-preview 能力）。
        assertTrue(WorkspaceFileActions.isMdPreviewableInApp("notes.md"))
        assertTrue(WorkspaceFileActions.isMdPreviewableInApp("out/notes.MARKDOWN"))
        assertTrue(WorkspaceFileActions.isPreviewableInApp("notes.md"))
        assertFalse(WorkspaceFileActions.isMdPreviewableInApp("html"))
        assertFalse(WorkspaceFileActions.isPreviewableInApp("no-ext"))
    }

    @Test
    fun mimeTypeForRelativePath_mapsHtml() {
        assertEquals("text/html", WorkspaceFileActions.mimeTypeForRelativePath("out/index.html"))
        assertEquals("text/html", WorkspaceFileActions.mimeTypeForRelativePath("demo.htm"))
        // 其余类型映射不回退
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            WorkspaceFileActions.mimeTypeForRelativePath("out/report.docx"),
        )
    }
}