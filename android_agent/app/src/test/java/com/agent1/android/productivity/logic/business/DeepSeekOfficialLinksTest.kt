package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekOfficialLinksTest {

    @Test
    fun officialHostOnly() {
        listOf(
            DeepSeekOfficialLinks.HOME,
            DeepSeekOfficialLinks.TOP_UP,
            DeepSeekOfficialLinks.API_KEYS,
            DeepSeekOfficialLinks.PRICING,
        ).forEach { url ->
            assertTrue(url, url.startsWith("https://") && url.contains("deepseek.com"))
        }
    }
}
