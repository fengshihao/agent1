---
name: html2pptx
description: 用户要 PPT 且需要自由版式、设计感（配色、渐变、卡片、图表混排）时使用。先写整页 slide HTML（每页一个 class="slide"，1280×720），再用 run_js 调 weizhi:html2pptx 生成 pptx——mode:"native" 产出原生可编辑文本框，默认整页截图。快速结构化内容仍用 pptx skill。
tags: pptx,ppt,html,幻灯片,演示,设计,版式
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
  mode: "native",              // native=原生可编辑（推荐）；缺省 screenshot=整页截图
  scale: 2                     // 截图/区域截图倍数，默认 2
});
```

`mode:"native"` 一次渲染同时做几何提取 + 区域截图，产物里文本是
**可在 PowerPoint/WPS 选中改字**的原生文本框，卡片是原生圆角矩形；图片与阴影
等复杂效果一律以区域截图补真（视觉一致，图片本身不可二次替换）；失败自动回退
整页截图（返回值 `fallback:true` 带原因），产物始终可用。

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

## 限制（v0.2）

- `screenshot` 模式产物每页是整页图片（观感优先、不可二次编辑）；要可编辑就用 `mode:"native"`。
- `native` 模式的近似取舍：渐变 radial 退化为纯色、跨出截图区域的文字可能与区域截图边缘轻微重叠；**渐变文字（`background-clip:text`）会退化为黑字**，深底上不可读——大字收尾页请用纯色或接受截图模式。
- 需要网络（CDN 加载截图库与图表库）。
