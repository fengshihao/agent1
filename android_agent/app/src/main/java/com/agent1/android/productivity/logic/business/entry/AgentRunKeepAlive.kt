package com.agent1.android.productivity.logic.business.entry

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import com.agent1.android.productivity.logic.business.AgentRunKeepAliveClient
import com.agent1.android.productivity.logic.business.AgentWorkKeepAlive
import com.agent1.android.productivity.logic.business.AgentWorkNotice
import com.agent1.android.productivity.logic.business.AgentWorkStatus
import com.agent1.android.productivity.logic.business.KeepAliveAction
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import com.agent1.android.productivity.logic.business.platform.AgentRunForegroundService
import com.agent1.android.productivity.logic.business.platform.AgentRunStopBridge

/**
 * 把「这一轮还在不在跑」同步成前台服务。
 *
 * 工作一开始就升前台（此时用户还在前台，Android 12+ 允许 startForegroundService）。
 * 等到 onStop 再拉起会和后台启动限制撞车，SSE 往往已经断了。
 * Run 结束立刻撤掉；别的会话的空闲状态不能把正在跑的那一轮停掉。
 */
object AgentRunKeepAlive : AgentRunKeepAliveClient {

    private const val TAG = "AgentRunKeepAlive"

    private val abortExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "agent-run-abort").apply { isDaemon = true }
        }
    }
    private val lock = Any()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var appContext: Context? = null
    private var holding = false
    private var ownerSessionId: String? = null
    private var lastNotice: AgentWorkNotice? = null
    private var lastShowElapsedMs = 0L
    private var pending: AgentWorkNotice? = null
    private var flushScheduled = false
    private var launchAccepted = false

    private val flushPending = Runnable {
        val (context, command) = synchronized(lock) {
            flushScheduled = false
            val ctx = appContext
            val notice = pending
            pending = null
            if (ctx == null || notice == null || !holding) {
                null to null
            } else {
                lastNotice = notice
                lastShowElapsedMs = SystemClock.elapsedRealtime()
                ctx to Command.Show(notice)
            }
        }
        if (context != null && command != null) {
            dispatch(context, command)
        }
    }

    init {
        // abortActiveRun 会 waitForIdle，不能放在 Service 的主线程上。
        AgentRunStopBridge.abort = { context ->
            val app = context.applicationContext
            abortExecutor.execute {
                try {
                    ProductivityGatewayProvider.get(app).abortActiveRun()
                } catch (ignored: RuntimeException) {
                    Log.w(TAG, "abort active run failed", ignored)
                }
            }
        }
    }

    override fun sync(context: Context, sessionId: String, status: AgentWorkStatus) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        val command = synchronized(lock) {
            appContext = app
            val since = if (lastShowElapsedMs == 0L) {
                Long.MAX_VALUE
            } else {
                now - lastShowElapsedMs
            }
            val decision = AgentWorkKeepAlive.decide(
                holding = holding,
                ownerSessionId = ownerSessionId,
                sessionId = sessionId,
                previous = lastNotice,
                status = status,
                millisSinceLastShow = since,
            )
            when (decision.action) {
                KeepAliveAction.STOP -> {
                    clearHoldLocked()
                    Command.Release
                }
                KeepAliveAction.SHOW -> {
                    cancelFlushLocked()
                    val notice = decision.notice ?: return@synchronized null
                    markShownLocked(sessionId, notice, now)
                    Command.Show(notice)
                }
                KeepAliveAction.SKIP -> {
                    val notice = decision.notice
                    if (decision.throttled && notice != null) {
                        pending = notice
                        if (!flushScheduled) {
                            flushScheduled = true
                            val wait = (AgentWorkKeepAlive.MIN_NOTICE_INTERVAL_MS - since)
                                .coerceIn(50L, AgentWorkKeepAlive.MIN_NOTICE_INTERVAL_MS)
                            handler.postDelayed(flushPending, wait)
                        }
                    }
                    null
                }
            }
        }
        if (command != null) {
            dispatch(app, command)
        }
    }

    private fun markShownLocked(sessionId: String, notice: AgentWorkNotice, now: Long) {
        holding = true
        ownerSessionId = sessionId
        lastNotice = notice
        lastShowElapsedMs = now
    }

    private fun clearHoldLocked() {
        holding = false
        ownerSessionId = null
        lastNotice = null
        lastShowElapsedMs = 0L
        cancelFlushLocked()
    }

    private fun cancelFlushLocked() {
        pending = null
        if (flushScheduled) {
            flushScheduled = false
            handler.removeCallbacks(flushPending)
        }
    }

    private fun dispatch(context: Context, command: Command) {
        try {
            when (command) {
                is Command.Show -> show(context, command.notice)
                Command.Release -> release(context)
            }
        } catch (error: IllegalStateException) {
            Log.w(TAG, "keep-alive service start rejected", error)
            failShowIfNeverLaunched(command)
        } catch (error: SecurityException) {
            Log.w(TAG, "keep-alive service start rejected", error)
            failShowIfNeverLaunched(command)
        }
    }

    private fun show(context: Context, notice: AgentWorkNotice) {
        AgentRunForegroundService.suppressUntilNextLaunch = false
        val live = AgentRunForegroundService.running
        if (live != null) {
            live.render(notice.title, notice.text)
            launchAccepted = true
            return
        }
        ContextCompat.startForegroundService(
            context,
            AgentRunForegroundService.syncIntent(context, notice.title, notice.text),
        )
        launchAccepted = true
    }

    private fun release(context: Context) {
        launchAccepted = false
        AgentRunForegroundService.suppressUntilNextLaunch = true
        val live = AgentRunForegroundService.running
        if (live != null) {
            live.stopFromClient()
            return
        }
        val started = context.startService(AgentRunForegroundService.releaseIntent(context))
        if (started == null) {
            Log.w(TAG, "keep-alive service missing on release")
        }
    }

    private fun failShowIfNeverLaunched(command: Command) {
        if (command is Command.Show && !launchAccepted && AgentRunForegroundService.running == null) {
            synchronized(lock) { clearHoldLocked() }
        }
    }

    private sealed interface Command {
        data class Show(val notice: AgentWorkNotice) : Command
        data object Release : Command
    }
}
