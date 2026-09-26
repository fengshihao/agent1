---
name: webview-canvas-draw
description: 用 webview_exec 在 canvas 上绘制 PNG 并落盘（禁止调用大模型生图）
---

# WebView Canvas 绘图（验收用）

当用户要求「画一张图」「绘制图片」且明确 **不要用大模型生图 / 不要用 multimodal 出图** 时：

1. **必须**使用工具 `webview_exec`，在 Chromium 无头 WebView 里用 **canvas 2D** 绘制。
2. **禁止**依赖模型的图像生成能力；若模型不支持生图，仍应走 webview_exec。
3. JavaScript 顶层 `return` 返回 PNG 的 **纯 base64**（`toDataURL('image/png').split(',')[1]`）。
4. 必须提供 `output_path`（工作区相对路径，如 `draw/webview.png`），把 base64 文本写入该文件。
5. 完成后用 `read_file` 或回执中的 `outputPath` 向用户确认路径；必要时 `list_dir` 展示。

## webview_exec 示例代码（可改配色与几何，保持 256×256）

```javascript
const canvas = document.createElement('canvas');
canvas.width = 256;
canvas.height = 256;
const ctx = canvas.getContext('2d');
ctx.fillStyle = '#ff6600';
ctx.fillRect(0, 0, 256, 256);
ctx.fillStyle = '#0066ff';
ctx.beginPath();
ctx.arc(128, 128, 72, 0, Math.PI * 2);
ctx.fill();
return canvas.toDataURL('image/png').split(',')[1];
```

参数：`output_path=draw/webview.png`，`timeout_ms=180000`。

## 说明

- 落盘内容是 base64 文本；用户或宿主可用 base64 解码得到 PNG 二进制。
- 普通计算不要用 webview_exec，仅 DOM/canvas/wasm 类任务使用。
