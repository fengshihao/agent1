package com.dynamicui.demo.pet.logic.business

/**
 * 业务层外发「会话运行期事件」的端口；由 Android runtime（如 ForegroundService）实现，
 * 再转交给 [AgentRunPresentation]（通常由 UI/overlay 注册）。
 */
fun interface AgentRunEventSink {
    fun onAgentUiEvent(event: AgentUiEvent)
}

/**
 * 展示层对 Agent 输出的订阅；由 UI 实现（默认空实现便于只关心部分回调）。
 */
interface AgentRunPresentation {
    fun onAssistantStreaming(fullMarkdown: String) {}
    fun onAgentFinished(finalMarkdown: String) {}
    fun onAgentAborted(partialMarkdown: String) {}
    fun onAgentError(message: String) {}
    fun onAgentBusyChanged(busy: Boolean) {}
}
