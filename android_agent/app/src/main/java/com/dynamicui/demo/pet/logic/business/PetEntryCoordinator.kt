package com.dynamicui.demo.pet.logic.business

import android.content.Context
import com.dynamicui.demo.pet.logic.business.platform.AgentForegroundService
import com.dynamicui.demo.pet.logic.business.entry.PetPlatformGateway
import kotlinx.coroutines.flow.StateFlow

class PetEntryCoordinator(appContext: Context) {
    private val gateway = PetPlatformGateway(appContext)

    val serviceReady: StateFlow<Boolean> = gateway.serviceReady
    val boundAgentService: StateFlow<AgentForegroundService?> = gateway.boundAgentService
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
}
