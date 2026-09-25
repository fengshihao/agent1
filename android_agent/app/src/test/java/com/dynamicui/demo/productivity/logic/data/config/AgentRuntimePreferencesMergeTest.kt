package com.dynamicui.demo.productivity.logic.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentRuntimePreferencesMergeTest {

    @Test
    fun savedInApp_usesUserApiKeyAndModel() {
        val prefs = AgentRuntimePreferences(
            apiKey = "user-key",
            baseUrl = "https://example.com/v1",
            modelId = "my-model",
            savedInApp = true,
        )
        assertEquals("user-key", AgentRuntimePreferencesMerge.resolveApiKey(prefs))
        assertEquals("https://example.com/v1", AgentRuntimePreferencesMerge.resolveBaseUrl(prefs))
        assertEquals("my-model", AgentRuntimePreferencesMerge.resolveModel(prefs))
        val props = AgentRuntimePreferencesMerge.toProperties(prefs)
        assertEquals("user-key", props.getProperty("apiKey"))
        assertEquals("my-model", props.getProperty("model"))
    }

    @Test
    fun savedInApp_blankKeyMeansUnconfigured() {
        val prefs = AgentRuntimePreferences(apiKey = "", savedInApp = true, modelId = "qwen")
        assertNull(AgentRuntimePreferencesMerge.resolveApiKey(prefs))
    }
}
