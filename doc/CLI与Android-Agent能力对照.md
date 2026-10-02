# CLI 与 Android Agent 能力对照

> **对比范围**：桌面 **生产力路径**（`./agent1` / `ProductivityCli`）与 Android **生产力助手**（`ProductivityNavHost` + `ProductivityAgentHost`）。  
> **不含**：经典 CLI（`JavaAgentCli` / `./run-java-agent`）的 bash/python/Claude Skill 四套老工具——该路径仅桌面有，见文末附录。  
> **代码基准**：仓库 `master`，以 `ProductivityAgentHost`、`ProductivityToolCapabilities`（CLI / Android）、`WeizhiWorkspaceTools`、`WeizhiAgentTools` 为准。

## 1. 总览

| 维度 | 桌面生产力 CLI（`./agent1`） | Android 生产力 App | 差距摘要 |
|------|------------------------------|--------------------|----------|
| 运行时内核 | `java-agent-core` → `ProductivityAgentHost` | 同左（Maven / 本地发布） | **已对齐** |
| Weizhi 扩展工具 | 需 classpath 含 weizhi（`../weizhi` 或 `AGENT1_WEIZHI_REPO`） | 需 `WEIZHI_INTEGRATED=true`（联编 weizhi 或 `weizhi-prebuilt`） | **条件对齐**；默认未编 weizhi 时两侧都有工作区读写、`chat_history`、`read_url` 和自进化只读工具 |
| 交互形态 | 终端 REPL + 子命令 | Jetpack Compose 会话列表 / 聊天 / 模型设置 | CLI 偏脚本化运维；Android 偏可视化 |
| 数据目录 `agentRoot` | `AgentDataPaths.agentRoot()`（`AGENT1_AGENT_ROOT`，默认 `~/files/agent`；`./agent1 --help` 文案写 `.agent1`，以代码为准） | `filesDir/agent1` | **路径不同**，布局一致（sessions / logs） |
| 事件 JSONL | `agentRoot/logs/events.jsonl` | 同布局 | **已对齐**；CLI 有查询子命令，App 无内置查询 UI |
| 模型与限额配置 | 环境变量为主（`AgentRuntimeConfig.fromEnvironment()`） | App 内加密偏好 + 可选 BuildConfig 注入 | Android 多 **GUI 配置**；CLI 多 **env/CI** |

## 2. Agent 工具（LLM tool calling）

### 2.1 共有（`ProductivityAgentHost` 内核，与是否集成 Weizhi 无关）

| 工具名 | 能力 | CLI | Android | 备注 |
|--------|------|:---:|:-------:|------|
| `read_file` | 读当前 Session workspace | ✅ | ✅ | `WorkspaceSandbox` |
| `read_url` | 读取公开 http(s) 页面标题与正文 | ✅ | ✅ | 有 `TAVILY_API_KEY` 时先 Tavily Extract，失败再本地抽正文；拒绝本机与内网地址 |
| `write_file` | 写 workspace | ✅ | ✅ | |
| `edit_file` | 补丁式编辑 | ✅ | ✅ | |
| `list_dir` | 列目录 | ✅ | ✅ | |
| `chat_history` | 读当前 Session  transcript | ✅ | ✅ | |
| `read_agent_doc` | 只读 `agentRoot` 下 `docs/system`、`docs/capabilities` | ✅ | ✅ | 资源来自 core 内 `agent-home` |
| `list_catalog` | catalog 摘要（按 kind 计数） | ✅ | ✅ | |
| `catalog_sync_status` | 同步状态占位说明 | ✅ | ✅ | 指向未来 catalog sync |
| `catalog_install` | 远程安装占位（stub） | ✅ | ✅ | 返回 stub 文案 |
| `promote_request` | 晋升占位（stub） | ✅ | ✅ | 未实现真实晋升 |

### 2.2 Weizhi 扩展（集成成功时两侧均有）

| 工具 / 能力 | CLI 装配 | Android 装配 | 差异 |
|-------------|----------|--------------|------|
| `grep` / `glob` / `zip` | `WeizhiWorkspaceTools` | `WeizhiAgentTools` | 行为对齐 |
| `bash` | 桌面 shell 环境 | 设备内 Weizhi bash 沙箱 | **运行环境不同**（权限、可用命令） |
| `load_skill` | 仓库 `.claude/skills` + workspace `skills/` | `assets/agent_skills` + workspace `skills/` | **Skill 来源不同** |
| `execute_script` | Weizhi QuickJS + `$tools` 桥 | 同左 | 两侧系统提示都只要求用脚本完成任务；沙盒细则走 `capability_search` |
| MCP | `McpAgentExtension(agentRoot)`，配置 `agentRoot/mcp_servers.json`；脚本 `$mcp.<server>.<tool>` | 同一 `agentRoot`（`filesDir/agent1`） | **已对齐** |
| Web 渲染 / 脚本页 | `webview_exec`：**Headless Chromium + CDP**（`DesktopWebViewExecTool`） | `WebViewAgentExtension`：**系统 WebView** | **实现不同**；协议与 skill（如 `webview-canvas-draw`）尽量对齐 |
| 脚本超时 | `AGENT1_SCRIPT_TIMEOUT_MS`（默认 600s） | 固定 `600_000` ms（`WeizhiHostLoader`） | Android **未暴露**超时配置 UI/env |

### 2.3 未集成 Weizhi 时的最小集

| 状态 | CLI | Android |
|------|-----|---------|
| 工具摘要 | `ProductivityToolCapabilities.summaryForCli(false)` | `ProductivityToolCapabilities.summaryForUi()`（`WEIZHI_INTEGRATED=false`） |
| 可用工具 | 2.1 节表格 | 同左 |
| 不可用 | grep/glob/zip/bash/load_skill/MCP/webview/execute_script | 同左 |

集成条件对照：

| 平台 | 如何启用完整 Weizhi 环 |
|------|------------------------|
| CLI | 构建含 `weizhi-bridge`；运行时 `WeizhiJniBootstrap` 成功；可选 `AGENT1_SCRIPT_ENGINE=off` 关闭脚本 |
| Android | `weizhi/android` 同级或 `weizhi-prebuilt/maven` → `BuildConfig.WEIZHI_INTEGRATED=true`；见 `android_agent/QUICKSTART.md` |

## 3. 宿主能力（非 tool，但影响体验）

| 能力 | CLI | Android | 差距 |
|------|:---:|:-------:|------|
| 多 Session 创建 / 切换 / 删除 | ✅ REPL：`/new` `/list` `/use` `/delete` | ✅ 会话列表 UI | **已对齐**（交互不同） |
| 单次提问（非交互） | ✅ `./agent1 "问题"` | ❌ 无等价快捷方式 | CLI 独有 |
| 中止当前 Run | ✅ `/stop`、Ctrl+C | ✅ 聊天页停止 | **已对齐** |
| 流式输出 | ✅ 终端 `MESSAGE_UPDATE` | ✅ Compose 订阅事件 | **已对齐** |
| `ProductivityCoach` | ✅ `agent.manifest.json` + env | ✅ 同（读 `agentRoot`） | **已对齐** |
| Run / transcript 落盘 | ✅ `FileSessionStore` / `FileRunStore` | ✅ 同 | **已对齐** |
| `ToolResultSpill` 大结果 spill | ✅ core | ✅ core | **已对齐** |
| 工具能力说明 | ✅ `./agent1 tools`、REPL `/tools` | ✅ 模型设置 / 聊天详情摘要 | 文案 intentionally 对齐 |
| 模型目录与运行时摘要 | ✅ `./agent1 models`（内置 Qwen 表 + 当前 env） | ✅ 设置页「从网络拉取模型」+ 内置表 + 智谱 Coding 回退 | Android **更强**（远程 list models） |
| 事件日志查询 | ✅ `./agent1 logs failed\|children\|summarize` | ❌ 无 App 内查询；可 adb 拉 `agent1/logs/events.jsonl` | **CLI 独有** |
| 诊断包导出 | ❌ 无一键 zip | ✅ `DiagnosticExport`（agent1 树 + crash + logcat） | **Android 独有** |
| 工作区图片 Markdown 预览 | ❌ 终端无内联渲染 | ✅ `WorkspaceImagePreview` / `ChatTranscriptFormatting` | **Android 独有** |
| WebView 绘图 E2E 文档 | ✅ `java_agent/doc/desktop-webview-cdp.md` | ✅ `android_agent/doc/webview-draw-e2e.md` | 各平台文档 |

## 4. 配置与环境

| 项 | CLI | Android |
|----|-----|---------|
| API Key | `DASHSCOPE_API_KEY` / `QWEN_API_KEY` / `OPENAI_API_KEY` 等 | 优先 App 内加密存储；BuildConfig 可注入开发 Key |
| Base URL / Model | 环境变量 | 设置页 + `AgentRuntimePreferences` |
| `maxContextTurns` / `maxTurnsPerRun` / `maxToolCallsPerRun` | 环境变量 | 设置页（0 表示用 core 默认） |
| 项目根（Skill 搜索） | `AGENT1_PROJECT_ROOT` 或 `agentRoot` 父目录 | 无「仓库根」概念；靠 assets + workspace skills |
| Chromium / WebView | `AGENT1_CHROMIUM_PATH`、`AGENT1_WEBVIEW=off` | 系统 WebView；无 CDP |
| Agent 根覆盖 | `AGENT1_AGENT_ROOT` | 固定应用私有目录（无 env） |

## 5. 差距优先级（产品 / 工程视角）

| 优先级 | CLI 已有、Android 仍缺或较弱 | 说明 |
|:------:|-------------------------------|------|
| P0 | **事件日志查询 UI 或导出入口** | 数据已在 `events.jsonl`，CLI 有 `ProductivityLogsCommand` |
| P1 | **`execute_script` 超时与脚本提示可配置** | CLI 有 env；Android 写死 600s，sandbox 提示仅内置 WebView 图片说明 |
| P1 | **与仓库 `.claude/skills` 同步** | 桌面 load_skill 可读项目 skill；Android 仅 assets + workspace |
| P2 | **非交互单次 Run API** | 便于自动化 / Shortcut |
| P2 | **统一 `agentRoot` 默认路径文档** | `./agent1 --help` 与 `AgentDataPaths` 默认值不一致，易误导 |
| — | **catalog_install / promote_request 真实实现** | 两侧均为 stub，属 roadmap 共 gap |

| 优先级 | Android 已有、CLI 可借鉴 | 说明 |
|:------:|--------------------------|------|
| P1 | 远程 `GET /v1/models` 选型 | `ModelCatalogService` |
| P2 | 诊断 zip 一键分享 | 可做成 `./agent1 diag export` |
| P2 | 聊天内 workspace 图片预览 | 终端可链到文件路径或 OSC |

## 6. 代码锚点

| 主题 | CLI / 桌面 | Android |
|------|------------|---------|
| Host 装配 | `ProductivityCli.java` | `ProductivityHostAssembly.kt` → `WeizhiHostLoader.kt` |
| 工具列表 | `ProductivityAgentHost.buildTools` | 同 core |
| Weizhi 环 | `WeizhiWorkspaceTools.java` | `WeizhiAgentTools.kt` |
| 能力摘要 | `ProductivityToolCapabilities.java` | `ProductivityToolCapabilities.kt` |
| Gateway / UI | — | `ProductivityAgentGateway.kt`、`ChatViewModel.kt` |
| 日志查询 | `ProductivityLogsCommand.java` | — |

---

## 附录 A — 经典 CLI（`JavaAgentCli`）独有

经典路径 **不是** 当前 `./agent1` 默认入口，但仍在仓库维护，能力如下：

| 能力 | 经典 CLI | Android |
|------|:--------:|:-------:|
| `run_bash` | ✅ 全机 shell | ❌ |
| `run_python` | ✅ | ❌ |
| `SkillTool` + `/skill-name` 斜杠命令 | ✅ 扫描仓库 `.claude/skills` | ❌ |
| JSONL 日志 | `logs/agent1.jsonl`（`AGENT1_LOG_FILE`） | ❌ |
| 生产力 Session / workspace | ❌（CWD 为 workspace） | ✅ |

进入生产力模式：`JavaAgentCli --productivity` 或 `./agent1`。

## 附录 B — 如何刷新本对照表

1. 改工具装配时同步更新 `ProductivityToolCapabilities`（CLI 与 Android 各一份摘要文案）。  
2. 跑 `./agent1 tools` 与 Android 设置页摘要，确认字符串仍一致。  
3. 集成状态以 `WeizhiHostSupport.tryCreateFactory`（CLI）与 `BuildConfig.WEIZHI_INTEGRATED`（Android）为准。
