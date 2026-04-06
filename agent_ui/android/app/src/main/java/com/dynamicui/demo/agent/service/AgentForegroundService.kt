package com.dynamicui.demo.agent.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.agent1.javaagent.core.AgentOptions
import com.agent1.javaagent.core.AgentRuntime
import com.agent1.javaagent.event.AgentEvent
import com.agent1.javaagent.event.AgentEventListener
import com.agent1.javaagent.event.AgentEventType
import com.agent1.javaagent.event.EventPayloads
import com.agent1.javaagent.llm.openai.OpenAiCompatibleClient
import com.agent1.javaagent.llm.openai.OpenAiCompatibleConfig
import com.agent1.javaagent.model.AgentMessage
import com.dynamicui.demo.BuildConfig
import com.dynamicui.demo.MainActivity
import com.dynamicui.demo.R
import com.dynamicui.demo.agent.accessibility.core.PageContextPromptBuilder
import com.dynamicui.demo.agent.accessibility.core.PageSnapshotStore
import com.dynamicui.demo.agent.accessibility.tools.ExtractMainContentTool
import com.dynamicui.demo.agent.accessibility.tools.GetCurrentPageSnapshotTool
import com.dynamicui.demo.agent.accessibility.tools.GetShellCapabilitiesTool
import com.dynamicui.demo.agent.accessibility.tools.QueryMediaStoreTool
import com.dynamicui.demo.agent.accessibility.tools.ActOnUiTool
import com.dynamicui.demo.agent.accessibility.tools.RunShellTool
import com.dynamicui.demo.agent.accessibility.tools.RunIntentTool
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class AgentForegroundService : Service(), AgentEventListener {

    inner class LocalBinder : Binder() {
        val service: AgentForegroundService
            get() = this@AgentForegroundService
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var runtime: AgentRuntime? = null
    private val listeners = CopyOnWriteArrayList<AgentUiListener>()

    private val abortRequested = AtomicBoolean(false)
    private var hadErrorThisRun = false

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
        createChannel()
        promoteForeground()
        // 预热一次 shell 能力探测，减少首轮脚本生成误判。
        ShellCapabilitiesProvider.probeIfNeeded(force = true)
        initRuntimeIfPossible()
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

    private fun initRuntimeIfPossible() {
        val key = BuildConfig.DASHSCOPE_API_KEY.trim()
        if (key.isEmpty()) {
            postError("未配置 DASHSCOPE_API_KEY")
            return
        }
        val voicePrompt = try {
            assets.open(VOICE_PROMPT_ASSET).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            postError("无法读取语音助手提示词: " + (e.message ?: ""))
            return
        }
        if (voicePrompt.isBlank()) {
            postError("语音助手提示词为空")
            return
        }
        val llmClient = OpenAiCompatibleClient(
            OpenAiCompatibleConfig(
                key,
                BuildConfig.DASHSCOPE_BASE_URL.trim(),
                Duration.ofSeconds(120),
                0.2
            )
        )
        val options = AgentOptions.builder("qwen3.5-flash")
            .systemPrompt(voicePrompt)
            .tools(
                listOf(
                    GetCurrentPageSnapshotTool(),
                    GetShellCapabilitiesTool(),
                    QueryMediaStoreTool(this),
                    RunIntentTool(this),
                    ActOnUiTool(),
                    ExtractMainContentTool(),
                    RunShellTool()
                )
            )
            .build()
        val rt = AgentRuntime(options, llmClient)
        rt.subscribe(this)
        runtime = rt
    }

    override fun onEvent(event: AgentEvent) {
        when (event.type) {
            AgentEventType.AGENT_START -> {
                hadErrorThisRun = false
                postBusy(true)
                postStreaming("")
            }
            AgentEventType.MESSAGE_UPDATE -> {
                val p = event.payload as? EventPayloads.MessageUpdate ?: return
                val text = p.partialMessage.content
                postStreaming(text)
            }
            AgentEventType.AGENT_END -> {
                val p = event.payload as? EventPayloads.AgentEnd ?: return
                val finalText = lastAssistantContent(p.messages)
                postBusy(false)
                when {
                    abortRequested.getAndSet(false) -> {
                        hadErrorThisRun = false
                        postAborted(finalText)
                    }
                    hadErrorThisRun -> {
                        hadErrorThisRun = false
                    }
                    else -> postFinished(finalText)
                }
            }
            AgentEventType.AGENT_ERROR -> {
                val p = event.payload as? EventPayloads.AgentError
                hadErrorThisRun = true
                postBusy(false)
                postError(p?.message ?: "未知错误")
            }
            else -> Unit
        }
    }

    private fun lastAssistantContent(messages: List<AgentMessage>): String {
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (AgentMessage.ROLE_ASSISTANT == m.role) {
                return m.content
            }
        }
        return ""
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
        val rt = runtime ?: run {
            postError("Agent 未初始化，请检查 API Key")
            return false
        }
        val msg = text.trim()
        if (msg.isEmpty()) return false
        val pageContext = PageContextPromptBuilder.build(PageSnapshotStore.latest())
        val shellContext = ShellContextBuilder.build()
        val enriched = buildString {
            appendLine("[当前环境]")
            appendLine(pageContext)
            appendLine()
            appendLine(shellContext)
            appendLine()
            appendLine("[用户请求]")
            append(msg)
        }
        return try {
            rt.prompt(enriched)
            true
        } catch (_: IllegalStateException) {
            postError("上一轮尚未结束，请稍候")
            false
        } catch (e: Exception) {
            postError(e.message ?: "请求失败")
            false
        }
    }

    fun abortAgent() {
        abortRequested.set(true)
        runtime?.abort()
    }

    override fun onDestroy() {
        runtime?.close()
        runtime = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "agent1_agent_service"
        private const val NOTIF_ID = 1001
        private const val VOICE_PROMPT_ASSET = "prompts/voice_assistant_system_prompt.txt"
    }
}
