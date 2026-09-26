package com.dynamicui.demo.productivity.logic.business

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.dynamicui.demo.productivity.logic.data.AndroidAgentRuntimeConfig
import com.dynamicui.demo.productivity.logic.data.config.AgentRuntimePreferences
import com.dynamicui.demo.productivity.logic.data.config.AgentRuntimePreferencesStore

class ModelSettingsCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val store = AgentRuntimePreferencesStore(appContext)
    private val catalogService = ModelCatalogService()

    fun readForm(): ModelSettingsForm {
        val prefs = store.read()
        val effective = effectiveRuntimeConfig()
        val summary = RuntimeConfigSummary.from(effective)
        return ModelSettingsForm(
            providerId = prefs.providerId,
            baseUrl = prefs.baseUrl.ifBlank { resolveProvider(prefs.providerId).defaultBaseUrl },
            apiKey = prefs.apiKey,
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
        )
    }

    fun effectiveRuntimeConfig(): AgentRuntimeConfig = AndroidAgentRuntimeConfig.load(appContext)

    fun configurationSummary(): RuntimeConfigSummary =
        RuntimeConfigSummary.from(effectiveRuntimeConfig())

    fun configurationError(): String? = effectiveRuntimeConfig().configurationError()

    fun fetchRemoteModels(baseUrl: String, apiKey: String): Result<List<RemoteModelOption>> {
        return catalogService.fetchRemoteModelOptions(baseUrl, apiKey)
    }

    fun bundledModels(): List<RemoteModelOption> = catalogService.bundledFallback()

    fun zhipuBundledModels(): List<RemoteModelOption> = catalogService.zhipuCodingFallback()

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
