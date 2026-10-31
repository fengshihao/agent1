# CLI Web UI（A + MVP）

> 状态：**MVP 已落地（2026-10-21）** · 交付：本机 HTTP + 系统浏览器 · 范围：聊天 MVP  
> 后续（规划稿、未实现）：系统 WebView 薄壳 + 原生桥 → [桌面WebView壳与原生桥-计划.md](./桌面WebView壳与原生桥-计划.md)

## 决策

- **入口**：`./agent1 ui` → 绑定 `127.0.0.1`，打开系统浏览器
- **平台**：Mac / Windows（浏览器方案）；Ubuntu 同路径可用。CLI REPL 仍以 macOS / Ubuntu 为主
- **内核**：`ProductivityAgentHost` + `AgentRuntime.observeEvents()` / `subscribe`
- **非目标（仍不做）**：MCP 设置页、诊断 zip 导出、Compose Desktop、Electron/Tauri、把 CDP `webview_exec` 当聊天壳

## 架构

```
Browser ──HTTP REST──► UiHttpServer ──► DesktopUiGateway ──► ProductivityAgentHost
Browser ◄──SSE events── UiHttpServer ◄── AgentEvent ◄──────┘
         └── static SPA (classpath web-ui/)
```

- 实时 UI 只订阅内存事件；`events.jsonl` 仅审计
- Gateway 单线程串行 Run；`abort` 旁路（对齐 Android `ProductivityAgentGateway`）
- 导航：持久聊天壳 + 设置 overlay（对齐 `ProductivityNavHost`）

## 配置合并

1. 基底：`AgentRuntimeConfig.fromEnvironment()`
2. 若存在 `agentRoot/ui-config.json`，非空字段覆盖同名项（`apiKey` / `baseUrl` / `model`）
3. UI 保存时写回 `ui-config.json`，并重建 Host（进程内生效，不改系统 env）

未配置 Key 时 **允许** 启动 UI（引导填写）；REPL / 单次提问仍要求已配置。

## 本地 API

仅 `127.0.0.1`；端口 `AGENT1_UI_PORT`（默认 `17831`）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/`、`/assets/*` | SPA |
| GET | `/api/health` | 探活 |
| GET | `/api/config` | 模型摘要（Key 脱敏） |
| PUT | `/api/config` | 更新 Key / baseUrl / model |
| GET | `/api/sessions` | 列表 + `activeSessionId` |
| POST | `/api/sessions` | 新建并切换 |
| POST | `/api/sessions/{id}/use` | 切换 |
| DELETE | `/api/sessions/{id}` | 删除 |
| GET | `/api/sessions/{id}/transcript` | 历史消息 |
| POST | `/api/sessions/{id}/messages` | `{ "text": "..." }` 异步启动 Run |
| POST | `/api/run/abort` | 停止 |
| GET | `/api/events` | SSE：`{ type, timestampMs, sessionId, runId, payload }` |
| GET | `/api/usage` | 全库 events.jsonl 累计 token（`UsageOverview`） |
| GET | `/api/models?provider=&refresh=1` | models.dev 模型列表（失败退回内置表） |
| POST | `/api/sessions/rollover` | `{ "summarize": true }` 规则化总结后新建会话 |
| GET | `/api/tools` | 当前 Host 已注册工具（对齐 Android 工具摘要） |
| GET | `/api/diagnostics/export` | 诊断 zip 下载（sessions/logs，不含 API Key） |
| GET/PUT | `/api/mcp/servers` | `mcp_servers.json` 列表与保存（保存后索引 MCP 工具） |
| GET | `/api/logs/failed?sessionId=` | 失败工具调用（对齐 `./agent1 logs failed`） |
| GET | `/api/sessions/{id}/brief` | 会话简报（`BriefChatExport`，对齐 Android） |
| GET | `/api/sessions/{id}/system-prompt` | 当前会话系统提示词 + 已注册工具（对齐 Android 系统提示词页） |
| GET | `/api/artifacts` | 跨会话产物列表 |
| GET | `/api/capabilities?q=` | `find_caps` 正文（产物库内检索） |
| GET/POST/DELETE | `/api/sessions/{id}/attachments` | 上传到 `imports/` |
| POST | `/api/sessions/{id}/artifacts/import` | 产物复制到当前会话 imports |
| GET | `/api/sessions/{id}/files/{path}` | workspace 文件直出 |
| GET | `/api/sessions/{id}/preview/{path}` | HTML 预览壳（注入审查脚本） |
| GET | `/api/sessions/{id}/preview-meta?path=` | 预览元数据（`isDeck`） |
| POST | `/api/html-inspect/compose` | 审查反馈文案（`elementJson` + `problem`） |

## 前端契约

状态形状对齐 Android `ChatUiState`：

- `lines`：已落盘 transcript
- `runTimeline` + `streamingText`：进行中 Run
- `pendingAskUser`：末尾 `ask_user` 待答
- Composer：发送 / 停止互斥

视觉：molan 纸本 token（见 Android `ProductivityTheme`）。

## 验收（MVP Done）

- [x] `./agent1 ui` 打开浏览器可见纸本风格聊天（静态资源已打包）
- [x] 新建 / 切换 / 删除会话；刷新后 transcript 仍在（REST + Host）
- [x] 流式正文；工具调用可折叠；停止按钮可用（SSE + abort）
- [x] `ask_user` 表单可提交并继续（前端检测 + 普通 user 消息）
- [x] 无 Key 时引导填写；保存后可对话（`ui-config.json`）
- [x] 仅监听 localhost；`:cli:test` 含 `UiHttpServerTest`

## 代码锚点

| 主题 | 路径 |
|------|------|
| 规划（本文） | `doc/规划/CLI-Web-UI-MVP.md` |
| Host | `agent_core/.../ProductivityAgentHost.java`（含 `loadTranscript`） |
| Gateway / HTTP | `java_agent/cli/.../ui/` |
| 前端源码 | `java_agent/web_ui/` |
| 静态资源 | `java_agent/cli/src/main/resources/web-ui/` |

## 进度记录

| 日期 | 内容 |
|------|------|
| 2026-10-21 | MVP 实现：`./agent1 ui`、`DesktopUiGateway`、`UiHttpServer`（REST/SSE）、`web_ui` 打包进 `cli` resources、`ProductivityAgentHost.loadTranscript`、单测 `UiHttpServerTest` / `UiConfigStoreTest` |
| 2026-10-21 | 联调修复：`run-java-agent-gradle` 改为 `:cli:runJavaAgentCli`，修复 `AGENT1_USE_GRADLE=1 ./agent1 ui` 报 `Unknown command-line option '--args'`；端口占用时提示 `--port` / `AGENT1_UI_PORT` |
| 2026-10-21 | P1/P2：新话题按钮、用量摘要 API/UI、core `UsageLogSummary`、系统墨夜主题 |
| 2026-10-21 | P2：core `ArtifactLibrary`、附件/产物库/HTML 预览/能力检索 API 与 Web UI |
| 2026-10-21 | P3：总结并新开话题、models.dev 模型目录、设置页 Run 限额（对齐 Android 设置能力） |
| 2026-10-21 | 对齐补强：core `BriefChatExport`、抽屉 ⚠ 失败工具查询、气泡代码块/行内代码 Markdown |
| 2026-10-21 | Tavily 迁到「MCP 与联网搜索」页；系统提示词页（`GET /api/sessions/{id}/system-prompt`） |

### 如何启动（备忘）

```bash
./agent1 ui                          # fat-jar（首次会构建）
AGENT1_USE_GRADLE=1 ./agent1 ui      # 改 Java 后快速跑 classpath
./agent1 ui --port 17832             # 默认 17831 被占用时
cd java_agent/web_ui && npm ci && npm run build   # 只改前端时
./java_agent/gradlew -p java_agent :cli:buildWebUi # 同上，Gradle 任务
```

## 下一步（建议优先级）

与 Android 对齐、且对**用量**有帮助的项（MVP 之后）：

| 优先级 | 项 | 说明 |
|:------:|-----|------|
| P1 | **「新话题」/ Fresh session** | ✅ Web：`开始新话题` + 抽屉「＋」新建会话；空会话 Toast「已是新会话」 |
| P1 | **抽屉用量摘要** | ✅ `GET /api/usage` + core `UsageLogSummary`；抽屉底栏可点开详情 |
| P2 | 附件 / 产物库 / HTML 预览 | ✅ 附件上传、产物库、HTML iframe 预览、`find_caps` 检索 |
| P2 | 墨夜主题 | ✅ `prefers-color-scheme: dark` CSS 变量 |
| P3 | **总结并新开话题** | ✅ `POST /api/sessions/rollover`（API/CLI；聊天区按钮已移除，对齐 Android） |
| P3 | **Run 限额 / 模型目录** | ✅ `ui-config.json` 限额 + `GET /api/models` + 设置页 |
| P3 | 诊断包 / Tavily / 图片预览 / 工具列表 | ✅ 抽屉导出、`webSearch` 配置、聊天气泡图、`GET /api/tools` |
| P3 | Run Token 摘要 / MCP 设置 / 文件链接 | ✅ SSE `USAGE` 汇总行、`GET/PUT /api/mcp/servers`、气泡内 `[文件](path)` |
| P4 | **HTML 点选审查** | ✅ 纯浏览器 A：`preview` 壳 + 审查模式 + 自动刷新 + Deck 标记（见 [CLI-Web-UI-HTML预览与审查对齐.md](CLI-Web-UI-HTML预览与审查对齐.md)） |
| — | **待拍板** | 见 [CLI-Web-UI-Android对齐-待决.md](CLI-Web-UI-Android对齐-待决.md)（#1～#3 等） |

### 切换 Session 会不会省 Token？

**不自动省。** 行为与 Android / `ProductivityAgentHost` 一致：

| 操作 | 对单次 Run 输入 Token 的影响 |
|------|------------------------------|
| **切换到另一个已有会话** | 加载**该会话**的 transcript 进上下文；历史短则更少、历史长则更多，不是固定「省钱」 |
| **新建会话再聊** | 新会话 transcript 为空或很短，**通常**比在同一长会话里继续追问更省 **input** token |
| **同一会话继续聊** | 历史会累积；受 `maxContextTurns` / `maxContextMessages`（env 或后续设置页）裁剪，不是无限增长 |
| **输出 token** | 与是否切换会话无关，取决于模型回复长度 |

因此：若下一步目标是**省 token**，产品侧更值得做的是 **「新话题」**（新建会话或 Android 式 fresh run）+ **展示用量/上下文裁剪提示**，而不是单纯「多切换几个旧会话」。切换旧会话只是换了一份历史，不会 magically 清空计费上下文。

技术侧已有：`./agent1 logs summarize`、会话 `session.summary.md`（REPL `/summarize`）；Web 可后续暴露「总结后新开话题」流程。
