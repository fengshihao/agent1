package com.dynamicui.demo.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.javaagent.event.AgentEvent
import com.agent1.javaagent.event.AgentEventType
import com.agent1.javaagent.event.EventPayloads
import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.modelcatalog.QwenModelCatalog
import com.dynamicui.demo.productivity.logic.business.ProductivityAgentGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val gateway: ProductivityAgentGateway,
    private val sessionId: String,
    private val sessionTitle: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(
            sessionId = sessionId,
            title = sessionTitle,
            configSummary = gateway.configurationSummary(),
            configError = gateway.configurationError(),
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val streamLock = Any()
    private val streamBuffer = StringBuilder()
    private var streamGeneration = 0
    @Volatile
    private var flushJob: Job? = null

    init {
        reloadTranscript()
    }

    fun reloadTranscript() {
        val messages = gateway.loadTranscript(sessionId)
        _state.value = _state.value.copy(
            lines = messages.map { it.toChatLine() },
            streamingText = "",
            toolTrail = emptyList(),
        )
    }

    fun toggleModelPanel() {
        _state.value = _state.value.copy(showModelPanel = !_state.value.showModelPanel)
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isRunning) return
        val err = gateway.configurationError()
        if (err != null) {
            _state.value = _state.value.copy(configError = err)
            return
        }
        resetStreamBuffer()
        _state.value = _state.value.copy(isRunning = true, streamingText = "", toolTrail = emptyList())
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    gateway.runUserMessage(sessionId, trimmed) { event ->
                        when (event.type) {
                            AgentEventType.MESSAGE_UPDATE -> {
                                val payload = event.payload as EventPayloads.MessageUpdate
                                enqueueDelta(payload.delta)
                            }
                            else -> viewModelScope.launch(Dispatchers.Main) {
                                publishStreamNow()
                                onAgentEvent(event)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    lines = _state.value.lines + ChatLine("assistant", "错误: ${e.message}"),
                )
            } finally {
                resetStreamBuffer()
                reloadTranscript()
                _state.value = _state.value.copy(isRunning = false, streamingText = "")
            }
        }
    }

    fun stopRun() {
        viewModelScope.launch(Dispatchers.IO) {
            gateway.abortActiveRun()
        }
    }

    private fun enqueueDelta(delta: String) {
        if (delta.isEmpty()) return
        val generation = synchronized(streamLock) {
            streamBuffer.append(delta)
            streamGeneration
        }
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch(Dispatchers.Main) {
            delay(STREAM_FLUSH_MS)
            publishStream(generation)
        }
    }

    private fun publishStream(generation: Int) {
        val text = synchronized(streamLock) {
            if (generation != streamGeneration) return
            streamBuffer.toString()
        }
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(streamingText = text)
        }
        val pending = synchronized(streamLock) {
            generation == streamGeneration && streamBuffer.length > text.length
        }
        if (pending) {
            flushJob = viewModelScope.launch(Dispatchers.Main) {
                delay(STREAM_FLUSH_MS)
                publishStream(generation)
            }
        }
    }

    private fun publishStreamNow() {
        val text = synchronized(streamLock) {
            if (streamBuffer.isEmpty()) return
            streamBuffer.toString()
        }
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(streamingText = text)
        }
    }

    private fun resetStreamBuffer() {
        synchronized(streamLock) {
            streamGeneration += 1
            streamBuffer.setLength(0)
        }
    }

    private fun onAgentEvent(event: AgentEvent) {
        when (event.type) {
            AgentEventType.MESSAGE_UPDATE -> Unit
            AgentEventType.TOOL_EXECUTION_START -> {
                val payload = event.payload as EventPayloads.ToolExecutionStart
                val name = payload.toolCall.name
                _state.value = _state.value.copy(
                    toolTrail = _state.value.toolTrail + "▶ $name",
                )
            }
            AgentEventType.TOOL_EXECUTION_END -> {
                val payload = event.payload as EventPayloads.ToolExecutionEnd
                val preview = payload.result?.text?.take(120) ?: ""
                _state.value = _state.value.copy(
                    toolTrail = _state.value.toolTrail + "✓ ${preview.replace('\n', ' ')}",
                )
            }
            else -> Unit
        }
    }

    private fun AgentMessage.toChatLine(): ChatLine {
        val tool = AgentMessage.ROLE_TOOL_RESULT == role
        return ChatLine(role = role, content = content ?: "", isTool = tool)
    }

    fun exportDiagnostics(activity: Context) {
        if (_state.value.exportInProgress) return
        launchDiagnosticExport(
            activity,
            onBusy = { busy -> _state.value = _state.value.copy(exportInProgress = busy) },
            onMessage = { message -> _state.value = _state.value.copy(exportMessage = message) },
        )
    }

    companion object {
        val catalogModels = QwenModelCatalog.primaryModels()
        private const val STREAM_FLUSH_MS = 80L
    }
}
