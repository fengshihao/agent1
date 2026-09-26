package com.dynamicui.demo.productivity.logic.data.config

import com.agent1.javaagent.config.AgentRuntimeDefaults
import com.dynamicui.demo.BuildConfig
import java.util.Properties

/** 合并 App 偏好与编译期 BuildConfig，供 {@link AgentRuntimeConfig#fromProperties} 使用。 */
object AgentRuntimePreferencesMerge {

    fun toProperties(prefs: AgentRuntimePreferences): Properties {
        val props = Properties()
        val apiKey = resolveApiKey(prefs)
        if (!apiKey.isNullOrBlank()) {
            props.setProperty("apiKey", apiKey)
        }
        val baseUrl = resolveBaseUrl(prefs)
        if (!baseUrl.isNullOrBlank()) {
            props.setProperty("baseUrl", baseUrl)
        }
        val model = resolveModel(prefs)
        if (!model.isNullOrBlank()) {
            props.setProperty("model", model)
        }
        if (prefs.maxContextTurns > 0) {
            props.setProperty("maxContextTurns", prefs.maxContextTurns.toString())
        }
        if (prefs.maxContextMessages > 0) {
            props.setProperty("maxContextMessages", prefs.maxContextMessages.toString())
        }
        if (prefs.maxTurnsPerRun > 0) {
            props.setProperty("maxTurnsPerRun", prefs.maxTurnsPerRun.toString())
        }
        if (prefs.maxToolCallsPerRun > 0) {
            props.setProperty("maxToolCallsPerRun", prefs.maxToolCallsPerRun.toString())
        }
        return props
    }

    fun resolveApiKey(prefs: AgentRuntimePreferences): String? {
        if (prefs.savedInApp) {
            return prefs.apiKey.trim().ifBlank { null }
        }
        return firstNonBlank(
            prefs.apiKey,
            BuildConfig.QWEN_API_KEY,
            BuildConfig.DASHSCOPE_API_KEY,
        )
    }

    fun resolveBaseUrl(prefs: AgentRuntimePreferences): String? {
        if (prefs.savedInApp && prefs.baseUrl.isNotBlank()) {
            return prefs.baseUrl.trim()
        }
        return firstNonBlank(
            prefs.baseUrl,
            BuildConfig.QWEN_BASE_URL,
            BuildConfig.DASHSCOPE_BASE_URL,
            AgentRuntimeDefaults.DEFAULT_BASE_URL,
        )
    }

    fun resolveModel(prefs: AgentRuntimePreferences): String? {
        if (prefs.savedInApp && prefs.modelId.isNotBlank()) {
            return prefs.modelId.trim()
        }
        return firstNonBlank(
            prefs.modelId,
            BuildConfig.QWEN_MODEL,
            BuildConfig.DEFAULT_MODEL,
            AgentRuntimeDefaults.DEFAULT_MODEL,
        )
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (v in values) {
            if (!v.isNullOrBlank()) {
                return v.trim()
            }
        }
        return null
    }
}
