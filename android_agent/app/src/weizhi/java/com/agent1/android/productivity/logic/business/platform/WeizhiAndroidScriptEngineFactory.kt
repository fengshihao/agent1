package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.catalog.AgentCatalogPaths
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.script.ScriptEngine
import com.agent1.javaagent.script.ScriptEngineFactory
import com.agent1.javaagent.script.ScriptToolBridge
import com.agent1.javaagent.weizhi.WeizhiScriptToolInstaller
import com.weizhi.WeizhiEngine
import com.weizhi.caps.AndroidCaps
import com.weizhi.platform.PlatformHost
import java.nio.file.Path

/**
 * Android 侧 Weizhi：会话工作区 + {@link AndroidCaps}（与 {@code WeizhiEngine.setFsRoot} 同路径）。
 */
class WeizhiAndroidScriptEngineFactory(
    private val appContext: Context,
    private val agentRoot: java.nio.file.Path,
    private val confirmer: (String) -> Boolean = { true },
    private val scriptToolBridge: ScriptToolBridge? = null,
) : ScriptEngineFactory {

    override fun open(workspace: Path): ScriptEngine {
        return AndroidWeizhiScriptEngine(appContext, agentRoot, workspace, confirmer, scriptToolBridge)
    }

    private class AndroidWeizhiScriptEngine(
        appContext: Context,
        private val agentRoot: java.nio.file.Path,
        workspace: Path,
        confirmer: (String) -> Boolean,
        private val scriptToolBridge: ScriptToolBridge?,
    ) : ScriptEngine {

        private val engine = WeizhiEngine()
        private val workspaceFile = workspace.toFile()

        init {
            AndroidOfficeCatalogSync.ensureFromAssets(appContext, agentRoot)
            workspaceFile.mkdirs()
            engine.setFsRoot(workspaceFile.absolutePath)
            val scriptDir = AgentCatalogPaths.resolveCatalogScriptFolder(agentRoot)
            if (scriptDir != null) {
                engine.setScriptFolder(scriptDir.toString())
            }
            engine.enableFetch()
            val session = AndroidCaps.Session(appContext, workspaceFile)
            session.confirmer = PlatformHost.Confirmer { message -> confirmer(message) }
            session.launchIntent = true
            session.launchShareSheet = true
            AndroidCaps.install(engine, session)
            WeizhiScriptToolInstaller.install(engine, scriptToolBridge)
        }

        override fun eval(jsSource: String, timeoutMs: Long, cancellationToken: CancellationToken): String {
            return evalForAgent(jsSource, null, timeoutMs, cancellationToken, null)
        }

        override fun evalForAgent(
            userSource: String?,
            agentArgsPrelude: String?,
            timeoutMs: Long,
            cancellationToken: CancellationToken,
            workspaceRelativeFile: String?,
        ): String {
            if (cancellationToken.isCancelled) {
                throw java.util.concurrent.CancellationException("cancelled")
            }
            val timeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            var last = ""
            for (step in WeizhiScriptToolInstaller.evalSteps(
                userSource,
                agentArgsPrelude,
                scriptToolBridge,
                workspaceRelativeFile,
                agentRoot,
            )) {
                if (cancellationToken.isCancelled) {
                    throw java.util.concurrent.CancellationException("cancelled")
                }
                last = engine.runJs(step.source, timeout, step.filename)
            }
            return last
        }

        override fun cancel() {
            engine.cancel()
        }

        override fun close() {
            engine.close()
        }
    }
}
