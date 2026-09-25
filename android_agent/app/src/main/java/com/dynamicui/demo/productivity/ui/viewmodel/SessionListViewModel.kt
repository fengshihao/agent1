package com.dynamicui.demo.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.javaagent.modelcatalog.QwenModelCatalog
import com.agent1.javaagent.session.SessionMeta
import com.dynamicui.demo.productivity.logic.business.ProductivityAgentGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionListViewModel(
    private val gateway: ProductivityAgentGateway,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(isLoading = true)
            val sessions = withContext(Dispatchers.IO) { gateway.listSessions() }
            _state.value = SessionListUiState(
                sessions = sessions,
                configSummary = gateway.configurationSummary(),
                catalogModels = QwenModelCatalog.primaryModels(),
                configError = gateway.configurationError(),
                isLoading = false,
                exportInProgress = current.exportInProgress,
                exportMessage = current.exportMessage,
            )
        }
    }

    fun createSession(onCreated: (SessionMeta) -> Unit) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { gateway.createSession() }
            val sessions = withContext(Dispatchers.IO) { gateway.listSessions() }
            val current = _state.value
            _state.value = current.copy(sessions = sessions, isLoading = false)
            onCreated(meta)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { gateway.deleteSession(sessionId) }
            refresh()
        }
    }

    fun exportDiagnostics(activity: Context) {
        if (_state.value.exportInProgress) return
        launchDiagnosticExport(
            activity,
            onBusy = { busy -> _state.value = _state.value.copy(exportInProgress = busy) },
            onMessage = { message -> _state.value = _state.value.copy(exportMessage = message) },
        )
    }
}
