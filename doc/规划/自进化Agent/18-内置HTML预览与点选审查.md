# 18 — 内置 HTML 预览与点选审查（Android）

> 承接用户方向：HTML 产物**不再跳外部浏览器**，app 内置预览组件；预览页提供**审查模式**——用户点选控件即生成结构化反馈草稿回填对话输入框；AI 改完文件后预览**自动刷新**，形成「预览 → 点选 → 反馈 → AI 修改 → 自动刷新」迭代闭环。
> **每条交付必须带自动化测试**（JVM 单测 / Android instrumented，不依赖真实 LLM）。

关联：[16-渲染-文档与Android附件计划.md](./16-渲染-文档与Android附件计划.md)（Phase F 附件链路）、`ChatTranscriptFormatting.kt`、`WorkspaceFileActions.kt`、`WorkspaceFileAttachments.kt`、`ProductivityNavHost.kt`、`ChatViewModel.kt`、`SessionWorkspacePaths.kt`。

---

## 1. 背景与目标

**现状痛点**

- `ChatTranscriptFormatting.attachmentExt` 不含 `html`，HTML 产物不进附件列表；
- `WorkspaceFileActions.openWorkspaceFile` 用 `ACTION_VIEW` + FileProvider 跳外部浏览器（`MimeTypeMap` 对 `html` 返回 `text/html`），用户被迫跳出 app 查看；
- 用户发现问题后，只能用文字凭记忆描述位置，AI 收到的反馈精度低。

**目标**

| 目标 | 说明 |
|------|------|
| 内置预览 | 会话 workspace 内 `.html` 点击后进入 app 内预览页（WebView），其余类型维持外跳 |
| 点选审查 | 预览页开关「审查模式」：tap 元素 → 高亮 → 结构化信息回传 → 生成反馈草稿回填输入框（**不自动发送**） |
| 反馈结构化 | 草稿含：文件相对路径、CSS selector、截断 outerHTML，AI 可直接定位源码改 `write_file`，无需反查 |
| 自动刷新 | 预览页在前台时文件被 Agent 改写（tool loop 中 `write_file`）→ 自动 reload，形成增量迭代闭环 |

**明确不做（本期）**：Console / Network / DOM 树面板等完整 DevTools UI；`chrome://inspect` 远程调试（开发者向，桌面 CDP 版 `webview_exec` 已覆盖该人群）。

---

## 2. 方案对比（为什么是点选审查）

| 方案 | 反馈精度 | 实现 | 结论 |
|------|----------|------|------|
| **点选审查（本方案）** | 高：selector + outerHTML 直达源码 | 注入一段固定 JS + 专用桥 | ✅ 采用 |
| 截图 + 圈点标注 | 低：仅坐标，AI 需反查源码多一轮 | 中 | 备选，本期不做 |
| chrome://inspect / CDP 远程 | 高 | 依赖桌面 Chrome，普通用户不可用 | 桌面 CDP `webview_exec` 已存在 |
| 完整 DevTools UI | 高 | 工程量大、手机交互差 | 不做 |

「点选审查」即移动端 F12 的合理等价物：用户表达的是**意图 + 精确锚点**，而不是调试面板本身。

---

## 3. 架构与分层（遵守 Android 分层规则）

```text
ui.view     HtmlPreviewScreen（AndroidView 包 WebView；审查模式开关/高亮气泡 UI）
            ↑ 事件回调（选中元素结构 / 返回草稿）
ChatViewModel（回填输入框草稿）
ui.view ←→ logic.business
logic.business  ElementSelectorBuilder（纯函数：回传 JSON → CSS selector）
                InspectFeedbackComposer（纯函数：组装反馈草稿文本）
                PreviewFileValidator（复用 SessionWorkspacePaths.resolveFile 校验路径合法性）
logic.data      WorkspaceFileWatcher（WatchService 监听 .html 变更 → 触发 reload）
                （文件存在性校验复用现有 SessionWorkspacePaths）
assets/logic.business 常量  inspect.js（审查模式注入脚本，非 Android asset 亦可：常量字符串便于 JVM 单测引用）
```

- **WebView 只出现在 `ui.view`**；`logic.*` 不得 import Android UI 类。
- selector 生成 / 草稿组装是**纯 JVM 可测**的纯函数（`logic.business`）。
- 文件监听（`WatchService`）属于 IO，放 `logic.data`；`ui.view` 不做 IO。
- 桥通道**只开审查回传**：`InspectBridge`（`@JavascriptInterface`），与 Weizhi `NativeBridge` 同模式；**不注册通用 JS eval 桥**。

**回传信息结构（inspect.js → InspectBridge → Kotlin）**

```json
{
  "tag": "button",
  "id": "checkout-btn",
  "classes": ["checkout", "primary"],
  "cssSelector": "#checkout-btn",
  "textPreview": "立即支付",
  "outerHtml": "<button id=\"checkout-btn\" class=\"checkout primary\">立即支付</button>（截断至 400 字）"
}
```

**反馈草稿格式（回填输入框，用户补充后手动发送）**

```text
页面 preview/index.html 中 #checkout-btn（<button id="checkout-btn" …>立即支付</button>）这里有问题：
```

---

## 4. 分阶段交付

### Phase A — HTML 内置预览路由（最小可用，1 PR）

| 做 | 验 |
|----|-----|
| `attachmentExt` 加 `html` | `ChatTranscriptFormattingTest`：tool 回执 `outputPath=out/index.html` → 出现在 `workspaceFilePaths` |
| 新路由 `preview/{sessionId}/{path}` + `HtmlPreviewScreen`（`ui.view`，`loadDataWithBaseURL` 以 workspace 目录为 base，解析相对 css/js） | instrumented：加载样例 HTML 断言 `progress==100`；Robolectric/NavHost UI test |
| 点击 HTML 链接不再 `ACTION_VIEW`，改为导航内置预览；其余类型不变 | 既有打开/分享测试不回退 |
| `WorkspaceFileActions` 增 `isPreviewableInApp(path)` 判断（纯函数） | JVM 单测 |

**REQ-120**：会话消息中 `[xx](out/index.html)` 或附件行点击 → app 内预览页打开该文件；外跳行为对其它扩展名不变。

### Phase B — 审查模式 + 反馈草稿回填（1 PR）

| 做 | 验 |
|----|-----|
| `inspect.js`（tap → preventDefault → outline 高亮 → 回传结构 JSON；开关控制注入/移除） | JVM 单测解析回传 JSON 各字段缺失容错 |
| `InspectBridge`（`@JavascriptInterface`）仅回传，不 eval | 静态检查：`ui.view` 之外无 WebView import；桥无 eval 面方法 |
| `ElementSelectorBuilder`：生成稳健 selector（优先 `#id` → 唯一 class → tag+nth 路径，长度上限） | JVM 单测：id / 无 id / class 重复 / 深层嵌套用例 |
| `InspectFeedbackComposer`：拼草稿（路径 + selector + 截断 outerHtml） | JVM 单测：截断、转义、空字段 |
| ChatViewModel：预览页返回时回填草稿到输入框（不自动发送） | `ChatRunActivityTest` 模式新增 VM 单测 |
| 审查模式下底部气泡显示选中元素信息 + 「反馈」/「取消」 | instrumented 可选（@LargeTest） |

**REQ-121**：审查模式开启后 tap 页面元素 → 高亮 → 回传含 `cssSelector` 与 `outerHtml` 的 JSON；关闭审查模式恢复普通浏览。

**REQ-122**：点「反馈」返回聊天页，输入框预填结构化草稿；用户编辑并发送后 AI 收到的消息含文件路径与 selector。

### Phase C — 文件变更自动刷新（闭环，1 PR）

| 做 | 验 |
|----|-----|
| `WorkspaceFileWatcher`（`logic.data`，WatchService，限当前预览文件） | JVM 单测（临时目录写改 → 回调触发，用 CountDownLatch） |
| 预览页前台收到变更 → debounce 300ms reload；后台返回时 resume 比对 mtime | instrumented：改文件 → 断言 reload 触发（桥回调计数） |
| 事件记录：`tool_call`（write_file）链路不回退 | 既有 `:core:test` / `:cli:test` 全绿 |

**REQ-123**：预览页打开期间文件被重写 → 页面自动刷新为新内容，无需用户手动操作。

**REQ-124**（可选增强，本期允许不做）：审查模式多选累计（一次反馈多个元素）与「问题截图随附」。

---

## 5. 安全

- 预览 WebView **只允许加载当前 session workspace 内文件**：加载前经 `SessionWorkspacePaths.resolveFile` 校验，拒绝越界路径；
- `InspectBridge` 只做数据回传，**不暴露 eval / 文件 / 网络能力**；
- `allowFileAccessFromFileURLs=false`、`allowUniversalAccessFromFileURLs=false`（默认，勿开）；相对资源经 baseURL 限制在 workspace；
- 外链 `http(s)://` 默认允许展示但**不走审查桥**（后续若需 vendor 白名单，接 16 号文档 Phase C 的 `trustedCdnHosts`）；
- 反馈草稿中 outerHTML 转义后再入 markdown，防止用户消息注入 prompt 混淆（Composer 纯函数负责截断 + 转义）。

---

## 6. 自动化测试矩阵

| 层级 | 命令 / 类 | 覆盖 |
|------|-----------|------|
| Android unit (JVM) | `ChatTranscriptFormattingTest`（+html 用例）、`ElementSelectorBuilderTest`、`InspectFeedbackComposerTest`、`WorkspaceFileWatcherTest` | A/B/C |
| Android instrumented | `HtmlPreviewInstrumentedTest`（加载 + 注入回传 + 自动刷新）、既有 Open/Share 测不回退 | A/B/C |
| VM 回归 | `:app:testDebugUnitTest` + `ChatRunActivityTest` 新增草稿回填 | B |
| 仓库脚本 | `./check-android-agent-static.sh`（分层）、`./scripts/ci-local.sh full` | 全部 |
| 禁止 | 本计划不新增 LLM E2E burn；审查链路以 Mock/注入断言为准 | — |

---

## 7. 与现有代码衔接点

| 模块 | 改动方向 |
|------|----------|
| `ChatTranscriptFormatting.kt` | `attachmentExt` + `html`；区分「可内置预览」与「外跳」 |
| `WorkspaceFileActions.kt` | 新增 `isPreviewableInApp`；HTML 分流到内置路由 |
| `WorkspaceFileAttachments.kt` / `WorkspaceInlineLinkifiedText.kt` | HTML 链接 onClick → 导航回调（非 context 跳 Intent） |
| `ProductivityNavHost.kt` | 新路由 `preview/{sessionId}/{path}` |
| `ChatViewModel.kt` | 草稿回填（新 state 字段或复用 pending 输入） |
| `SessionWorkspacePaths.kt` | 复用 `resolveFile`；不动语义 |
| `WeizhiHostLoader.kt` / system prompt | 后续可选：告知 AI「可用 `![](path)` + 相对路径产出 HTML，用户会在内置预览中审查」 |

---

## 8. 风险与决策点

| 项 | 选项 / 缺省决策 |
|----|----------------|
| 草稿自动发送 vs 回填 | **回填不自动发送**（用户补充意图，防误发）——保留后续「一键发送」开关 |
| selector 生成策略 | `#id` 优先 → 唯一 class → tag+nth-of-type 路径；长度截断，避免超长 nth 链 |
| WebView 内 console 错误如何反馈 | 本期仅在 UI Toast/角标提示；是否回传给 AI 待定（决策点） |
| 文件监听实现 | `WatchService` vs mtime 轮询；**先 WatchService**，Robolectric/JVM 单测以临时目录验证 |
| 多窗口/外链 | 新 URL 仍限 workspace；`target=_blank` 用同一 WebView 打开 |
| Android 版本 | WebView 特性按 minSdk 现状，不引新依赖 |

---

## 9. 文档索引

| 读者 | 文档 |
|------|------|
| 实施者 | 本文 Phase A–C |
| REQ 登记 | 合并到 [15-可验证需求.md](./15-可验证需求.md) 时追加 REQ-120～124 |
| E2E | 桌面对照见 [java_agent/doc/desktop-webview-cdp.md](../../java_agent/doc/desktop-webview-cdp.md) |