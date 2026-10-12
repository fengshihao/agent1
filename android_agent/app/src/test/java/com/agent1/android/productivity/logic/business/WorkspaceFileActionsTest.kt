package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileActionsTest {

    @Test
    fun isPreviewableInApp_acceptsHtmlOnly() {
        assertTrue(WorkspaceFileActions.isPreviewableInApp("out/index.html"))
        assertTrue(WorkspaceFileActions.isPreviewableInApp("demo.htm"))
        assertTrue(WorkspaceFileActions.isPreviewableInApp("INDEX.HTML"))
        assertFalse(WorkspaceFileActions.isPreviewableInApp("out/report.docx"))
        assertFalse(WorkspaceFileActions.isPreviewableInApp("cat.png"))
        assertFalse(WorkspaceFileActions.isPreviewableInApp("notes.md"))
        assertFalse(WorkspaceFileActions.isPreviewableInApp("html"))
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