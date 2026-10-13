package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingAttachmentImportsTest {

    @Test
    fun merge_appendsNewUrisAndDedupesByUriString() {
        val existing = listOf(
            PendingAttachmentImport("content://a/1", "a.pdf"),
        )
        val incoming = listOf(
            PendingAttachmentImport("content://a/1", "dup"),
            PendingAttachmentImport("content://a/2", "b.pdf"),
        )
        val merged = PendingAttachmentImports.merge(existing, incoming)
        assertEquals(2, merged.size)
        assertEquals("a.pdf", merged[0].displayName)
        assertEquals("content://a/2", merged[1].uriString)
    }
}
