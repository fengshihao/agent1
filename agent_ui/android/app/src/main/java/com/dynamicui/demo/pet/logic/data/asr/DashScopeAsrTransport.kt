package com.dynamicui.demo.pet.logic.data.asr

import kotlinx.coroutines.CoroutineScope

class DashScopeAsrTransport(
    scope: CoroutineScope,
    runOnMain: (() -> Unit) -> Unit
) : AsrTransport {
    private val session = DashScopeFunAsrSession(scope, runOnMain)

    override fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        session.start(onPartial, onFinal, onError)
    }

    override fun stop(submit: Boolean) {
        session.stop(submit)
    }
}

