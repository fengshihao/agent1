package com.dynamicui.demo.pet.logic.business.voice

import com.dynamicui.demo.pet.logic.data.asr.DashScopeAsrTransport
import kotlinx.coroutines.CoroutineScope

/**
 * 在 business 层装配 ASR 传输与 [VoiceInputController]，避免 ui.viewmodel 直接依赖 logic.data。
 */
object PetVoicePipelineFactory {

    fun createController(
        scope: CoroutineScope,
        runOnMain: (() -> Unit) -> Unit,
        submitter: VoiceInputSubmitter
    ): VoiceInputController {
        val transport = DashScopeAsrTransport(scope, runOnMain)
        return VoiceInputController(transport, submitter)
    }
}
