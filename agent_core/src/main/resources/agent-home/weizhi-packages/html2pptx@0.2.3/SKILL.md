---
name: html2pptx
description: 用户要 PPT 且需要自由版式、设计感（配色、渐变、卡片、图表混排）时使用。工作流：生成 slide HTML（每页一个 class="slide"，1280×720）→ 用户在宿主预览里指点修改（或点选审查）→ 用户点导出按钮/明说导出才导出 pptx（原样版截图观感 100%；可编辑版 mode:"native"）。快速结构化内容仍用 pptx skill。
tags: pptx,ppt,html,幻灯片,演示,设计,版式
---

# 用 HTML 排版生成 PPT

## 工作流：HTML 是源，导出由用户触发

**deck.html 是资产**：放 workspace 根、固定命名、绝不清理；迭代改的是 HTML，
导出产物只是快照。

1. **生成**：按下面页型骨架写 `deck.html`（骨架起步，别自由发挥结构）。
   写完**交付 deck.html 本身即可，不要立刻导出**，让用户先进预览。
2. **预览**（Android 宿主首选）：用户在聊天里打开 deck.html → 宿主自动进
   deck 演示模式：全屏翻页（左右滑/点两侧）、页码、投屏演示；开审查模式可
   **点选元素**把 selector 反馈给 AI。引导用户走这条路，不要先出 PNG。
   桌面/无宿主预览时才用 `exportDeckPages({htmlPath:"deck.html"})` 出每页 PNG；
   其返回值带 `overflow: [{p, over, el, text}]`（内容越出页盒的页与元素）——
   `text:true` 的越界（文字被裁）改 HTML 收掉；`text:false` 多是出界 deco/光晕
   属正常设计。`[]` = 全部无溢出。
3. **指点修改**：用户说「第 2 页左上角大标题太大」「封面换成深蓝」——读
   deck.html 定位（第 N 个 `section.slide` + 语义 class；用户点选反馈会带
   cssSelector），`edit_file` 改，预览会自动刷新。循环到用户满意。
4. **导出（用户触发才做）**：Android 预览页有「导出 PPT」按钮，用户点了会以
   用户消息发来明确指令（可编辑版/原样版，照做即可）；用户**没点按钮、没明说
   「导出」就不要 renderHtmlDeck**。用户明说时按需求选 mode：

```javascript
import { renderHtmlDeck, exportDeckPages } from "weizhi:html2pptx";

export default await renderHtmlDeck({
  htmlPath: "deck.html",
  output: "deck.pptx"          // 默认整页截图版：观感 100%
  // mode: "native",           // 需要接收方在 PowerPoint/WPS 里改字时用：
                               // 文字原生可编辑，图片/阴影/表格/渐变字走区域截图
});
// 截图模式：WebView 内 writeFile 逐页落 PNG，再组 pptx（避免整包 Base64 JSON 撑爆堆）。
// 返回值 overflow 字段同 exportDeckPages；预览/图片页：
// exportDeckPages({ htmlPath: "deck.html" }) → deck-pages/page-N.png
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
