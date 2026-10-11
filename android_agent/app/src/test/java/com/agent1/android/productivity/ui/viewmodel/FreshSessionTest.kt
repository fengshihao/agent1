package com.agent1.android.productivity.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshSessionTest {

    @Test
    fun emptyLoadedSessionIsFresh() {
        assertTrue(ChatUiState(isLoadingTranscript = false).isFreshSession())
    }

    @Test
    fun loadingOrRunningIsNotFresh() {
        assertFalse(ChatUiState(isLoadingTranscript = true).isFreshSession())
        assertFalse(
            ChatUiState(isLoadingTranscript = false, isRunning = true).isFreshSession(),
        )
    }

    @Test
    fun visibleHistoryIsNotFresh() {
        val withLine = ChatUiState(
            isLoadingTranscript = false,
            lines = listOf(ChatLine(role = "user", content = "hi")),
        )
        assertFalse(withLine.isFreshSession())
    }

    @Test
    fun hiddenOnlyLinesStillCountAsFresh() {
        val hidden = ChatUiState(
            isLoadingTranscript = false,
            lines = listOf(ChatLine(role = "assistant", content = "x", hideInChat = true)),
        )
        assertTrue(hidden.isFreshSession())
    }
}
