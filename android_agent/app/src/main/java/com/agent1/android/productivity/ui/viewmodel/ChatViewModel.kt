package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.javaagent.event.AgentEvent
import com.agent1.javaagent.event.AgentEventType
import com.agent1.javaagent.event.EventPayloads
import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.model.ToolCall
import com.agent1.android.productivity.logic.business.AskUserFormState
import com.agent1.android.productivity.logic.business.AskUserFormatting
import com.agent1.android.productivity.logic.business.AskUserPendingDetector
import com.agent1.android.productivity.logic.business.AskUserReplyFormatter
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.logic.business.ContinueAfterBudget
import org.json.JSONObject
import com.agent1.android.productivity.logic.business.ApiSetupGate
import com.agent1.android.productivity.logic.business.AttachmentSheetDraft
import com.agent1.android.productivity.logic.business.DeepSeekApiKeyFormat
import com.agent1.android.productivity.logic.business.DeepSeekKeyImportResult
import com.agent1.android.productivity.logic.business.ModelSettingsCoordinator
import com.agent1.android.productivity.logic.business.PendingAttachmentImport
import com.agent1.android.productivity.logic.business.PendingAttachmentImports
import com.agent1.android.productivity.logic.business.IncomingSharePayload
import com.agent1.android.productivity.logic.business.ProductivityAgentGateway
import com.agent1.android.productivity.logic.business.WorkspaceFileImport
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import com.agent1.android.productivity.logic.business.SessionAccessibleFilesStore
import com.agent1.android.productivity.logic.business.SessionAttachmentLimits
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.UserFacingError
import com.agent1.android.productivity.logic.business.i18n.AppLocalization
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.UserAttachmentMessageSuffix
import com.agent1.android.productivity.logic.business.UserPickedFilesImportResult
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

    private val modelSettings = ModelSettingsCoordinator(appContext.applicationContext)

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
    private var setupKeyPersistJob: Job? = null

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
        val summary = gateway.configurationSummary()
        val keyConfigured = summary?.isApiKeyConfigured == true
        val savedInApp = modelSettings.readForm().savedInApp
        val needsSetup = ApiSetupGate.needsUserKeySetup(savedInApp, keyConfigured)
        val explained = gateway.configurationError()?.let { UserFacingError.explain(it) }
        val showInline = when {
            !needsSetup -> false
            !keyConfigured -> true
            else -> _state.value.inlineApiSetupVisible
        }
        _state.value = _state.value.copy(
            configSummary = summary,
            needsUserKeySetup = needsSetup,
            configError = if (needsSetup) null else explained,
            inlineApiSetupVisible = showInline,
        )
    }

    fun onSetupKeyDraftChange(value: String) {
        _state.value = _state.value.copy(
            setupKeyDraft = value,
            setupKeyFieldHighlight = false,
            setupKeyFieldError = null,
        )
        if (!DeepSeekApiKeyFormat.isPlausible(value)) {
            setupKeyPersistJob?.cancel()
            return
        }
        setupKeyPersistJob?.cancel()
        setupKeyPersistJob = viewModelScope.launch {
            delay(SETUP_KEY_AUTOSAVE_MS)
            val current = _state.value.setupKeyDraft
            if (!DeepSeekApiKeyFormat.isPlausible(current)) return@launch
            applySetupKey()
        }
    }

    fun applySetupKey() {
        val raw = _state.value.setupKeyDraft.trim()
        if (!DeepSeekApiKeyFormat.isPlausible(raw)) {
            _state.value = _state.value.copy(
                setupKeyFieldHighlight = true,
                setupKeyFieldError = null,
            )
            return
        }
        if (_state.value.setupKeySaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                setupKeySaving = true,
                setupKeyFieldError = null,
            )
            val result = withContext(Dispatchers.IO) { modelSettings.importDeepSeekApiKey(raw) }
            val ok = result != DeepSeekKeyImportResult.INVALID
            refreshConfigSummary()
            if (ok) {
                _state.value = _state.value.copy(
                    setupKeySaving = false,
                    setupKeyDraft = "",
                    setupKeyFieldHighlight = false,
                    setupKeyFieldError = null,
                    inlineApiSetupVisible = false,
                )
                return@launch
            }
            _state.value = _state.value.copy(
                setupKeySaving = false,
                setupKeyFieldHighlight = true,
                setupKeyFieldError = AppLocalization.getString(R.string.setup_key_invalid),
                inlineApiSetupVisible = true,
            )
        }
    }

    private fun needsUserKeySetup(): Boolean {
        val summary = gateway.configurationSummary()
        return ApiSetupGate.needsUserKeySetup(
            savedInApp = modelSettings.readForm().savedInApp,
            apiKeyConfigured = summary?.isApiKeyConfigured == true,
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
                val accessibleEntries = gateway.listAccessibleFileEntries(sessionId)
                val pending = AskUserPendingDetector.detectPendingRequest(messages)
                val toolCallsById = indexToolCalls(messages)
                val lines = messages
                    .filter { it.role != AgentMessage.ROLE_SYSTEM }
                    .flatMap { it.toChatLines(ws, toolCallsById) }
                    .mapIndexed { index, line ->
                    line.copy(stableKey = "transcript-$index-${line.role}")
                }
                data class Loaded(
                    val lines: List<ChatLine>,
                    val entries: List<SessionAccessibleFilesStore.Entry>,
                    val pending: AskUserFormatting.Request?,
                    val offerContinue: Boolean,
                )
                Loaded(
                    lines,
                    accessibleEntries,
                    pending,
                    ContinueAfterBudget.shouldOffer(
                        messages,
                        running = false,
                        pendingAsk = pending != null,
                    ),
                )
            }
            val pendingForm = built.pending?.let { initialAskUserForm(it) }
            _state.value = _state.value.copy(
                lines = built.lines,
                offerContinue = built.offerContinue,
                accessibleFilePaths = built.entries.map { it.workspaceRelativePath },
                accessibleFileEntries = built.entries,
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
                transcriptLoadError = UserFacingError.explain(e.message ?: e.javaClass.simpleName),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun sendMessage(text: String): Boolean {
        val trimmed = text.trim()
        val attachmentChanges = _state.value.attachmentChangesForNextSend
        if (trimmed.isEmpty() || _state.value.isRunning) return false
        if (needsUserKeySetup()) {
            _state.value = _state.value.copy(
                inlineApiSetupVisible = true,
                setupKeyFieldHighlight = true,
                setupKeyFieldError = null,
                configError = null,
            )
            return false
        }
        val err = gateway.configurationError()
        if (err != null) {
            _state.value = _state.value.copy(configError = UserFacingError.explain(err))
            return false
        }
        resetStreamBuffers()
        resetRunUsageAccumulators()
        val forModel = UserAttachmentMessageSuffix.appendToUserMessage(trimmed, attachmentChanges)
        _state.value = _state.value.copy(
            isRunning = true,
            streamingText = "",
            streamingReasoning = "",
            runTimeline = emptyList(),
            runActivityLabel = AppLocalization.getString(R.string.run_connecting),
            lastRunTokenSummary = null,
            pendingAskUser = null,
            attachmentChangesForNextSend = UserAttachmentMessageSuffix.ChangeSet(),
            lines = _state.value.lines + ChatLine(
                role = "user",
                content = trimmed,
                stableKey = "live-user-${System.nanoTime()}",
            ),
        )
        viewModelScope.launch {
            var sendFailure: String? = null
            try {
                withContext(Dispatchers.IO) {
                    gateway.runUserMessage(sessionId, forModel) { event ->
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
                sendFailure = UserFacingError.explain(e.message ?: e.javaClass.simpleName)
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
                // 失败发生在落盘之前时，刷新记录会把刚发出的消息和错误一起清掉。
                if (sendFailure != null) {
                    appendUnpersistedSendResult(trimmed, sendFailure)
                }
            }
        }
        return true
    }

    /** 轮次或工具次数用尽后的底部按钮。发给模型的仍是「继续」，和用户手打一致。 */
    fun continueAfterBudget() {
        sendMessage("继续")
    }

    private fun appendUnpersistedSendResult(text: String, failure: String) {
        val lines = _state.value.lines.toMutableList()
        if (lines.none { it.role == "user" && it.content.trim() == text }) {
            lines += ChatLine(
                role = "user",
                content = text,
                stableKey = "unpersisted-user",
            )
        }
        lines += ChatLine(
            role = "assistant",
            content = failure,
            stableKey = "unpersisted-error",
        )
        _state.value = _state.value.copy(lines = lines)
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
            runActivityLabel = AppLocalization.getString(R.string.run_preparing),
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
                _state.value = _state.value.copy(
                    runActivityLabel = AppLocalization.getString(R.string.run_assistant),
                )
            }
            AgentEventType.TURN_START -> {
                _state.value = _state.value.copy(
                    runActivityLabel = AppLocalization.getString(R.string.run_thinking),
                )
            }
            AgentEventType.MESSAGE_START -> {
                if (_state.value.streamingText.isEmpty() && _state.value.streamingReasoning.isEmpty()) {
                    _state.value = _state.value.copy(
                        runActivityLabel = AppLocalization.getString(R.string.run_generating),
                    )
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
                    runActivityLabel = AppLocalization.getString(R.string.run_tool_call, name),
                    runTimeline = _state.value.runTimeline + segment,
                )
            }
            AgentEventType.TOOL_EXECUTION_UPDATE -> {
                val payload = event.payload as EventPayloads.ToolExecutionUpdatePayload
                val snippet = payload.update.text?.trim()?.take(100).orEmpty()
                if (snippet.isNotEmpty()) {
                    val line = snippet.replace('\n', ' ')
                    _state.value = _state.value.copy(
                        runActivityLabel = AppLocalization.getString(R.string.run_tool_running, line),
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
                        ?: AppLocalization.getString(R.string.run_tool_failed)
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
                            pending != null -> AppLocalization.getString(R.string.run_waiting_user)
                            payload.isError -> AppLocalization.getString(R.string.run_tool_failed_short)
                            else -> AppLocalization.getString(R.string.run_tool_done)
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
                val message = (event.payload as? EventPayloads.AgentError)?.message
                _state.value = _state.value.copy(
                    runActivityLabel = UserFacingError.explain(message),
                )
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
                    hideInChat = HIDE_TOOLS_IN_MAIN_CHAT,
                    toolName = call?.name ?: AppLocalization.getString(R.string.run_tool_default_name),
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
        val pickHint = if (pick) UserFileRequestMarkers.parseHint(content) else null
        val isSystemNotice = role == AgentMessage.ROLE_USER &&
            content.trim() == com.agent1.javaagent.core.RunOutcome.progressSummaryUserPrompt()
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
                content = ChatTranscriptFormatting.truncateForUiDisplay(
                    if (role == AgentMessage.ROLE_ASSISTANT) {
                        UserFacingError.explainAssistantFailure(merged)
                    } else {
                        merged
                    },
                ),
                reasoning = ChatTranscriptFormatting.truncateForUiDisplay(reasoningContent, 8_000),
                isTool = false,
                workspaceFilePaths = files,
                requestUserPickFiles = pick,
                userPickFilesHint = pickHint,
                askUserForm = askRequest,
                isSystemNotice = isSystemNotice,
            ),
        )
    }

    fun onAskUserTextChange(questionId: String, value: String) {
        val form = _state.value.pendingAskUser ?: return
        _state.value = _state.value.copy(pendingAskUser = form.withText(questionId, value))
    }

    fun onAskUserSingleSelect(questionId: String, option: String) {
        val form = _state.value.pendingAskUser ?: return
        _state.value = _state.value.copy(pendingAskUser = form.withSingleChoice(questionId, option))
    }

    fun onAskUserMultiToggle(questionId: String, option: String) {
        val form = _state.value.pendingAskUser ?: return
        _state.value = _state.value.copy(pendingAskUser = form.withMultiToggle(questionId, option))
    }

    fun onAskUserCustomAnswerChange(questionId: String, value: String) {
        val form = _state.value.pendingAskUser ?: return
        _state.value = _state.value.copy(pendingAskUser = form.withCustomAnswer(questionId, value))
    }

    fun onAskUserWorkspaceFileToggle(questionId: String, relativePath: String) {
        val form = _state.value.pendingAskUser ?: return
        val question = form.request.questions.firstOrNull { it.id == questionId } ?: return
        val max = com.agent1.android.productivity.logic.business.AskUserQuestionUi.workspaceMaxFiles(question)
        _state.value = _state.value.copy(
            pendingAskUser = form.withWorkspaceFileToggle(questionId, relativePath, max),
        )
    }

    fun onAskUserWorkspaceFileRemove(questionId: String, relativePath: String) {
        val form = _state.value.pendingAskUser ?: return
        _state.value = _state.value.copy(
            pendingAskUser = form.withoutWorkspaceFile(questionId, relativePath),
        )
    }

    fun removeAttachmentSheetRow(row: AttachmentSheetDraft.Row) {
        if (_state.value.isRunning) return
        val (imports, removals) = AttachmentSheetDraft.removeFromDraft(
            row,
            _state.value.pendingAttachmentImports,
            _state.value.pendingAttachmentRemovals,
        )
        _state.value = _state.value.copy(
            pendingAttachmentImports = imports,
            pendingAttachmentRemovals = removals,
        )
    }

    fun submitAskUserForm() {
        val form = _state.value.pendingAskUser ?: return
        if (_state.value.isRunning) return
        val error = form.requiredError()
        if (error != null) {
            _state.value = _state.value.copy(
                pendingAskUser = form.copy(validationError = error),
            )
            return
        }
        val body = AskUserReplyFormatter.format(form)
        sendMessage(body)
    }

    private fun initialAskUserForm(request: AskUserFormatting.Request): AskUserFormState {
        val text = linkedMapOf<String, String>()
        val single = linkedMapOf<String, String>()
        for (q in request.questions) {
            when (q.type) {
                "text", "rating" -> if (q.defaultValue.isNotEmpty()) text[q.id] = q.defaultValue
                "single_choice" -> if (q.defaultValue.isNotEmpty()) single[q.id] = q.defaultValue
            }
        }
        return AskUserFormState(request, text, single)
    }

    /** 系统选择器返回后立刻复制进 imports 并登记，供 AI read_file。 */
    fun importPickedFilesFromDevice(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isRunning || _state.value.attachmentImportInProgress) return
        val slots = attachmentSlotsRemaining(_state.value)
        if (slots <= 0) {
            _state.value = _state.value.copy(
                fileImportMessage = AppLocalization.getString(
                    R.string.chat_attachments_max_reached,
                    SessionAttachmentLimits.MAX_FILES_PER_SESSION,
                ),
            )
            return
        }
        val accepted = uris.take(slots)
        val skipped = uris.size - accepted.size
        viewModelScope.launch {
            _state.value = _state.value.copy(
                attachmentImportInProgress = true,
                fileImportMessage = null,
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    gateway.importUserPickedFiles(sessionId, accepted)
                }
            }
            result.onSuccess { importResult ->
                val importedPaths = importResult.workspaceRelativePaths
                val journal = if (importedPaths.isEmpty()) {
                    _state.value.attachmentChangesForNextSend
                } else {
                    _state.value.attachmentChangesForNextSend.merge(
                        UserAttachmentMessageSuffix.ChangeSet(addedPaths = importedPaths),
                    )
                }
                val message = attachmentImportUserMessage(
                    importedCount = importedPaths.size,
                    skippedBySessionLimit = skipped,
                    rejectedOversized = importResult.rejectedOversizedCount,
                )
                _state.value = _state.value.copy(
                    attachmentImportInProgress = false,
                    attachmentChangesForNextSend = journal,
                    fileImportMessage = message,
                )
                loadTranscriptIntoState(initialLoad = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    attachmentImportInProgress = false,
                    fileImportMessage = UserFacingError.explain(error.message ?: error.javaClass.simpleName),
                )
            }
        }
    }

    /** 其他 App 「分享到 Agent1」：复制到 imports 并刷新可访问列表。 */
    fun importIncomingShareFromHost(payload: IncomingSharePayload) {
        if (payload.isEmpty() || _state.value.isRunning || _state.value.attachmentImportInProgress) {
            return
        }
        val slots = attachmentSlotsRemaining(_state.value)
        val fileUris = payload.fileUris
        val sharedText = payload.sharedText
        val hasText = !sharedText.isNullOrBlank()
        if (fileUris.isEmpty() && hasText) {
            if (slots <= 0) {
                _state.value = _state.value.copy(
                    fileImportMessage = AppLocalization.getString(
                        R.string.chat_attachments_max_reached,
                        SessionAttachmentLimits.MAX_FILES_PER_SESSION,
                    ),
                )
                return
            }
        } else if (fileUris.isNotEmpty() && slots <= 0) {
            _state.value = _state.value.copy(
                fileImportMessage = AppLocalization.getString(
                    R.string.chat_attachments_max_reached,
                    SessionAttachmentLimits.MAX_FILES_PER_SESSION,
                ),
            )
            return
        }
        val acceptedFiles = if (fileUris.isEmpty()) emptyList() else fileUris.take(slots)
        val remainingAfterFiles = (slots - acceptedFiles.size).coerceAtLeast(0)
        val includeText = hasText && (fileUris.isEmpty() || remainingAfterFiles > 0)
        val trimmed = IncomingSharePayload(
            fileUris = acceptedFiles,
            sharedText = if (includeText) sharedText else null,
        )
        if (trimmed.isEmpty()) {
            return
        }
        val skippedFiles = fileUris.size - acceptedFiles.size
        viewModelScope.launch {
            _state.value = _state.value.copy(
                attachmentImportInProgress = true,
                fileImportMessage = null,
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    gateway.importIncomingShare(sessionId, trimmed)
                }
            }
            result.onSuccess { importResult ->
                val importedPaths = importResult.workspaceRelativePaths
                val journal = if (importedPaths.isEmpty()) {
                    _state.value.attachmentChangesForNextSend
                } else {
                    _state.value.attachmentChangesForNextSend.merge(
                        UserAttachmentMessageSuffix.ChangeSet(addedPaths = importedPaths),
                    )
                }
                val message = attachmentImportUserMessage(
                    importedCount = importedPaths.size,
                    skippedBySessionLimit = skippedFiles,
                    rejectedOversized = importResult.rejectedOversizedCount,
                )
                _state.value = _state.value.copy(
                    attachmentImportInProgress = false,
                    attachmentChangesForNextSend = journal,
                    fileImportMessage = message,
                )
                loadTranscriptIntoState(initialLoad = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    attachmentImportInProgress = false,
                    fileImportMessage = UserFacingError.explain(error.message ?: error.javaClass.simpleName),
                )
            }
        }
    }

    internal fun attachmentSlotsRemaining(state: ChatUiState = _state.value): Int {
        val current = state.accessibleFileEntries.count {
            it.workspaceRelativePath !in state.pendingAttachmentRemovals
        }
        return (SessionAttachmentLimits.MAX_FILES_PER_SESSION - current).coerceAtLeast(0)
    }

    fun clearAttachmentSheetStaging() {
        if (_state.value.pendingAttachmentRemovals.isEmpty()) return
        _state.value = _state.value.copy(pendingAttachmentRemovals = emptySet())
    }

    fun commitAttachmentSheetChanges(onApplied: (() -> Unit)? = null) {
        val snapshot = _state.value
        val toRemove = snapshot.pendingAttachmentRemovals.toList()
        val toImport = snapshot.pendingAttachmentImports
        if (toRemove.isEmpty() && toImport.isEmpty()) return
        if (snapshot.isRunning || snapshot.attachmentImportInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                attachmentImportInProgress = true,
                fileImportMessage = null,
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (toRemove.isNotEmpty()) {
                        gateway.removeAccessibleFiles(sessionId, toRemove)
                    }
                    if (toImport.isNotEmpty()) {
                        gateway.importUserPickedFiles(
                            sessionId,
                            toImport.map { Uri.parse(it.uriString) },
                        )
                    } else {
                        UserPickedFilesImportResult(emptyList(), 0)
                    }
                }
            }
            result.onSuccess { importResult ->
                val importAttempted = toImport.isNotEmpty()
                val importedPaths = importResult.workspaceRelativePaths
                val journal = _state.value.attachmentChangesForNextSend.merge(
                    UserAttachmentMessageSuffix.ChangeSet(
                        addedPaths = importedPaths,
                        removedPaths = toRemove,
                    ),
                )
                _state.value = _state.value.copy(
                    attachmentImportInProgress = false,
                    pendingAttachmentImports = emptyList(),
                    pendingAttachmentRemovals = emptySet(),
                    attachmentChangesForNextSend = journal,
                    fileImportMessage = if (importAttempted) {
                        attachmentImportUserMessage(
                            importedCount = importedPaths.size,
                            skippedBySessionLimit = 0,
                            rejectedOversized = importResult.rejectedOversizedCount,
                        )
                    } else {
                        null
                    },
                )
                loadTranscriptIntoState(initialLoad = false)
                onApplied?.invoke()
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    attachmentImportInProgress = false,
                    fileImportMessage = UserFacingError.explain(error.message ?: error.javaClass.simpleName),
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

    private fun attachmentImportUserMessage(
        importedCount: Int,
        skippedBySessionLimit: Int,
        rejectedOversized: Int,
    ): String {
        val maxMb = SessionAttachmentLimits.MAX_FILE_SIZE_MB
        if (importedCount == 0) {
            return when {
                rejectedOversized > 0 ->
                    AppLocalization.getString(R.string.chat_attachments_import_all_too_large, maxMb)
                skippedBySessionLimit > 0 ->
                    AppLocalization.getString(
                        R.string.chat_attachments_pick_truncated,
                        SessionAttachmentLimits.MAX_FILES_PER_SESSION,
                    )
                else -> AppLocalization.getString(R.string.file_import_failed)
            }
        }
        val parts = mutableListOf(AppLocalization.getString(R.string.file_import_ok, importedCount))
        if (skippedBySessionLimit > 0) {
            parts.add(
                AppLocalization.getString(
                    R.string.chat_attachments_pick_truncated,
                    SessionAttachmentLimits.MAX_FILES_PER_SESSION,
                ),
            )
        }
        if (rejectedOversized > 0) {
            parts.add(
                AppLocalization.getString(R.string.chat_attachments_some_too_large, rejectedOversized, maxMb),
            )
        }
        return parts.joinToString(" ")
    }

    companion object {
        private const val STREAM_FLUSH_MS = 80L
        private const val SETUP_KEY_AUTOSAVE_MS = 350L
        private const val HIDE_TOOLS_IN_MAIN_CHAT = true
    }
}
