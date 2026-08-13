package com.dynamicui.demo.productivity.logic.business

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.dynamicui.demo.productivity.logic.business.platform.WeizhiAndroidScriptEngineFactory
import com.agent1.javaagent.event.AgentEventListener
import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.agent1.javaagent.session.ProductivityAgentHost
import com.agent1.javaagent.session.SessionMeta
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 会话与 Run 编排入口（logic.business）；所有 Host 调用在同一线程串行。
 */
class ProductivityAgentGateway(
    context: Context,
    agentRoot: Path,
    config: AgentRuntimeConfig,
) : Closeable {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "productivity-agent").apply { isDaemon = true }
    }
    private val runtimeConfig: AgentRuntimeConfig = config
    private val host = ProductivityAgentHost(
        agentRoot,
        runtimeConfig,
        WeizhiAndroidScriptEngineFactory(context.applicationContext),
        600_000L,
        "",
    )

    fun configurationSummary(): RuntimeConfigSummary =
        RuntimeConfigSummary.from(runtimeConfig)

    fun configurationError(): String? = runtimeConfig.configurationError()

    fun listSessions(): List<SessionMeta> = execute { host.listSessions() }

    fun createSession(): SessionMeta = execute {
        host.createSession()
    }

    fun deleteSession(sessionId: String) = execute {
        host.deleteSession(sessionId)
    }

    fun switchSession(sessionId: String) = execute {
        host.switchSession(sessionId)
    }

    fun getActiveSessionId(): String? = execute { host.activeSessionId }

    fun loadTranscript(sessionId: String): List<AgentMessage> = execute {
        host.switchSession(sessionId)
        host.runtime().stateSnapshot.messages
    }

    fun readTranscriptWithoutSwitch(sessionId: String): List<AgentMessage> = execute {
        val previous = host.activeSessionId
        host.switchSession(sessionId)
        val messages = host.runtime().stateSnapshot.messages
        if (previous != null && previous != sessionId) {
            host.switchSession(previous)
        }
        messages
    }

    fun isRunInProgress(): Boolean = execute { host.isRunInProgress() }

    fun abortActiveRun() = execute { host.abortActiveRun() }

    fun runUserMessage(
        sessionId: String,
        text: String,
        listener: AgentEventListener,
    ): String = execute {
        host.switchSession(sessionId)
        val subscription = host.runtime().subscribe(listener)
        try {
            host.runUserMessage(text)
        } finally {
            closeQuietly(subscription)
        }
    }

    private fun <T> execute(block: () -> T): T {
        val task: Future<T> = executor.submit(Callable { block() })
        return task.get()
    }

    override fun close() {
        execute { host.close() }
        executor.shutdown()
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        if (closeable == null) return
        try {
            closeable.close()
        } catch (_: Exception) {
        }
    }
}
