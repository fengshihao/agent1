package com.agent1.android.productivity.ui.view

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListAutoscrollTest {

    @Test
    fun isAtBottom_emptyList() {
        assertTrue(
            ChatListAutoscroll.isAtBottom(
                totalItems = 0,
                lastVisibleIndex = -1,
                lastVisibleOffset = 0,
                lastVisibleSize = 0,
                viewportEndOffset = 800,
            ),
        )
    }

    @Test
    fun isAtBottom_lastItemFullyVisible() {
        assertTrue(
            ChatListAutoscroll.isAtBottom(
                totalItems = 5,
                lastVisibleIndex = 4,
                lastVisibleOffset = 700,
                lastVisibleSize = 80,
                viewportEndOffset = 800,
            ),
        )
    }

    @Test
    fun isAtBottom_falseWhenLastItemStartIsPinnedToTop() {
        assertFalse(
            ChatListAutoscroll.isAtBottom(
                totalItems = 3,
                lastVisibleIndex = 2,
                lastVisibleOffset = 0,
                lastVisibleSize = 2000,
                viewportEndOffset = 800,
            ),
        )
    }

    @Test
    fun isAtBottom_falseWhenEarlierItemIsLastVisible() {
        assertFalse(
            ChatListAutoscroll.isAtBottom(
                totalItems = 6,
                lastVisibleIndex = 3,
                lastVisibleOffset = 100,
                lastVisibleSize = 200,
                viewportEndOffset = 800,
            ),
        )
    }
}
