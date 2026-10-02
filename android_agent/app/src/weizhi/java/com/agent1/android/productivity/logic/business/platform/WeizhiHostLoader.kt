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
            图像与 WebView：webview_exec 写入的是脚本 return 值的 UTF-8 文本，不是按扩展名生成的二进制图片。
            要保存图，return 纯 Base64，例如 return canvas.toDataURL('image/png').split(',')[1]。
            output_path 可选：省略时图片 Base64 会自动落盘到 tmp/webview_exec/*.b64；也可自行指定路径（扩展名 .png 时内容仍是 Base64 文本）。
            成功回执含 resultType 与 outputPath（落盘时 resultPreview 仅为占位说明）。返回 null 或 undefined 时 ok 为 false，不要告诉用户已经画好。
            仅当 ok 为 true 时，在最终回复里用 Markdown 引用回执 outputPath（png/jpg 或 tmp/webview_exec/*.b64），例如 ![小猫](tmp/webview_exec/wv-123-1.b64)，
            不要粘贴工具 JSON、base64 或 resultPreview。工具执行后若尚未给出带 ![](...) 的总结，应再调用一轮完成说明。
            SVG 转 PNG 或 JPG：在 execute_script 的 file 脚本里 import { svgToImage } from './svg-raster.js'（svgPath、width、height 或 length、format 为 png 或 jpg）。它写出二进制图片，回复里用 ![](outputPath) 引用该路径，不要引用临时 .b64。

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
