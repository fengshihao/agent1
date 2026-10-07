# 桌面生产力路径 — 运行时数据目录

> 与 Android 侧布局一致，仅 **根路径** 不同。对照见 [Android 版](../../android_agent/doc/runtime-data-layout.md) 与 [CLI 与 Android 能力对照](../../doc/CLI与Android-Agent能力对照.md)。  
> 实现以 `agent_core` 为准：`AgentHomeBootstrap`、`FileSessionStore`、`AgentDataPaths`、`McpServersFile`。

## 根路径 `agentRoot`

| 项 | 说明 |
|----|------|
| 解析 | `ProductivityCli.resolveAgentRoot()` → `AgentDataPaths.agentRoot()` |
| 环境变量 | `AGENT1_AGENT_ROOT`（设置后覆盖默认） |
| 默认（未设置 env） | `$HOME/files/agent`（`user.home` + `files/agent`） |
| 项目根 `projectRoot` | `AGENT1_PROJECT_ROOT`；否则 agentRoot 目录名为 `.agent1` 时取其父目录，否则为进程当前工作目录的绝对路径 |

首次构造 `ProductivityAgentHost` 时调用 `AgentHomeBootstrap.ensure(agentRoot)`，创建目录树并写入 `agent.manifest.json`（若不存在）。

**经典 CLI**（`JavaAgentCli` / `./run-java-agent`）不走上述 `agentRoot`；其 JSONL 默认为 `./logs/agent1.jsonl`（`AGENT1_LOG_FILE` 可覆盖）。下文仅描述 **生产力路径**（`./agent1` / `ProductivityCli`）。

## `agentRoot` 目录树（bootstrap 创建）

| 路径 | 类型 | 作用 |
|------|------|------|
| `agent.manifest.json` | 文件 | 自省：schema 版本、coach 阈值、catalog 占位等 |
| `sessions/` | 目录 | 所有会话 |
| `logs/` | 目录 | 全局审计 |
| `sync/` | 目录 | 目录同步状态（`state.json`、`pending.json`、`remote/` 等，按需出现） |
| `docs/system/` | 目录 | 预留；bootstrap **不**拷贝出厂手册，升级时会删除一批已退役文件名 |
| `docs/capabilities/` | 目录 | 能力说明 markdown + SQLite 索引 |
| `shared/catalog/skills/` | 目录 | 目录 Skill |
| `shared/catalog/scripts/` | 目录 | `execute_script` / QuickJS 脚本（含 `svg-raster.js`、Office 脚本等） |
| `shared/catalog/libs/js/` | 目录 | JS 库；sync 后可镜像到 scripts |
| `shared/catalog/libs/qjs/` | 目录 | 纯 JS QJS bundle |
| `shared/catalog/assets/images/` | 目录 | 目录图片资源 |
| `shared/catalog/assets/data/` | 目录 | 目录数据资源 |
| `shared/catalog/native/` | 目录 | Native 插件；常见子路径 `native/<os>-<arch>/`（如 `linux-x64`） |
| `shared/catalog/bundles/` | 目录 | 打包型目录项 |
| `shared/local/skills/` | 目录 | 晋升 / 本地 Skill |
| `shared/local/scripts/` | 目录 | 晋升脚本 |

bootstrap 还会从 classpath `agent-home` 补齐空缺的 catalog 脚本（如 `svg-raster.js`）；Weizhi 联编时 Office 脚本也可从 weizhi 资源拷贝。

| 路径 | 何时出现 | 作用 |
|------|----------|------|
| `mcp_servers.json` | 用户配置 MCP | 已启用 server 列表 |
| `mcp_cache/<name>.json` | MCP 工具列表缓存 | 供 `capability_search` 等 |
| `mcp_cache/<name>.url` / `.schema` | 同上 | URL 与 schema 戳 |
| `docs/capabilities/capabilities.db` | bootstrap | FTS5 能力索引（种子在 core `agent-home`） |
| `docs/capabilities/*.md` | 晋升 / catalog | 单条能力说明（只读） |
| `logs/events.jsonl` | 首次审计事件 | Run、model、tool、usage 等；启动时可能裁剪 |
| `shared/catalog/**` 下具体文件 | `catalog_install` / sync | 远程 manifest 安装产物 |

规划中的 `shared/templates`、`shared/mcp` **当前 bootstrap 不创建**。

## 每个会话 `sessions/<sessionId>/`

`sessionId` 为 UUID。创建会话时由 `FileSessionStore.createSession()` 初始化。

| 路径 | 作用 |
|------|------|
| `meta.json` | 标题、createdAt、updatedAt（写入时可能有 `meta.json.tmp`） |
| `transcript.jsonl` | 消息历史（运行时 append） |
| `accessible-files.json` | 仅 Android 宿主常用；桌面一般为空或不存在 |
| `session.summary.md` | Run 结束后规则化摘要（`SessionSummaryService`） |
| `todos.json` | 当前会话任务清单（`todo_write` 整表替换；空清单则删除） |
| `runs/<runId>.json` | 单次 Run 状态与统计 |
| `workspace/` | **会话沙箱**，工具唯一可写根 |
| `workspace/artifacts/` | 约定产出目录 |
| `workspace/.spill/` | 工具结果过大时 spill 的 `.txt` |
| `workspace/**` | Agent / 脚本写入的任意相对路径 |

读写边界：`WorkspaceSandbox` + `AgentDocReadMounts`（若存在则 `read_file` / `list_dir` / Weizhi grep 可读 `docs/system`、`docs/capabilities` 前缀路径）。**无**独立 `read_agent_doc` 工具。

## 环境变量（与路径相关）

| 变量 | 作用 |
|------|------|
| `AGENT1_AGENT_ROOT` | 数据根 |
| `AGENT1_PROJECT_ROOT` | Skill / 项目上下文根 |
| `AGENT1_EVENTS_LOG_FILE` | 覆盖 `logs/events.jsonl` 路径 |

## 维护说明

变更 `AgentHomeBootstrap`、`FileSessionStore` 或 Android `SessionWorkspacePaths` 时，请同步更新本文与 [android_agent/doc/runtime-data-layout.md](../../android_agent/doc/runtime-data-layout.md)。
