package com.dynamicui.demo.pet.logic.data.asr

interface AsrTransport {
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    )

    fun stop(submit: Boolean)
}

