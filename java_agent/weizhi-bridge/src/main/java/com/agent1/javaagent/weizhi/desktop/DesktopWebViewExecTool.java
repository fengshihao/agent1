package com.agent1.javaagent.weizhi.desktop;

import com.agent1.javaagent.weizhi.WebViewExecHost;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.workspace.WorkspaceSandbox;

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
        description = WebViewExecHost.DESCRIPTION,
        readOnly = false,
        concurrencySafe = false
    )
    public String webviewExec(
        @ToolParam(name = "code", description = WebViewExecHost.CODE_PARAM_DESCRIPTION)
        String code,
        @ToolParam(name = "wasm_url", required = false,
            description = "WebAssembly 模块 URL(http/https,≤50MB),脚本内 loadWasm() 取用")
        String wasmUrl,
        @ToolParam(name = "input_path", required = false,
            description = "输入文件路径(工作区内,≤20MB),脚本内以 Uint8Array 全局变量 input 取用")
        String inputPath,
        @ToolParam(name = "output_path", required = false,
            description = WebViewExecHost.OUTPUT_PATH_DESCRIPTION)
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
