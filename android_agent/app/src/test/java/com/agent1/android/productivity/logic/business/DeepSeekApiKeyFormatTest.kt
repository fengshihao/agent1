package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekApiKeyFormatTest {

    @Test
    fun plausibleSkDashKey() {
        assertTrue(DeepSeekApiKeyFormat.isPlausible("sk-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(DeepSeekApiKeyFormat.isPlausible("  sk_abcdefghijklmnop  "))
    }

    @Test
    fun rejectsBlankOrShortOrWrongPrefix() {
        assertFalse(DeepSeekApiKeyFormat.isPlausible(""))
        assertFalse(DeepSeekApiKeyFormat.isPlausible("   "))
        assertFalse(DeepSeekApiKeyFormat.isPlausible("sk-short"))
        assertFalse(DeepSeekApiKeyFormat.isPlausible("pk-abcdefghijklmnopqrstuvwxyz"))
        assertFalse(DeepSeekApiKeyFormat.isPlausible("not-a-key-at-all-123456"))
    }

    @Test
    fun normalizeTrims() {
        assertEquals("sk-abcdefghijklmnopqrstuvwxyz", DeepSeekApiKeyFormat.normalize("  sk-abcdefghijklmnopqrstuvwxyz\n"))
    }
}
