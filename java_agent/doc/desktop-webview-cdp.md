# 桌面 webview_exec（Headless Chromium + CDP）

CLI 生产力路径在检测到本机 Chromium / Chrome 时，会注册与 Android **同名** 工具 `webview_exec`：

- 复用 weizhi `bridge.js` 与 `WebViewTask` / `BridgeCodec` 协议
- 通过 **Chrome DevTools Protocol** 驱动无头浏览器（非 Android WebView 控件）
- 适合 canvas / DOM / wasm 等 QuickJS `run_js` 无法覆盖的任务

## 环境

```bash
# 任选其一
export AGENT1_CHROMIUM_PATH=/usr/bin/google-chrome
# 或安装 chromium / google-chrome 并在 PATH 中

# 关闭桌面 webview_exec
export AGENT1_WEBVIEW=off
```

## 验证（canvas 画 PNG，不走 LLM）

```bash
./sync-weizhi.sh
gradle -p java_agent :weizhi-bridge:test --tests CdpWebViewCanvasDrawTest
./agent1 tools   # 摘要中应含 webview_exec（CDP）
```

## 与 Android 差异

| 项 | Android | 桌面 CDP |
|----|---------|----------|
| 引擎 | 系统 WebView（Chromium） | 独立 headless Chrome 进程 |
| 桥接 | `@JavascriptInterface NativeBridge` | `Runtime.addBinding` + 内存队列兜底 |
| 引导页 | `loadDataWithBaseURL` | 临时 `file://` HTML |

工具参数、回执 JSON、落盘语义与 Android 一致。

## Mock LLM 端到端

不访问大模型时，用 `ScriptedLlmClient` 对用户消息 `webview-canvas-draw` 返回预定 `webview_exec` tool call，见 [`mock-llm-automation.md`](mock-llm-automation.md) 与 `ProductivityWebViewDrawScriptedTest`。
