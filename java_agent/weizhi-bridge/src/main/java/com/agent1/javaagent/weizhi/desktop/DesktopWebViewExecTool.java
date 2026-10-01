package com.agent1.javaagent.weizhi.desktop;

import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.weizhi.agent.sandbox.WorkspaceSandbox;
import com.weizhi.agent.tool.Tool;
import com.weizhi.agent.tool.ToolParam;

/**
 * 桌面 CDP（Headless Chromium）版 {@code webview_exec}，与 Android {@code WebViewExecTool} 同名同参。
 */
public final class DesktopWebViewExecTool {

    private final CdpWebViewRuntime runtime;
    private final WorkspaceSandbox sandbox;

    public DesktopWebViewExecTool(CdpWebViewRuntime runtime, WorkspaceSandbox sandbox) {
        this.runtime = runtime;
        this.sandbox = sandbox;
    }

    @Tool(
        name = "webview_exec",
        description = "在无头 WebView(Chromium 内核,支持 WebAssembly/DOM/canvas)中执行一段 JavaScript,"
            + "适合 PDF 生成、格式转换、wasm 重计算等 run_js(QuickJS)跑不了的任务;"
            + "普通计算请直接用 run_js。code 顶层 return 返回结果;"
            + "可用全局:input(input_path 文件的 Uint8Array,未提供则 null)、"
            + "loadWasm()——返回 wasm_url 模块的 WebAssembly.Module Promise、console.log(随回执返回)。"
            + "图片 Base64(PNG/JPEG/GIF/WebP 开头)不论大小都会写入工作区临时文件"
            + " tmp/webview_exec/<id>.b64,回执给 outputPath 与 outputBytes;"
            + "resultPreview 只有说明,不含 Base64 正文。其它结果超过 64KB 时同样自动落盘。"
            + "不超过 64KB 且不是图片 Base64 的结果完整放在 resultPreview,不写文件。"
            + "output_path 可选,用来覆盖默认临时路径;文件内容仍是返回值的 UTF-8 文本,"
            + "扩展名不表示二进制。若 return Base64,文件内容就是这段 Base64。"
            + "返回 null 或 undefined 时 ok 为 false,不创建、不覆盖文件。"
            + "成功回执含 resultType(null、string、number、boolean、object、array)。"
            + "任务超时(timeout_ms,默认 60000,上限 600000)后页面被强杀重置。",
        readOnly = false,
        concurrencySafe = false
    )
    public String webviewExec(
        @ToolParam(name = "code", description = "要执行的 JavaScript 代码(顶层 return 返回结果;异步任务返回 Promise)")
        String code,
        @ToolParam(name = "wasm_url", required = false,
            description = "WebAssembly 模块 URL(http/https,≤50MB),脚本内 loadWasm() 取用")
        String wasmUrl,
        @ToolParam(name = "input_path", required = false,
            description = "输入文件路径(工作区内,≤20MB),脚本内以 Uint8Array 全局变量 input 取用")
        String inputPath,
        @ToolParam(name = "output_path", required = false,
            description = "可选。覆盖默认临时路径 tmp/webview_exec/<id>.b64。"
                + "写入返回值的 UTF-8 文本,扩展名不表示二进制格式;"
                + "return Base64 则文件内容就是这段 Base64。"
                + "返回 null 或 undefined 时拒绝写入(ok:false),不覆盖已有文件。"
                + "不传则由运行时在图片 Base64 或结果超过 64KB 时自动落盘。")
        String outputPath,
        @ToolParam(name = "timeout_ms", required = false,
            description = "任务超时毫秒数(默认 60000,上限 600000)")
        String timeoutMs
    ) {
        return WebViewExecSupport.execute(
            runtime,
            sandbox,
            code,
            wasmUrl,
            inputPath,
            outputPath,
            timeoutMs
        );
    }
}
