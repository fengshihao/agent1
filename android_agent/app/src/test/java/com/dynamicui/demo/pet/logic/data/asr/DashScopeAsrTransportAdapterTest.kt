package com.dynamicui.demo.pet.logic.data.asr

import com.agent1.javaagent.asr.AsrClient
import com.agent1.javaagent.asr.AsrEvent
import com.agent1.javaagent.asr.AsrSession
import com.agent1.javaagent.asr.AsrStartRequest
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class DashScopeAsrTransportAdapterTest {
    @Test
    fun mapsCoreEventsToCallbacks() {
        val fakeSession = FakeAsrSession()
        val transport = DashScopeAsrTransport(
            scope = CoroutineScope(Dispatchers.Unconfined),
            runOnMain = { block -> block() },
            asrClient = FakeAsrClient(fakeSession)
        )
        val partials = mutableListOf<String>()
        val finals = mutableListOf<String>()
        val errors = mutableListOf<String>()

        transport.start(
            onPartial = { partials += it },
            onFinal = { finals += it },
            onError = { errors += it }
        )

        fakeSession.emit(AsrEvent.Partial("你好"))
        fakeSession.emit(AsrEvent.Partial("你好，帮我查天气"))
        fakeSession.emit(AsrEvent.Final("你好，帮我查天气"))
        fakeSession.emit(AsrEvent.Error("网络错误"))

        assertEquals(listOf("你好", "你好，帮我查天气"), partials)
        assertEquals(listOf("你好，帮我查天气"), finals)
        assertEquals(listOf("网络错误"), errors)
    }

    /**
     * 回归：stop(submit=true) 必须先让 finish 触发的 Final 送达再 dispose，否则抬起手指后不会提交识别结果。
     */
    @Test
    fun stopSubmitTrueReceivesFinalEmittedAfterFinish() {
        val session = FinishingFakeSession()
        val transport = DashScopeAsrTransport(
            scope = CoroutineScope(Dispatchers.Unconfined),
            runOnMain = { block -> block() },
            asrClient = FakeAsrClient(session)
        )
        val finals = mutableListOf<String>()
        transport.start(
            onPartial = {},
            onFinal = { finals += it },
            onError = {}
        )
        transport.stop(submit = true)
        assertEquals(listOf("done"), finals)
    }

    private class FakeAsrClient(
        private val session: FakeAsrSession
    ) : AsrClient {
        override fun startSession(request: AsrStartRequest): AsrSession = session

        override fun close() {}
    }

    private open class FakeAsrSession : AsrSession {
        private val stream = PublishSubject.create<AsrEvent>().toSerialized()

        override fun observeEvents(): Observable<AsrEvent> = stream

        override fun sendAudio(pcmChunk: ByteArray) {}

        override fun finish() {}

        override fun cancel() {}

        override fun close() {}

        fun emit(event: AsrEvent) {
            stream.onNext(event)
        }
    }

    private class FinishingFakeSession : FakeAsrSession() {
        override fun finish() {
            emit(AsrEvent.Final("done"))
            emit(AsrEvent.Completed())
        }
    }
}
