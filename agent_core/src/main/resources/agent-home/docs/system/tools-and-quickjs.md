# 工具与 QuickJS

## 文件工具

- **read_file / write_file / edit_file / list_dir**：仅当前会话 **workspace**。
- **read_url**：抓取公开 http(s) 页面，返回标题和正文。设置 `TAVILY_API_KEY` 时先用 Tavily Extract 抽同一个 URL，失败再本地抓取。不访问本机、私网或链路本地地址；工作区文件仍用 read_file。
- **read_file / grep / glob / list_dir**（路径 `docs/system/...`）：只读 **agentRoot** 下 docs。

## execute_script

- **code 与 file 二选一**：短一次性用 code；超过约 20 行或 1000 字符、或还要改时，写到 workspace `.js` 再用 file。后续用 `edit_file`，少占 token。按此原则自行判断。
- 失败时工具返回 JSON，字段 **location.userLine** 相对用户脚本（prelude 已扣减；Weizhi 内建注入需 D4 完全对齐）。
- inline 过长会触发 Coach **script.inline_long**。
- **Catalog 脚本库（7.2）**：`shared/catalog/scripts` → `setScriptFolder`；见 Weizhi `MODULE_LOADING.md`。
- **工作区 orchestrator（file 模式）**：`runJs` 使用 workspace 相对 filename（如 `jobs/run.js`）；`import './docx.js'` 先查 workspace 再 **回退 catalog**；`import './helper.js'` 仍在 workspace。Agent1 不再镜像 `.workspace-run/`。
- **Word**：在 `execute_script` 里 `import … from './docx.js'`（先 workspace，再 catalog）。API 见 `docs/system/office-docx.md`。没有外层 docx 工具。
- **Zip / bash**：集成 Weizhi 时有外层 `zip_extract`、`zip_create`、`bash`（沙箱内、命令白名单）。脚本内仍可用 `import zip from "zip"` 或平台 `files.zipExtract` / `files.zipCreate`。
- **SVG → PNG/JPG**：bootstrap `svg-raster.js`。orchestrator `import { svgToImage } from './svg-raster.js'`，传入 `svgPath`、`width`、`height`（或 `length`）、`format`。写出二进制图片。详见 `docs/system/svg-raster.md`。
- **Native**：`await host.ensureNative("插件名")`；缺插件时同一轮 `execute_script` 会尝试 catalog sync 并重试（见 catalog-install.md）。

## 进化与安装

- **promote_request** → shared/local（staging/skills、staging/scripts）
- **catalog_install** / **catalog_sync_status** → 云端资源（阶段 5）
