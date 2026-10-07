# Weizhi 路径策略（Agent1 跟随）

跟踪：[agent1#72](https://github.com/fengshihao/agent1/issues/72)。Weizhi 真源：[INTEGRATION_FOR_AI.md §0.1](https://github.com/fengshihao/weizhi/blob/master/docs/INTEGRATION_FOR_AI.md)、[MODULE_LOADING.md「路径沙箱」](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)、[AGENT_SANDBOX_PROMPT.md](https://github.com/fengshihao/weizhi/blob/master/docs/AGENT_SANDBOX_PROMPT.md)。

## 三层不要混

| 层 | 谁 | 路径规则 |
|----|-----|----------|
| **QuickJS 引擎 `fs` / workspace 内 `import`** | `setFsRoot` = 会话 workspace | 相对或绝对均可；`realpath` / `normalize` 后须在 workspace 根下；越界 `path escape` |
| **Catalog 裸 `import`** | `setScriptFolder` = `agentRoot/shared/catalog/scripts` | 仅单层叶子 `from "docx.js"`，不要 `./` |
| **Java 工具环** | `read_file` / `write_file` / `grep` / `bash`… | `WorkspaceSandbox`：工作区相对或「落在 workspace/agent 文档区」的绝对路径，收成逻辑路径 |
| **平台 Caps** | 脚本内 `android.files.*`、`intent.start({ path })` 等 | **仅**工作区相对路径 |

引擎已统一处理 workspace 内绝对路径与 `..` 归一化；Agent1 **不再**在调用 `runJs` 前把脚本里的 `fs` 路径强行改成相对路径。`execute_script` 的 `file` 仍传 workspace 逻辑路径（如 `jobs/run.js`），便于 `./` 相对 import。

## Agent1 装配

| 项 | 位置 |
|----|------|
| `setFsRoot` | `WeizhiScriptEngine` / `WeizhiAndroidScriptEngineFactory` → 会话 workspace |
| `setScriptFolder` | `AgentCatalogPaths.catalogScriptsDir(agentRoot)` |
| 晋升脚本可 `import` | `PromotionService` 将 `shared/local/scripts/*.js` 镜像到 `shared/catalog/scripts/`（与 scriptFolder 一致） |

## 验收

- `:weizhi-bridge:test`：`WeizhiWorkspaceCatalogModuleFallbackTest`、`WeizhiWorkspaceFsAbsolutePathTest`（需 `libweizhijni`）
- 越界绝对路径仍应失败（引擎报错含 `escape` / `path`）
