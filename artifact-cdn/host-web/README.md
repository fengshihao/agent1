# host-web

宿主 WebView 静态资源（`agentone.guoyoutech.cn`），与 `web_lib`（Agent 写 HTML 用公共 CDN）分轨。

## 发布流程

1. **`manifest.source.json`** — 维护 `shell` + vendor 的 **绝对 URL**（仅给打包脚本用）。
2. **`pack-host-web.mjs`** — 复制 vendor 到 `dist/host-web/vendor/`，把 `__JSZIP_URL__` / `__DOCX_PREVIEW_URL__` 替换进 `shell.html` 后写入 `dist`。
3. **`registry/stable.json`** — 只含 `docx_preview.shell`；**不再**向客户端暴露 jszip/docx-preview URL（已 baked 进 shell）。

Android 仅 `WebView.loadUrl(shell)`，不在 Kotlin 里拼 HTML。

```bash
cd artifact-cdn && npm run pack-host-web
```

见 [README.md](../README.md) 中「宿主 Web 静态资源」一节。
