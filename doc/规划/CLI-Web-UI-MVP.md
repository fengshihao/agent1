# CLI Web UI（A + MVP）

> 状态：**MVP 已落地（2026-10-21）** · 交付：本机 HTTP + 系统浏览器 · 范围：聊天 MVP

## 决策

- **入口**：`./agent1 ui` → 绑定 `127.0.0.1`，打开系统浏览器
- **平台**：Mac / Windows（浏览器方案）；Ubuntu 同路径可用。CLI REPL 仍以 macOS / Ubuntu 为主
- **内核**：`ProductivityAgentHost` + `AgentRuntime.observeEvents()` / `subscribe`
- **非目标（延后）**：附件、产物库、HTML 预览、MCP/能力检索页、诊断导出、Compose Desktop、Electron/Tauri、把 CDP `webview_exec` 当聊天壳

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
| P2 | 附件 / 产物库 / HTML 预览 | 仍延后 |
| P2 | 墨夜主题 | ✅ `prefers-color-scheme: dark` CSS 变量 |

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
