---
name: html2pptx
description: 用户要有设计感的 PPT 时用。交付物是 deck.html（每页 class="slide"，1280×720）+ 可打开的预览链接；禁止在用户未点导出/未明说导出时调用 renderHtmlDeck。快速结构化内容仍用 pptx skill。
tags: pptx,ppt,html,幻灯片,演示,设计,版式
---

# 用 HTML 排版生成 PPT

## 硬契约：做 PPT = 写 deck.html，不是立刻出 pptx

**产品定义**：「做一份 PPT」= workspace 根写入 `deck.html`（固定名、不清理）+ 回复里给出可点开的链接/路径。  
**HTML 是唯一源资产**；`.pptx` 只是用户主动导出时的快照。

| 阶段 | 你做什么 | 禁止 |
|------|----------|------|
| 生成 | 按页型骨架写 `deck.html`，总结并给预览入口 | 同轮或下一轮擅自 `renderHtmlDeck` / `exportDeckPages` |
| 改稿 | 按用户指点 `edit_file` 改 HTML（预览会热更新） | 用导出 pptx「交差」 |
| 导出 | **仅当**用户消息来自预览导出按钮，或用户明说「导出 / 要 pptx」 | 猜测用户「应该要文件了」就导出 |

Android 预览会默认进演示模式（全屏翻页）；底栏有「导出可编辑版」「导出原样版」两按钮，点了会以用户消息发来完整 `renderHtmlDeck` 指令——照做即可。

## 工作流

1. **生成**：骨架起步写 `deck.html`（见下）。写完即交付，**停**。
2. **预览**：引导用户打开 deck.html。桌面/无宿主预览时才用  
   `exportDeckPages({ htmlPath: "deck.html" })` 出 PNG；返回值 `overflow` 用于改越界文字（`text:true` 必改；`text:false` 多为出界 deco）。
3. **指点修改**：用户说「第 2 页标题太大」或点选反馈带 cssSelector → 改 HTML → 预览刷新。
4. **导出（用户触发）**：

```javascript
import { renderHtmlDeck } from "weizhi:html2pptx";

export default await renderHtmlDeck({
  htmlPath: "deck.html",
  output: "deck.pptx"          // 原样版：整页截图，观感 100%
  // mode: "native",           // 可编辑版：文字在 PowerPoint/WPS 可改
});
```

打开产物：`android.intent.start({ action: "view", path })`。

## 页型骨架（8 种，从这些起步改内容）

约定：每页 `<section class="slide">`，CSS 尺寸 1280×720；正文页背景浅色
（#faf9f6/#fff），封面结尾深色渐变；字号阶梯 88/44/28/21px；每页元素 ≤40。

- **封面** `.cover`：linear-gradient 深底 + kicker(22px 金色字距 8px) + h1(88px/800)
  + rule 短条(96×6 渐变圆角) + sub(30px)。可选 deco：大圆 radial-gradient 出界。
- **KPI 卡** `.kpis`：h2 标题 + grid 3-4 列 `.card`（#fff、radius 18、上边框 6px
  三色轮换、box-shadow `0 12px 32px rgba(18,32,51,.08)`），卡内 num(52px/800) +
  delta 涨跌色 + label(17px 灰)。
- **图文两栏** `.split`：左 46% 文字列（h2 + p + 圆点列表 ul li::before 12px 圆点），
  右侧整幅 img（object-fit:cover）。
- **时间线** `.tl`：左竖线（4px 渐变圆角条）+ 每条目（空心圆点 20px 5px 边框 +
  when(15px 灰) + h3 + p），条目间距 120px。
- **表格** `.tbl`：thead 深底白字、tbody 斑马纹（nth-child(even) #f8fafc）、
  tfoot 浅绿底加粗、数字列右对齐。≤5 列。
- **图表** `.charts`：容器写死像素宽高 + `<script src>` 引 echarts CDN，
  **animation:false**，canvas 渲染。
- **引言** `.quote`：深底 + 大引号(120px 金) + blockquote(34px/1.75) + 圆形头像
  （border-radius:50% overflow:hidden）+ 署名。
- **结尾** `.end`：radial 深底 + 大字(120px/800，渐变字用 background-clip:text)
  + 一句副标 + 圆角描边标签条。

## 硬性约束（超了导出质量会崩）

- 禁 `transform` / `animation` / `filter` / `mix-blend-mode` / CSS 多列。
- 阴影只用一档：`0 12px 32px rgba(18,32,51,.08)`（深底页可到 .2）。
- 图片：workspace 相对路径自动内联；`https://` 远图可用；图表 CDN 只用 echarts。
- 字体用系统栈（PingFang SC / Microsoft YaHei / Helvetica）。
- 渐变字（background-clip:text）只在封面/结尾大字用；其余文字纯色。

## native 导出的已知边界（用户问再答）

文字/卡片/背景/时间线原生可编辑；图片、echarts 图表、表格、阴影卡、渐变字是
区域截图（视觉一致、不可在 PPT 里改）；多行段落折行位置可能与浏览器略差。
云端导出（PDF/Word/Keynote 版式化）在规划中。
