package com.agent1.android.productivity.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class RunTokenSummaryTest {

    @Test
    fun formatCompactTokenCount_underOneK_usesInteger() {
        assertEquals("999", RunTokenSummary.formatCompactTokenCount(999))
        assertEquals("0", RunTokenSummary.formatCompactTokenCount(0))
    }

    @Test
    fun formatCompactTokenCount_atOrAboveOneK_usesKSuffix() {
        assertEquals("1k", RunTokenSummary.formatCompactTokenCount(1_000))
        assertEquals("1.1k", RunTokenSummary.formatCompactTokenCount(1_100))
        assertEquals("2.2k", RunTokenSummary.formatCompactTokenCount(2_200))
        assertEquals("12.3k", RunTokenSummary.formatCompactTokenCount(12_345))
    }
}
