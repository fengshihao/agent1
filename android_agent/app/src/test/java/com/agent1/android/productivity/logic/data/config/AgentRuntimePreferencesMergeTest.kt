package com.agent1.android.productivity.logic.data.config

import com.agent1.javaagent.config.AgentRuntimeDefaults
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
    fun webSearchKeyIsPassedThroughWhenPresent() {
        val prefs = AgentRuntimePreferences(
            apiKey = "user-key",
            savedInApp = true,
            webSearchApiKey = "tvly-user",
        )
        val props = AgentRuntimePreferencesMerge.toProperties(prefs)
        assertEquals("tvly-user", props.getProperty("webSearchApiKey"))
        assertEquals(AgentRuntimeDefaults.DEFAULT_TAVILY_BASE_URL, props.getProperty("webSearchBaseUrl"))
    }

    @Test
    fun blankWebSearchKeyIsOmitted() {
        val prefs = AgentRuntimePreferences(apiKey = "user-key", savedInApp = true, webSearchApiKey = "  ")
        val props = AgentRuntimePreferencesMerge.toProperties(prefs)
        assertNull(props.getProperty("webSearchApiKey"))
    }

    @Test
    fun savedInApp_blankKeyMeansUnconfigured() {
        val prefs = AgentRuntimePreferences(apiKey = "", savedInApp = true, modelId = "qwen")
        assertNull(AgentRuntimePreferencesMerge.resolveApiKey(prefs))
    }
}
