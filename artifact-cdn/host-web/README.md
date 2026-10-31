# host-web

宿主 WebView 静态资源（官方 CDN，见 `AGENT1_CDN_BASE_URL`），与 `web_lib`（Agent 写 HTML 用公共 CDN）分轨。**Android APK 不内置**预览壳；宿主通过 `host-web/registry/stable.json` 拿 URL，`WebView.loadUrl` 加载。

> `webview_exec` 无头沙箱引导页（`weizhi-web`）**不走 CDN**：协议与 weizhi 版本绑定、体积小、要求离线可用，随 APK assets 分发（`android_agent/app/src/weizhi/assets/weizhi-web/`）。

## 发布流程

1. **`manifest.source.json`** — 维护各壳 URL，主机用 `{CDN}` 占位，打包时换成 `--base-url`（仅给打包脚本用；含 vendor URL 供 docx shell 烘焙）。
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

**服务器缓存策略**（nginx）：`/host-web/` 版本目录 `immutable`（30 天），`/host-web/registry/` `no-cache`；改壳时 bump `manifest` / `SHELL_VERSION_QUERY` 的 `?v=`（shell 与 molan/agent1 的 css/js 一并带版本，避免 WebView 粘住旧资源）。

只改了预览壳时，不要跑全量 `./upload.sh`（它还要求 packages / skills 清单和 zip）。

```bash
cd artifact-cdn && npm run pack-host-web
./upload-host-web.sh
```

见 [README.md](../README.md) 与 [doc/集成/MOLAN.md](../../doc/集成/MOLAN.md)。
