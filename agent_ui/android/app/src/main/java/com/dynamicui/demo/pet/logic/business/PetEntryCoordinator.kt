package com.dynamicui.demo.pet.logic.business

import android.content.Context
import com.dynamicui.demo.pet.logic.data.PetPlatformGateway
import kotlinx.coroutines.flow.StateFlow

class PetEntryCoordinator(appContext: Context) {
    private val gateway = PetPlatformGateway(appContext)

    val serviceReady: StateFlow<Boolean> = gateway.serviceReady
    val overlayShown: StateFlow<Boolean> = gateway.overlayShown
    val a11yEnabled: StateFlow<Boolean> = gateway.a11yEnabled

    fun onStart() {
        gateway.connect()
        gateway.refreshAccessibilityState()
    }

    fun onStop() {
        gateway.disconnect()
    }

    fun onResume() {
        gateway.refreshAccessibilityState()
    }

    fun showOverlay(): Boolean = gateway.showOverlay()

    fun hideOverlay() {
        gateway.hideOverlay()
    }
}
