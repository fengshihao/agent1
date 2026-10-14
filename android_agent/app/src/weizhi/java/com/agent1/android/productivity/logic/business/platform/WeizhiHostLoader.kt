package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.javaagent.script.MutableScriptToolBridge
import com.agent1.javaagent.script.ScriptWeizhiPackageEnsurer
import com.agent1.javaagent.session.ProductivityAgentHost
import com.agent1.javaagent.weizhi.WeizhiPackageRegistrySync
import com.agent1.javaagent.weizhi.WeizhiScriptPackageEnsurer
import com.weizhi.caps.WeizhiPackages
import com.weizhi.packages.PackageInstaller
import java.io.IOException
import java.nio.file.Path

/** 由 {@link com.agent1.android.productivity.logic.business.ProductivityHostAssembly} 反射加载。 */
object WeizhiHostLoader {

    @JvmStatic
    fun create(
        context: Context,
        agentRoot: Path,
        config: AgentRuntimeConfig,
    ): ProductivityAgentHost {
        com.agent1.javaagent.agent.AgentHomeBootstrap.ensure(agentRoot)
        val scriptTools = MutableScriptToolBridge()
        val weizhiHome = PackageInstaller.weizhiHome(context.filesDir.toPath())
        val packageEnsurer: ScriptWeizhiPackageEnsurer? = WeizhiScriptPackageEnsurer.optional(
            agentRoot,
            weizhiHome,
            Runnable {
                try {
                    WeizhiPackages.syncBundled(context)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            },
        )
        val registryRefresh = Runnable {
            WeizhiPackageRegistrySync.ensureIndexed(agentRoot)
            WeizhiPackageRegistrySync.backgroundUpgradeInstalled(agentRoot, weizhiHome)
        }
        return ProductivityAgentHost(
            agentRoot,
            config,
            WeizhiAndroidScriptEngineFactory(
                context,
                agentRoot,
                scriptToolBridge = scriptTools,
            ),
            600_000L,
            """
            用户手机上的原件（相册、微信文件等）不在工作区时：先说明需要什么，并在回复开头单独一行写
            [需要用户选文件] 或 [需要用户选文件: pdf,最多3个]，请用户点 App「选择文件」添加。
            用户添加后会写入环境里的可访问文件列表；再用 read_file 读 imports/ 下路径。不要编造已读内容。
            """.trimIndent(),
            scriptTools,
            WeizhiAgentTools(context, agentRoot),
            "android",
            packageEnsurer,
            registryRefresh,
        )
    }
}
