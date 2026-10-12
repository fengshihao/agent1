package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.android.productivity.logic.business.ProductivityAgentGateway
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.UsageLogSummary
import com.agent1.android.productivity.logic.business.UsageOverview
import com.agent1.android.productivity.logic.business.UserFacingError
import com.agent1.javaagent.log.AgentDataPaths
import com.agent1.javaagent.session.SessionMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SessionListViewModel"

class SessionListViewModel(
    private val appContext: Context,
) : ViewModel() {

    private val gateway: ProductivityAgentGateway
        get() = ProductivityGatewayProvider.get(appContext.applicationContext)

    private val _state = MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(isLoading = true, startupError = null)
            @Suppress("TooGenericExceptionCaught")
            try {
                val (sessions, usage) = withContext(Dispatchers.IO) {
                    gateway.listSessions() to loadUsage()
                }
                _state.value = SessionListUiState(
                    sessions = sessions,
                    configSummary = gateway.configurationSummary(),
                    configError = gateway.configurationError()?.let { UserFacingError.explain(it) },
                    isLoading = false,
                    exportInProgress = current.exportInProgress,
                    exportMessage = current.exportMessage,
                    usage = usage,
                )
            } catch (t: Exception) {
                _state.value = current.copy(
                    isLoading = false,
                    startupError = UserFacingError.explain(t.message ?: t.javaClass.simpleName),
                )
            }
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

    @Suppress("TooGenericExceptionCaught")
    fun deleteSession(sessionId: String, onFinished: (List<SessionMeta>) -> Unit = {}) {
        viewModelScope.launch {
            val current = _state.value
            try {
                val sessions = withContext(Dispatchers.IO) {
                    gateway.deleteSession(sessionId)
                    gateway.listSessions()
                }
                _state.value = current.copy(
                    sessions = sessions,
                    isLoading = false,
                    startupError = null,
                )
                onFinished(sessions)
            } catch (t: Exception) {
                _state.value = current.copy(
                    isLoading = false,
                    startupError = UserFacingError.explain(t.message ?: t.javaClass.simpleName),
                )
            }
        }
    }

    /** 打开抽屉时刷新累计用量，不打断会话列表。 */
    fun refreshUsage() {
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) { loadUsage() }
            _state.value = _state.value.copy(usage = usage)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadUsage(): UsageOverview {
        return try {
            val events = AgentDataPaths.eventsJsonl(SessionWorkspacePaths.agentRoot(appContext))
            UsageLogSummary.summarize(events)
        } catch (t: Exception) {
            Log.w(TAG, "read usage failed", t)
            UsageOverview()
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
