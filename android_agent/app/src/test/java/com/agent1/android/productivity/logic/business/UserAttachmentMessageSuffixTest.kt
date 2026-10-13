package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAttachmentMessageSuffixTest {

    @Test
    fun appendToUserMessage_joinsBodyAndChanges() {
        val out = UserAttachmentMessageSuffix.appendToUserMessage(
            "请分析",
            UserAttachmentMessageSuffix.ChangeSet(
                addedPaths = listOf("imports/a.pdf"),
                removedPaths = listOf("imports/b.pdf"),
            ),
        )
        assertTrue(out.startsWith("请分析"))
        assertTrue(out.contains("imports/a.pdf"))
        assertTrue(out.contains("imports/b.pdf"))
    }
}
