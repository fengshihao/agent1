package com.dynamicui.demo.pet.ui.overlay

import com.dynamicui.demo.pet.logic.data.asr.AsrTransport

/** 无网络实现，供 @Preview 或纯 UI 展示路径使用。 */
class FakeAsrTransport : AsrTransport {
    override fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        onPartial("预览")
        onFinal("预览识别文本")
    }

    override fun stop(submit: Boolean) {}
}
