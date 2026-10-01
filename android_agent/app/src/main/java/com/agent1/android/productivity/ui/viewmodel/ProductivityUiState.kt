package com.agent1.android.productivity.ui.viewmodel

import com.agent1.android.productivity.logic.business.AskUserFormatting
import com.agent1.javaagent.modelcatalog.QwenModelInfo
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.agent1.javaagent.session.SessionMeta

data class AskUserFormState(
    val request: AskUserFormatting.Request,
    val textAnswers: Map<String, String> = emptyMap(),
    val singleChoice: Map<String, String> = emptyMap(),
    val multiChoice: Map<String, Set<String>> = emptyMap(),
    val validationError: String? = null,
)

data class SessionListUiState(
    val sessions: List<SessionMeta> = emptyList(),
    val configSummary: RuntimeConfigSummary? = null,
    val catalogModels: List<QwenModelInfo> = emptyList(),
    val configError: String? = null,
    val isLoading: Boolean = true,
    val exportInProgress: Boolean = false,
    val exportMessage: String? = null,
    /** Agent 宿主初始化失败（Weizhi/SQLite 等）；详情见 last_crash_report.txt */
    val startupError: String? = null,
)

/** 单次 Run 内按时间顺序展示的片段（助手正文 / 工具调用）。 */
sealed interface ChatRunTimelineItem {
    val id: String

    data class AssistantPart(
        override val id: String,
        val content: String,
        val reasoning: String = "",
    ) : ChatRunTimelineItem

    data class ToolPart(
        override val id: String,
        val toolCallId: String,
        val toolName: String,
        val argsPreview: String = "",
        /** 开始、执行中、结束（成功或失败）的单行状态。 */
        val statusLine: String,
        val progressLines: List<String> = emptyList(),
        val finished: Boolean = false,
        val isError: Boolean = false,
    ) : ChatRunTimelineItem
}

data class ChatLine(
    val role: String,
    val content: String,
    val reasoning: String = "",
    /** LazyColumn 稳定 key，避免刷新后滚动跳动。 */
    val stableKey: String = "",
    val isTool: Boolean = false,
    /** 工作区内图片相对路径（工具结果或助手 Markdown 引用）。 */
    val workspaceImagePath: String? = null,
    val imageWarning: String? = null,
    val workspaceFilePaths: List<String> = emptyList(),
    /** 助手消息含 [需要用户选文件] 等标记时，展示「选择文件」按钮。 */
    val requestUserPickFiles: Boolean = false,
    /** 历史消息中的 ask_user 摘要（只读）。 */
    val askUserForm: AskUserFormatting.Request? = null,
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
    val runTimeline: List<ChatRunTimelineItem> = emptyList(),
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
    /** 当前待回复的 ask_user 表单（对话列表与输入框之间，限高可滚动）。 */
    val pendingAskUser: AskUserFormState? = null,
)
