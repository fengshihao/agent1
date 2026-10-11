package com.agent1.android.productivity.ui.viewmodel

import com.agent1.android.productivity.logic.business.AgentWorkNoticeCopy
import com.agent1.android.productivity.logic.business.AgentWorkStatus

/** 聊天状态 → 保活通知。只有 [ChatUiState.isRunning] 为真才占着前台服务。 */
internal fun ChatUiState.toAgentWorkStatus(): AgentWorkStatus {
    return AgentWorkStatus(
        active = isRunning,
        sessionTitle = title,
        activityLabel = runActivityLabel,
        detail = AgentWorkNoticeCopy.streamingExcerpt(streamingText),
    )
}
