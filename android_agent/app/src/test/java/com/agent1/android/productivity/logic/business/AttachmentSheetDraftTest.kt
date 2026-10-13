package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentSheetDraftTest {

    @Test
    fun rows_mergesKeptAndNewPicksInOneList() {
        val rows = AttachmentSheetDraft.rows(
            committed = listOf(
                SessionAccessibleFilesStore.Entry("imports/old.pdf", "old.pdf", ""),
            ),
            pendingImports = listOf(
                PendingAttachmentImport("content://x", "new.pdf"),
            ),
            pendingRemovals = emptySet(),
        )
        assertEquals(2, rows.size)
    }

    @Test
    fun removeFromDraft_stagesRemovalForKeptRow() {
        val kept = AttachmentSheetDraft.Row.Kept(
            SessionAccessibleFilesStore.Entry("imports/a.pdf", "a.pdf", ""),
        )
        val (_, removals) = AttachmentSheetDraft.removeFromDraft(
            kept,
            pendingImports = emptyList(),
            pendingRemovals = emptySet(),
        )
        assertEquals(setOf("imports/a.pdf"), removals)
    }
}
