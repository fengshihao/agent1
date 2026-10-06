package com.agent1.android.productivity.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.android.productivity.logic.business.McpServerForm
import com.agent1.android.productivity.logic.business.McpSettingsCoordinator
import com.agent1.android.productivity.logic.business.ModelSettingsCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class McpSettingsUiState(
    val servers: List<McpServerForm> = emptyList(),
    val name: String = "",
    val url: String = "",
    val authorization: String = "",
    val webSearchApiKey: String = "",
    val webSearchBaseUrl: String = "",
    val busy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

class McpSettingsViewModel(
    private val coordinator: McpSettingsCoordinator,
    private val modelSettings: ModelSettingsCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(McpSettingsUiState())
    val state: StateFlow<McpSettingsUiState> = _state.asStateFlow()
    private var webSearchJob: Job? = null
    private var webSearchReady = false

    init {
        viewModelScope.launch {
            val servers = withContext(Dispatchers.IO) { coordinator.load() }
            val form = withContext(Dispatchers.IO) { modelSettings.readForm() }
            _state.update {
                it.copy(
                    servers = servers,
                    webSearchApiKey = form.webSearchApiKey,
                    webSearchBaseUrl = form.webSearchBaseUrl,
                )
            }
            webSearchReady = true
        }
    }

    fun onWebSearchApiKeyChange(value: String) {
        _state.update { it.copy(webSearchApiKey = value) }
        scheduleWebSearchSave()
    }

    fun onWebSearchBaseUrlChange(value: String) {
        _state.update { it.copy(webSearchBaseUrl = value) }
        scheduleWebSearchSave()
    }

    private fun scheduleWebSearchSave() {
        if (!webSearchReady) return
        webSearchJob?.cancel()
        webSearchJob = viewModelScope.launch {
            delay(WEB_SEARCH_DEBOUNCE_MS)
            val snapshot = _state.value
            withContext(Dispatchers.IO) {
                val form = modelSettings.readForm().copy(
                    webSearchApiKey = snapshot.webSearchApiKey.trim(),
                    webSearchBaseUrl = snapshot.webSearchBaseUrl.trim(),
                )
                modelSettings.saveAndReload(form)
            }
        }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value) }
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value) }
    }

    fun onAuthorizationChange(value: String) {
        _state.update { it.copy(authorization = value) }
    }

    fun addServer() {
        val current = _state.value
        val next = current.servers.filterNot { it.name == current.name.trim() } + McpServerForm(
            name = current.name,
            url = current.url,
            authorization = current.authorization,
            enabled = true,
        )
        persist(next, clearDraft = true)
    }

    fun setEnabled(name: String, enabled: Boolean) {
        val next = _state.value.servers.map { server ->
            if (server.name == name) server.copy(enabled = enabled) else server
        }
        persist(next, clearDraft = false)
    }

    fun remove(name: String) {
        persist(_state.value.servers.filterNot { it.name == name }, clearDraft = false)
    }

    private fun persist(servers: List<McpServerForm>, clearDraft: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errorMessage = null, statusMessage = null) }
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val saved = coordinator.save(servers)
                    Result.success(saved to coordinator.load())
                } catch (error: IllegalArgumentException) {
                    Result.failure(error)
                } catch (error: IllegalStateException) {
                    Result.failure(error)
                }
            }
            outcome.fold(
                onSuccess = { pair ->
                    val saved = pair.first
                    val warning = saved.warnings.firstOrNull()
                    _state.update {
                        it.copy(
                            servers = pair.second,
                            name = if (clearDraft) "" else it.name,
                            url = if (clearDraft) "" else it.url,
                            authorization = if (clearDraft) "" else it.authorization,
                            busy = false,
                            statusMessage = if (warning == null) {
                                "已保存，检索到 ${saved.toolCount} 个工具。脚本里用 \$mcp.名称.工具。"
                            } else {
                                "已保存。$warning"
                            },
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            errorMessage = error.message ?: "保存失败",
                        )
                    }
                },
            )
        }
    }

    companion object {
        private const val WEB_SEARCH_DEBOUNCE_MS = 450L
    }
}
