# Mock LLM 自动化测试

真实 E2E **不必**调用 DashScope / OpenAI：用 `ScriptedLlmClient` 按用户任务关键字或固定轮次返回预定助手回复（含 tool call）。

## 组件

| 类 | 模块 | 作用 |
|----|------|------|
| `ScriptedLlmClient` | `agent_core` | 实现 `LlmClient`，dequeue 预定 `AssistantResponse` |
| `ScriptedResponses` | `agent_core` | 构造纯文本轮次 / 工具调用轮次 |

## Skill 放哪？会进 APK 吗？

| 位置 | 说明 |
|------|------|
| `android_agent/app/src/main/assets/agent_skills/<id>/SKILL.md` | **打进 APK**（`AssetSkillRepository`） |
| 会话 `workspace/skills/<id>/` | 运行时工作区，可覆盖同名内置 skill |
| 项目 `.claude/skills/<id>/` | 桌面 CLI 项目级 skill |

Skill 面向真模型；Mock 剧本在 Java 测试里，通常不进 APK。

## 用法示例

```java
ScriptedLlmClient llm = ScriptedLlmClient.builder()
    .whenUserMessageContains(
        "webview-canvas-draw",
        ScriptedResponses.toolCall("webview_exec", argsJson)
    )
    .whenToolResultSucceeded(ScriptedResponses.text("画好了"))
    .whenToolResultFailed(ScriptedResponses.text("WebView 未成功"))
    .build();
```

**优先级**：上一轮是 `toolResult` 时，先匹配 `whenToolResult*`（按注册顺序、窄规则），再匹配用户任务队列。  
**成败**仍以 JUnit 断言 `RunState`、tool 正文、工作区文件为准，不要只信 Mock 最后一句话。

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
| `ProductivityScriptedToolFailureTest` | `:core:test` | Mock 根据 tool 失败回执分支 |
| `ProductivityWebViewDrawScriptedTest` | `:weizhi-bridge:test` | Mock LLM + 真实 CDP `webview_exec`（需 Chromium） |
| `CdpWebViewCanvasDrawTest` | `:weizhi-bridge:test` | 直接调工具，无 Host |
| `WebViewCanvasDrawInstrumentedTest` | Android `androidTest` | 真机 WebView，无 LLM |

Android 侧可同样注入 `ScriptedLlmClient` 构造 `ProductivityAgentHost`（需测试 harness 暴露入口）。

## 运行

```bash
gradle -p java_agent :core:test :weizhi-bridge:test
cd android_agent && ./run-webview-draw-test.sh   # 无 LLM
```
