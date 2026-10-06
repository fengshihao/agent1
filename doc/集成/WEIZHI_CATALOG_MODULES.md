# Catalog 模块解析（Weizhi 主干行为）

Weizhi [weizhi#10](https://github.com/fengshihao/weizhi/pull/10)（closes [weizhi#9](https://github.com/fengshihao/weizhi/issues/9)）已合并：**workspace 相对 import 失败时，单层 `./leaf.js` 回退到 `setScriptFolder`**。

规范真源：[weizhi `docs/MODULE_LOADING.md`](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)

## Agent1 约定

| 根 | 路径 |
|----|------|
| `setFsRoot` | 会话 workspace |
| `setScriptFolder` | `agentRoot/shared/catalog/scripts`（bootstrap：`docx.js`、`docx-raw.js`、`docx-build.js`、`svg-raster.js`） |

- **给模型的写法**：catalog 脚本用裸导入 `import { markdownToDocx } from "docx.js"`。`./` 只表示 workspace 里和当前脚本放在一起的文件（如 `import './helper.js'`）。
- **兼容**：单层 `./leaf.js` 在 workspace 找不到时仍回退到 `setScriptFolder`，但不再教模型写 `./`。
- Agent1 **不再**使用 `.workspace-run/` 镜像（见 [agent1#30](https://github.com/fengshihao/agent1/issues/30)）。

## 集成测

- `WeizhiWorkspaceCatalogModuleFallbackTest`（`:weizhi-bridge:test`，需已构建 `libweizhijni`，Weizhi ≥ `dd7904c`）

## 关联

- [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、weizhi#8
