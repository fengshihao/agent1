package com.dynamicui.demo.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.script.ScriptEngine
import com.agent1.javaagent.script.ScriptEngineFactory
import com.weizhi.WeizhiEngine
import com.weizhi.caps.AndroidCaps
import com.weizhi.platform.PlatformHost
import java.nio.file.Path

/**
 * Android 侧 Weizhi：会话工作区 + {@link AndroidCaps}（与 {@code WeizhiEngine.setFsRoot} 同路径）。
 */
class WeizhiAndroidScriptEngineFactory(
    private val appContext: Context,
    private val confirmer: (String) -> Boolean = { true },
) : ScriptEngineFactory {

    override fun open(workspace: Path): ScriptEngine {
        return AndroidWeizhiScriptEngine(appContext, workspace, confirmer)
    }

    private class AndroidWeizhiScriptEngine(
        appContext: Context,
        workspace: Path,
        confirmer: (String) -> Boolean,
    ) : ScriptEngine {

        private val engine = WeizhiEngine()
        private val workspaceFile = workspace.toFile()

        init {
            workspaceFile.mkdirs()
            engine.setFsRoot(workspaceFile.absolutePath)
            val session = AndroidCaps.Session(appContext, workspaceFile)
            session.confirmer = PlatformHost.Confirmer { message -> confirmer(message) }
            AndroidCaps.install(engine, session)
        }

        override fun eval(jsSource: String, timeoutMs: Long, cancellationToken: CancellationToken): String {
            if (cancellationToken.isCancelled) {
                throw java.util.concurrent.CancellationException("cancelled")
            }
            val timeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return engine.runJs(jsSource, timeout)
        }

        override fun cancel() {
            engine.cancel()
        }

        override fun close() {
            engine.close()
        }
    }
}
