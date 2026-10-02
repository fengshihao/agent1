# 工具与 QuickJS

## 文件工具

- **read_file / write_file / edit_file / list_dir**：仅当前会话 **workspace**。
- **read_url**：抓取公开 http(s) 页面，返回标题和正文。设置 `TAVILY_API_KEY` 时先用 Tavily Extract 抽同一个 URL，失败再本地抓取。不访问本机、私网或链路本地地址；工作区文件仍用 read_file。
- **read_file / grep / glob / list_dir**（路径 `docs/system/...`）与 **list_catalog**：只读 **agentRoot** 下 docs 与 catalog 摘要。

## execute_script

- 优先 **file** 模式（workspace 内 `.js`），便于行号与调试。
- 失败时工具返回 JSON，字段 **location.userLine** 相对用户脚本（prelude 已扣减；Weizhi 内建注入需 D4 完全对齐）。
- inline 过长会触发 Coach **script.inline_long**。
- **Catalog 脚本库（7.2）**：`shared/catalog/scripts` → `setScriptFolder`；见 Weizhi `MODULE_LOADING.md`。
- **工作区 orchestrator（file 模式）**：`runJs` 使用 workspace 相对 filename（如 `jobs/run.js`）；`import './docx.js'` 先查 workspace 再 **回退 catalog**；`import './helper.js'` 仍在 workspace。Agent1 不再镜像 `.workspace-run/`。
- **Word**：在 `execute_script` 里 `import … from './docx.js'`（先 workspace，再 catalog）。API 见 `docs/system/office-docx.md`。没有外层 docx 工具。
- **Zip**：`import zip from "zip"` 后 `zip.extractSync` / `zip.createSync`，或平台对象 `files.zipExtract` / `files.zipCreate`。没有外层 zip / bash 工具。
- **Native**：`await host.ensureNative("插件名")`；缺插件时同一轮 `execute_script` 会尝试 catalog sync 并重试（见 catalog-install.md）。

## 进化与安装

- **promote_request** → shared/local（staging/skills、staging/scripts）
- **catalog_install** / **catalog_sync_status** → 云端资源（阶段 5）
