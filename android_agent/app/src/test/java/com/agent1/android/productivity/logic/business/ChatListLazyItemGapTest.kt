package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListLazyItemGapTest {

    @Test
    fun firstRow_neverPads() {
        assertFalse(ChatListLazyItemGap.shouldPadTop(0, null))
        assertFalse(ChatListLazyItemGap.shouldPadTop(0, false))
        assertFalse(ChatListLazyItemGap.shouldPadTop(0, true))
    }

    @Test
    fun messageContinuation_tightTop_skipsGap() {
        assertFalse(ChatListLazyItemGap.shouldPadTop(2, true))
    }

    @Test
    fun messageNewSegment_orNonMessage_addsGap() {
        assertTrue(ChatListLazyItemGap.shouldPadTop(1, false))
        assertTrue(ChatListLazyItemGap.shouldPadTop(1, null))
    }
}
