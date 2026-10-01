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
            + "output_path 写入的是返回值的 UTF-8 文本,不是按扩展名生成的二进制文件;"
            + "若 return 的是 Base64,文件内容就是这段 Base64。"
            + "返回 null 或 undefined 且提供了 output_path 时 ok 为 false,不会把文本 null 写入文件。"
            + "成功回执含 resultType(null、string、number、boolean、object、array);"
            + "字符串 \"null\" 与 JSON null 靠 resultType 区分。"
            + "结果 >64KB 时须提供 output_path 落盘(回执只含路径+预览)。"
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
            description = "结果落盘路径(工作区内相对路径)。写入返回值的 UTF-8 文本,"
                + "扩展名不表示二进制格式;return Base64 则文件内容就是这段 Base64。"
                + "返回 null 或 undefined 时拒绝落盘(ok:false),不会写入文本 null。"
                + "大结果(>64KB)必须提供,回执返回路径+预览")
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
