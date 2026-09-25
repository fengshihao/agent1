package com.dynamicui.demo.productivity.logic.data.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** 加密存储 API Key 与模型连接参数（仅本机 App 沙箱）。 */
class AgentRuntimePreferencesStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs by lazy { createPrefs(appContext) }

    fun read(): AgentRuntimePreferences {
        return AgentRuntimePreferences(
            providerId = prefs.getString(KEY_PROVIDER, ModelProviderDefaults.PROVIDER_DASHSCOPE)
                ?: ModelProviderDefaults.PROVIDER_DASHSCOPE,
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            baseUrl = prefs.getString(KEY_BASE_URL, ModelProviderDefaults.DEFAULT_BASE_URL)
                ?: ModelProviderDefaults.DEFAULT_BASE_URL,
            modelId = prefs.getString(KEY_MODEL, "").orEmpty(),
            maxContextTurns = prefs.getInt(KEY_MAX_CONTEXT_TURNS, 0),
            maxContextMessages = prefs.getInt(KEY_MAX_CONTEXT_MESSAGES, 0),
            maxTurnsPerRun = prefs.getInt(KEY_MAX_TURNS_PER_RUN, 0),
            maxToolCallsPerRun = prefs.getInt(KEY_MAX_TOOL_CALLS_PER_RUN, 0),
            savedInApp = prefs.getBoolean(KEY_SAVED_IN_APP, false),
        )
    }

    fun save(preferences: AgentRuntimePreferences) {
        prefs.edit()
            .putString(KEY_PROVIDER, preferences.providerId)
            .putString(KEY_API_KEY, preferences.apiKey.trim())
            .putString(KEY_BASE_URL, preferences.baseUrl.trim())
            .putString(KEY_MODEL, preferences.modelId.trim())
            .putInt(KEY_MAX_CONTEXT_TURNS, preferences.maxContextTurns)
            .putInt(KEY_MAX_CONTEXT_MESSAGES, preferences.maxContextMessages)
            .putInt(KEY_MAX_TURNS_PER_RUN, preferences.maxTurnsPerRun)
            .putInt(KEY_MAX_TOOL_CALLS_PER_RUN, preferences.maxToolCallsPerRun)
            .putBoolean(KEY_SAVED_IN_APP, true)
            .apply()
    }

    fun clearToBuildDefaults() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "agent_runtime_prefs"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_MAX_CONTEXT_TURNS = "max_context_turns"
        private const val KEY_MAX_CONTEXT_MESSAGES = "max_context_messages"
        private const val KEY_MAX_TURNS_PER_RUN = "max_turns_per_run"
        private const val KEY_MAX_TOOL_CALLS_PER_RUN = "max_tool_calls_per_run"
        private const val KEY_SAVED_IN_APP = "saved_in_app"

        private fun createPrefs(context: Context) = EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
