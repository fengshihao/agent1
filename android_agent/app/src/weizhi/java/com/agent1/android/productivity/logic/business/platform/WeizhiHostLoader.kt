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
        // 与桌面 AgentHomeBootstrap 对齐：创建 catalog 目录并安装 docx.js，否则 docx_* 工具不会注册
        com.agent1.javaagent.agent.AgentHomeBootstrap.ensure(agentRoot)
        AndroidOfficeCatalogSync.ensureFromAssets(context.applicationContext, agentRoot)
        val scriptTools = MutableScriptToolBridge()
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
            图像与 WebView：当 webview_exec 等工具返回 outputPath 指向 png/jpg 等图片时，
            你必须在面向用户的最终回复里用 Markdown 引用工作区相对路径，例如 ![小猫](cat.png)，
            不要粘贴工具 JSON、base64 或 resultPreview。工具执行后若尚未给出带 ![](...) 的总结，应再调用一轮完成说明。

            Word（.docx）：Markdown 转 Word 必须用 docx_markdown_to_word（input_path / output_path），
            调整版式用 docx_inspect、docx_read_grep_edit 或 docx_raw_edit。
            禁止在工作区 execute_script 里 import docx.js（QuickJS 仅 catalog 脚本目录可 import './docx.js'）；
            禁止 bash/read 访问 agentRoot、assets 或 shared/catalog 路径（沙箱外会 Access denied）。
            """.trimIndent(),
            scriptTools,
            WeizhiAgentTools(context),
        )
    }
}
