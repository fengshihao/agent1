package com.dynamicui.demo.productivity.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.agent1.javaagent.modelcatalog.QwenModelCatalog
import com.agent1.javaagent.session.SessionMeta
import com.dynamicui.demo.productivity.logic.business.ProductivityAgentGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionListViewModel(
    private val gateway: ProductivityAgentGateway,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val summary = gateway.configurationSummary()
        val err = gateway.configurationError()
        _state.value = SessionListUiState(
            sessions = gateway.listSessions(),
            configSummary = summary,
            catalogModels = QwenModelCatalog.primaryModels(),
            configError = err,
            isLoading = false,
        )
    }

    fun createSession(): SessionMeta {
        val meta = gateway.createSession()
        refresh()
        return meta
    }

    fun deleteSession(sessionId: String) {
        gateway.deleteSession(sessionId)
        refresh()
    }
}
