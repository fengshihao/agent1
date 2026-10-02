# Catalog 模块解析（Weizhi 主干行为）

Weizhi [weizhi#10](https://github.com/fengshihao/weizhi/pull/10)（closes [weizhi#9](https://github.com/fengshihao/weizhi/issues/9)）已合并：**workspace 相对 import 失败时，单层 `./leaf.js` 回退到 `setScriptFolder`**。

规范真源：[weizhi `docs/MODULE_LOADING.md`](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)

## Agent1 约定

| 根 | 路径 |
|----|------|
| `setFsRoot` | 会话 workspace |
| `setScriptFolder` | `agentRoot/shared/catalog/scripts`（bootstrap：`docx.js`、`docx-raw.js`、`docx-build.js`、`svg-raster.js`） |

- **`execute_script` file 模式**：`runJs(..., "jobs/run.js")`，脚本内 `import './docx.js'`（先 workspace，再 catalog）；`import './helper.js'` 仅 workspace。
- **系统提示**：教 AI 用 `import … from "./docx.js"` 等同目录叶子名；**避免** bare `import 'xxx'`（除文档明确列出的官方库外），以免与 catalog 扁平命名冲突。
- Agent1 **不再**使用 `.workspace-run/` 镜像（见 [agent1#30](https://github.com/fengshihao/agent1/issues/30)）。

## 集成测

- `WeizhiWorkspaceCatalogModuleFallbackTest`（`:weizhi-bridge:test`，需已构建 `libweizhijni`，Weizhi ≥ `dd7904c`）

## 关联

- [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、weizhi#8
