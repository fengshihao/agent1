# SVG 转 PNG / JPG

把工作区里的 `.svg` 导出成二进制 PNG 或 JPG。在 `execute_script` 的 **file** 脚本里调用 catalog 函数 `svgToImage`，不要手写 canvas，也不要把 `.svg` 当作 `execute_script` 的 `file`。

脚本：`shared/catalog/scripts/svg-raster.js`（bootstrap 安装）。模块解析与 `docx.js` 相同：`import './svg-raster.js'` 先找 workspace，再回退 catalog。

## 调用

```javascript
import { svgToImage } from "./svg-raster.js";

export default await svgToImage({
  svgPath: "logo.svg",
  outputPath: "out/logo.png",
  width: 800,
  height: 400,
  format: "png"
});
```

成功时脚本结果为：

```json
{"ok":true,"outputPath":"out/logo.png","width":800,"height":400,"format":"png","bytes":12345}
```

回复里用 `![](out/logo.png)` 引用 **outputPath**。这是二进制图片，不是 `tmp/webview_exec/*.b64`。

## 参数

| 字段 | 说明 |
|------|------|
| `svgPath` | 工作区相对路径，必须以 `.svg` 结尾 |
| `width` | 导出宽度，像素，1–8192 |
| `height` | 导出高度，像素，1–8192 |
| `length` | `height` 的别名 |
| `size` | 正方形边长。缺 `width` 或 `height` 时用它补齐 |
| `format` | `png`（默认）、`jpg` 或 `jpeg` |
| `outputPath` | 可选。省略时把 `svgPath` 的扩展名换成 `.png` 或 `.jpg`。PNG 必须以 `.png` 结尾；JPG 以 `.jpg` 或 `.jpeg` 结尾 |
| `quality` | 可选，仅 JPG。0 到 1，默认 `0.92` |

路径不要加 `workspace/` 前缀，不要写绝对路径，不要包含 `..`。

JPG 会先铺白底再画 SVG（JPEG 没有透明通道）。输出会按给定宽高拉伸铺满；需要保持比例时，在 SVG 里写好 `viewBox`，并自己算好宽高。

## 依赖

函数内部调用 `$tools.webview_exec` 做栅格化。桌面需要本机 Chromium / Chrome；Android 使用系统 WebView。QuickJS 里没有 `document`。
