# Catalog 模块解析（目标行为 vs 当前）

## 期望（产品）

1. **Catalog 脚本目录**（`shared/catalog/scripts`）内的脚本互相引用时，尽量 **不写路径** 或只写逻辑名，例如 `import { markdownToDocx } from 'docx'`，由引擎在 `scriptFolder` 内自动解析。
2. **工作区 orchestrator**（`execute_script` + workspace 内 `.js`）引用标准库时，同样 **不应** 靠 bash/cp；引擎应在解析 `./docx.js` 或 bare name 时 **回退到 scriptFolder**，本地 `./helper.js` 仍相对 workspace。

上述第 2 条需要在 **Weizhi QuickJS 模块加载器** 中实现（workspace 入口 + catalog 回退）。Agent1 在合入前用 `.workspace-run` 镜像作为兼容层。

## 当前 Weizhi 行为（Agent1 观测）

- `setScriptFolder` → catalog 根目录。
- `setFsRoot` → 会话 workspace。
- ES module 的 `import './x.js'` 相对 **当前模块文件路径**；`execute_script` file 模式用 workspace 相对路径作文件名时，**`.` 落在 workspace**，找不到 catalog 里的 `docx.js`。
- 报错形如：`unsupported: module "…" (available: … or ./file.js under script folder!)` —— 即 **仅 scriptFolder 下的 `./leaf.js`** 可靠。

## Agent1 过渡策略（已实现）

| 场景 | 行为 |
|------|------|
| file + ES module，**仅 import catalog**（含 bare `docx` / `docx.js` 重写为 `./docx.js`） | 用 `<eval>` 在 scriptFolder 上下文执行，**不复制文件** |
| file + ES module，**含 workspace 本地** `./helper.js` | 镜像到 `.workspace-run/`（直到 Weizhi 支持 workspace+catalog 双根解析） |
| inline `code` + import | 已是 `<eval>`，可直接 `./docx.js` |

## 建议在 Weizhi 仓库实现

1. **Catalog bare specifier**：在 scriptFolder 维护模块表（`docx` → `docx.js`），支持 `import from 'docx'` / `'docx.js'`。
2. **Workspace 入口回退**：当当前文件在 workspace 下且 `./x` 不存在时，尝试 `scriptFolder/x`（仅一层 `./`，防越权）。
3. （可选）**Import map** JSON 由 Agent1 bootstrap 写入 catalog，Weizhi 启动时加载。

跟踪：与 [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、weizhi#8 同线。
