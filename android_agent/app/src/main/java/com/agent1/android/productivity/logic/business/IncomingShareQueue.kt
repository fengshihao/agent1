package com.agent1.android.productivity.logic.business

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference

/** 进程内待处理的入站分享（MainActivity 写入，聊天层消费）。 */
object IncomingShareQueue {

    private val pending = AtomicReference<IncomingSharePayload?>(null)
    private val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val updates: SharedFlow<Unit> = signals.asSharedFlow()

    fun offer(payload: IncomingSharePayload) {
        if (payload.isEmpty()) {
            return
        }
        pending.set(payload)
        signals.tryEmit(Unit)
    }

    fun poll(): IncomingSharePayload? = pending.getAndSet(null)
}
