package com.agent1.android.productivity.logic.business.platform

import android.content.Context

/**
 * 通知里的「停止」回调。由 entry 在进程内装上，Service 只负责转发，避免平台宿主去编排 Gateway。
 */
internal object AgentRunStopBridge {
    @Volatile
    var abort: ((Context) -> Unit)? = null
}
