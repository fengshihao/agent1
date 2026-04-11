package com.dynamicui.demo.pet.logic.business.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dynamicui.demo.R
import com.dynamicui.demo.pet.logic.business.AgentRunEventSink
import com.dynamicui.demo.pet.logic.business.AgentRunPresentation
import com.dynamicui.demo.pet.logic.business.AgentSessionCoordinator
import com.dynamicui.demo.pet.logic.business.AgentUiEvent
import com.dynamicui.demo.pet.logic.data.service.AgentFileLogger
import com.dynamicui.demo.pet.logic.data.service.ShellCapabilitiesProvider
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 前台服务宿主：属于业务层的 Android 薄壳（`logic.business.platform`），负责进程保活、通知与主线程派发；
 * 会话编排仍在 [AgentSessionCoordinator]。
 */
class AgentForegroundService : Service(), AgentRunEventSink {

    inner class LocalBinder : Binder() {
        val service: AgentForegroundService
            get() = this@AgentForegroundService
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<AgentRunPresentation>()
    private lateinit var sessionCoordinator: AgentSessionCoordinator

    override fun onAgentUiEvent(event: AgentUiEvent) {
        postUiEvent(event)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        AgentFileLogger.init(this)
        AgentFileLogger.clear()
        AgentFileLogger.log("AgentForegroundService", "onCreate")
        createChannel()
        promoteForeground()
        // 预热一次 shell 能力探测，减少首轮脚本生成误判。
        ShellCapabilitiesProvider.probeIfNeeded(force = true)
        sessionCoordinator = AgentSessionCoordinator(
            appContext = this,
            eventSink = this,
            logger = { tag, message -> AgentFileLogger.log(tag, message) }
        )
        sessionCoordinator.initRuntimeIfPossible()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.agent_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun promoteForeground() {
        // 前台服务必须展示通知（系统约束）；点击行为使用 launcher intent，避免 Service 编译期依赖具体 Activity。
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = launchIntent?.let { li ->
            PendingIntent.getActivity(
                this,
                0,
                li,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.agent_notification_title))
            .setContentText(getString(R.string.agent_notification_body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .apply { contentPendingIntent?.let { setContentIntent(it) } }
            .setOngoing(true)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, fgsType)
    }

    private fun postUiEvent(event: AgentUiEvent) {
        when (event) {
            is AgentUiEvent.Streaming -> postStreaming(event.markdown)
            is AgentUiEvent.Finished -> postFinished(event.markdown)
            is AgentUiEvent.Aborted -> postAborted(event.markdown)
            is AgentUiEvent.Error -> postError(event.message)
            is AgentUiEvent.BusyChanged -> postBusy(event.busy)
        }
    }

    private fun postStreaming(text: String) {
        for (l in listeners) {
            mainHandler.post { l.onAssistantStreaming(text) }
        }
    }

    private fun postFinished(text: String) {
        for (l in listeners) {
            mainHandler.post { l.onAgentFinished(text) }
        }
    }

    private fun postAborted(text: String) {
        for (l in listeners) {
            mainHandler.post { l.onAgentAborted(text) }
        }
    }

    private fun postError(msg: String) {
        for (l in listeners) {
            mainHandler.post { l.onAgentError(msg) }
        }
    }

    private fun postBusy(busy: Boolean) {
        for (l in listeners) {
            mainHandler.post { l.onAgentBusyChanged(busy) }
        }
    }

    fun addPresentationListener(listener: AgentRunPresentation) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removePresentationListener(listener: AgentRunPresentation) {
        listeners.remove(listener)
    }

    fun submitUserMessage(text: String): Boolean {
        return sessionCoordinator.submitUserMessage(text)
    }

    fun abortAgent() {
        sessionCoordinator.abortAgent()
    }

    override fun onDestroy() {
        sessionCoordinator.close()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "agent1_agent_service"
        private const val NOTIF_ID = 1001
    }
}
