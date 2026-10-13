package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentComposerLabelTest {

    @Test
    fun resolve_prefersPendingOverCommitted() {
        val resolved = AttachmentComposerLabel.resolve(
            pending = listOf(PendingAttachmentImport("u1", "a.pdf")),
            committed = listOf(
                SessionAccessibleFilesStore.Entry("imports/b.pdf", "b.pdf", ""),
            ),
        )
        assertEquals(1, resolved.selectedCount)
        assertEquals("a.pdf", resolved.singleFileName)
        assertTrue(resolved.unsavedDraft)
    }

    @Test
    fun ellipsizeStart_keepsSuffixWithLeadingDots() {
        val out = AttachmentComposerLabel.ellipsizeStart("very-long-report-name.pdf", maxChars = 14)
        assertTrue(out.startsWith("..."))
        assertTrue(out.endsWith(".pdf"))
        assertEquals(14, out.length)
    }

    @Test
    fun resolve_countsVisibleCommittedAfterStagedRemovals() {
        val resolved = AttachmentComposerLabel.resolve(
            pending = emptyList(),
            committed = listOf(
                SessionAccessibleFilesStore.Entry("imports/a.pdf", "a.pdf", ""),
                SessionAccessibleFilesStore.Entry("imports/b.pdf", "b.pdf", ""),
            ),
            pendingRemovalPaths = setOf("imports/a.pdf"),
        )
        assertEquals(1, resolved.selectedCount)
        assertEquals("b.pdf", resolved.singleFileName)
    }

    @Test
    fun resolve_multiPending() {
        val resolved = AttachmentComposerLabel.resolve(
            pending = listOf(
                PendingAttachmentImport("u1", "a.pdf"),
                PendingAttachmentImport("u2", "b.pdf"),
            ),
            committed = emptyList(),
        )
        assertTrue(resolved.isMulti)
        assertEquals(null, resolved.singleFileName)
        assertEquals(2, resolved.selectedCount)
    }
}
