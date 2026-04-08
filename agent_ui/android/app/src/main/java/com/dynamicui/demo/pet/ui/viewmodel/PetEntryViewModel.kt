package com.dynamicui.demo.pet.ui.viewmodel

import com.dynamicui.demo.pet.logic.business.PetEntryCoordinator
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

class PetEntryViewModel(private val coordinator: PetEntryCoordinator) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(PetEntryUiState())
    val uiState: StateFlow<PetEntryUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            combine(
                coordinator.serviceReady,
                coordinator.overlayShown,
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

    fun onStop() = coordinator.onStop()

    fun onResume() = coordinator.onResume()

    fun onShowOverlayClicked() {
        val ok = coordinator.showOverlay()
        if (!ok) {
            val current = _uiState.value
            _uiState.value = current.copy(
                statusText = if (!current.serviceReady) {
                    "状态：服务未就绪，请稍候重试"
                } else {
                    "状态：缺少悬浮窗权限，无法显示"
                }
            )
        }
    }

    fun onHideOverlayClicked() = coordinator.hideOverlay()
}
