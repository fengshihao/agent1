package com.dynamicui.demo.pet.logic.business.entry

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.dynamicui.demo.pet.logic.business.platform.AgentForegroundService
import com.dynamicui.demo.pet.logic.data.accessibility.service.PetAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 宠物入口的进程侧编排：启动/绑定前台 Agent 服务、读无障碍开关等。
 * 悬浮层由 [com.dynamicui.demo.pet.ui.overlay.PetOverlayManager] 持有；悬浮窗权限在 ViewModel 查询。
 */
class PetPlatformGateway(private val appContext: Context) {

    private var serviceBound = false

    private val _boundAgentService = MutableStateFlow<AgentForegroundService?>(null)
    val boundAgentService: StateFlow<AgentForegroundService?> = _boundAgentService.asStateFlow()

    private val _serviceReady = MutableStateFlow(false)
    val serviceReady: StateFlow<Boolean> = _serviceReady.asStateFlow()

    private val _a11yEnabled = MutableStateFlow(isAccessibilityServiceEnabled(appContext))
    val a11yEnabled: StateFlow<Boolean> = _a11yEnabled.asStateFlow()

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? AgentForegroundService.LocalBinder ?: return
            _boundAgentService.value = local.service
            serviceBound = true
            _serviceReady.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _boundAgentService.value = null
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
        if (serviceBound) {
            appContext.unbindService(conn)
            serviceBound = false
            _boundAgentService.value = null
            _serviceReady.value = false
        }
    }

    fun refreshAccessibilityState() {
        _a11yEnabled.value = isAccessibilityServiceEnabled(appContext)
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
