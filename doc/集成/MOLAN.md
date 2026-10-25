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

官方 Skill **`md-preview`**（[agent1-skills](https://github.com/fengshihao/agent1-skills) `skills-src/md-preview/`）封装了这套用法，agent 通过 `load_skill(md-preview)` + `run_bash` 自主预览。内置副本经 `./scripts/sync-skills-bundled.sh` 同步到 `agent_core/.../skills-bundled/md-preview/`。

## Android：直接用线上 try 页（不打 APK、不起本地服务）

墨览线上版已部署：`https://fengshihao.github.io/molan/try/`。Android 侧不打包 studio assets、不依赖 Node，用 **WebView + `MolanHost` 注入接口**（molan PR feat/studio-host-inject）：

```kotlin
webView.loadUrl("https://fengshihao.github.io/molan/try/")
// 页面 ready 后（MolanHost.isReady() === true，或轮询/注入接口自带排队）：
webView.evaluateJavascript(
    "MolanHost.openText(${json(name)}, ${json(text)})", null
)
```

- md 文本由 `logic.data` 从 workspace 读取；WebView 调用属于 `ui.*`（分层规则见 android-layering）
- 未保存修改时 studio 走丢弃确认；注入是**只读副本**语义（保存为下载，不写回原文件）
- molan 侧同窗口 `message { type: "molan:openText" }` 通道仅接受 `event.source === window`，无第三方投喂风险
- 备选：文档若已发布为 URL，`?file=<路径>` 参数可直达（fetch 同源路径）

## 边界

- 预览为**只读语义**：单文件模式内容载入内存，保存为下载副本，不写回原文件。
- 仅桌面（macOS / Ubuntu）；Android 侧沿用自身文档预览能力。
- 需要 `node >= 20`；首次 vendor 准备另需 `pnpm`。
