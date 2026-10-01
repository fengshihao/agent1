package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSettingsModelsTest {

    @Test
    fun providerOptionsIncludeDeepseek() {
        val deepseek = providerOptions().first { it.id == PROVIDER_DEEPSEEK }
        assertEquals("DeepSeek", deepseek.displayName)
        assertEquals("https://api.deepseek.com", deepseek.defaultBaseUrl)
        assertEquals("deepseek-flash", deepseek.defaultModelId)
    }
}
