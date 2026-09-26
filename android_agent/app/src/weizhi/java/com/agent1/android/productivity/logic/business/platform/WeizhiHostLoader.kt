package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.javaagent.script.MutableScriptToolBridge
import com.agent1.javaagent.session.ProductivityAgentHost
import java.nio.file.Path

/** 由 {@link com.agent1.android.productivity.logic.business.ProductivityHostAssembly} 反射加载。 */
object WeizhiHostLoader {

    @JvmStatic
    fun create(
        context: Context,
        agentRoot: Path,
        config: AgentRuntimeConfig,
    ): ProductivityAgentHost {
        val scriptTools = MutableScriptToolBridge()
        return ProductivityAgentHost(
            agentRoot,
            config,
            WeizhiAndroidScriptEngineFactory(
                context,
                scriptToolBridge = scriptTools,
            ),
            600_000L,
            """
            图像与 WebView：当 webview_exec 等工具返回 outputPath 指向 png/jpg 等图片时，
            你必须在面向用户的最终回复里用 Markdown 引用工作区相对路径，例如 ![小猫](cat.png)，
            不要粘贴工具 JSON、base64 或 resultPreview。工具执行后若尚未给出带 ![](...) 的总结，应再调用一轮完成说明。
            """.trimIndent(),
            scriptTools,
            WeizhiAgentTools(context),
        )
    }
}
