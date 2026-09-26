package com.dynamicui.demo.productivity.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.dynamicui.demo.productivity.logic.business.ModelSettingsCoordinator
import com.dynamicui.demo.productivity.logic.business.ModelSettingsForm
import com.dynamicui.demo.productivity.logic.business.PROVIDER_CUSTOM
import com.dynamicui.demo.productivity.logic.business.PROVIDER_ZHIPU_CODING
import com.dynamicui.demo.productivity.logic.business.ProviderOption
import com.dynamicui.demo.productivity.logic.business.RemoteModelOption
import com.dynamicui.demo.productivity.logic.business.providerOptions
import com.dynamicui.demo.productivity.logic.business.resolveProvider
import kotlinx.coroutines.Dispatchers
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

    init {
        loadFromStore()
    }

    fun loadFromStore() {
        val form = coordinator.readForm()
        val bundled = if (form.providerId == PROVIDER_ZHIPU_CODING) {
            coordinator.zhipuBundledModels()
        } else {
            coordinator.bundledModels()
        }
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
            remoteModels = bundled,
            configError = coordinator.configurationError(),
            effectiveSummary = coordinator.configurationSummary(),
            savedInApp = form.savedInApp,
            statusMessage = null,
        )
    }

    fun onProviderSelected(providerId: String) {
        val preset = resolveProvider(providerId)
        val modelId = if (preset.defaultModelId.isNotBlank()) {
            preset.defaultModelId
        } else {
            _state.value.modelId
        }
        val remoteModels = if (preset.id == PROVIDER_ZHIPU_CODING) {
            coordinator.zhipuBundledModels()
        } else {
            coordinator.bundledModels()
        }
        _state.value = _state.value.copy(
            providerId = preset.id,
            baseUrl = if (preset.id == PROVIDER_CUSTOM) {
                _state.value.baseUrl
            } else {
                preset.defaultBaseUrl
            },
            modelId = modelId,
            remoteModels = remoteModels,
        )
    }

    fun onBaseUrlChange(value: String) {
        _state.value = _state.value.copy(baseUrl = value)
    }

    fun onApiKeyChange(value: String) {
        _state.value = _state.value.copy(apiKey = value)
    }

    fun onModelSelected(modelId: String) {
        _state.value = _state.value.copy(modelId = modelId)
    }

    fun onModelIdChange(value: String) {
        _state.value = _state.value.copy(modelId = value)
    }

    fun onMaxContextTurnsChange(value: String) {
        _state.value = _state.value.copy(maxContextTurns = value.filter { it.isDigit() })
    }

    fun onMaxContextMessagesChange(value: String) {
        _state.value = _state.value.copy(maxContextMessages = value.filter { it.isDigit() })
    }

    fun onMaxTurnsPerRunChange(value: String) {
        _state.value = _state.value.copy(maxTurnsPerRun = value.filter { it.isDigit() })
    }

    fun onMaxToolCallsPerRunChange(value: String) {
        _state.value = _state.value.copy(maxToolCallsPerRun = value.filter { it.isDigit() })
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

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        viewModelScope.launch {
            _state.value = current.copy(isSaving = true, statusMessage = null)
            val form = ModelSettingsForm(
                providerId = current.providerId,
                apiKey = current.apiKey.trim(),
                baseUrl = current.baseUrl.trim(),
                modelId = current.modelId.trim(),
                maxContextTurns = current.maxContextTurns.toIntOrNull()
                    ?: coordinator.readForm().maxContextTurns,
                maxContextMessages = current.maxContextMessages.toIntOrNull() ?: 0,
                maxTurnsPerRun = current.maxTurnsPerRun.toIntOrNull()
                    ?: coordinator.readForm().maxTurnsPerRun,
                maxToolCallsPerRun = current.maxToolCallsPerRun.toIntOrNull()
                    ?: coordinator.readForm().maxToolCallsPerRun,
                savedInApp = true,
            )
            val err = withContext(Dispatchers.IO) { coordinator.saveAndReload(form) }
            _state.value = _state.value.copy(
                isSaving = false,
                savedInApp = true,
                configError = err,
                effectiveSummary = coordinator.configurationSummary(),
                statusMessage = if (err == null) "已保存并生效" else "已保存，但 $err",
            )
        }
    }

    fun resetToBuildDefaults() {
        viewModelScope.launch {
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
}
