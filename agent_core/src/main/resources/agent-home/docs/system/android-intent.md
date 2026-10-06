# Android Intent（android.intent.start）

只在 **Android** 的 `execute_script` 里可用。桌面调用会得到 `unsupported: android.* on this host`。

宿主系统能力经这一条交给别的 App 或系统面板。不要猜 `component`、`package`、extras 或 flags。

```javascript
android.intent.start({ action: "view", path: "每周AI新闻.docx" })
android.intent.start({ action: "send", path: "每周AI新闻.docx", title: "分享" })
android.intent.start({ action: "panel", panel: "wifi" })
android.intent.start({ action: "view", data: "https://example.com" })
```

| 字段 | 说明 |
|---|---|
| `action` | `view`、`send`、`panel` |
| `path` | 工作区相对路径。禁止 `..` 和绝对路径 |
| `type` | 可选 MIME；省略时按扩展名推断 |
| `text` / `title` | `send` 时可选。纯文本、无文件用 `android.share.send({ title, text })` |
| `panel` | `wifi`、`bluetooth`、`location`、`nfc`、`internet` |
| `data` | 仅 `http(s):`、`geo:`、`tel:`、`mailto:` |
| `chooser` | 可选。`send` / `panel` 默认弹出选择器 |

失败时错误原文含 `unsupported: intent.action`、`unsupported: intent.panel`、`bad argument`、`path escape`，或「未找到可打开此文件的应用」。
