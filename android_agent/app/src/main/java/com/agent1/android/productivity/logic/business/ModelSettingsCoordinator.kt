package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.javaagent.config.AgentRuntimeDefaults
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.agent1.android.productivity.logic.data.AndroidAgentRuntimeConfig
import com.agent1.android.productivity.logic.data.config.AgentRuntimePreferences
import com.agent1.android.productivity.logic.data.config.AgentRuntimePreferencesStore

class ModelSettingsCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val store = AgentRuntimePreferencesStore(appContext)
    private val catalogService = ModelCatalogService()

    fun apiKeyForProvider(providerId: String): String =
        store.readApiKeyForProvider(resolveProvider(providerId).id)

    fun readForm(): ModelSettingsForm {
        val prefs = store.read()
        val effective = effectiveRuntimeConfig()
        val summary = RuntimeConfigSummary.from(effective)
        return ModelSettingsForm(
            providerId = prefs.providerId,
            baseUrl = prefs.baseUrl.ifBlank { resolveProvider(prefs.providerId).defaultBaseUrl },
            apiKey = store.readApiKeyForProvider(prefs.providerId),
            modelId = prefs.modelId.ifBlank { summary.modelId },
            maxContextTurns = if (prefs.maxContextTurns > 0) {
                prefs.maxContextTurns
            } else {
                effective.maxContextTurns
            },
            maxContextMessages = prefs.maxContextMessages,
            maxTurnsPerRun = if (prefs.maxTurnsPerRun > 0) {
                prefs.maxTurnsPerRun
            } else {
                effective.maxTurnsPerRun
            },
            maxToolCallsPerRun = if (prefs.maxToolCallsPerRun > 0) {
                prefs.maxToolCallsPerRun
            } else {
                effective.maxToolCallsPerRun
            },
            savedInApp = prefs.savedInApp,
            webSearchApiKey = prefs.webSearchApiKey,
            webSearchBaseUrl = prefs.webSearchBaseUrl.ifBlank { AgentRuntimeDefaults.DEFAULT_TAVILY_BASE_URL },
        )
    }

    fun effectiveRuntimeConfig(): AgentRuntimeConfig = AndroidAgentRuntimeConfig.load(appContext)

    fun configurationSummary(): RuntimeConfigSummary =
        RuntimeConfigSummary.from(effectiveRuntimeConfig())

    fun configurationError(): String? = effectiveRuntimeConfig().configurationError()

    fun fetchPublicModels(providerId: String): Result<List<RemoteModelOption>> {
        return catalogService.fetchPublicModelOptions(providerId)
    }

    fun bundledModelsFor(providerId: String): List<RemoteModelOption> = when (providerId) {
        PROVIDER_ZHIPU_CODING -> catalogService.zhipuCodingFallback()
        PROVIDER_DEEPSEEK -> catalogService.deepseekFallback()
        else -> catalogService.bundledFallback()
    }

    fun saveAndReload(form: ModelSettingsForm): String? {
        val prefs = AgentRuntimePreferences(
            providerId = resolveProvider(form.providerId).id,
            apiKey = form.apiKey.trim(),
            baseUrl = form.baseUrl.trim(),
            modelId = form.modelId.trim(),
            maxContextTurns = form.maxContextTurns,
            maxContextMessages = form.maxContextMessages,
            maxTurnsPerRun = form.maxTurnsPerRun,
            maxToolCallsPerRun = form.maxToolCallsPerRun,
            savedInApp = true,
            webSearchApiKey = form.webSearchApiKey.trim(),
            webSearchBaseUrl = form.webSearchBaseUrl.trim().ifBlank {
                AgentRuntimeDefaults.DEFAULT_TAVILY_BASE_URL
            },
        )
        store.save(prefs)
        ProductivityGatewayProvider.reload(appContext)
        return ProductivityGatewayProvider.get(appContext).configurationError()
    }

    fun resetToBuildDefaults(): String? {
        store.clearToBuildDefaults()
        ProductivityGatewayProvider.reload(appContext)
        return ProductivityGatewayProvider.get(appContext).configurationError()
    }
}
