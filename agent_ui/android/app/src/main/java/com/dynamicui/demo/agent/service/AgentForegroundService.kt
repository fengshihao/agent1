package com.dynamicui.demo.agent.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.dynamicui.demo.MainActivity
import com.dynamicui.demo.R
import com.dynamicui.demo.pet.logic.business.AgentSessionCoordinator
import com.dynamicui.demo.pet.logic.business.AgentUiEvent
import android.os.Binder
import java.util.concurrent.CopyOnWriteArrayList

class AgentForegroundService : Service() {

    inner class LocalBinder : Binder() {
        val service: AgentForegroundService
            get() = this@AgentForegroundService
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<AgentUiListener>()
    private lateinit var sessionCoordinator: AgentSessionCoordinator

    interface AgentUiListener {
        fun onAssistantStreaming(fullMarkdown: String) {}
        fun onAgentFinished(finalMarkdown: String) {}
        fun onAgentAborted(partialMarkdown: String) {}
        fun onAgentError(message: String) {}
        fun onAgentBusyChanged(busy: Boolean) {}
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
            emitUiEvent = { postUiEvent(it) },
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
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.agent_notification_title))
            .setContentText(getString(R.string.agent_notification_body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
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

    fun addUiListener(listener: AgentUiListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeUiListener(listener: AgentUiListener) {
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
