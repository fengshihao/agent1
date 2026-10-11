package com.agent1.android.productivity.logic.business.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.agent1.android.R

/**
 * 只在一轮对话进行时升到前台。
 *
 * 前台服务让进程离开缓存档，并豁免 Doze / App Standby 的断网。通知是系统要求的可见标记，
 * 文案由 entry 推进来。另外拿一把 partial wake lock，避免 CPU 睡下去把 SSE 读线程饿死；
 * 当前走 Wi-Fi 时再加 WifiLock，避免息屏后射频休眠。
 *
 * 不申请「忽略电池优化」：那会在空闲时也豁免限制。本服务在 Run 结束时立刻退出。
 */
class AgentRunForegroundService : Service() {

    private var promoted = false
    private var title: String = "新对话"
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (suppressUntilNextLaunch && intent?.action != ACTION_STOP_RUN) {
            demoteAndStop(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_RELEASE -> demoteAndStop(startId)
            ACTION_STOP_RUN -> {
                ensureForeground(title, "正在停止…")
                requestAbort()
            }
            ACTION_SYNC -> {
                title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: title
                val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: "正在工作…"
                ensureForeground(title, text)
            }
            else -> demoteAndStop(startId)
        }
        return START_NOT_STICKY
    }

    /** 服务已在前台时直接改通知，避免切后台后再 startForegroundService。 */
    fun render(title: String, text: String) {
        if (suppressUntilNextLaunch) {
            demoteAndStop(lastStartId)
            return
        }
        ensureForeground(title, text)
    }

    fun stopFromClient() {
        suppressUntilNextLaunch = true
        // 先摘掉 running，下一轮必须重新 startForegroundService，避免 stopSelf 把刚续上的前台一起停掉。
        if (running === this) running = null
        demoteAndStop(lastStartId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "dataSync foreground timed out type=$fgsType startId=$startId")
        demoteAndStop(startId)
    }

    override fun onDestroy() {
        if (running === this) running = null
        releaseLocks()
        promoted = false
        super.onDestroy()
    }

    private fun ensureForeground(title: String, text: String) {
        this.title = title
        val notification = buildNotification(title, text)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        promoted = true
        holdRadios()
    }

    private fun requestAbort() {
        val abort = AgentRunStopBridge.abort
        if (abort == null) {
            Log.w(TAG, "stop requested but abort bridge is empty")
            return
        }
        try {
            abort.invoke(applicationContext)
        } catch (ignored: RuntimeException) {
            Log.w(TAG, "abort active run failed", ignored)
        }
    }

    private fun demoteAndStop(startId: Int) {
        releaseLocks()
        if (promoted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            promoted = false
        }
        stopSelf(startId)
    }

    private fun holdRadios() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && wakeLock?.isHeld != true) {
            val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "agent1:run").also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            lock.acquire(MAX_HOLD_MS)
        }
        if (activeNetworkIsWifi()) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi != null && wifiLock?.isHeld != true) {
                @Suppress("DEPRECATION")
                val lock = wifiLock ?: wifi.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "agent1:run-wifi",
                ).also {
                    it.setReferenceCounted(false)
                    wifiLock = it
                }
                lock.acquire()
            }
        } else {
            releaseWifiLock()
        }
    }

    private fun activeNetworkIsWifi(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun releaseLocks() {
        releaseWifiLock()
        val cpu = wakeLock
        if (cpu != null && cpu.isHeld) {
            cpu.release()
        }
    }

    private fun releaseWifiLock() {
        val lock = wifiLock
        if (lock != null && lock.isHeld) {
            lock.release()
        }
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "进行中的对话",
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = "对话进行时保持网络连接。空闲时不会显示。"
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentRunForegroundService::class.java).setAction(ACTION_STOP_RUN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_agent_run)
            .setContentTitle(title)
            .setContentText("正在工作")
            .build()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_agent_run)
            .setColor(0xFFD4773B.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(0, 0, true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(contentIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "AgentRunKeepAlive"
        private const val CHANNEL_ID = "agent_run"
        private const val NOTIFICATION_ID = 0xA11
        private const val MAX_HOLD_MS = 6L * 60L * 60L * 1000L

        @Volatile
        var running: AgentRunForegroundService? = null

        /**
         * Release 先于 onStartCommand 到达、而 startService 又被后台限制拒绝时，
         * 已经排队的 SYNC 看到这个标记就退出，避免通知停不掉。
         */
        @Volatile
        var suppressUntilNextLaunch: Boolean = false

        const val ACTION_SYNC = "com.agent1.android.productivity.RUN_KEEP_ALIVE_SYNC"
        const val ACTION_RELEASE = "com.agent1.android.productivity.RUN_KEEP_ALIVE_RELEASE"
        const val ACTION_STOP_RUN = "com.agent1.android.productivity.RUN_STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"

        fun syncIntent(context: Context, title: String, text: String): Intent {
            return Intent(context, AgentRunForegroundService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
        }

        fun releaseIntent(context: Context): Intent {
            return Intent(context, AgentRunForegroundService::class.java).apply {
                action = ACTION_RELEASE
            }
        }
    }
}
