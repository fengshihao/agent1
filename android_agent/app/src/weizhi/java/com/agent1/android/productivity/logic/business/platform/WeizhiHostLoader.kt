package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.javaagent.script.MutableScriptToolBridge
import com.agent1.javaagent.session.ProductivityAgentHost
import com.agent1.android.llm.AppFileLog
import com.agent1.javaagent.weizhi.WeizhiPackageSync
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
        AppFileLog.i("WeizhiHost", "refresh package registry")
        WeizhiPackageSync.refreshIndexAsync(agentRoot)
        installBundledAsync(context, agentRoot)
        val scriptTools = MutableScriptToolBridge()
        return ProductivityAgentHost(
            agentRoot,
            config,
            WeizhiAndroidScriptEngineFactory(
                context,
                agentRoot,
                scriptToolBridge = scriptTools,
            ),
            120_000L, // ExecuteScriptTool.DEFAULT_TIMEOUT_MS；timeout_ms 上限 10 分钟
            """
            用户手机上的原件（相册、微信文件等）不在工作区时：先说明需要什么，并在回复开头单独一行写
            [需要用户选文件] 或 [需要用户选文件: pdf,最多3个]，请用户点 App「选择文件」添加。
            用户添加后会写入环境里的可访问文件列表；再用 read_file 读 imports/ 下路径。不要编造已读内容。
            """.trimIndent(),
            scriptTools,
            WeizhiAgentTools(context, agentRoot),
            "android",
        )
    }

    /**
     * 启动时后台装好内置包并刷随包 skill 卡，让第一次 run_js 之前的 find_caps 也能命中包内 skill。
     * engine open 时 [WeizhiBundledInstall] 还会幂等重装。
     */
    private fun installBundledAsync(context: Context, agentRoot: Path) {
        Thread(
            {
                try {
                    WeizhiBundledInstall.syncBundled(context, agentRoot)
                } catch (e: Exception) {
                    AppFileLog.e("WeizhiHost", "install bundled packages failed: ${e.message}")
                }
            },
            "weizhi-bundled-install",
        ).apply {
            isDaemon = true
            start()
        }
    }
}
