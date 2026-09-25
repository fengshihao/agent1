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
        viewModelScope.launch {
            loadTranscriptIntoState(initialLoad = true)
        }
    }

    fun reloadTranscript() {
        viewModelScope.launch {
            loadTranscriptIntoState(initialLoad = false)
        }
    }

    private suspend fun loadTranscriptIntoState(initialLoad: Boolean = false) {
        if (initialLoad || _state.value.lines.isEmpty()) {
            _state.value = _state.value.copy(isLoadingTranscript = true)
        }
        val messages = withContext(Dispatchers.IO) {
            gateway.loadTranscript(sessionId)
        }
        _state.value = _state.value.copy(
            lines = messages.map { it.toChatLine() },
            streamingText = "",
            toolTrail = emptyList(),
            isLoadingTranscript = false,
        )
    }

    fun toggleModelPanel() {
        _state.value = _state.value.copy(showModelPanel = !_state.value.showModelPanel)
    }

    @Suppress("TooGenericExceptionCaught")
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isRunning) return
        val err = gateway.configurationError()
        if (err != null) {
            _state.value = _state.value.copy(configError = err)
            return
        }
        resetStreamBuffer()
        _state.value = _state.value.copy(
            isRunning = true,
            streamingText = "",
            toolTrail = emptyList(),
            runActivityLabel = "正在连接模型…",
            lines = _state.value.lines + ChatLine(role = "user", content = trimmed),
        )
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
                // Run 失败须在 UI 展示一条助手消息，不可静默；具体类型因 Gateway/Host 多样而宽 catch。
                _state.value = _state.value.copy(
                    lines = _state.value.lines + ChatLine("assistant", "错误: ${e.message}"),
                )
            } finally {
                resetStreamBuffer()
                _state.value = _state.value.copy(
                    isRunning = false,
                    streamingText = "",
                    runActivityLabel = null,
                )
                loadTranscriptIntoState(initialLoad = false)
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
            _state.value = _state.value.copy(
                streamingText = text,
                runActivityLabel = if (text.isNotEmpty()) null else _state.value.runActivityLabel,
            )
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
            _state.value = _state.value.copy(
                streamingText = text,
                runActivityLabel = if (text.isNotEmpty()) null else _state.value.runActivityLabel,
            )
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
            AgentEventType.AGENT_START -> {
                _state.value = _state.value.copy(runActivityLabel = "助手运行中…")
            }
            AgentEventType.TURN_START -> {
                _state.value = _state.value.copy(runActivityLabel = "思考中…")
            }
            AgentEventType.MESSAGE_START -> {
                if (_state.value.streamingText.isEmpty()) {
                    _state.value = _state.value.copy(runActivityLabel = "正在生成回复…")
                }
            }
            AgentEventType.TOOL_EXECUTION_START -> {
                val payload = event.payload as EventPayloads.ToolExecutionStart
                val name = payload.toolCall.name
                _state.value = _state.value.copy(
                    runActivityLabel = "调用工具 · $name",
                    toolTrail = _state.value.toolTrail + "▶ $name",
                )
            }
            AgentEventType.TOOL_EXECUTION_UPDATE -> {
                val payload = event.payload as EventPayloads.ToolExecutionUpdatePayload
                val snippet = payload.update.text?.trim()?.take(100).orEmpty()
                if (snippet.isNotEmpty()) {
                    _state.value = _state.value.copy(runActivityLabel = "工具执行 · ${snippet.replace('\n', ' ')}")
                }
            }
            AgentEventType.TOOL_EXECUTION_END -> {
                val payload = event.payload as EventPayloads.ToolExecutionEnd
                val preview = payload.result?.text?.take(120) ?: ""
                _state.value = _state.value.copy(
                    runActivityLabel = if (_state.value.streamingText.isEmpty()) "工具已完成" else null,
                    toolTrail = _state.value.toolTrail + "✓ ${preview.replace('\n', ' ')}",
                )
            }
            AgentEventType.TURN_END -> {
                if (_state.value.streamingText.isEmpty()) {
                    _state.value = _state.value.copy(runActivityLabel = "准备下一步…")
                }
            }
            AgentEventType.AGENT_ERROR -> {
                val message = (event.payload as? EventPayloads.AgentError)?.message ?: "运行出错"
                _state.value = _state.value.copy(runActivityLabel = message)
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
