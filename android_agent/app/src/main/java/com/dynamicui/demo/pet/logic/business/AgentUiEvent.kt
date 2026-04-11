package com.dynamicui.demo.pet.logic.business

sealed class AgentUiEvent {
    data class Streaming(val markdown: String) : AgentUiEvent()
    data class Finished(val markdown: String) : AgentUiEvent()
    data class Aborted(val markdown: String) : AgentUiEvent()
    data class Error(val message: String) : AgentUiEvent()
    data class BusyChanged(val busy: Boolean) : AgentUiEvent()
}
