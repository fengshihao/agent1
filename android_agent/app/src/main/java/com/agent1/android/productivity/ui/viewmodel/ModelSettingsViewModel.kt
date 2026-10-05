package com.agent1.android.productivity.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.agent1.android.productivity.logic.business.ModelSettingsCoordinator
import com.agent1.android.productivity.logic.business.ModelSettingsForm
import com.agent1.android.productivity.logic.business.PROVIDER_CUSTOM
import com.agent1.android.productivity.logic.business.ProviderOption
import com.agent1.android.productivity.logic.business.RemoteModelOption
import com.agent1.android.productivity.logic.business.providerOptions
import com.agent1.android.productivity.logic.business.resolveProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelSettingsUiState(
    val providerId: String = "",
    val providerOptions: List<ProviderOption> = providerOptions(),
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelId: String = "",
    val maxContextTurns: String = "",
    val maxContextMessages: String = "",
    val maxTurnsPerRun: String = "",
    val maxToolCallsPerRun: String = "",
    val webSearchApiKey: String = "",
    val webSearchBaseUrl: String = "",
    val remoteModels: List<RemoteModelOption> = emptyList(),
    val showAdvanced: Boolean = false,
    val isFetchingModels: Boolean = false,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val configError: String? = null,
    val effectiveSummary: RuntimeConfigSummary? = null,
    val savedInApp: Boolean = false,
)

class ModelSettingsViewModel(
    private val coordinator: ModelSettingsCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(ModelSettingsUiState())
    val state: StateFlow<ModelSettingsUiState> = _state.asStateFlow()

    private var persistJob: Job? = null
    private var loaded = false

    init {
        loadFromStore()
    }

    fun loadFromStore() {
        val form = coordinator.readForm()
        val bundled = coordinator.bundledModelsFor(form.providerId)
        _state.value = _state.value.copy(
            providerId = form.providerId,
            baseUrl = form.baseUrl,
            apiKey = form.apiKey,
            modelId = form.modelId,
            maxContextTurns = form.maxContextTurns.toString(),
            maxContextMessages = if (form.maxContextMessages > 0) {
                form.maxContextMessages.toString()
            } else {
                ""
            },
            maxTurnsPerRun = form.maxTurnsPerRun.toString(),
            maxToolCallsPerRun = form.maxToolCallsPerRun.toString(),
            webSearchApiKey = form.webSearchApiKey,
            webSearchBaseUrl = form.webSearchBaseUrl,
            remoteModels = bundled,
            configError = coordinator.configurationError(),
            effectiveSummary = coordinator.configurationSummary(),
            savedInApp = form.savedInApp,
            statusMessage = null,
        )
        loaded = true
    }

    fun onProviderSelected(providerId: String) {
        val current = _state.value
        if (current.providerId == providerId) return
        viewModelScope.launch {
            persistJob?.cancel()
            withContext(Dispatchers.IO) { persistForm(current) }
            val preset = resolveProvider(providerId)
            val apiKey = withContext(Dispatchers.IO) { coordinator.apiKeyForProvider(preset.id) }
            val modelId = if (preset.defaultModelId.isNotBlank()) {
                preset.defaultModelId
            } else {
                current.modelId
            }
            val remoteModels = coordinator.bundledModelsFor(preset.id)
            val updated = current.copy(
                providerId = preset.id,
                baseUrl = if (preset.id == PROVIDER_CUSTOM) {
                    current.baseUrl
                } else {
                    preset.defaultBaseUrl
                },
                apiKey = apiKey,
                modelId = modelId,
                remoteModels = remoteModels,
            )
            _state.value = updated
            persistNow(updated)
        }
    }

    fun onBaseUrlChange(value: String) {
        _state.value = _state.value.copy(baseUrl = value)
        schedulePersist()
    }

    fun onApiKeyChange(value: String) {
        _state.value = _state.value.copy(apiKey = value)
        schedulePersist()
    }

    fun onModelSelected(modelId: String) {
        _state.value = _state.value.copy(modelId = modelId)
        schedulePersist()
    }

    fun onModelIdChange(value: String) {
        _state.value = _state.value.copy(modelId = value)
        schedulePersist()
    }

    fun onMaxContextTurnsChange(value: String) {
        _state.value = _state.value.copy(maxContextTurns = value.filter { it.isDigit() })
        schedulePersist()
    }

    fun onMaxContextMessagesChange(value: String) {
        _state.value = _state.value.copy(maxContextMessages = value.filter { it.isDigit() })
        schedulePersist()
    }

    fun onMaxTurnsPerRunChange(value: String) {
        _state.value = _state.value.copy(maxTurnsPerRun = value.filter { it.isDigit() })
        schedulePersist()
    }

    fun onMaxToolCallsPerRunChange(value: String) {
        _state.value = _state.value.copy(maxToolCallsPerRun = value.filter { it.isDigit() })
        schedulePersist()
    }

    fun onWebSearchApiKeyChange(value: String) {
        _state.value = _state.value.copy(webSearchApiKey = value)
        schedulePersist()
    }

    fun onWebSearchBaseUrlChange(value: String) {
        _state.value = _state.value.copy(webSearchBaseUrl = value)
        schedulePersist()
    }

    fun toggleAdvanced() {
        _state.value = _state.value.copy(showAdvanced = !_state.value.showAdvanced)
    }

    fun fetchRemoteModels() {
        val current = _state.value
        if (current.isFetchingModels) return
        viewModelScope.launch {
            _state.value = current.copy(isFetchingModels = true, statusMessage = null)
            val result = withContext(Dispatchers.IO) {
                coordinator.fetchRemoteModels(current.baseUrl, current.apiKey)
            }
            result.fold(
                onSuccess = { models ->
                    _state.value = _state.value.copy(
                        isFetchingModels = false,
                        remoteModels = models,
                        statusMessage = "已拉取 ${models.size} 个模型",
                        modelId = if (_state.value.modelId.isBlank()) {
                            models.firstOrNull()?.modelId.orEmpty()
                        } else {
                            _state.value.modelId
                        },
                    )
                    schedulePersist()
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isFetchingModels = false,
                        statusMessage = err.message ?: "拉取模型失败",
                    )
                },
            )
        }
    }

    fun resetToBuildDefaults() {
        viewModelScope.launch {
            persistJob?.cancel()
            val err = withContext(Dispatchers.IO) { coordinator.resetToBuildDefaults() }
            loadFromStore()
            _state.value = _state.value.copy(
                statusMessage = if (err == null) {
                    "已恢复编译期默认（若 APK 未内置 Key 仍需在此填写）"
                } else {
                    "已恢复默认：$err"
                },
            )
        }
    }

    private fun schedulePersist() {
        if (!loaded) return
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistNow(_state.value)
        }
    }

    private suspend fun persistNow(snapshot: ModelSettingsUiState) {
        if (snapshot.isSaving) return
        _state.value = snapshot.copy(isSaving = true, statusMessage = null)
        val err = withContext(Dispatchers.IO) { persistForm(snapshot) }
        _state.value = _state.value.copy(
            isSaving = false,
            savedInApp = true,
            configError = err,
            effectiveSummary = coordinator.configurationSummary(),
            statusMessage = if (err == null) "已自动保存" else "已保存，但 $err",
        )
    }

    private fun persistForm(snapshot: ModelSettingsUiState): String? {
        val defaults = coordinator.readForm()
        val form = ModelSettingsForm(
            providerId = snapshot.providerId,
            apiKey = snapshot.apiKey.trim(),
            baseUrl = snapshot.baseUrl.trim(),
            modelId = snapshot.modelId.trim(),
            maxContextTurns = snapshot.maxContextTurns.toIntOrNull() ?: defaults.maxContextTurns,
            maxContextMessages = snapshot.maxContextMessages.toIntOrNull() ?: 0,
            maxTurnsPerRun = snapshot.maxTurnsPerRun.toIntOrNull() ?: defaults.maxTurnsPerRun,
            maxToolCallsPerRun = snapshot.maxToolCallsPerRun.toIntOrNull()
                ?: defaults.maxToolCallsPerRun,
            savedInApp = true,
            webSearchApiKey = snapshot.webSearchApiKey.trim(),
            webSearchBaseUrl = snapshot.webSearchBaseUrl.trim(),
        )
        return coordinator.saveAndReload(form)
    }

    companion object {
        private const val PERSIST_DEBOUNCE_MS = 450L
    }
}
