package com.agent1.android.productivity.logic.business

import com.agent1.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSettingsModelsTest {

    @Test
    fun providerOptionsIncludeDeepseek() {
        val deepseek = providerOptions().first { it.id == PROVIDER_DEEPSEEK }
        assertEquals(R.string.provider_deepseek, deepseek.displayNameRes)
        assertEquals("https://api.deepseek.com", deepseek.defaultBaseUrl)
        assertEquals("deepseek-flash", deepseek.defaultModelId)
        assertEquals(listOf("deepseek", "dashscope", "zhipu_coding"), providerOptions().map { it.id })
        assertEquals(listOf("alibaba-cn"), modelsDevProviderKeys(PROVIDER_DASHSCOPE))
        assertEquals(
            listOf("zhipuai-coding-plan", "zai-coding-plan"),
            modelsDevProviderKeys(PROVIDER_ZHIPU_CODING),
        )
    }
}
