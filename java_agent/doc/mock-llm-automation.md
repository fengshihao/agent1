# Mock LLM 自动化测试

真实 E2E **不必**调用 DashScope / OpenAI：用 `ScriptedLlmClient` 按用户任务关键字或固定轮次返回预定助手回复（含 tool call）。

## 组件

| 类 | 模块 | 作用 |
|----|------|------|
| `ScriptedLlmClient` | `agent_core` | 实现 `LlmClient`，dequeue 预定 `AssistantResponse` |
| `ScriptedResponses` | `agent_core` | 构造纯文本轮次 / 工具调用轮次 |

## 用法示例

```java
ScriptedLlmClient llm = ScriptedLlmClient.builder()
    .whenUserMessageContains(
        "webview-canvas-draw",
        ScriptedResponses.toolCall("webview_exec", argsJson),
        ScriptedResponses.text("画好了")
    )
    .build();

try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm, ...)) {
    host.createSession();
    host.runUserMessage("任务:webview-canvas-draw …");
}
```

按**调用顺序**驱动工具循环时：

```java
ScriptedLlmClient llm = ScriptedLlmClient.sequence(
    ScriptedResponses.toolCall("write_file", "{\"path\":\"a.txt\",\"content\":\"x\"}"),
    ScriptedResponses.text("完成")
);
```

## 现有用例

| 测试 | 模块 | 说明 |
|------|------|------|
| `ScriptedLlmClientTest` | `:core:test` | Mock 工具循环 |
| `ProductivityScriptedReadWriteTest` | `:core:test` | Mock LLM + 真实 write/read 工具 |
| `ProductivityWebViewDrawScriptedTest` | `:weizhi-bridge:test` | Mock LLM + 真实 CDP `webview_exec`（需 Chromium） |
| `CdpWebViewCanvasDrawTest` | `:weizhi-bridge:test` | 直接调工具，无 Host |
| `WebViewCanvasDrawInstrumentedTest` | Android `androidTest` | 真机 WebView，无 LLM |

Android 侧可同样注入 `ScriptedLlmClient` 构造 `ProductivityAgentHost`（需测试 harness 暴露入口）。

## 运行

```bash
gradle -p java_agent :core:test :weizhi-bridge:test
cd android_agent && ./run-webview-draw-test.sh   # 无 LLM
```
