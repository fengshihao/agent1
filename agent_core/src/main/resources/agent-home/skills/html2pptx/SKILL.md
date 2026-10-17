---
name: html2pptx
description: 用户要 PPT 且需要自由版式、设计感（配色、渐变、卡片、图表混排）时使用。先写整页 slide HTML（每页一个 class="slide"，1280×720），再用 run_js 调 weizhi:html2pptx 截图打包成 pptx。快速结构化内容仍用 pptx skill。
---

# 用 HTML 排版生成 PPT

## 什么时候用它

- 要**自由版式**：设计感封面、非对称布局、渐变、阴影、大数字、图文混排——CSS 能排出来的都能上。
- `pptx` skill 的 11 个版式不够用、或用户明确嫌「模板感重、重复」时。
- 只是快速把要点排成规范 deck 时，继续用 `pptx` skill，更快。

## 用法

先写 `deck.html`（或直接传内联字符串），再一段脚本出 pptx：

```javascript
import { renderHtmlDeck } from "weizhi:html2pptx";

export default await renderHtmlDeck({
  htmlPath: "deck.html",       // 或 html: "<html>…</html>"
  output: "out/deck.pptx",
  scale: 2                     // 截图倍数，默认 2
});
```

## 约定

- **每页一个 `<section class="slide">`**，CSS 尺寸 1280×720（改尺寸就传 `width`/`height`）。
- 字体用系统栈（`PingFang SC`/`Microsoft YaHei`/`Arial`）或 CDN webfont。
- 图片：workspace 相对路径会**自动内联**；`https://`、`data:` 也可以。找不到的图会报「图片加载失败: 路径」。
- 图表：直接 `<script src>` 引 web_lib 卡里的 CDN 地址（echarts/mermaid 等），**选项里 `animation: false`**，图表容器要写死像素宽高。
- 打开产物：`android.intent.start({ action: "view", path })`。

## 排版要点

- 一页一个观点；留白也是版式，不要把内容塞满。
- 页与页之间切换版式节奏：封面全幅 → 卡片网格 → 图表+结论 → 大数字收尾。
- 文字层级用字号/字重/颜色拉开，别全靠加粗。

## 限制（v0.1 截图模式）

- 产物每页是整页图片：**观感优先、不可二次编辑**；原生可编辑转换在计划中。
- 需要网络（CDN 加载截图库与图表库）。
