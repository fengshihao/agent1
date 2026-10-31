# 集成：墨览（molan）本地 Markdown 预览

> 墨览 · molan：[github.com/fengshihao/molan](https://github.com/fengshihao/molan) —— 打开即阅读，要点再编辑。
> 本文说明 Agent1 如何用 molan studio 在本地预览任意 Markdown 文件。

## 快速使用

```bash
./scripts/molan-preview.sh doc/规划/CLI-Web-UI-MVP.md   # 单文件
./scripts/molan-preview.sh a.md b.md                    # 多文件，侧栏切换
```

脚本流程：定位 molan 仓库（`MOLAN_REPO`，默认与本仓库同级的 `../molan`）→
缺 `vendor/vditor` 时自动 `pnpm install && pnpm build && sync-media`（首次约 1–2 分钟）→
起 `apps/studio/serve.mjs`（默认 `MOLAN_PORT=5517`，绑定 `127.0.0.1`）→ 打开系统浏览器：

```
http://127.0.0.1:5517/?file=/doc/<文件名>
```

## 工作原理

- **molan studio**（`apps/studio`）是零构建纯静态页面（编辑器核心 vditor 以 vendored 产物随仓分发思路管理，`vendor/` 由 `sync-media.mjs` 从 vditor npm 包生成）。
- `serve.mjs` 支持 `MOLAN_ROOT` 指向任意目录。脚本用**临时目录软链组合**：studio 静态资源 + 目标 md 所在目录挂到 `/doc/`（`path.resolve` 前缀检查天然防目录穿越）。
- studio 新增 `?file=<路径>` 参数：启动时 `fetch` 该相对路径的 Markdown，作为单文件"预览"库打开（`molan-app.js` 的 `loadFileParamLibrary`；i18n key `previewFolder` / `previewFetchFailed` 已覆盖 11 种语言）。
- 服务生命周期：脚本前台 `wait`，`Ctrl+C` / 进程退出即停止；服务已在跑则复用，只刷新 URL。

## 与 Skill 的关系

Skill **`md-preview`** 封装了这套用法，agent 通过 `load_skill(md-preview)` + `run_bash` 自主预览（云端 registry，不进 jar/APK）。

## Android：embed 壳 + 官方 CDN + MolanHost 注入

与 docx 预览同轨（同级 `agent1-skills/artifact-cdn/host-web`）。Android **不打包** vditor 大库（约 8.8MB）也 **不打包** 壳 HTML；首装需联网。

- **CDN 目录**：`host-web/molan-embed/1/`（`shell.html`、`molan.css`、`molan-app.js`、`vendor/vditor`、embed 样式与桥）
- **打包**：`cd ../agent1-skills/artifact-cdn && npm run pack-host-web`（含 `pack-molan-embed.mjs`，需同级 `molan` 仓 `apps/studio` 与 vendor）
- **清单**：`host-web/registry/stable.json` 的 `md_preview.shell`；`HostWebRegistrySync.mdPreviewUrls` 可热更新地址
- **加载**：`WebView.loadUrl(md_preview.shell)`；首屏 `agent1-boot` 隐藏布局，正文渲染后 `Agent1Molan.onContentReady()`
- **数据**：`MolanPreviewFileReader` → `Agent1Molan.readMdJson()` → `MolanHost.openText`；磁盘变更 `__agent1MolanRenderFromBridge()`（`force` 覆盖）
- **molan 侧**：`apps/studio/molan-app.js` 支持 `data-agent1-embed="1"` / Agent1 桥；勿再指向 GitHub `try/` 整页 studio
- **入口**：`preview/{sessionId}/{path}` 路由新增 `.md` / `.markdown` 分支（`WorkspaceFileActions.isMdPreviewableInApp`）；聊天气泡与产物库的「应用内打开」自动生效
- **分层**：WebView/壳在 `ui.view`，读取在 `logic.business`，解析在 `ui.viewmodel`（android-layering 规则）
- **不采用** `file://` 域：壳走 https 同源 CDN，`MolanHost` 经 `@JavascriptInterface` 读 workspace 文件
- 备选：文档若已发布为 URL，线上页 `?file=<路径>` 参数可直达（fetch 同源路径）

## 边界

- 预览为**只读语义**：内容载入 WebView 内存，墨览内保存为下载副本，不写回 workspace 原文件。
- 需要 `node >= 20`；首次 vendor 准备另需 `pnpm`（仅桌面脚本）。
