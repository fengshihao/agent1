package com.agent1.android.productivity.logic.business

import android.content.Context

/**
 * 一轮对话要不要占着前台服务，以及通知上写什么。
 *
 * 纯决策，不碰 Service / Notification。Android 在后台会挂起网络，SSE 会被拆掉；
 * 只有用户正在跑的这一轮才需要保活，空闲时必须放掉。
 */
data class AgentWorkStatus(
    val active: Boolean,
    val sessionTitle: String,
    val activityLabel: String?,
    /** 正在生成的正文摘录。工具状态已经写在 [activityLabel] 里时留空。 */
    val detail: String? = null,
)

data class AgentWorkNotice(
    val title: String,
    val text: String,
    /** 不含流式摘录。摘录变化要节流，步骤变化要立刻刷新。 */
    val activityLabel: String,
)

enum class KeepAliveAction {
    SHOW,
    SKIP,
    STOP,
}

data class KeepAliveDecision(
    val action: KeepAliveAction,
    val notice: AgentWorkNotice? = null,
    /** 正文摘录变了，但还没到刷新间隔。调用方应延后展示 [notice]，不要丢掉。 */
    val throttled: Boolean = false,
)

object AgentWorkNoticeCopy {
    const val EXCERPT_MAX = 42
    private const val TEXT_MAX = 120

    fun streamingExcerpt(text: String, maxChars: Int = EXCERPT_MAX): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) return ""
        if (flat.length <= maxChars) return flat
        return "…" + flat.takeLast(maxChars - 1)
    }

    fun from(status: AgentWorkStatus): AgentWorkNotice? {
        if (!status.active) return null
        val title = status.sessionTitle.trim().ifEmpty { "新对话" }
        val label = status.activityLabel
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "正在工作…"
        val detail = status.detail?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val text = when {
            detail.isEmpty() || detail == label -> label
            else -> "$label $detail"
        }
        return AgentWorkNotice(
            title = title,
            text = text.take(TEXT_MAX),
            activityLabel = label,
        )
    }
}

object AgentWorkKeepAlive {
    const val MIN_NOTICE_INTERVAL_MS = 1_000L

    fun decide(
        holding: Boolean,
        ownerSessionId: String?,
        sessionId: String,
        previous: AgentWorkNotice?,
        status: AgentWorkStatus,
        millisSinceLastShow: Long,
        minIntervalMs: Long = MIN_NOTICE_INTERVAL_MS,
    ): KeepAliveDecision {
        val notice = AgentWorkNoticeCopy.from(status)
        if (notice == null) {
            return if (holding && ownerSessionId == sessionId) {
                KeepAliveDecision(KeepAliveAction.STOP)
            } else {
                KeepAliveDecision(KeepAliveAction.SKIP)
            }
        }
        if (!holding || ownerSessionId != sessionId) {
            return KeepAliveDecision(KeepAliveAction.SHOW, notice)
        }
        if (notice == previous) {
            return KeepAliveDecision(KeepAliveAction.SKIP, notice)
        }
        val labelChanged = notice.activityLabel != previous?.activityLabel
        if (!labelChanged && millisSinceLastShow < minIntervalMs) {
            return KeepAliveDecision(KeepAliveAction.SKIP, notice, throttled = true)
        }
        return KeepAliveDecision(KeepAliveAction.SHOW, notice)
    }
}

/** 进程内保活入口。实现放在 [com.agent1.android.productivity.logic.business.entry]。 */
interface AgentRunKeepAliveClient {
    fun sync(context: Context, sessionId: String, status: AgentWorkStatus)
}
