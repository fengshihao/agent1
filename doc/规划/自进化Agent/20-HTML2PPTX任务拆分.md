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
- 几何 fixture 重生成：`HTML2PPTX_UPDATE_FIXTURES=1` 时 `Html2PptxExtractTest` 把真 Chrome 提取 JSON 写回 `src/test/resources/html2pptx/`（Chrome 版本导致渐变/阴影序列化或字体度量漂移时用）。

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
**状态**：完成（commit 9bfe11b，2026-10-17）
**落地形态与关键结论**：
- `html2pptx@0.2.0`：包内 `SKILL.md`，manifest 声明 `"skill": "SKILL.md"`（显式声明是规范，不做「目录里有就算」的约定式）；`agent-home/skills/html2pptx/` 已删。
- `agent_core` 新增 `skill/PackageSkills`：扫 `weizhi-home/packages/<name>@<ver>`（只认 `packages-index.json` 当前版本）→ `kind=skill` 卡（id `skill.<name>`、entry `skill:<name>`、`source=package_skill`、weight 1.3）+ 正文加载（source 标 `package`）。`AgentSkillLoader.loadMerged` 合并顺序变为 bundled → package → project → catalog → local。
- 刷卡调用点三处：桌面 `WeizhiBundledPackages.syncFromClasspath`（engine open 幂等重装后）、registry `WeizhiPackageSync.beforeEval`（`ensureInstalled` 实际装了包才刷）、Android `WeizhiBundledInstall.syncBundled`（engine init + 宿主启动后台线程，与 engine open 共用锁防并发重装）。
- 桌面/Android 启动各加了后台「装内置包 + 刷卡」线程，闭合**首次 run_js 之前 find_caps 查不到包内 skill** 的空窗（对齐 web_lib 卡启动即有的行为）。
- **坑**：weizhi 桥的 `EnsureResult` 是 public 字段（`ensured.installed`），不是 record 访问器，写法要看本地 weizhi 树。seed 重建会清掉 `package_skill` 行，但每次 engine open 都会补回；云端 registry 的 html2pptx 条目发布前，`beforeEval` 对它走 offline-fallback（本地已装即放行），不会降级。
- **待办（发布侧）**：`artifact-cdn` 需 `pack.mjs + upload.sh` 重打 html2pptx@0.2.0（含 SKILL.md）并更新云端 stable.json，否则联网设备 ensure 时 registry 无此包走 fallback（可接受）。

### T1 — DOM 几何提取器（PR2 核心，纯增量）

**目标**：`html2pptx` 包新增 `extractDeckGeometry({htmlPath|html, output})`：webview_exec 里渲染 slide HTML 后遍历 `.slide` 内可见元素，输出 JSON：每元素 `{tag, rect:{x,y,w,h}, font(字号/字重/颜色/对齐), bg/border/radius, textRuns[], imgSrc}`，整页附 `{slideW, slideH}`。落盘 `*-geometry.json`。不改 `renderHtmlDeck` 行为。
**涉及**：`html2pptx.js`（新增导出 + 提取代码生成器）、`Html2PptxScriptTest`（mock webview 断言 JSON 结构）。
**验收**：给定样例 HTML，mock 回执路径断言编排正确；真 Chrome 端到端出 JSON 且首页元素数 > N。
**状态**：完成（2026-10-17，commit 见 git log：feat(office): html2pptx extractDeckGeometry）
**落地形态与关键结论**：
- `extractDeckGeometry` 并入 `html2pptx@0.2.0`（未发布，不再 bump）；默认输出 `<htmlPath 词干>-geometry.json`，内联 html 必须显式传 `output`。返回 `{ok, path, slides, elements}`。
- JSON schema：`{version:1, generator, slides:[{slideW, slideH, bg?, bgImage?, elements:[…]}]}`；元素 `{tag, cls?, rect(相对 slide 左上), font{size,weight,color,align,lineHeight,family}, bg?, bgImage?, border(逐边 w+hex color)?, radius(四角一致给数值，否则 {tl,tr,br,bl}；百分比圆角按 min(w,h)/2 折算 px)?, shadow?, textRuns[](相邻同款 run 合并，含 size/weight/color/italic/underline), imgSrc?, imgOmitted?}`。
- **纯结构容器跳过**（无背景/边框/圆角/阴影/直接文本/图片的元素不下发），出界元素保留（封面 deco 负坐标有意义）；每页上限 800 元素。`img/canvas/svg` 记 data URI（canvas 走 `toDataURL`，svg 走 outerHTML），**超过 1MB 字符省略并标 `imgOmitted:true`**（T4 截图补图的依据）。
- 截图与提取共用 `deckLoadCode(width,height,needLib)`（decode→iframe srcdoc→fonts→.slide→图片等待+broken 收集）；提取**不注入** html-to-image（离线可用，不依赖 CDN）。已用 Node 对拍验证重构后 `captureCode` 生成串与旧版**逐字节一致**，`renderHtmlDeck` 行为不变。
- 提取模式对 broken 图片不硬报错（截图模式才报），几何里 imgSrc 为空的加载失败图自然消失——两模式行为刻意不同。
- 测试：`extractsGeometryThroughMockWebView`（编排/code 特征/落盘/清理，断言提取 code 不含 html-to-image）、`rejectsGeometryWithoutSlides`、`extractsGeometryThroughRealChromium`（真 CDP：封面元素 ≥5、h1 左上+88px+textRuns、card radius==18/bg==#ffffff/border-top 6px、渐变 bgImage，全过）。

### T2 — 提取器 CDP 端到端与样式快照

**目标**：补 `CdpWebViewRuntime` 驱动的端到端：真实渲染样例 deck（渐变/卡片/图表页），断言提取 JSON 的关键元素矩形与颜色合理（如封面 h1 在左上区域、卡片圆角>0），把输出 JSON 存为测试 fixture 防回归。
**涉及**：`Html2PptxScriptTest` 或新 `Html2PptxExtractTest`。
**验收**：断言通过；fixture 落 `src/test/resources`。
**状态**：完成（2026-10-17，commit 见 git log：feat(office): html2pptx 提取器 CDP 端到端与样式快照）
**落地形态与关键结论**：
- 新 `Html2PptxExtractTest`（weizhi-bridge）：三页 deck——渐变封面（出界 deco）、卡片页（圆角 18/上边框 6px 三色/第三卡阴影/em 斜体拆 run）、图表页（**内联 canvas 柱状图 + 内联 svg 环图，零 CDN**，deckLoadCode 不等 canvas，绘图须在 HTML 内联 `<script>` 同步完成）。断言关键矩形/颜色/字号/run 拆分与合并（相邻同款 span 合并单 run）。
- fixture：`weizhi-bridge/src/test/resources/html2pptx/extract-deck-geometry-fixture.json`（真 Chrome 生成，29KB）。两条测试：`fixtureBaselineHolds`（纯 classpath 读 fixture 断言，无 Chrome/JNI 也能跑，已加入 `e2e-self-evolve-smoke.sh` 的 `:weizhi-bridge:test` 列表进 CI）；`extractsGeometryThroughCdpAndMatchesFixture`（真 Chrome live 提取 → 关键事实断言 → 与 fixture 结构化对比：generator/元素序列/颜色/边框/圆角/字号精确，矩形 ±6px 字体度量容差，imgSrc 只比 data URI 前缀——canvas 光栅字节随 Chrome 版本抖动）。
- fixture 更新流：`HTML2PPTX_UPDATE_FIXTURES=1` 重跑即写回 resources（快照测试惯例，Chrome 序列化漂移时用）。
- **几何行为记录**（对 T3 有用）：flex 列里的块级 h1/div 宽度撑满内容区（如 1060px），真实文本宽度要看 textRuns 或行内元素自身 rect；行内 `em`/`span`/svg 内 `text` 会作为**独立元素**下发（有直接文本即 visual），T3 生成器须按 rect 去重/取舍；`.grid` 三列 card 宽 =（1280−2×96−2×36)/3 ≈ 338.67；50% 圆角折算为 min(w,h)/4（deco 520px → radius 130，T1 既定简化）。

### T3 — geometry JSON → 原生 OOXML 生成器（PR3 核心，纯 QuickJS 可单测）

**目标**：`html2pptx` 包新增 `geometryToPptx(geometry, outputPath)`：文本块→文本框（字号/颜色/对齐/字重，EMU 换算 9525/px）、圆角矩形/卡片 div→`roundRect`（fill/line/adj）、`<img>`/`canvas`→图片（引用提取 JSON 里的 data URI 或截图路径）、背景纯色/渐变（渐变先简化为双色线性或整页截图兜底）。复杂效果（阴影/滤镜/混合）标记为「区域截图」占位，由 T4 补图。
**涉及**：`html2pptx.js`（或拆 `html2pptx-native.js`）、JUnit 用 QuickJS 直跑生成器断言 slide XML。
**验收**：给定 fixture geometry → pptx 内 slide XML 含原生 `p:sp` 文本框与 `p:pic`；PowerPoint/WPS 打开不报修复（人工验一次并记录）。
**状态**：完成（2026-10-17，commit 见 git log：feat(office): html2pptx geometryToPptx 原生生成器）**，PowerPoint/WPS 人工打开验收待补**
**落地形态与关键结论**：
- `geometryToPptx({geometryPath | geometry, output})` 并入 `html2pptx@0.2.0`（未发布不再 bump），纯 QuickJS（fs+zip，内置 zip 存储实现），不需要 webview/网络。默认输出 `<词干去 -geometry>.pptx`（deck-geometry.json → deck.pptx）；内联 geometry 必须显式传 `output`（报错兜底）。返回 `{ok, path, slides, shapes, pictures, needsScreenshot, bytes}`。
- 映射规则：EMU=px×9525、字号 px×0.75 磅、≥600 记 bold、start/center/end→l/ctr/r、行距→`spcPct`、字体族拆 latin+ea；圆角块→`roundRect`（adj=圆角/min(w,h)×100000 封顶 50000，四角不等取最大）；linear 渐变→`gradFill`（CSS 角度→OOXML `ang=(θ-90°)×60000`）、radial 退化首色 alpha 近似；四边同宽同色→`a:ln`，否则逐边细色条（卡片彩色上边框）；阴影→`outerShdw` 近似并记 `needsScreenshot`；PNG data URI→`p:pic`，svg→媒体件+`asvg:svgBlip`+1×1 透明 PNG 兜底；`https://`/`imgOmitted`/不支持 mime 不出图，记 `needsScreenshot`（T4 区域截图输入）。
- **去重**：行内元素（em/span/svg text）文本是祖先 textRuns 子串且 rect 被包含时跳过——extractor 会把行内元素同时作为独立元素下发（T2 已记录），不去重会同一文字画两遍。
- 测试 `Html2PptxNativeGeneratorTest`（需 `libweizhijni`，无 Chrome/webview）：①fixture→pptx 全链路（slide XML 断言渐变角度/双色、roundRect adj 折算、EMU/字号/行距、上边框色条、outerShdw、svgBlip+rels+Content_Types、PNG 魔数与兜底件、em 斜体去重、span 合并、needsScreenshot 记 shadow）②默认输出名与内联缺 output 报错 ③远程/超限图片不出 `p:pic` 只记 needsScreenshot。已进 `e2e-self-evolve-smoke.sh`。
- Android assets 同步（`android_agent/.../weizhi-packages/html2pptx@0.2.0/html2pptx.js` 与 agent_core 逐字节一致）；映射规则文档化进 `doc/集成/HTML2PPTX.md`。

### T4 — `renderHtmlDeck` mode:"native" 集成

**目标**：一次 webview_exec 同时完成「几何提取 + 需要截图的区域/图片导出」，再走 T3 生成原生 pptx；`mode` 默认仍 `"screenshot"`，`"native"` 为新选项；失败自动回退截图并提示。
**涉及**：`html2pptx.js`、`Html2PptxScriptTest`（mock + 真 Chrome 各一）。
**验收**：端到端 native 模式产出可编辑 pptx（文本框可在 PowerPoint 选中改字）；回退路径有测试。
**状态**：完成（2026-10-17，commit 见 git log：feat(office): html2pptx native 模式）**，PowerPoint/WPS 人工打开验收待补（与 T3 一并）**
**落地形态与关键结论**：
- `renderHtmlDeck({..., mode})`：`"screenshot"`（默认，原行为不变，主体抽出为 `renderScreenshotDeck`）；非法 mode 在任何 webview 调用前报错；native 任一步失败（提取/区域截图/组包）自动回退整页截图，同一 output 产物始终可用，返回值 `{mode:"native", fallback, fallbackReason}`（成功也带 `fallback:false`）。
- 一次 webview_exec（`nativeCaptureCode` = `deckLoadCode(needLib=true)` + 与 extractCode **共享**的 `geometryHelpers()/geometryRecord()` 提取段 + clip 段）：几何先在页面里算好，再对带 shadow（矩形外扩 offset/blur/spread，px 后缀 token 解析，inset 不特判与 T3 一致）、`imgOmitted`、非 data: imgSrc 的元素逐页整页 `toPng` 一次 → Image → canvas 按区域（clamp 到页内）裁剪 clip PNG 挂 `slide.clips`。
- QuickJS 侧 `applyClips`：clip 矩形完全包含（tol 2px）的元素删除（阴影近似/远程占位不再出、避免重复绘制），clip 转成 `{tag:"img", cls:"html2pptx-clip", imgSrc:data URI}` 追加到页尾（顶层 z 序），再整体走 `geometryToPptx({geometry})` 组包——成功产物 `needsScreenshot:[]` 全消解。
- 取舍：跨出 clip 边界的文字保留原生文本框（与 clip 边缘可能轻微重叠）；native 对 broken 图片不硬报错（对齐提取模式，交给 clip 真实渲染），截图模式照旧报错。回执 clip b64 做魔数校验（非 PNG 即抛→回退）。
- 测试 `Html2PptxScriptTest` 新增四测：`rendersNativeDeckThroughMockWebView`（code 特征 = getComputedStyle+html-to-image+drawImage；clip 消解卡片文本/阴影近似；tmp 清理排除组包目录）、`fallsBackToScreenshotWhenNativeFails`（native 回执坏 → 二次 webview 截图 → `fallback:true` 产物为整页图片版）、`rejectsUnknownRenderMode`、`rendersNativeDeckThroughRealChromium`（真 Chrome：封面纯文本框无 pic、卡片页 clip p:pic 补真、needs 全消解）。`GEOMETRY_DECK_HTML` 的 `.card` 补了 box-shadow（对齐 SAMPLE_DECK，原生/提取断言不受影响）。
- SKILL.md（随包）更新为 v0.2 双模式说明，推荐 `mode:"native"`；Android assets 同步（js + SKILL.md）。

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
- 2026-10-17 T0 完成：html2pptx@0.2.0 随包 SKILL + `PackageSkills`（kind=skill 卡 + 正文加载）+ 三端安装链路刷卡 + 启动兜底安装；测试 `PackageSkillsTest`（含删包重装、双卡命中、local 覆盖）、`WeizhiBundledPackagesSkillTest`。下一个未开始任务：T1。
- 2026-10-17 T1 完成：`extractDeckGeometry`（DOM 几何提取，共用 deck 装载段、零 CDN 依赖）+ mock/错误/真 Chrome 三测全绿；schema 与坑见 T1 小节。下一个未开始任务：T2。
- 2026-10-17 T2 完成：`Html2PptxExtractTest` CDP 端到端（渐变/卡片/图表三页，图表页内联 canvas+svg 零 CDN）+ fixture 落 `src/test/resources/html2pptx/`（基线测试进 CI 冒烟、live 对比带 ±6px 容差、`HTML2PPTX_UPDATE_FIXTURES=1` 重生成）；几何行为记录见 T2 小节。下一个未开始任务：T3。
- 2026-10-17 T3 完成：`geometryToPptx` 纯 QuickJS 原生生成器（映射规则与去重结论见 T3 小节）+ `Html2PptxNativeGeneratorTest` 三测 + Android assets 同步 + 文档/smoke 接线；PowerPoint/WPS 人工打开验收待补。下一个未开始任务：T4。
- 2026-10-17 T4 完成：`renderHtmlDeck mode:"native"` 一次 webview 提取+区域截图、applyClips 补真、失败回退截图（详见 T4 小节）+ `Html2PptxScriptTest` 新四测全绿 + SKILL.md v0.2 双模式 + Android 同步。下一个未开始任务：T5。
