package com.dynamicui.demo.agent.asr

interface AsrTransport {
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    )

    fun stop(submit: Boolean)
}

