package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.weizhi.caps.WeizhiPackages
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.script.ScriptEngine
import com.agent1.javaagent.script.ScriptEngineFactory
import com.agent1.javaagent.script.ScriptToolBridge
import com.agent1.javaagent.skill.PackageSkills
import com.agent1.javaagent.weizhi.WeizhiIoExecutors
import com.agent1.javaagent.weizhi.WeizhiPackageSync
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

        private val engine = WeizhiEngine(null, WeizhiIoExecutors.io(16))
        private val workspaceFile = workspace.toFile()
        private val packageRoot = WeizhiBundledInstall.syncBundled(appContext, agentRoot)

        init {
            workspaceFile.mkdirs()
            engine.setFsRoot(workspaceFile.absolutePath)
            engine.setPackageRoot(packageRoot)
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
            WeizhiPackageSync.beforeEval(userSource, packageRoot, agentRoot)
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

/**
 * 刷新随包 skill 卡；Package 安装走**云端 registry 动态拉取**（APK 不再预装
 * weizhi-packages，20-A 收口）：engine init 时 assets 为空、syncBundled 只返回
 * weizhi-home 路径；首次 run_js 由 `WeizhiPackageSync.beforeEval` 按源里的
 * import 从 registry ensure。宿主启动线程与 engine open 共用一把锁。
 */
internal object WeizhiBundledInstall {

    private val lock = Any()

    fun syncBundled(context: Context, agentRoot: java.nio.file.Path): String {
        val packageRoot = synchronized(lock) { WeizhiPackages.syncBundled(context) }
        PackageSkills.syncCapabilityCards(agentRoot)
        return packageRoot
    }
}
