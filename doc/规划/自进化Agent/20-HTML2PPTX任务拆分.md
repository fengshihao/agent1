# 20-A — html2pptx 任务拆分与进度（跨 session 协作用）

> 配套主文档：[20-Office排版升级与视觉素材管线.md](20-Office排版升级与视觉素材管线.md)（含 2026-10-17 拍板决策）。
> **新 session 使用方法**：读本文件 + `AGENTS.md` → 找第一个「未开始」的任务 → 只做该任务 → 更新本文件状态（含关键结论/坑）→ 跑门禁。不要跨任务操作。

## 状态图例

`未开始` / `进行中(谁)` / `完成(commit)` / `搁置(原因)`

## 全局约束（每个任务都适用）

- 新增 Skill / 脚本 / 库类能力**一律微智 Package 形态**：优先上云（package registry / artifact-cdn），本地 `find_caps` 必须能检索且 `kind` 对得上（`weizhi_package` / `skill` / `web_lib` / `caps`）。不再往 `agent-home/skills` 加内置 skill（存量 `pptx`、`skill-creator` 例外）。
- 门禁：`./java_agent/gradlew --no-daemon -p java_agent :core:test :cli:test` + `./scripts/ci-local.sh fast`；改 Java 必带 JUnit。
- 本地跑 CDP 测试的环境变量：`AGENT1_WEIZHI_REPO=/Users/fengshihao/Work/weizhi`、`AGENT1_CHROMIUM_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"`。
- 样例对比输出：`HTML2PPTX_SAMPLE_DIR=<dir>` 触发 `Html2PptxScriptTest.rendersSampleDecksForComparison`。

## 已完成（PR1，commit 见 git log）

- `html2pptx@0.1.0` 截图模式包 + 图片自动内联 + 图片预检报错
- `html2pptx` skill（内置形态，T0 会迁包化）、能力卡 `html2pptx.render`（kind 自动 `weizhi_package`）
- 系统提示词路由、Android 资产同步、`doc/集成/HTML2PPTX.md`
- 修复桌面 CDP 两个既有缺陷：`DesktopBootstrapAssets` 资产路径失效（weizhi-web 已迁入 agent1）、分块回执双重投递
- 视觉验收 4/4 通过；对比样例在 `out-samples/`（已 gitignore）

## 任务清单

### T0 — Skill 包化上云规范落地（新规则，先做）

**目标**：把 `agent-home/skills/html2pptx/SKILL.md` 迁为随 `html2pptx` 包分发的 skill：包内携带 SKILL 内容，`WeizhiPackageSync` 安装包时写入 `kind=skill` 的能力卡（对齐 web_lib 卡 `source=web_lib` / 包卡 `source=registry` 的既有模式），`AgentSkillLoader`/`CapabilitySearchTool` 能从已安装包加载 skill 正文；云端 registry 分发后本地 find_caps 命中且 kind 正确。内置 skill 文件从 `AgentSkillLoader.BUNDLED_SKILL_RESOURCES` 移除（保留兜底安装）。
**涉及**：`WeizhiPackageSync.java`、`AgentSkillLoader.java`、`CapabilitySearchTool.java`、`weizhi-packages/html2pptx@0.1.0/`（SKILL 进包，可 bump 0.2.0）、`scripts/build-weizhi-bundled-packages.sh`、相关测试。
**验收**：`find_caps` 查 html2pptx 同时命中 `weizhi_package` 卡与 `skill` 卡（正文可加载）；删包重装链路测试；门禁绿。
**状态**：未开始

### T1 — DOM 几何提取器（PR2 核心，纯增量）

**目标**：`html2pptx` 包新增 `extractDeckGeometry({htmlPath|html, output})`：webview_exec 里渲染 slide HTML 后遍历 `.slide` 内可见元素，输出 JSON：每元素 `{tag, rect:{x,y,w,h}, font(字号/字重/颜色/对齐), bg/border/radius, textRuns[], imgSrc}`，整页附 `{slideW, slideH}`。落盘 `*-geometry.json`。不改 `renderHtmlDeck` 行为。
**涉及**：`html2pptx.js`（新增导出 + 提取代码生成器）、`Html2PptxScriptTest`（mock webview 断言 JSON 结构）。
**验收**：给定样例 HTML，mock 回执路径断言编排正确；真 Chrome 端到端出 JSON 且首页元素数 > N。
**状态**：未开始

### T2 — 提取器 CDP 端到端与样式快照

**目标**：补 `CdpWebViewRuntime` 驱动的端到端：真实渲染样例 deck（渐变/卡片/图表页），断言提取 JSON 的关键元素矩形与颜色合理（如封面 h1 在左上区域、卡片圆角>0），把输出 JSON 存为测试 fixture 防回归。
**涉及**：`Html2PptxScriptTest` 或新 `Html2PptxExtractTest`。
**验收**：断言通过；fixture 落 `src/test/resources`。
**状态**：未开始

### T3 — geometry JSON → 原生 OOXML 生成器（PR3 核心，纯 QuickJS 可单测）

**目标**：`html2pptx` 包新增 `geometryToPptx(geometry, outputPath)`：文本块→文本框（字号/颜色/对齐/字重，EMU 换算 9525/px）、圆角矩形/卡片 div→`roundRect`（fill/line/adj）、`<img>`/`canvas`→图片（引用提取 JSON 里的 data URI 或截图路径）、背景纯色/渐变（渐变先简化为双色线性或整页截图兜底）。复杂效果（阴影/滤镜/混合）标记为「区域截图」占位，由 T4 补图。
**涉及**：`html2pptx.js`（或拆 `html2pptx-native.js`）、JUnit 用 QuickJS 直跑生成器断言 slide XML。
**验收**：给定 fixture geometry → pptx 内 slide XML 含原生 `p:sp` 文本框与 `p:pic`；PowerPoint/WPS 打开不报修复（人工验一次并记录）。
**状态**：未开始

### T4 — `renderHtmlDeck` mode:"native" 集成

**目标**：一次 webview_exec 同时完成「几何提取 + 需要截图的区域/图片导出」，再走 T3 生成原生 pptx；`mode` 默认仍 `"screenshot"`，`"native"` 为新选项；失败自动回退截图并提示。
**涉及**：`html2pptx.js`、`Html2PptxScriptTest`（mock + 真 Chrome 各一）。
**验收**：端到端 native 模式产出可编辑 pptx（文本框可在 PowerPoint 选中改字）；回退路径有测试。
**状态**：未开始

### T5 — 三方对比样例与视觉验收

**目标**：`rendersSampleDecksForComparison` 扩展为三方：pptx.js 版 / html2pptx 截图版 / html2pptx native 版；跑 visual-judge 出结论记录进本文件。
**验收**：三份样例 + 验收记录（每页一行 verdict）。
**状态**：未开始

### T6 — image_search / image_download（配套，可与 T1-T5 并行）

**目标**：Java 侧新工具 `image_search`（Tavily `/search` 传 `include_images:true`，复用 `WebSearchTool` 的客户端与 key，返回 url/尺寸/来源）与 `image_download`（https 白名单 + ≤10MB + 魔数校验，落 workspace）。能力卡进 find_caps（kind 对齐新规则；工具类卡参考 `tool.media_resize` 的 `builtin` 或按 T0 定的规范走包化）。deck HTML 里可直接用下载后的 workspace 图片（html2pptx 自动内联）。
**涉及**：`tool/web/`、`ProductivityToolCapabilities`、能力卡、JUnit（mock HTTP）。
**验收**：mock Tavily 返回图片列表；下载魔数校验测试；门禁绿。
**状态**：未开始

### T7 —（低优）office 能力卡 kind 显式化

**目标**：`office-api-cards.jsonl` 各卡显式补 `kind` 字段（现在靠 `parseWeizhiCard` 按 id 前缀推断）；顺带评估 `search-index.seed.jsonl` 与云卡 kind 枚举统一文档化（`docs/system/`）。
**状态**：未开始

## 进度记录（每任务完成后追加一行）

- 2026-10-17 PR1 完成：截图模式包 + CDP 双修复 + 接线全绿（详见上文「已完成」）。
