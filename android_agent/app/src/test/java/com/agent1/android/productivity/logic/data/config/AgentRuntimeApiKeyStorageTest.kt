package com.agent1.android.productivity.logic.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentRuntimeApiKeyStorageTest {

    @Test
    fun resolve_prefersPerProviderKey() {
        val key = AgentRuntimeApiKeyStorage.resolveApiKeyForProvider(
            providerId = "deepseek",
            currentProviderId = "dashscope",
            legacyApiKey = "legacy",
            storedForProvider = "deepseek-key",
        )
        assertEquals("deepseek-key", key)
    }

    @Test
    fun resolve_fallsBackToLegacyForCurrentProvider() {
        val key = AgentRuntimeApiKeyStorage.resolveApiKeyForProvider(
            providerId = "dashscope",
            currentProviderId = "dashscope",
            legacyApiKey = "legacy",
            storedForProvider = null,
        )
        assertEquals("legacy", key)
    }

    @Test
    fun resolve_emptyWhenNoKeyForOtherProvider() {
        val key = AgentRuntimeApiKeyStorage.resolveApiKeyForProvider(
            providerId = "openai",
            currentProviderId = "dashscope",
            legacyApiKey = "legacy",
            storedForProvider = null,
        )
        assertEquals("", key)
    }
}
