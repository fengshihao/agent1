package com.agent1.android.productivity.logic.data.config

/** 各服务商独立保存 API Key，避免切换服务商时 Key 串台。 */
internal object AgentRuntimeApiKeyStorage {

    fun storageKey(providerId: String): String =
        "api_key_provider_${providerId.trim()}"

    fun resolveApiKeyForProvider(
        providerId: String,
        currentProviderId: String,
        legacyApiKey: String,
        storedForProvider: String?,
    ): String {
        if (storedForProvider != null) {
            return storedForProvider
        }
        if (providerId == currentProviderId && legacyApiKey.isNotBlank()) {
            return legacyApiKey
        }
        return ""
    }
}
