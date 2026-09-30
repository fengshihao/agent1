package com.agent1.android.productivity.ui.viewmodel

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
    /** Agent 宿主初始化失败（Weizhi/SQLite 等）；详情见 last_crash_report.txt */
    val startupError: String? = null,
)

data class ChatLine(
    val role: String,
    val content: String,
    val reasoning: String = "",
    val isTool: Boolean = false,
    /** 工作区内图片相对路径（工具结果或助手 Markdown 引用）。 */
    val workspaceImagePath: String? = null,
    val imageWarning: String? = null,
    val workspaceFilePaths: List<String> = emptyList(),
    /** 助手消息含 [需要用户选文件] 等标记时，展示「选择文件」按钮。 */
    val requestUserPickFiles: Boolean = false,
    /** {@code ask_user} 结构化提问；不渲染为普通工具气泡。 */
    val askUserRequest: Boolean = false,
    /** 不在聊天气泡列表中展示（已由助手气泡承载）。 */
    val hideInChat: Boolean = false,
)

data class ChatUiState(
    val sessionId: String = "",
    /** 当前会话 workspace 绝对路径，供 UI 加载 Markdown 本地图。 */
    val workspacePath: String = "",
    val title: String = "",
    val isLoadingTranscript: Boolean = true,
    val lines: List<ChatLine> = emptyList(),
    val streamingText: String = "",
    val streamingReasoning: String = "",
    val toolTrail: List<String> = emptyList(),
    /** 运行中、尚未有流式正文时的状态文案（思考、等模型、工具等） */
    val runActivityLabel: String? = null,
    val isRunning: Boolean = false,
    val configError: String? = null,
    val configSummary: RuntimeConfigSummary? = null,
    val showModelPanel: Boolean = false,
    val exportInProgress: Boolean = false,
    val exportMessage: String? = null,
    val transcriptLoadError: String? = null,
    /** 本会话用户已导入、可供 AI read_file 的路径。 */
    val accessibleFilePaths: List<String> = emptyList(),
    val fileImportMessage: String? = null,
)
