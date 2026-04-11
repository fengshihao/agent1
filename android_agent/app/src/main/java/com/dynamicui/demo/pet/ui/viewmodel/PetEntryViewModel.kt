package com.dynamicui.demo.pet.ui.viewmodel

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.dynamicui.demo.pet.logic.business.PetEntryCoordinator
import com.dynamicui.demo.pet.logic.business.voice.PetVoicePipelineFactory
import com.dynamicui.demo.pet.logic.business.voice.VoiceInputController
import com.dynamicui.demo.pet.ui.overlay.PetOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PetEntryUiState(
    val serviceReady: Boolean = false,
    val overlayVisible: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val statusText: String = "状态：正在连接 Agent 服务…"
)

class PetEntryViewModel(
    private val appContext: Context,
    private val coordinator: PetEntryCoordinator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val overlayManager = PetOverlayManager(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val runOnMain: (() -> Unit) -> Unit = { block -> mainHandler.post { block() } }
    private val voiceController: VoiceInputController = PetVoicePipelineFactory.createController(
        scope = scope,
        runOnMain = runOnMain,
        submitter = { text -> coordinator.boundAgentService.value?.submitUserMessage(text) ?: false }
    )
    private val _overlayShown = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(PetEntryUiState())
    val uiState: StateFlow<PetEntryUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            combine(
                coordinator.serviceReady,
                _overlayShown,
                coordinator.a11yEnabled
            ) { serviceReady, overlayVisible, a11yEnabled ->
                PetEntryUiState(
                    serviceReady = serviceReady,
                    overlayVisible = overlayVisible,
                    accessibilityEnabled = a11yEnabled,
                    statusText = when {
                        !serviceReady -> "状态：正在连接 Agent 服务…"
                        overlayVisible -> "状态：悬浮层已显示"
                        else -> "状态：服务已绑定，可显示悬浮层"
                    }
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onStart() = coordinator.onStart()

    fun onStop() {
        voiceController.stopAll()
        overlayManager.hide()
        _overlayShown.value = false
        coordinator.onStop()
    }

    fun onResume() = coordinator.onResume()

    fun onShowOverlayClicked() {
        val svc = coordinator.boundAgentService.value
        if (svc == null) {
            val current = _uiState.value
            _uiState.value = current.copy(statusText = "状态：服务未就绪，请稍候重试")
            return
        }
        if (!Settings.canDrawOverlays(appContext)) {
            val current = _uiState.value
            _uiState.value = current.copy(statusText = "状态：缺少悬浮窗权限，无法显示")
            return
        }
        overlayManager.show(svc, voiceController)
        _overlayShown.value = true
    }

    fun onHideOverlayClicked() {
        voiceController.stopAll()
        overlayManager.hide()
        _overlayShown.value = false
    }
}
