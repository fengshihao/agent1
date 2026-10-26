# host-web

宿主 WebView 静态资源（`agentone.guoyoutech.cn`），与 `web_lib`（Agent 写 HTML 用公共 CDN）分轨。**Android APK 不内置**预览壳；宿主通过 `host-web/registry/stable.json` 拿 URL，`WebView.loadUrl` 加载。

> `webview_exec` 无头沙箱引导页（`weizhi-web`）**不走 CDN**：协议与 weizhi 版本绑定、体积小、要求离线可用，随 APK assets 分发（`android_agent/app/src/weizhi/assets/weizhi-web/`）。

## 发布流程

1. **`manifest.source.json`** — 维护各壳的 **绝对 URL**（仅给打包脚本用；含 vendor URL 供 docx shell 烘焙）。
2. **`pack-host-web.mjs`** — vendor、`docx-preview` shell、`registry/stable.json`。
3. **`pack-molan-embed.mjs`**（`npm run pack-host-web` 第二步）— `molan-embed/1/` 运行时 + 烘焙 `shell.html`。

`registry/stable.json` 字段：

| 字段 | 用途 |
|------|------|
| `docx_preview.shell` | Word 预览 WebView |
| `md_preview.shell` | 墨览 Markdown 预览 |

jszip / docx-preview **不**进 registry（已 bake 进 docx shell）。

**Android 加载**

- docx / md 预览：`loadUrl(registry 里的 shell URL)`；`HostWebRegistrySync` 后台刷新 registry。
- `webview_exec`：`BootstrapAssets` 读 APK assets（不经网络）。

**服务器缓存策略**（nginx）：`/host-web/` 版本目录 `immutable`（30 天），`/host-web/registry/` `no-cache`；改壳时 bump URL 的 `?v=`。

```bash
cd artifact-cdn && npm run pack-host-web
./upload.sh
```

见 [README.md](../README.md) 与 [doc/集成/MOLAN.md](../../doc/集成/MOLAN.md)。
