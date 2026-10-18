# 21 — HTML 演示模式与导出编排（Android deck 预览强化）

> 背景：2026-10-18 真机会话（华为 MAA-AN00，「调研 26 年中国汽车行业做 PPT」）实测发现：
> AI 跳过 SKILL 规定的预览回路直接导出 pptx 交付；且 deck.html 在手机上的预览体验不达预期
> ——不适配屏幕、无翻页、非全屏，离「不导出 PPT 也能投屏演示」差距很大。
> 本文档记录目标形态与任务拆分，**后续单开 session 设计实现**。

关联：[18-内置HTML预览与点选审查.md](./18-内置HTML预览与点选审查.md)（预览页与点选审查基线，本篇复用其审查模式）、[20-HTML2PPTX任务拆分.md](./20-HTML2PPTX任务拆分.md)（deck.html 资产化工作流）、`HtmlPreviewWebViewLifecycle.kt`、`ChatTranscriptFormatting.kt`。

## 状态图例

`未开始` / `进行中(谁)` / `完成(commit)` / `搁置(原因)`

## 1. 目标形态（用户原话整理）

1. **AI 生成 deck.html 后只做一次告知**，用户打开 HTML 预览看效果，而不是直接收到 pptx。
2. **预览即审查**：预览中的反馈按钮让用户点选不满意的元素告诉 AI 修改（18 号文档已实现点选审查，直接复用）。
3. **预览内导出按钮**：用户满意后自己选择导出方式——
   - 「导出可编辑版」→ `renderHtmlDeck({mode:"native"})`（PowerPoint/WPS 里能改字）
   - 「导出原样版」→ `renderHtmlDeck({})`（整页截图，观感 100%）
4. **HTML 版 PPT 预览要达到演示级**：全屏、屏幕自适应缩放、上一页/下一页、页码指示；
   起码做到「不导出 pptx 一样可以投屏给其他人演示」。

## 2. 现状差距（真机实测）

| 差距 | 现状 |
|------|------|
| 屏幕适配 | deck.html 固定 1280×720 CSS 像素，WebView 按 1:1 渲染，手机上只能看局部 |
| 翻页 | 无上一页/下一页，只能滚动整页文档 |
| 全屏 | 预览页带 app chrome，非沉浸式 |
| 导出编排 | AI 自己决定导出时机与模式；用户没有决策入口 |
| 投屏演示 | 无 |

## 3. 方案要点（待设计时细化）

- **deck 预览容器**：检测 HTML 含 `.slide` 结构（html2pptx 约定）时自动进入「deck 模式」——
  WebView viewport 固定 1280×720，外层 CSS `transform: scale()` 适配屏宽，横屏时铺满。
- **翻页**：deck 模式下水平分页（每屏一页 `.slide`），左右滑动手势 + 底部页码 `3/16`；
  与点选审查模式互斥（审查开着是编辑视角，关掉是演示视角）。
- **演示模式**：全屏（immersive）+ 保持屏幕常亮 + 音量键/点击翻页；投屏走系统 Cast/无线投屏，
  宿主只保证 WebView 全屏渲染正确。
- **导出按钮**：预览页底栏「导出 PPT」→ 半屏选择「可编辑版 / 原样版」→ 生成一条**用户消息**
  回填对话（如「导出可编辑版 deck.html」），AI 调 `renderHtmlDeck` 对应 mode——
  导出决策从 AI 端挪到用户端，AI 不再擅自导出（配合 SKILL.md 工作流修订）。
- **SKILL.md 修订**（随 html2pptx 下个版本）：「生成 deck.html → 提示用户预览（移动端宿主
  有演示预览与导出按钮）→ 用户点导出或明说要求时才 renderHtmlDeck」。

## 4. 任务拆分

| 任务 | 内容 | 状态 |
|------|------|------|
| T1 | deck 检测 + 缩放适配 + 全屏沉浸（预览页改造） | 完成（2026-10-18，落地结论见下） |
| T2 | 翻页手势 + 页码指示 + 屏幕常亮 | 未开始 |
| T3 | 导出按钮 → 用户消息回填 → AI renderHtmlDeck（mode 由按钮决定） | 未开始 |
| T4 | html2pptx SKILL.md 工作流修订（预览前置、导出由用户触发） | 未开始 |
| T5 | 真机验收：投屏演示一轮 + 点选修改 + 两种导出 | 未开始 |

### T1 落地结论（2026-10-18）

- **deck 检测**：`logic/business/DeckHtmlDetector`——正则匹配 class 属性含独立单词
  `slide`（`class="slide cover"` / 单引号均命中；`slideshow`/`myslide` 词边界不命中；
  仅 CSS `.slide{}` 规则不命中）。`scanFile` 只扫文件头 256KB：deck 源图片是 workspace
  相对路径（导出时才内联 data URI），首个 `<section class="slide">` 必在头部，避免主线程
  读几 MB 大文件。显式双分支正则而非 `\1` 反向引用（Java 字符类内不按 backreference 解析）。
- **`HtmlPreviewState.isDeck`**：`HtmlPreviewViewModel.resolve` 在 file 解析成功后同步检测；
  error 分支恒 false。误报（轮播图同名 class）只影响预览形态，无破坏性。
- **缩放适配**：`logic/business/DeckFitScript.INSTALL_JS`（对齐 HtmlInspectScript 常量模式）——
  每页 `.slide` 包 `data-agent1-deck-wrap` wrapper（transform 不改布局，直接 scale 会叠页），
  wrapper 尺寸 = 页尺寸×scale、水平居中、页距 12px；slide `transform: scale()` origin 0 0。
  scale = min(1, 视口宽/页宽)：竖屏每页等宽、横屏铺满；页宽从首页 `offsetWidth` 实测
  （非写死 1280，兼容自定义页尺寸）；resize/orientationchange 100ms 防抖重算；幂等标记
  `__agent1DeckFitInstalled`。`onPageFinished` 时 deck 模式注入（reload/热更新后重装）。
  与审查模式并存（transform 不影响 tap 事件）；不改 deck 源文件，html2pptx 导出走无头
  webview_exec 不经过预览，互不影响。
- **全屏沉浸**：deck 模式顶栏加「全屏演示」按钮（`AgentIcons.Fullscreen/FullscreenExit`，
  自绘 path 不引 material-icons-extended）→ 隐藏 topBar/审查底栏/AI 活动条 +
  `contentWindowInsets` 归零 + `WindowInsetsControllerCompat` 隐藏系统栏
  （BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 滑动可临时呼出）。返回键先退全屏（BackHandler），
  右上角半透明浮动按钮同效；退出/离开组合恢复系统栏（防止带着隐藏栏回对话页）。
  非 deck HTML 行为逐字节不变（回归安全）。
- **测试**：`DeckHtmlDetectorTest`（命中/误报面/截断语义）、`DeckFitScriptTest`
  （幂等标记/wrapper 标记/min(1,·) 封顶/resize 监听的内容特征锚点）、
  `HtmlPreviewViewModelTest` +deck 标记与 error 不标记用例。门禁：
  `:app:testDebugUnitTest` / `check-android-agent-static.sh`（分层+Detekt）/
  `:core:test :cli:test` / `ci-local.sh fast` 全绿。
- **留给 T2 的衔接**：翻页在 wrapper 化后的页序列上做（scrollIntoView/translateY 或
  WebView 逐页截图缓存均可）；「审查开着是编辑视角、关掉是演示视角」的互斥在 T2 实现
  （当前全屏时审查底栏仅隐藏不禁用，退出全屏恢复）。

## 5. 验收用例

1. 打开会话里的 deck.html → 自动 deck 模式，竖屏单页等宽、横屏铺满，可翻页。
2. 审查模式点选第 2 页标题 → 反馈草稿带 selector → AI 改后预览自动刷新（复用 18 号能力）。
3. 点「导出可编辑版」→ 对话出现用户消息 → 产物为 native pptx，PowerPoint 里文字可选中。
4. 投屏后全屏翻页演示 16 页，无白屏、无比例失真。
5. AI 在用户未点导出、未明说「导出」时不产生 pptx（真机会话回归）。
