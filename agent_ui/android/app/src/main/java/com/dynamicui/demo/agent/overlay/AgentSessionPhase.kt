package com.dynamicui.demo.pet.ui.overlay

/**
 * 悬浮宠物一轮对话的 UI 阶段（与 ASR / AgentForegroundService 事件对齐）。
 */
enum class AgentSessionPhase {
    Idle,
    Listening,
    Transcribing,
    Sending,
    Streaming,
    Done,
    Error,
}

fun AgentSessionPhase.allowsVoiceInput(): Boolean =
    this == AgentSessionPhase.Idle ||
        this == AgentSessionPhase.Done ||
        this == AgentSessionPhase.Error

fun AgentSessionPhase.isLlmActive(): Boolean =
    this == AgentSessionPhase.Sending || this == AgentSessionPhase.Streaming
