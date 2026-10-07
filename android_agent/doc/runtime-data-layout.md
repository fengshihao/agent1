# Android 生产力 App — 运行时数据目录

> 与桌面生产力 CLI **同一套 `agentRoot` 布局**；根路径为应用私有 `filesDir`。对照见 [桌面版](../../java_agent/doc/runtime-data-layout.md) 与 [CLI 与 Android 能力对照](../../doc/CLI与Android-Agent能力对照.md)。  
> 包名：`com.agent1.android`。`adb` 查看示例：`run-as com.agent1.android ls files/agent1`

## 路径前缀

| 符号 | API | 典型绝对路径 |
|------|-----|----------------|
| **$FILES** | `Context.getFilesDir()` | `/data/user/0/com.agent1.android/files/` |
| **$CACHE** | `Context.getCacheDir()` | `/data/user/0/com.agent1.android/cache/` |
| **$PREFS** | SharedPreferences | `/data/user/0/com.agent1.android/shared_prefs/` |
| **$AGENT** | `filesDir/agent1` | `$FILES/agent1/` |

代码入口：`ProductivityGatewayProvider.agentRoot()`、`SessionWorkspacePaths.agentRoot()`。

首次使用生产力 Gateway（打开聊天等）时构造 `ProductivityAgentHost` → `AgentHomeBootstrap.ensure($AGENT)`。集成 Weizhi（`WEIZHI_INTEGRATED=true`）时额外执行 `AndroidOfficeCatalogSync`，从 APK `assets/office/` 写入 `shared/catalog/scripts/` 下 Office 脚本。

## $FILES 根下（App 壳层，非 agent1）

| 路径 | 何时创建 | 作用 |
|------|----------|------|
| `boot_trace.txt` | `Application.onCreate` 起 | 启动阶段轨迹（`BootTrace`） |
| `last_crash_report.txt` | 崩溃或 `recordHandledFailure` | 最近一次错误栈 |
| `crash-reports/crash-*.txt` | 同上 | 崩溃归档（诊断 zip 会打包） |

## $AGENT — 与桌面一致的 agent 家目录

完整树形说明见 [java_agent/doc/runtime-data-layout.md](../../java_agent/doc/runtime-data-layout.md) 中「`agentRoot` 目录树」与「每个会话」两节。Android 上 **路径相同、语义相同**。

摘要：

| 区域 | 要点 |
|------|------|
| 根 | `agent.manifest.json`、`sessions/`、`logs/`、`sync/`、`docs/`、`shared/`、`mcp_servers.json`、`mcp_cache/` |
| 能力 | `docs/capabilities/capabilities.db`（系统 SQLite，经 `AndroidCapabilityDatabase`） |
| 审计 | `logs/events.jsonl` |
| 会话 | `sessions/<id>/meta.json`、`transcript.jsonl`、`runs/*.json`、`workspace/` |

## Android 特有（会话与宿主）

| 路径（相对 `sessions/<id>/` 或 workspace） | 作用 |
|-------------------------------------------|------|
| `accessible-files.json` | 用户「选择文件」后的可访问列表；注入系统提示（`SessionAccessibleFilesStore`） |
| `workspace/imports/` | 系统选择器导入的副本（`WorkspaceFileImport`） |
| `workspace/tmp/webview_exec/*.b64` | Weizhi `webview_exec` 大结果 / 图片（UTF-8 Base64 文本） |

助手请用户选文件：回复中的 `[需要用户选文件]` 标记 + 聊天页「选择文件」按钮（见 `UserFileRequestMarkers`）。

MCP 配置与缓存在 **`$AGENT` 根**，**不**写入 `workspace/.mcp`。

## $CACHE — 临时

| 路径 | 作用 |
|------|------|
| `diagnostics/agent1-diag-*.zip` | 用户导出诊断包；分享后删除，启动时清理超过 24h 的残留（`DiagnosticExport`） |

Instrumented 测试可能使用 `cache/webview-canvas-test/`；正式用户路径不会出现。

## $PREFS — 模型与调试

| 文件 | 作用 |
|------|------|
| `agent_runtime_prefs.xml` | 加密存储 API Key、模型、限额等（失败时回退 `agent_runtime_prefs_plain.xml`） |
| `llm_ui_debug_prefs.xml` | 崩溃栈备份（与 `last_crash_report.txt` 互补） |

## 诊断 zip 内容（代码内 README）

导出时打包：`agent1/` 树、`crash-reports/`、`logcat.txt`、`device.txt` 等；**不含** API Key。字段说明见 `DiagnosticExport` 内 `readme()` 文本。

## 维护说明

变更 Android 落盘逻辑时，请同步更新本文与 [java_agent/doc/runtime-data-layout.md](../../java_agent/doc/runtime-data-layout.md)。
