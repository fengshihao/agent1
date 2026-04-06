package com.dynamicui.demo.agent.voice.core

import com.dynamicui.demo.agent.asr.AsrTransport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceInputState {
    Idle,
    Pressing,
    Listening,
    Transcribing,
    Submitting,
    Error,
}

sealed interface VoiceInputSignal {
    data class StateChanged(val state: VoiceInputState) : VoiceInputSignal
    data class PartialText(val text: String) : VoiceInputSignal
    data class FinalText(val text: String) : VoiceInputSignal
    data class Submitted(val text: String) : VoiceInputSignal
    data class Error(val message: String) : VoiceInputSignal
    data object Cancelled : VoiceInputSignal
    data object Busy : VoiceInputSignal
}

fun interface VoiceInputSubmitter {
    fun submit(text: String): Boolean
}

class VoiceInputController(
    private val transport: AsrTransport,
    private val submitter: VoiceInputSubmitter
) {
    private val _state = MutableStateFlow(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val _signals = MutableSharedFlow<VoiceInputSignal>(
        replay = 32,
        extraBufferCapacity = 32
    )
    val signals: SharedFlow<VoiceInputSignal> = _signals.asSharedFlow()

    private var cancelled = false
    private var lastPartial = ""

    fun onPressStart() {
        if (_state.value != VoiceInputState.Idle && _state.value != VoiceInputState.Error) {
            emit(VoiceInputSignal.Busy)
            return
        }
        setState(VoiceInputState.Pressing)
    }

    fun onLongPressTriggered() {
        if (_state.value != VoiceInputState.Pressing) return
        cancelled = false
        lastPartial = ""
        setState(VoiceInputState.Listening)
        try {
            transport.start(
                onPartial = { text ->
                    lastPartial = text
                    emit(VoiceInputSignal.PartialText(text))
                },
                onFinal = { text ->
                    emit(VoiceInputSignal.FinalText(text))
                    if (cancelled) {
                        resetToIdle()
                        return@start
                    }
                    // 某些服务端响应 final 可能为空，兜底使用最后一段 partial，避免“松手后无结果”。
                    val finalText = text.trim().ifBlank { lastPartial.trim() }
                    if (finalText.isEmpty()) {
                        resetToIdle()
                        return@start
                    }
                    setState(VoiceInputState.Submitting)
                    val ok = try {
                        submitter.submit(finalText)
                    } catch (_: Exception) {
                        false
                    }
                    if (ok) {
                        emit(VoiceInputSignal.Submitted(finalText))
                        resetToIdle()
                    } else {
                        setState(VoiceInputState.Error)
                        emit(VoiceInputSignal.Error("发送失败或未就绪"))
                        resetToIdle()
                    }
                },
                onError = { msg ->
                    setState(VoiceInputState.Error)
                    emit(VoiceInputSignal.Error(msg))
                    // 错误后自动回到 Idle，保证下一次长按可立即重试。
                    resetToIdle()
                }
            )
        } catch (_: Exception) {
            setState(VoiceInputState.Error)
            emit(VoiceInputSignal.Error("语音启动失败"))
            resetToIdle()
        }
    }

    fun onPressEnd() {
        when (_state.value) {
            VoiceInputState.Pressing -> resetToIdle()
            VoiceInputState.Listening -> {
                setState(VoiceInputState.Transcribing)
                transport.stop(submit = true)
            }
            else -> Unit
        }
    }

    fun onPressCancel() {
        when (_state.value) {
            VoiceInputState.Pressing -> {
                cancelled = true
                emit(VoiceInputSignal.Cancelled)
                resetToIdle()
            }
            VoiceInputState.Listening,
            VoiceInputState.Transcribing,
            VoiceInputState.Submitting -> {
                cancelled = true
                transport.stop(submit = false)
                emit(VoiceInputSignal.Cancelled)
                resetToIdle()
            }
            else -> Unit
        }
    }

    fun stopAll() {
        cancelled = true
        transport.stop(submit = false)
        resetToIdle()
    }

    private fun setState(next: VoiceInputState) {
        _state.value = next
        emit(VoiceInputSignal.StateChanged(next))
    }

    private fun resetToIdle() {
        setState(VoiceInputState.Idle)
    }

    private fun emit(signal: VoiceInputSignal) {
        _signals.tryEmit(signal)
    }
}

