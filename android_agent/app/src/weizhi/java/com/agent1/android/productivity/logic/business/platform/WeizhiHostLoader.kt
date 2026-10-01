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
            图像与 WebView：webview_exec 的 output_path 写入的是脚本 return 值的 UTF-8 文本，不是按扩展名生成的二进制图片。
            要保存图，必须 return 纯 Base64，例如 return canvas.toDataURL('image/png').split(',')[1]；文件内容就是这段 Base64。
            成功回执含 resultType。返回 null 或 undefined 且带了 output_path 时 ok 为 false，文件不会写成文本 null，不要告诉用户已经画好。
            仅当 ok 为 true 且 outputPath 指向 png/jpg 时，在最终回复里用 Markdown 引用工作区相对路径，例如 ![小猫](cat.png)，
            不要粘贴工具 JSON、base64 或 resultPreview。工具执行后若尚未给出带 ![](...) 的总结，应再调用一轮完成说明。

            Word（.docx）：简单转换用 docx_markdown_to_word（`#` 标题自带字号层次）；勿在 Markdown 里插 HTML 改样式。
            复杂流程在工作区写 orchestrator（file 模式，如 jobs/run.js），`import './docx.js'` 由 Weizhi 回退到 catalog（勿 cp）。勿 bash/read agentRoot 或 assets。

            用户手机上的原件（相册、微信文件等）不在工作区时：先说明需要什么，并在回复开头单独一行写
            [需要用户选文件] 或 [需要用户选文件: pdf,最多3个]，请用户点 App「选择文件」添加。
            用户添加后会写入环境里的可访问文件列表；再用 read_file 读 imports/ 下路径。不要编造已读内容。
            """.trimIndent(),
            scriptTools,
            WeizhiAgentTools(context),
            "android",
        )
    }
}
