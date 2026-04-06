package com.dynamicui.demo.agent.voice.core

import com.dynamicui.demo.agent.asr.AsrTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputControllerTest {

    @Test
    fun pressStartThenEnd_withoutLongPress_returnsIdle() {
        val transport = FakeTransport()
        val submitted = mutableListOf<String>()
        val controller = VoiceInputController(transport) {
            submitted += it
            true
        }

        controller.onPressStart()
        controller.onPressEnd()

        assertEquals(VoiceInputState.Idle, controller.state.value)
        assertTrue(submitted.isEmpty())
        assertEquals(0, transport.stopCalls.size)
    }

    @Test
    fun longPressAndRelease_submitsFinalText() {
        val transport = FakeTransport()
        val submitted = mutableListOf<String>()
        val controller = VoiceInputController(transport) {
            submitted += it
            true
        }

        controller.onPressStart()
        controller.onLongPressTriggered()
        controller.onPressEnd()
        transport.emitFinal("你好，世界")

        assertEquals(listOf("你好，世界"), submitted)
        assertEquals(listOf(true), transport.stopCalls)
        assertEquals(VoiceInputState.Idle, controller.state.value)
    }

    @Test
    fun longPressThenCancel_doesNotSubmit() {
        val transport = FakeTransport()
        val submitted = mutableListOf<String>()
        val controller = VoiceInputController(transport) {
            submitted += it
            true
        }

        controller.onPressStart()
        controller.onLongPressTriggered()
        controller.onPressCancel()
        transport.emitFinal("不应发送")

        assertTrue(submitted.isEmpty())
        assertEquals(listOf(false), transport.stopCalls)
        assertEquals(VoiceInputState.Idle, controller.state.value)
    }

    @Test
    fun secondPressWhileBusy_emitsBusySignal() {
        val transport = FakeTransport()
        val controller = VoiceInputController(transport) { true }
        controller.onPressStart()
        controller.onLongPressTriggered()
        controller.onPressStart() // busy
        controller.onPressCancel()
        val signals = controller.signals.replayCache

        assertTrue(signals.any { it is VoiceInputSignal.Busy })
    }

    @Test
    fun signalOrder_forNormalFlow_isStable() {
        val transport = FakeTransport()
        val submitted = mutableListOf<String>()
        val controller = VoiceInputController(transport) {
            submitted += it
            true
        }
        controller.onPressStart()
        controller.onLongPressTriggered()
        transport.emitPartial("你好")
        transport.emitPartial("你好，帮我查")
        controller.onPressEnd()
        transport.emitFinal("你好，帮我查天气")
        val signals = controller.signals.replayCache

        assertEquals(listOf("你好，帮我查天气"), submitted)
        assertTrue(
            signals.contains(VoiceInputSignal.StateChanged(VoiceInputState.Pressing)) &&
                signals.contains(VoiceInputSignal.StateChanged(VoiceInputState.Listening)) &&
                signals.contains(VoiceInputSignal.StateChanged(VoiceInputState.Transcribing)) &&
                signals.contains(VoiceInputSignal.StateChanged(VoiceInputState.Submitting)) &&
                signals.contains(VoiceInputSignal.Submitted("你好，帮我查天气")) &&
                signals.last() == VoiceInputSignal.StateChanged(VoiceInputState.Idle)
        )
    }

    @Test
    fun errorPath_emitsErrorAndAutoRecoversToIdle() {
        val transport = FakeTransport()
        val controller = VoiceInputController(transport) { true }
        controller.onPressStart()
        controller.onLongPressTriggered()
        transport.emitError("网络错误")
        val signals = controller.signals.replayCache

        assertEquals(VoiceInputState.Idle, controller.state.value)
        assertTrue(signals.any { it == VoiceInputSignal.Error("网络错误") })
        assertTrue(signals.any { it == VoiceInputSignal.StateChanged(VoiceInputState.Error) })
        assertTrue(signals.last() == VoiceInputSignal.StateChanged(VoiceInputState.Idle))
    }

    private class FakeTransport : AsrTransport {
        private var onPartial: ((String) -> Unit)? = null
        private var onFinal: ((String) -> Unit)? = null
        private var onError: ((String) -> Unit)? = null
        val stopCalls = mutableListOf<Boolean>()

        override fun start(
            onPartial: (String) -> Unit,
            onFinal: (String) -> Unit,
            onError: (String) -> Unit
        ) {
            this.onPartial = onPartial
            this.onFinal = onFinal
            this.onError = onError
        }

        override fun stop(submit: Boolean) {
            stopCalls += submit
        }

        fun emitFinal(text: String) {
            onFinal?.invoke(text)
        }

        fun emitPartial(text: String) {
            onPartial?.invoke(text)
        }

        fun emitError(message: String) {
            onError?.invoke(message)
        }
    }
}

