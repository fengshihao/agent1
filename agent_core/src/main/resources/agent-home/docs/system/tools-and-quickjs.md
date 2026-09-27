# 工具与 QuickJS

## 文件工具

- **read_file / write_file / edit_file / list_dir**：仅当前会话 **workspace**。
- **read_agent_doc / list_catalog**：只读 **agentRoot** 下 docs 与 catalog 摘要。

## execute_script

- 优先 **file** 模式（workspace 内 `.js`），便于行号与调试。
- 失败时工具返回 JSON，字段 **location.userLine** 相对用户脚本（prelude 已扣减；Weizhi 内建注入需 D4 完全对齐）。
- inline 过长会触发 Coach **script.inline_long**。
- **Catalog 脚本库（7.2）**：`shared/catalog/scripts` 会挂到 Weizhi `scriptFolder`；workspace 脚本可用 `loadScript("leaf.js")` / `import './leaf.js'` 加载已 sync 的 catalog 脚本或 **js_lib**（js_lib 装完后会镜像叶子名到 `scripts/`）。
- **Native**：`await host.ensureNative("插件名")`；缺插件时同一轮 `execute_script` 会尝试 catalog sync 并重试（见 catalog-install.md）。

## 进化与安装

- **promote_request** → shared/local（staging/skills、staging/scripts）
- **catalog_install** / **catalog_sync_status** → 云端资源（阶段 5）
