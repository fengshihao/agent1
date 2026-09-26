package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.javaagent.event.AgentEvent
import com.agent1.javaagent.event.AgentEventType
import com.agent1.javaagent.event.EventPayloads
import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.modelcatalog.QwenModelCatalog
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.logic.business.ProductivityAgentGateway
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val appContext: Context,
    private val sessionId: String,
    private val sessionTitle: String,
) : ViewModel() {

    private val gateway: ProductivityAgentGateway
        get() = ProductivityGatewayProvider.get(appContext.applicationContext)

    private val _state = MutableStateFlow(
        ChatUiState(
            sessionId = sessionId,
            title = sessionTitle,
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val streamLock = Any()
    private val contentBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var streamGeneration = 0
    @Volatile
    private var flushJob: Job? = null

    init {
        refreshConfigSummary()
        viewModelScope.launch {
            loadTranscriptIntoState(initialLoad = true)
        }
    }

    fun refreshConfigSummary() {
        _state.value = _state.value.copy(
            configSummary = gateway.configurationSummary(),
            configError = gateway.configurationError(),
        )
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
            streamingReasoning = "",
            toolTrail = emptyList(),
            isLoadingTranscript = false,
            workspacePath = sessionWorkspacePath(),
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
        resetStreamBuffers()
        _state.value = _state.value.copy(
            isRunning = true,
            streamingText = "",
            streamingReasoning = "",
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
                                enqueueContentDelta(payload.delta)
                            }
                            AgentEventType.REASONING_UPDATE -> {
                                val payload = event.payload as EventPayloads.ReasoningUpdate
                                enqueueReasoningDelta(payload.delta)
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
                resetStreamBuffers()
                _state.value = _state.value.copy(
                    isRunning = false,
                    streamingText = "",
                    streamingReasoning = "",
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

    private fun enqueueContentDelta(delta: String) {
        if (delta.isEmpty()) return
        synchronized(streamLock) {
            contentBuffer.append(delta)
        }
        scheduleStreamFlush()
    }

    private fun enqueueReasoningDelta(delta: String) {
        if (delta.isEmpty()) return
        synchronized(streamLock) {
            reasoningBuffer.append(delta)
        }
        scheduleStreamFlush()
    }

    private fun scheduleStreamFlush() {
        val generation = synchronized(streamLock) { streamGeneration }
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch(Dispatchers.Main) {
            delay(STREAM_FLUSH_MS)
            publishStream(generation)
        }
    }

    private fun publishStream(generation: Int) {
        val (content, reasoning) = synchronized(streamLock) {
            if (generation != streamGeneration) return
            contentBuffer.toString() to reasoningBuffer.toString()
        }
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(
                streamingText = content,
                streamingReasoning = reasoning,
                runActivityLabel = streamActivityLabel(content, reasoning),
            )
        }
        val pending = synchronized(streamLock) {
            generation == streamGeneration && (
                contentBuffer.length > content.length ||
                    reasoningBuffer.length > reasoning.length
                )
        }
        if (pending) {
            flushJob = viewModelScope.launch(Dispatchers.Main) {
                delay(STREAM_FLUSH_MS)
                publishStream(generation)
            }
        }
    }

    private fun publishStreamNow() {
        val (content, reasoning) = synchronized(streamLock) {
            if (contentBuffer.isEmpty() && reasoningBuffer.isEmpty()) return
            contentBuffer.toString() to reasoningBuffer.toString()
        }
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(
                streamingText = content,
                streamingReasoning = reasoning,
                runActivityLabel = streamActivityLabel(content, reasoning),
            )
        }
    }

    private fun streamActivityLabel(content: String, reasoning: String): String? {
        if (content.isNotEmpty()) return null
        if (reasoning.isNotEmpty()) return "思考中…"
        return _state.value.runActivityLabel
    }

    private fun resetStreamBuffers() {
        synchronized(streamLock) {
            streamGeneration += 1
            contentBuffer.setLength(0)
            reasoningBuffer.setLength(0)
        }
    }

    private fun onAgentEvent(event: AgentEvent) {
        when (event.type) {
            AgentEventType.MESSAGE_UPDATE, AgentEventType.REASONING_UPDATE -> Unit
            AgentEventType.AGENT_START -> {
                _state.value = _state.value.copy(runActivityLabel = "助手运行中…")
            }
            AgentEventType.TURN_START -> {
                _state.value = _state.value.copy(runActivityLabel = "思考中…")
            }
            AgentEventType.MESSAGE_START -> {
                if (_state.value.streamingText.isEmpty() && _state.value.streamingReasoning.isEmpty()) {
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
                    runActivityLabel = if (
                        _state.value.streamingText.isEmpty() && _state.value.streamingReasoning.isEmpty()
                    ) {
                        "工具已完成"
                    } else {
                        null
                    },
                    toolTrail = _state.value.toolTrail + "✓ ${preview.replace('\n', ' ')}",
                )
            }
            AgentEventType.TURN_END -> {
                if (_state.value.streamingText.isEmpty() && _state.value.streamingReasoning.isEmpty()) {
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
        if (tool) {
            val ws = SessionWorkspacePaths.workspaceRoot(appContext, sessionId)
            val display = ChatTranscriptFormatting.formatToolResult(content, ws)
            return ChatLine(
                role = role,
                content = display.summary,
                reasoning = reasoningContent,
                isTool = true,
                workspaceImagePath = display.workspaceImagePath,
                imageWarning = display.imageWarning,
            )
        }
        return ChatLine(
            role = role,
            content = content,
            reasoning = reasoningContent,
            isTool = false,
        )
    }

    fun exportDiagnostics(activity: Context) {
        if (_state.value.exportInProgress) return
        launchDiagnosticExport(
            activity,
            onBusy = { busy -> _state.value = _state.value.copy(exportInProgress = busy) },
            onMessage = { message -> _state.value = _state.value.copy(exportMessage = message) },
        )
    }

    private fun sessionWorkspacePath(): String {
        return SessionWorkspacePaths.workspaceRoot(appContext, sessionId)?.toAbsolutePath()?.toString().orEmpty()
    }

    companion object {
        val catalogModels = QwenModelCatalog.primaryModels()
        private const val STREAM_FLUSH_MS = 80L
    }
}
