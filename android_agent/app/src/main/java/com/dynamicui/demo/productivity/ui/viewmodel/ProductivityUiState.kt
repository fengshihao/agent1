package com.dynamicui.demo.productivity.ui.viewmodel

import com.agent1.javaagent.modelcatalog.QwenModelInfo
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.agent1.javaagent.session.SessionMeta

data class SessionListUiState(
    val sessions: List<SessionMeta> = emptyList(),
    val configSummary: RuntimeConfigSummary? = null,
    val catalogModels: List<QwenModelInfo> = emptyList(),
    val configError: String? = null,
    val isLoading: Boolean = false,
    val exportInProgress: Boolean = false,
    val exportMessage: String? = null,
)

data class ChatLine(
    val role: String,
    val content: String,
    val isTool: Boolean = false,
)

data class ChatUiState(
    val sessionId: String = "",
    val title: String = "",
    val isLoadingTranscript: Boolean = true,
    val lines: List<ChatLine> = emptyList(),
    val streamingText: String = "",
    val toolTrail: List<String> = emptyList(),
    /** 运行中、尚未有流式正文时的状态文案（思考、等模型、工具等） */
    val runActivityLabel: String? = null,
    val isRunning: Boolean = false,
    val configError: String? = null,
    val configSummary: RuntimeConfigSummary? = null,
    val showModelPanel: Boolean = false,
    val exportInProgress: Boolean = false,
    val exportMessage: String? = null,
)
