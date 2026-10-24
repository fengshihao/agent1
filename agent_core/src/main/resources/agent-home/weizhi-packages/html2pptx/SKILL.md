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
| 生成 | 选题 → 按页型骨架写 `deck.html`，总结并给预览入口 | 同轮或下一轮擅自 `renderHtmlDeck` / `exportDeckPages` |
| 改稿 | 按用户指点 `edit_file` 改 HTML（预览会热更新） | 用导出 pptx「交差」 |
| 导出 | **仅当**用户消息来自预览导出按钮，或用户明说「导出 / 要 pptx」 | 猜测用户「应该要文件了」就导出 |

Android 预览会默认进演示模式（全屏翻页）；底栏有「导出可编辑版」「导出原样版」两按钮，点了会以用户消息发来完整 `renderHtmlDeck` 指令——照做即可。

## 工作流

1. **生成**：先选题（见下），再按骨架写 `deck.html`。写完即交付，**停**。
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

## 主题变体（6 种：5 个与 weizhi:pptx 同名 + `mono` 仅 deck）

**整份 deck 只用一个主题**。在 `<html data-theme="…">` 标明，拷入对应 `:root` token；**同一 deck 内禁止混主题**。

T8 收口的是「8 种页型 + 导出可验证的 CSS 模式」，不是「永远深紫封面 + 金 kicker + 渐变短条」。若只换 token、封面/结尾仍照抄深底路演配方，观感会千篇一律——**先按主题选封面/结尾配方（下节），再填 8 页内容**；KPI/表格/图表等页型结构共用，颜色走变量。

| 气质（用户可点名） | `data-theme` | 封面配方 | accent 角色 | 渐变字 `background-clip:text` |
|--------------------|--------------|----------|-------------|----------------------------------|
| 深色路演 / 品牌夜场 | `dusk` | **A 深底渐变** | 暖金 kicker、双色 rule | 封面 h1、结尾大字**可用** |
| 夜间 / 科技数据 | `dark` | **A 深底渐变** | 青绿 + 琥珀 kicker | 同上 |
| 汇报 / 复盘 / 浅底杂志感 | `briefing` | **B 浅底大留白** | 海军蓝强调 | **禁止**（大字纯色） |
| 产品 / B2B 冷白 | `harbor` | **B** 或轻 **A**（封面可深蓝条，正文仍浅底） | 海军 + 珊瑚点缀 | **禁止** |
| 故事 / 人文 / 暖色商务 | `editorial` | **D 暖底 + 侧色条** | 赭橙 | 仅结尾大字**可选** |
| 黑白极简 / 演讲极简 | `mono` | **C 纯黑白对比** | 无彩色（灰阶） | **禁止** |

选题：用户指定则用；否则按上表气质匹配。未指明时默认 **`dusk`**（历史默认）。连续多份 deck **主动轮换** `data-theme` 与封面配方，勿份份深底金 kicker。

把对应主题的 `:root` 块整段拷进 `<style>`（勿混用两套色）：

```css
/* dusk — 深紫暖金，路演默认 */
:root {
  --bg: #faf9f6; --bg2: #f3efe6; --surface: #ffffff;
  --text: #1a1423; --muted: #6b6258;
  --accent: #c4a35a; --accent2: #7dd3c0; --on-accent: #1a1423;
  --cover-from: #1a1423; --cover-to: #2a2140;
  --kicker: #e8b86d; --card-top: #c4a35a; --card-top-2: #7dd3c0; --card-top-3: #8b7cf6;
  --table-head: #1a1423; --table-stripe: #f8fafc; --table-foot: #ecfdf5;
  --shadow: 0 12px 32px rgba(18, 32, 51, .08);
  --shadow-deep: 0 12px 32px rgba(18, 32, 51, .2);
  --delta-up: #059669; --delta-down: #dc2626;
}
/* briefing — 浅底海军蓝，商务汇报 */
:root {
  --bg: #f7f5f2; --bg2: #e7eef5; --surface: #ffffff;
  --text: #1a1a1a; --muted: #5c6570;
  --accent: #1f4e79; --accent2: #c4a35a; --on-accent: #ffffff;
  --cover-from: #1f4e79; --cover-to: #163a5c;
  --kicker: #c4a35a; --card-top: #1f4e79; --card-top-2: #c4a35a; --card-top-3: #3d7ab5;
  --table-head: #1f4e79; --table-stripe: #f1f5f9; --table-foot: #e7eef5;
  --shadow: 0 12px 32px rgba(18, 32, 51, .08);
  --shadow-deep: 0 12px 32px rgba(18, 32, 51, .2);
  --delta-up: #059669; --delta-down: #dc2626;
}
/* dark — 全深底青绿强调，科技风 */
:root {
  --bg: #1b2430; --bg2: #243044; --surface: #2c3a4f;
  --text: #f4f7fa; --muted: #a8b3c0;
  --accent: #3ddc97; --accent2: #f2c14e; --on-accent: #102018;
  --cover-from: #121820; --cover-to: #243044;
  --kicker: #f2c14e; --card-top: #3ddc97; --card-top-2: #f2c14e; --card-top-3: #5b8def;
  --table-head: #121820; --table-stripe: #243044; --table-foot: #1f3a2e;
  --shadow: 0 12px 32px rgba(0, 0, 0, .35);
  --shadow-deep: 0 12px 32px rgba(0, 0, 0, .5);
  --delta-up: #3ddc97; --delta-down: #f87171;
}
/* editorial — 暖纸赭石，故事/人文 */
:root {
  --bg: #f6f1e7; --bg2: #e7dcc8; --surface: #fffcf7;
  --text: #1c1917; --muted: #6b6258;
  --accent: #9a3412; --accent2: #3f6212; --on-accent: #fff7ed;
  --cover-from: #292524; --cover-to: #44403c;
  --kicker: #c2410c; --card-top: #9a3412; --card-top-2: #3f6212; --card-top-3: #b45309;
  --table-head: #292524; --table-stripe: #f5efe3; --table-foot: #ecfccb;
  --shadow: 0 12px 32px rgba(28, 25, 23, .08);
  --shadow-deep: 0 12px 32px rgba(28, 25, 23, .2);
  --delta-up: #3f6212; --delta-down: #9a3412;
}
/* harbor — 冷白海军+珊瑚，产品/B2B */
:root {
  --bg: #f3f6fb; --bg2: #e4eaf3; --surface: #ffffff;
  --text: #122033; --muted: #5b6b7c;
  --accent: #0f4c81; --accent2: #c2410c; --on-accent: #f8fbff;
  --cover-from: #0f4c81; --cover-to: #0a355c;
  --kicker: #ea580c; --card-top: #0f4c81; --card-top-2: #c2410c; --card-top-3: #0284c7;
  --table-head: #0f4c81; --table-stripe: #eef2f7; --table-foot: #fff7ed;
  --shadow: 0 12px 32px rgba(18, 32, 51, .08);
  --shadow-deep: 0 12px 32px rgba(18, 32, 51, .2);
  --delta-up: #059669; --delta-down: #dc2626;
}
/* mono — 黑白极简（仅 deck.html，weizhi:pptx 无同名） */
:root {
  --bg: #ffffff; --bg2: #f4f4f5; --surface: #ffffff;
  --text: #09090b; --muted: #71717a;
  --accent: #18181b; --accent2: #52525b; --on-accent: #ffffff;
  --cover-from: #09090b; --cover-to: #18181b;
  --kicker: #71717a; --card-top: #18181b; --card-top-2: #52525b; --card-top-3: #a1a1aa;
  --table-head: #09090b; --table-stripe: #f4f4f5; --table-foot: #e4e4e7;
  --shadow: 0 12px 32px rgba(0, 0, 0, .06);
  --shadow-deep: 0 12px 32px rgba(0, 0, 0, .12);
  --delta-up: #18181b; --delta-down: #71717a;
}
```

通用用法：`body/正文页` 用 `var(--bg)`；卡片 `var(--surface)` + `var(--shadow)`；文字 `var(--text)` / `var(--muted)`；kicker/短条/大引号用 `var(--kicker)`；KPI 卡上边框三色轮换 `--card-top*`；涨跌用 `--delta-up/down`。`dark` / `dusk` 正文页可深底（`--bg`），卡片用 `--surface`。引言 `.quote` 需要深底对比时：A 主题用 `--cover-*` 渐变，B/C/D 可用 `var(--text)` 底 + `var(--on-accent)` 字或反色块，勿强行套 A 深紫底。

### 封面 / 结尾配方（四选一，与 `data-theme` 绑定）

| 代号 | 适用主题 | `.cover` 要点 | `.end` 要点 |
|------|----------|---------------|-------------|
| **A 深底路演** | `dusk`, `dark` | `linear-gradient(135deg, var(--cover-from), var(--cover-to))`；kicker 22px、`var(--kicker)`、字距 8px；h1 88px/800、`var(--on-accent)` 或白；rule 96×6 accent→accent2 渐变圆角；sub 30px；可选出界 radial deco | `radial-gradient` 深底；大字 120px/800，**可**渐变字 accent→accent2；副标 + 圆角描边标签 |
| **B 浅底杂志** | `briefing`, `harbor`（封面） | 底 `var(--bg)` 或浅竖向渐变，**不要**深紫满屏；h1 同 88px、`var(--text)`，标题栈加衬线：`Georgia, "Noto Serif SC", "Songti SC", serif`；kicker 用 `var(--accent)`、字距 4px；rule 改为 64×4 **纯色** `var(--accent)`；sub `var(--muted)` | 浅底 `var(--bg)`；大字纯色 `var(--text)` 或 `var(--accent)`，**不用**渐变字；底部分隔线 1px `var(--bg2)` |
| **C 黑白极简** | `mono` | 整页 `#fff` 或 `#09090b` 二选一；h1 96–120px/800 对比色；kicker 可选 18px `var(--muted)`；**无**彩色 rule，最多 1px `#e4e4e7` 线 | 与封面同黑白场；超大字纯色；**无** radial、**无**渐变字 |
| **D 暖色侧条** | `editorial` | 底 `var(--bg)`；左侧 `12px` 宽 `var(--accent)` 竖条（或左 38% `var(--bg2)` 色块）；h1 `var(--text)`；kicker `var(--kicker)`；sub 暖灰 | 底 `var(--cover-from)`→`var(--cover-to)` 线性深暖色 **或** 浅底 + 居中大字；渐变字**仅**在允许时用于结尾一行 |

生成时：**只实现当前主题那一行配方**；勿在 `briefing` 封面写 A 深底金 kicker，勿在 `mono` 写三色 KPI 以外的彩色装饰。

## 页型骨架（8 种，从这些起步改内容）

约定：每页 `<section class="slide">`，CSS 尺寸 1280×720；字号阶梯 88/44/28/21px（`mono` 封面 h1 可到 96–120）；每页元素 ≤40。颜色一律走主题变量，不要写死旧版硬编码色。

- **封面** `.cover`：结构见上表 **A/B/C/D**；勿默认一律 A。
- **KPI 卡** `.kpis`：h2 标题 + grid 3-4 列 `.card`（`var(--surface)`、radius 18、上边框 6px
  三色轮换、`var(--shadow)`），卡内 num(52px/800) +
  delta 涨跌色 + label(17px `var(--muted)`)。
- **图文两栏** `.split`：左 46% 文字列（h2 + p + 圆点列表 ul li::before 12px 圆点 `var(--accent)`），
  右侧整幅 img（object-fit:cover）。
- **时间线** `.tl`：左竖线（4px accent→accent2 渐变圆角条）+ 每条目（空心圆点 20px 5px 边框 +
  when(15px `var(--muted)`) + h3 + p），条目间距 120px。
- **表格** `.tbl`：thead `var(--table-head)` 白字、tbody 斑马纹（nth-child(even) `var(--table-stripe)`）、
  tfoot `var(--table-foot)` 加粗、数字列右对齐。≤5 列。
- **图表** `.charts`：容器写死像素宽高 + `<script src>` 引 echarts CDN，
  **animation:false**，**`renderer:'svg'`**（`echarts.init(el, null, {renderer:'svg'})`）——
  native 导出时图表转**原生矢量形状**（柱/线/弧/标签可编辑，不再区域截图）；
  canvas 渲染的图表仍走截图补真。系列色优先 `--accent` / `--accent2` / `--card-top-3`。
- **引言** `.quote`：深底 + 大引号(120px `var(--kicker)`) + blockquote(34px/1.75) + 圆形头像
  （border-radius:50% overflow:hidden）+ 署名。
- **结尾** `.end`：结构见上表 **A/B/C/D**；渐变字仅 `dusk`/`dark` 或 `editorial` 结尾且表内允许时用。

## 硬性约束（超了导出质量会崩）

- 禁 `transform` / `animation` / `filter` / `mix-blend-mode` / CSS 多列。
- 阴影只用主题档：`var(--shadow)`（深底页可用 `var(--shadow-deep)`）。
- 图片：workspace 相对路径自动内联；`https://` 远图可用；图表 CDN 只用 echarts。
- 字体用系统栈（PingFang SC / Microsoft YaHei / Helvetica）。
- 渐变字（background-clip:text）：**仅**当前主题上表允许处（封面 h1 / 结尾大字）；`briefing`/`harbor`/`mono` **全程禁止**；其余文字纯色。native 导出对渐变字仍可能近似，浅色主题本来就不该依赖渐变字。

## native 导出的已知边界（用户问再答）

文字/卡片/背景/时间线原生可编辑；**svg 渲染的 echarts 图表原生矢量形状**（0.2.6 起）；
图片、canvas 图表、表格、阴影卡、渐变字是区域截图（视觉一致、不可在 PPT 里改）；
多行段落折行位置可能与浏览器略差。
云端导出（PDF/Word/Keynote 版式化）在规划中。
