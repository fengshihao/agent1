package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.javaagent.event.AgentEvent
import com.agent1.javaagent.event.AgentEventType
import com.agent1.javaagent.event.EventPayloads
import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.model.ToolCall
import com.agent1.android.productivity.logic.business.AskUserFormatting
import com.agent1.android.productivity.logic.business.AskUserPendingDetector
import com.agent1.android.productivity.logic.business.AskUserReplyFormatter
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import org.json.JSONObject
import com.agent1.android.productivity.logic.business.ProductivityAgentGateway
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.UserFileRequestMarkers
import android.net.Uri
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

    private var runInputTokens = 0L
    private var runOutputTokens = 0L
    private var runCachedTokens = 0L
    private var runEndedWithFailure = false

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

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadTranscriptIntoState(initialLoad: Boolean = false) {
        if (initialLoad || _state.value.lines.isEmpty()) {
            _state.value = _state.value.copy(isLoadingTranscript = true, transcriptLoadError = null)
        }
        try {
            val wsPath = sessionWorkspacePath()
            val built = withContext(Dispatchers.IO) {
                val messages = gateway.loadTranscript(sessionId)
                val ws = SessionWorkspacePaths.workspaceRoot(appContext, sessionId)
                val accessible = gateway.listAccessibleFilePaths(sessionId)
                val pending = AskUserPendingDetector.detectPendingRequest(messages)
                val toolCallsById = indexToolCalls(messages)
                val lines = messages
                    .filter { it.role != AgentMessage.ROLE_SYSTEM }
                    .flatMap { it.toChatLines(ws, toolCallsById) }
                    .mapIndexed { index, line ->
                    line.copy(stableKey = "transcript-$index-${line.role}")
                }
                Triple(lines, accessible, pending)
            }
            val pendingForm = built.third?.let { initialAskUserForm(it) }
            _state.value = _state.value.copy(
                lines = built.first,
                accessibleFilePaths = built.second,
                streamingText = "",
                streamingReasoning = "",
                runTimeline = emptyList(),
                isLoadingTranscript = false,
                workspacePath = wsPath,
                transcriptLoadError = null,
                pendingAskUser = pendingForm,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoadingTranscript = false,
                transcriptLoadError = e.message ?: e.javaClass.simpleName,
            )
        }
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
        resetRunUsageAccumulators()
        _state.value = _state.value.copy(
            isRunning = true,
            streamingText = "",
            streamingReasoning = "",
            runTimeline = emptyList(),
            runActivityLabel = "正在连接模型…",
            lastRunTokenSummary = null,
            pendingAskUser = null,
            lines = _state.value.lines + ChatLine(
                role = "user",
                content = trimmed,
                stableKey = "live-user-${System.nanoTime()}",
            ),
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
                            AgentEventType.MESSAGE_RESET -> {
                                // LLM 流中途失败自动重试：丢弃已展示的增量，避免重试后内容重复
                                synchronized(streamLock) {
                                    contentBuffer.setLength(0)
                                    reasoningBuffer.setLength(0)
                                }
                                viewModelScope.launch(Dispatchers.Main) {
                                    publishStreamNow()
                                }
                            }
                            else -> viewModelScope.launch(Dispatchers.Main) {
                                publishStreamNow()
                                onAgentEvent(event)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runEndedWithFailure = true
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
                    lastRunTokenSummary = buildRunTokenSummaryIfNeeded(),
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
                runActivityLabel = streamActivityLabel(content, reasoning, _state.value.runActivityLabel),
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
                runActivityLabel = streamActivityLabel(content, reasoning, _state.value.runActivityLabel),
            )
        }
    }


    private fun resetStreamBuffers() {
        synchronized(streamLock) {
            streamGeneration += 1
            contentBuffer.setLength(0)
            reasoningBuffer.setLength(0)
        }
    }

    private fun resetRunUsageAccumulators() {
        runInputTokens = 0L
        runOutputTokens = 0L
        runCachedTokens = 0L
        runEndedWithFailure = false
    }

    private fun accumulateUsage(payload: EventPayloads.Usage) {
        runInputTokens += payload.inputTokens
        runOutputTokens += payload.outputTokens
        payload.cachedTokens?.let { runCachedTokens += it }
    }

    private fun buildRunTokenSummaryIfNeeded(): RunTokenSummary? {
        val hasUsage = runInputTokens > 0 || runOutputTokens > 0
        if (!runEndedWithFailure && !hasUsage) return null
        return RunTokenSummary(
            inputTokens = runInputTokens,
            outputTokens = runOutputTokens,
            cachedTokens = runCachedTokens,
            failed = runEndedWithFailure,
        )
    }

    /** 工具开始前把当前流式助手气泡写入时间线，保证正文和工具按发生顺序交错。 */
    private fun flushStreamingIntoTimeline() {
        publishStreamNow()
        val content = _state.value.streamingText
        val reasoning = _state.value.streamingReasoning
        if (content.isEmpty() && reasoning.isEmpty()) {
            resetStreamBuffers()
            return
        }
        val segment = ChatRunTimelineItem.AssistantPart(
            id = "live-asst-${System.nanoTime()}",
            content = content,
            reasoning = reasoning,
        )
        resetStreamBuffers()
        _state.value = _state.value.copy(
            runTimeline = _state.value.runTimeline + segment,
            streamingText = "",
            streamingReasoning = "",
            runActivityLabel = "准备下一步…",
        )
    }

    /** 列表 key 必须唯一。模型若反复给出空 id 或字面量 null，不能都落到 tool-null。 */
    private fun uniqueTimelineId(rawId: String?): String {
        val usable = rawId?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val base = if (usable != null) "call-$usable" else "call-${System.nanoTime()}"
        val taken = _state.value.runTimeline.any { it.id == base }
        return if (taken) "$base-${System.nanoTime()}" else base
    }

    private fun updateToolTimeline(
        toolCallId: String,
        appendProgress: String? = null,
        finished: Boolean = false,
        isError: Boolean = false,
        resultSummary: String? = null,
        workspaceImagePath: String? = null,
        imageWarning: String? = null,
        workspaceFilePaths: List<String>? = null,
    ): List<ChatRunTimelineItem> {
        val timeline = _state.value.runTimeline
        if (timeline.isEmpty()) return timeline
        val index = timeline.indexOfLast {
            it is ChatRunTimelineItem.ToolPart && it.toolCallId == toolCallId
        }
        if (index < 0) return timeline
        val current = timeline[index] as ChatRunTimelineItem.ToolPart
        val updated = current.copy(
            progressLines = appendProgress?.let { current.progressLines + it } ?: current.progressLines,
            finished = finished || current.finished,
            isError = isError || current.isError,
            resultSummary = resultSummary ?: current.resultSummary,
            workspaceImagePath = workspaceImagePath ?: current.workspaceImagePath,
            imageWarning = imageWarning ?: current.imageWarning,
            workspaceFilePaths = workspaceFilePaths ?: current.workspaceFilePaths,
        )
        return timeline.toMutableList().also { it[index] = updated }
    }

    private fun indexToolCalls(messages: List<AgentMessage>): Map<String, ToolCall> {
        val map = linkedMapOf<String, ToolCall>()
        for (message in messages) {
            for (call in message.toolCalls) {
                if (call.id.isNotBlank()) {
                    map[call.id] = call
                }
            }
        }
        return map
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
                flushStreamingIntoTimeline()
                val call = payload.toolCall
                val name = call.name
                val segment = ChatRunTimelineItem.ToolPart(
                    id = uniqueTimelineId(call.id),
                    toolCallId = call.id,
                    toolName = name,
                    argsPreview = call.argumentsJson.trim().take(220),
                )
                _state.value = _state.value.copy(
                    runActivityLabel = "调用工具 · $name",
                    runTimeline = _state.value.runTimeline + segment,
                )
            }
            AgentEventType.TOOL_EXECUTION_UPDATE -> {
                val payload = event.payload as EventPayloads.ToolExecutionUpdatePayload
                val snippet = payload.update.text?.trim()?.take(100).orEmpty()
                if (snippet.isNotEmpty()) {
                    val line = snippet.replace('\n', ' ')
                    _state.value = _state.value.copy(
                        runActivityLabel = "工具执行中 · $line",
                        runTimeline = updateToolTimeline(
                            toolCallId = payload.toolCallId,
                            appendProgress = line,
                        ),
                    )
                }
            }
            AgentEventType.TOOL_EXECUTION_END -> {
                val payload = event.payload as EventPayloads.ToolExecutionEnd
                val pending = payload.result?.takeIf { it.stopRunWaitingUser() }?.details?.let { node ->
                    AskUserFormatting.parseRequest(JSONObject(node.toString()))
                }
                val rawResult = if (payload.isError) {
                    payload.errorMessage?.trim()
                        ?: payload.result?.text?.trim()
                        ?: "工具执行失败"
                } else {
                    payload.result?.text?.trim().orEmpty()
                }
                val ws = SessionWorkspacePaths.workspaceRoot(appContext, sessionId)
                val display = ChatTranscriptFormatting.formatToolResult(rawResult, ws)
                val toolFiles = ChatTranscriptFormatting.mergeWorkspaceFilePaths(
                    display.summary,
                    display.workspaceFilePaths,
                    ws,
                )
                _state.value = _state.value.copy(
                    runActivityLabel = if (
                        _state.value.streamingText.isEmpty() && _state.value.streamingReasoning.isEmpty()
                    ) {
                        when {
                            pending != null -> "等待您回答…"
                            payload.isError -> "工具失败"
                            else -> "工具已完成"
                        }
                    } else {
                        null
                    },
                    runTimeline = updateToolTimeline(
                        toolCallId = payload.toolCallId,
                        finished = true,
                        isError = payload.isError && pending == null,
                        resultSummary = when {
                            pending != null -> AskUserFormatting.summaryForBubble(pending)
                            else -> display.summary
                        },
                        workspaceImagePath = display.workspaceImagePath,
                        imageWarning = display.imageWarning,
                        workspaceFilePaths = toolFiles,
                    ),
                    pendingAskUser = pending?.let { initialAskUserForm(it) } ?: _state.value.pendingAskUser,
                )
            }
            AgentEventType.TURN_END -> {
                if (_state.value.streamingText.isEmpty() && _state.value.streamingReasoning.isEmpty()) {
                    _state.value = _state.value.copy(runActivityLabel = "准备下一步…")
                }
            }
            AgentEventType.AGENT_ERROR -> {
                runEndedWithFailure = true
                val message = (event.payload as? EventPayloads.AgentError)?.message ?: "运行出错"
                _state.value = _state.value.copy(runActivityLabel = message)
            }
            AgentEventType.USAGE -> {
                val payload = event.payload as EventPayloads.Usage
                accumulateUsage(payload)
            }
            else -> Unit
        }
    }

    private fun AgentMessage.toChatLines(
        workspaceRoot: java.nio.file.Path?,
        toolCallsById: Map<String, ToolCall>,
    ): List<ChatLine> {
        val tool = AgentMessage.ROLE_TOOL_RESULT == role
        if (tool) {
            if (AskUserFormatting.isPauseSummary(content)) {
                return listOf(
                    ChatLine(
                        role = role,
                        content = content.trim(),
                        isTool = true,
                        hideInChat = true,
                    ),
                )
            }
            val display = ChatTranscriptFormatting.formatToolResult(content, workspaceRoot)
            val toolFiles = ChatTranscriptFormatting.mergeWorkspaceFilePaths(
                display.summary,
                display.workspaceFilePaths,
                workspaceRoot,
            )
            val call = toolCallId?.let { toolCallsById[it] }
            return listOf(
                ChatLine(
                    role = role,
                    content = display.summary,
                    reasoning = ChatTranscriptFormatting.truncateForUiDisplay(reasoningContent, 8_000),
                    isTool = true,
                    toolName = call?.name ?: "工具",
                    toolArgsPreview = call?.argumentsJson?.trim()?.take(220),
                    toolFinished = true,
                    toolIsError = isError,
                    workspaceImagePath = display.workspaceImagePath,
                    imageWarning = display.imageWarning,
                    workspaceFilePaths = toolFiles,
                ),
            )
        }
        val askCall = toolCalls.firstOrNull { it.name == AskUserFormatting.TOOL_NAME }
        val askRequest = askCall?.let { AskUserFormatting.parseRequestFromToolCall(it) }
        val files = ChatTranscriptFormatting.mergeWorkspaceFilePaths(content, emptyList(), workspaceRoot)
        val pick = role == AgentMessage.ROLE_ASSISTANT && UserFileRequestMarkers.containsRequest(content)
        val baseDisplay = if (pick) {
            UserFileRequestMarkers.stripForDisplay(content)
        } else {
            content
        }
        val askText = askRequest?.let { AskUserFormatting.summaryForBubble(it) }
        val merged = when {
            baseDisplay.isNotBlank() && askText != null -> "${baseDisplay.trim()}\n\n$askText"
            askText != null -> askText
            else -> baseDisplay
        }
        if (merged.isBlank() && askRequest == null) {
            if (reasoningContent.isBlank() && toolCalls.isEmpty()) {
                return emptyList()
            }
        }
        return listOf(
            ChatLine(
                role = role,
                content = ChatTranscriptFormatting.truncateForUiDisplay(merged),
                reasoning = ChatTranscriptFormatting.truncateForUiDisplay(reasoningContent, 8_000),
                isTool = false,
                workspaceFilePaths = files,
                requestUserPickFiles = pick,
                askUserForm = askRequest,
            ),
        )
    }

    fun onAskUserTextChange(questionId: String, value: String) {
        val form = _state.value.pendingAskUser ?: return
        _state.value = _state.value.copy(
            pendingAskUser = form.copy(
                textAnswers = form.textAnswers + (questionId to value),
                validationError = null,
            ),
        )
    }

    fun onAskUserSingleSelect(questionId: String, option: String) {
        val form = _state.value.pendingAskUser ?: return
        _state.value = _state.value.copy(
            pendingAskUser = form.copy(
                singleChoice = form.singleChoice + (questionId to option),
                validationError = null,
            ),
        )
    }

    fun onAskUserMultiToggle(questionId: String, option: String) {
        val form = _state.value.pendingAskUser ?: return
        val current = form.multiChoice[questionId].orEmpty()
        val next = if (option in current) current - option else current + option
        _state.value = _state.value.copy(
            pendingAskUser = form.copy(
                multiChoice = form.multiChoice + (questionId to next),
                validationError = null,
            ),
        )
    }

    fun submitAskUserForm() {
        val form = _state.value.pendingAskUser ?: return
        if (_state.value.isRunning) return
        val error = AskUserReplyFormatter.validateRequired(
            form.request,
            form.textAnswers,
            form.singleChoice,
            form.multiChoice,
        )
        if (error != null) {
            _state.value = _state.value.copy(
                pendingAskUser = form.copy(validationError = error),
            )
            return
        }
        val body = AskUserReplyFormatter.format(
            form.request,
            form.textAnswers,
            form.singleChoice,
            form.multiChoice,
        )
        sendMessage(body)
    }

    private fun initialAskUserForm(request: AskUserFormatting.Request): AskUserFormState {
        val text = linkedMapOf<String, String>()
        val single = linkedMapOf<String, String>()
        for (q in request.questions) {
            when (q.type) {
                "text" -> if (q.defaultValue.isNotEmpty()) text[q.id] = q.defaultValue
                "single_choice" -> if (q.defaultValue.isNotEmpty()) single[q.id] = q.defaultValue
            }
        }
        return AskUserFormState(request, text, single)
    }

    fun onUserPickedFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isRunning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(fileImportMessage = null)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    gateway.importUserPickedFiles(sessionId, uris)
                }
            }
            result.onSuccess { paths ->
                if (paths.isEmpty()) {
                    _state.value = _state.value.copy(fileImportMessage = "未能导入所选文件")
                } else {
                    _state.value = _state.value.copy(
                        fileImportMessage = "已添加 ${paths.size} 个文件",
                    )
                }
                loadTranscriptIntoState(initialLoad = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    fileImportMessage = "导入失败：${error.message ?: error.javaClass.simpleName}",
                )
            }
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

    fun exportBriefTranscript(activity: Context) {
        if (_state.value.exportInProgress) return
        launchBriefChatExport(
            activity,
            sessionId = sessionId,
            sessionTitle = _state.value.title.ifBlank { sessionTitle },
            onBusy = { busy -> _state.value = _state.value.copy(exportInProgress = busy) },
            onMessage = { message -> _state.value = _state.value.copy(exportMessage = message) },
        )
    }

    private fun sessionWorkspacePath(): String {
        return SessionWorkspacePaths.workspaceRoot(appContext, sessionId)?.toAbsolutePath()?.toString().orEmpty()
    }

    companion object {
        private const val STREAM_FLUSH_MS = 80L
    }
}
