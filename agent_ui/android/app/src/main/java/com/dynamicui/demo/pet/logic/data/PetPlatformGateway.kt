package com.dynamicui.demo.pet.logic.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.dynamicui.demo.agent.accessibility.service.PetAccessibilityService
import com.dynamicui.demo.pet.ui.overlay.PetOverlayManager
import com.dynamicui.demo.agent.service.AgentForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PetPlatformGateway(private val appContext: Context) {
    private val overlayManager = PetOverlayManager(appContext)

    private var service: AgentForegroundService? = null
    private var serviceBound = false
    private var overlayVisible = false

    private val _serviceReady = MutableStateFlow(false)
    val serviceReady: StateFlow<Boolean> = _serviceReady.asStateFlow()

    private val _overlayShown = MutableStateFlow(false)
    val overlayShown: StateFlow<Boolean> = _overlayShown.asStateFlow()

    private val _a11yEnabled = MutableStateFlow(isAccessibilityServiceEnabled(appContext))
    val a11yEnabled: StateFlow<Boolean> = _a11yEnabled.asStateFlow()

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? AgentForegroundService.LocalBinder ?: return
            service = local.service
            serviceBound = true
            _serviceReady.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            serviceBound = false
            _serviceReady.value = false
        }
    }

    fun connect() {
        val intent = Intent(appContext, AgentForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            appContext.startService(intent)
        }
        appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        if (overlayVisible) {
            overlayManager.hide()
            overlayVisible = false
            _overlayShown.value = false
        }
        if (serviceBound) {
            appContext.unbindService(conn)
            serviceBound = false
            service = null
            _serviceReady.value = false
        }
    }

    fun refreshAccessibilityState() {
        _a11yEnabled.value = isAccessibilityServiceEnabled(appContext)
    }

    fun showOverlay(): Boolean {
        val svc = service ?: return false
        if (!Settings.canDrawOverlays(appContext)) return false
        overlayManager.show(svc)
        overlayVisible = true
        _overlayShown.value = true
        return true
    }

    fun hideOverlay() {
        overlayManager.hide()
        overlayVisible = false
        _overlayShown.value = false
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, PetAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
