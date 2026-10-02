# Agent 运行时说明（system）

本目录由 Agent1 安装到 `agentRoot`，**只读**（工作 Agent 不可 write_file 修改）。

- `directories.md` — 目录用途与读写边界
- `promotion.md` — 创建 Skill / 脚本并 `promote_request` 到 `shared/local`
- `catalog-install.md` — 从 CDN 清单按需 `sync apply` / `catalog_install`（含 native SO）
- `tools-and-quickjs.md` — 文件工具、execute_script、QuickJS 与 Caps
- `office-docx.md` — Word / docx 脚本 API
- `svg-raster.md` — SVG 转 PNG / JPG（`svgToImage`）
- `events-audit.md` — `logs/events.jsonl` 审计事件
- `trusted-sources.md` — catalog 只通过 sync / catalog_install 更新

更完整规划见仓库 `doc/规划/自进化Agent/`。
