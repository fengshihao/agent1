package com.agent1.android.productivity.logic.business

import android.content.Context
import android.net.Uri
import com.agent1.javaagent.config.AgentRuntimeConfig
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
 * 会话与 Run 编排入口（logic.business）。
 *
 * **阻塞语义**：除 [configurationSummary]、[configurationError]、[isRunInProgress]、[abortActiveRun] 外，
 * 公开方法均在内部 `Future.get()` 上等待 agent 线程，**调用方线程会被阻塞**。
 * Android UI / 主线程须用 `withContext(Dispatchers.IO)`；禁止在 Compose 组合阶段直接调用。
 * 静态检查：`./check-android-agent-main-thread.sh`。
 *
 * 进行中的 Run 会占住 agent 线程；[abortActiveRun] 不走该队列，避免停止被本轮 IO 拖住。
 */
class ProductivityAgentGateway(
    private val appContext: Context,
    agentRoot: Path,
    config: AgentRuntimeConfig,
) : Closeable {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "productivity-agent").apply { isDaemon = true }
    }
    private val runtimeConfig: AgentRuntimeConfig = config
    private val host = ProductivityHostAssembly.create(
        appContext,
        agentRoot,
        runtimeConfig,
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
        prepareSession(sessionId)
        host.runtime().stateSnapshot.messages
    }

    fun listAccessibleFilePaths(sessionId: String): List<String> = execute {
        SessionAccessibleFilesStore.listRelativePaths(appContext, sessionId)
    }

    /**
     * 用户从系统文件选择器导入到 workspace/imports/，更新可访问列表与 transcript，不触发 LLM。
     */
    fun importUserPickedFiles(sessionId: String, uris: List<Uri>): List<String> = execute {
        prepareSession(sessionId)
        val workspace = SessionWorkspacePaths.workspaceRoot(appContext, sessionId)
            ?: throw IllegalStateException("workspace missing for session $sessionId")
        val imported = WorkspaceFileImport.copyContentUrisToWorkspace(appContext, workspace, uris)
        if (imported.isEmpty()) {
            return@execute emptyList()
        }
        SessionAccessibleFilesStore.addEntries(
            appContext,
            sessionId,
            imported.map { it.workspaceRelativePath },
            imported.map { it.displayName },
        )
        host.setSessionEnvironmentSupplement(
            SessionAccessibleFilesStore.formatForSystemPrompt(appContext, sessionId),
        )
        host.switchSession(sessionId)
        val paths = imported.map { it.workspaceRelativePath }
        host.appendUserMessageToTranscript("[已添加附件] ${paths.joinToString(", ")}")
        paths
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

    fun isRunInProgress(): Boolean = host.isRunInProgress()

    fun abortActiveRun() {
        host.abortActiveRun()
    }

    fun runUserMessage(
        sessionId: String,
        text: String,
        listener: AgentEventListener,
    ): String = execute {
        prepareSession(sessionId)
        val subscription = host.runtime().subscribe(listener)
        try {
            host.runUserMessage(text)
        } finally {
            closeQuietly(subscription)
        }
    }

    private fun prepareSession(sessionId: String) {
        host.setSessionEnvironmentSupplement(
            SessionAccessibleFilesStore.formatForSystemPrompt(appContext, sessionId),
        )
        host.switchSession(sessionId)
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
