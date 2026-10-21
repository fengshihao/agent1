# CLI Web UI — HTML 预览与点选审查（对齐 Android 18 号）

> **拍板（2026-10-21）**：桌面采用 **纯浏览器方案 A**（`./agent1 ui` SPA + 同源 preview API）。**不**要求用户安装 Chromium；**不**做 CDP headed 预览窗作为产品路径。Agent 侧 `webview_exec` / `html_smoke` 仍依赖本机 Chrome（可选），与交互预览解耦。

关联：[18-内置HTML预览与点选审查.md](./自进化Agent/18-内置HTML预览与点选审查.md)（Android 真源行为）、[CLI-Web-UI-MVP.md](./CLI-Web-UI-MVP.md)、[CLI-Web-UI-Android对齐-待决.md](./CLI-Web-UI-Android对齐-待决.md)。

---

## 1. 目标与边界

| 目标 | 说明 |
|------|------|
| 内置预览 | 会话 workspace 内 `.html` 在 Web UI 内打开（当前 iframe 只读 → 升级为可审查） |
| 点选审查 | 开关「审查模式」：点击元素 → 高亮 → 结构化信息 → **草稿回填 Composer（不自动发送）** |
| 自动刷新 | 预览打开期间 Agent `write_file` 改写该 html → debounce reload |
| 契约对齐 | 与 Android 同一套回传 JSON、selector 策略、反馈草稿格式（REQ-120～123） |

| 非目标 | 说明 |
|--------|------|
| 用户安装 Chromium | 预览链路只用系统浏览器；不引导 `AGENT1_CHROMIUM_PATH` |
| CDP 当预览壳 | 不把 `CdpWebViewRuntime` 嵌进聊天 UI；headless 仅服务 `webview_exec` 工具环 |
| 完整 DevTools | 无 Console/Network 面板（与 18 号 Android 一致） |
| Electron / Tauri / JCEF | 维持 MVP 决策 |

**能力分层（避免混淆）**

```text
人眼闭环（本计划）     → 浏览器 SPA + preview API + inspect.js
机器检验（已有）       → html_smoke / 包脚本 + webview_exec（有 Chrome 才更强）
```

---

## 2. 架构

```text
Browser SPA (web_ui)
  preview overlay / 路由
  postMessage ←→ inspect.js（注入在 preview 文档内）
  SSE tool_call → debounce reload
       │
       ▼
UiHttpServer
  GET /api/sessions/{id}/files/...     （现有：直出文件，iframe 只读）
  GET /api/sessions/{id}/preview/...   （新增：包装页，base=workspace，注入 inspect.js）
       │
       ▼
agent_core（新增，与 Android logic.business 对齐）
  ElementSelectorBuilder
  InspectFeedbackComposer
  InspectPayload（回传 JSON 解析 / 容错）
  常量 inspect.js（与 Android assets 同源字符串，单测引用）
```

- **桥接**：Android 用 `@JavascriptInterface`；桌面用 `window.parent.postMessage({ type: 'agent1-inspect', ... }, origin)`，SPA 校验 `event.origin` 与当前页同源。
- **安全**：预览 URL 须经 `SessionWorkspacePaths` 等价校验（CLI 已有 workspace 文件 API）；`inspect` 仅回传 DOM 结构，无 eval / 无任意文件读。
- **外链**：`http(s)` 页面可展示；审查模式对外链文档 **不注入** 或点击无效（对齐 18 号 §5）。

---

## 3. 分阶段交付

### Phase L1 — 共享契约上迁 `agent_core`（1 PR，可与 Android 并行）

| 做 | 验 |
|----|-----|
| Java 实现 `ElementSelectorBuilder`、`InspectFeedbackComposer`、回传 DTO 解析 | 从 Android 单测用例移植/对照：`ElementSelectorBuilderTest`、`InspectFeedbackComposerTest` |
| `inspect.js` 放 `agent_core` resources 或 Java 常量，Android 改为引用同一来源（或代码生成一步，避免双份漂移） | `./java_agent/gradlew -p java_agent :core:test` |
| 文档：Android `logic.business` 保留薄包装或 `@Deprecated` 转发到 core | `./check-android-agent-static.sh` 仍绿 |

### Phase A′ — Preview 包装 API + 只读增强（1 PR）

| 做 | 验 |
|----|-----|
| `GET /api/sessions/{id}/preview/{path...}`：返回 HTML shell，`base` 指向 workspace，内嵌目标页或 `iframe` 同源子帧 | `UiHttpServerTest`：越界路径 404；样例 html 200 |
| Web UI：预览 iframe `src` 改为 preview URL；保留关闭/路径展示 | 手测：相对 css/js 可加载 |
| 聊天气泡 / 产物库「预览」入口不变 | — |

### Phase B′ — 审查模式 + 草稿回填（1 PR）

| 做 | 验 |
|----|-----|
| preview 页注入 `inspect.js`；工具栏「审查模式」由 SPA 控制（postMessage 开关） | 前端单测或 Playwright 可选；最低：core 单测 + `UiHttpServerTest` 断言 shell 含脚本引用 |
| 选中元素 → SPA 调 core composer → `setState` 回填 composer 文本 | 手测：格式与 18 号 §2 示例一致 |
| 底部气泡：selector + 截断文本、「填入输入框」「取消」 | — |

**REQ**：对齐 Android REQ-121、REQ-122。

### Phase C′ — SSE 自动刷新（1 PR）

| 做 | 验 |
|----|-----|
| 预览打开且 path 匹配时，监听 SSE `tool_call`（`write_file` / `edit_file`）或轮询 mtime（fallback） | `UiHttpServerTest` 或 gateway 单测：事件 → 刷新信号 |
| debounce ~300ms 后改 iframe src 或 `location.reload` | 手测：Agent 改 html 后预览更新 |

**REQ**：对齐 REQ-123。

### Phase D′ — Deck 预览标记（小 PR，对齐 21 号）

| 做 | 验 |
|----|-----|
| core 或 cli 复用 `DeckHtmlDetector` 语义；预览工具栏显示 deck 模式提示（与 Android `HtmlPreviewState.isDeck` 一致） | JVM 单测对照 Android `HtmlPreviewViewModelTest` |

---

## 4. 与现有代码锚点

| 主题 | 路径 |
|------|------|
| Android 参考 UI | `android_agent/.../HtmlPreviewScreen.kt`、`HtmlPreviewViewModel.kt` |
| Android 纯函数（迁移源） | `ElementSelectorBuilder.kt`、`InspectFeedbackComposer.kt` |
| Web UI 预览 | `java_agent/web_ui/src/main.ts`（`renderPreviewPanel`） |
| 文件 API | `java_agent/cli/.../ui/`（`UiHttpServer`、workspace files） |
| 机器冒烟 | `agent_core/.../HtmlHarnessSmoke.java`（**不**纳入本计划 UI 依赖） |

---

## 5. 产品文案（对外）

- 「HTML 预览与点选改稿」：**打开 `./agent1 ui` 即可**，无需额外安装浏览器引擎。
- 「自动检查网页」：若本机已安装 Chrome/Chromium，Agent 可在后台做 `html_smoke` / 截图类任务；**没有也不影响预览与审查**。

---

## 6. 进度

| 日期 | 内容 |
|------|------|
| 2026-10-21 | 拍板：纯浏览器方案 A；Chromium 非用户前置条件；本文建档 |
| 2026-10-21 | **Phase L1 完成**：`agent_core` 包 `com.agent1.javaagent.html.inspect`（parser/selector/composer/script + 单测）；Android 改引 core，删除 Kotlin 重复实现 |
| 2026-10-21 | **Phase A′–D′ 完成**：`preview` / `preview-meta` API、`HtmlPreviewShell`、Web UI 审查/草稿/反馈/SSE 刷新、Deck 标记；`DeckHtmlDetector` 上迁 core |
