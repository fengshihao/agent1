# 20 — Office 排版升级与视觉素材管线

> 背景：用户反馈「生成 Word / PPT 格式不好看；PPT 只有几个版式，生成两次就觉得重复；什么时候该配图、配什么图（搜图 / SVG / 图表 / 地图 / 流程图 / 四象限）没有章法」。
> 承接 [16-渲染-文档与Android附件计划.md](16-渲染-文档与Android附件计划.md)（双车道、vendor 缓存）与 [19-内置能力云升级.md](19-内置能力云升级.md)（卡片/包上云）。

## 0. 决策记录（2026-10-17 拍板）

| 决策点 | 结论 |
|--------|------|
| html2pptx 车道 | **做，且提前到第一期**。pptx.js 版式扩容（原 B1）不再作为主线，只在需要时顺手补 |
| 可编辑性 | **终态必须是原生可编辑转换**（DOM 几何 → 原生文本框/形状/图片）。整页截图模式只作为**第一期里程碑**：先看 HTML 排版出来的效果，与现有 pptx.js 版式做对比，不是终态 |
| 搜图版权 | 定位**个人使用场景**，Tavily 搜图 + 产物标注来源即可，不接 Unsplash/Pexels |
| 网络与 CDN | 服务本身依赖网络大模型，**不考虑离线降级**；远期把 CDN 库/字体镜像到自有服务器（复用 weizhi package registry 同款机制），不着急，见 §7 遗留项 |
| 文生图 / 多模态生成 | **本期不做**，记为远期：文生图、ASR、图生视频、文生视频等各类生成式后端，见 §7 遗留项；接口分层见 [23-多模态模型能力接口设计.md](./23-多模态模型能力接口设计.md) |
| 能力交付形态（2026-10-17 补充） | 新增 Skill / 脚本 / 库类能力**一律微智 Package 形态**：优先上云分发，本地 `find_caps` 必须能检索且 `kind` 对得上；执行拆分见 [20-HTML2PPTX任务拆分.md](20-HTML2PPTX任务拆分.md) |

---

## 1. 现状与根因（代码调研结论）

Office 生成全部由 `agent-home/weizhi-packages/` 下手写 JS OOXML 渲染器承担（`docx@1.0.0`、`pptx@1.1.0` 等），无模板文件、无 pandoc/LibreOffice。「不好看、重复」的根因：

| # | 根因 | 位置 |
|---|------|------|
| 1 | **版式总量小且每页 chrome 完全相同**：11 个版式 × 5 主题就是全部视觉空间；每页同样的左色条 + 同位置标题下划线 + 页脚 | `pptx.js` `LAYOUTS` 表（:24-36）、`addChrome/addTitleBlock/addFooter`（:557-611） |
| 2 | **没有真正的母版/版式体系**：`slideLayout1.xml` 是空壳，版式 = 渲染函数逐页画形状，换主题只换 9 个色槽，版面结构零变化 | `pptx.js` `layoutXml`（:1273-1284） |
| 3 | **形状同质化**：cards 同宽圆角 + 同色条、stat 同顶条、steps 固定 chevron；`MAX_ITEMS=8` 压扁信息密度差异 | `renderCards/renderStat/renderSteps`（:745-897） |
| 4 | **文字度量是估的**：ASCII 0.55 / CJK 1.0 估宽，字号只有两档切换，中英混排「字小框空」或溢出 | `charUnits/wrapLines`（:345-376） |
| 5 | **无叙事模板**：SKILL.md 只给约束（要点≤6），没有推荐页序，模型自由发挥常产出 title + 连续 bullets | `skills/pptx/SKILL.md` |
| 6 | **图片只收本地 png/jpeg/gif**：无搜图、无图标库、无生图；SVG 必须先经 svg-raster 转 PNG | `pptx.js` `imageExt`（:240-250） |
| 7 | **图表与 Office 断层**：web_lib 24 张卡（echarts/mermaid/leaflet/…）只产交互 HTML，「卡片 → 图片 → 插入文档」没有封装；doc16 Phase D 的 `render-mermaid.js` 仍是计划 | `artifact-cdn/web-libs/cards.jsonl`、doc16 |
| 8 | **Word 是排版基座缺口**：无封面/目录/页眉页脚；列表是「• 」字符前缀伪造（非原生编号）；表格无表头底色/列宽/斑马纹；块样式仅 5 个字段 | `docx.js`（:517-526、:549-572、:666-692） |

**已具备的地基**（方案都建立在这些之上）：`webview_exec` 双端运行时（Android WebView / 桌面 CDP Chromium）+ `WebViewExecHost.writeFile` 二进制落盘；`svg-raster` 包证明了「webview 画布 → base64 → 魔数校验 → 写 workspace」模式可行；docx/pptx 均有 `addImage`/`image` 版式入口；Tavily 已用于 `web_search`；web_lib 卡与 weizhi 包均已有云端注册表热更新机制。

---

## 2. 业界调研结论

| 参考 | 做法 | 对我们的启示 |
|------|------|--------------|
| **Anthropic 官方 pptx skill（html2pptx 路线）** | 每页先用 HTML/CSS（1280×720）排版 → 无头浏览器渲染 → 读取每个元素的 computed style + 几何 → 转成**原生可编辑** PPTX 元素（文本框/圆角矩形/图片），配缩略图视觉检查循环 | HTML/CSS 的排版表达力 ≫ 手写 OOXML 布局函数；「先 HTML 后转换」是版式自由度天花板最高的路线，且我们能完全复用 webview_exec 车道 |
| **ECharts 5.3+ SSR** | `echarts.init(null,null,{renderer:'svg',ssr:true,width,height}).renderToSVGString()` 零依赖直出 SVG | 图表→SVG 不需要截图，WebView 车道更没问题；接 svg-raster 即得高清 PNG |
| **Mermaid 11** | `mermaid.render()` 直接返回 SVG；11.x 原生支持 quadrantChart（四象限）、mindmap、timeline | 流程图/时序图/四象限/时间线一个库全覆盖，卡片里已有 mermaid@11.4.0 |
| **Tavily 图片搜索** | `/search` 传 `include_images:true` 即返回图片 URL 数组（新 API 还带每条结果的来源页） | 仓库已有 Tavily key 与 Java 客户端，加 `image_search` 成本极低 |
| **Gamma 类产品** | 卡片式自由布局本质是 HTML/CSS grid 约束排版，导出时转原生或截图 | 印证「版式=CSS」方向；截图模式可作保真 fallback |

---

## 3. 方案：三条主线 + 一条闭环

### 主线 A — 视觉素材管线（图表/图片进文档）★ 性价比最高，建议最先做

**A1. 新 weizhi 包 `chart@1.x`：图表 → 图片**

```
renderChart({ lib: "echarts" | "mermaid", spec | text, width, height, scale: 2, format: "png" })
```

- 编排层在 QuickJS（照抄 `svg-raster` 模式）：把 spec 传给 `webview_exec`，页面内动态加载 CDN echarts/mermaid → `renderToSVGString()` / `mermaid.render()` 得 SVG（或 canvas.toDataURL）→ base64 回传 → 魔数校验 → 写 workspace PNG。
- echarts 统计图/仪表盘、mermaid 流程/时序/四象限（quadrantChart）/时间线/思维导图一次覆盖；`scale:2` 出 2x 图避免插入后发虚。
- pptx 新增 `layout:"chart"`（图表 + 结论文字并排）与 docx 的 `addImage` 直通；两张能力卡进 find_caps。
- **需先验证**：webview_exec 页面内动态 `<script src=CDN>` 加载（Android WebView 与桌面 CDP 各测一次）；无网降级 → 走 doc16 Phase C 的 `shared/vendor/web` 缓存（建议把 vendor 缓存从 Phase C 提前，作为本包依赖）。
- 地图进阶：echarts geo 需注册 geojson（可缓存在 vendor）；leaflet 瓦片图只能走截图模式，放二期以后。

**A2. `image_search` + `image_download` 工具（Java 侧，同 web_search 模式）**

- `image_search({query, count})`：Tavily `include_images:true`，返回 URL + 尺寸/来源（Java 实现，复用现有 Tavily 客户端）。
- `image_download({url, path})`：下载二进制到 workspace（https 白名单 + 大小上限 + 魔数校验），接现有 `media_resize` 再插入。
- 解决「什么时候加图」的**素材来源**；版权风险见决策点 2。

**A3. 装饰性 SVG 资产**

- 模型直接写 SVG（几何插画/icon 风格图形/封面背景 pattern）→ `svg-raster` 转 PNG，已有能力，只缺**提示词引导**。
- 可选：内置 `icons@1.x` 包（lucide 等按需子集的 path 数据），`icon({name, color, size})` 出 PNG。

**A4. 「什么时候加图」写进规则层**

- pptx/docx SKILL.md + ProductivityCoach 增加排版节奏规则：每 3-5 页一张视觉；数据页优先图表而非表格；章节页可全幅主题背景；封面默认配图（有素材时）；概念解释优先 mermaid 流程/四象限。
- find_caps 的路由卡 `agent.render_pipeline` 升级为「图表→图片→Office」完整链路描述。

### 主线 B — PPT 版式双轨：pptx.js 扩容（短期）+ html2pptx 车道（中期）

**B1. 短期：pptx.js 继续扩容（1-2 个 PR，不动架构）**

- **版式 11 → 20+**：timeline（横向时间线）、quote（大字引言）、bigNumber（单一巨型数字）、kpiDashboard（多指标+迷你趋势）、comparison（左右对照 VS）、pyramid/funnel、agenda、ending（结尾页）、imageGrid（多图）、iconCards（带图标卡）等。
- **版式参数化**：cards 列数 2/3/4、stat 带涨跌趋势色、bullets 超过 4 条自动双栏——同版式不再长一个样。
- **chrome 变体**：按版式类型分装饰组（内容页/章节页/数据页/结尾页不同 chrome），消除「每页同款色条」的重复感。
- **主题包化上云**：THEMES 从 pptx.js 拆出为独立 JSON 包，走 weizhi package registry 热更新，不发版持续上新主题（复用 19 号文档机制）。
- **叙事模板**：deck 级 `narrative: "report" | "pitch" | "training"`，渲染器按模板给出版式序列建议（title→agenda→section→数据→洞察→行动→ending），模型填内容；SKILL.md 附页序示例。
- **真实文字度量**：排版前把文本丢给 webview_exec `measureText` 断行（可选步骤，量测结果缓存），替换 ASCII 0.55 估宽。

**B2. 中期：`html2pptx@1.x` 包（对齐 Anthropic 路线，版式天花板）**

- 模型写 `slides.html`：每页固定 1280×720，CSS grid/flex 自由排版，可混用任意 web_lib 卡与主题 CSS → `webview_exec` 渲染 → 遍历 DOM + `getBoundingClientRect` + `getComputedStyle` → 映射为**原生可编辑** pptx 元素（文本框/圆角矩形/图片，复用 pptx.js 的 zip/OOXML 底座）。
- **fallback/海报模式**：整页截图为全幅图片页（保真但不可编辑），先上截图模式快速见效，原生转换逐步演进。
- 版式 = CSS，无限扩展；同一 HTML 也能截 PNG 进 Word——主线 A/B/C 共用。
- 工作量最大，独立包孵化、先 dogfood，不阻塞 B1。

### 主线 C — Word 排版基座补齐

- `docx.js` 补：封面页块（title/subtitle/date/配图）、TOC 域（打开时 F9 更新）、页眉页脚 + 页码域、**原生编号列表**（numbering.xml，替换字符前缀伪造）、表格样式（表头底色加粗/斑马纹/列宽/对齐）、块样式扩字段（alignment、spacing、color）、主题色 token 与 pptx 共用。
- 新增 docx 的 SKILL.md（现在没有）+ 报告叙事模板（封面→摘要→目录→章节→图表→附录），配图节奏规则同 A4。

### 闭环 D — 渲染回看（质量兜底，Anthropic 的 thumbnail review 同款思路）

- 桌面/CI：LibreOffice headless 把样例 deck/docx 转 PDF→PNG，做「非空白/文字溢出启发式」冒烟（Cloud Agent Ubuntu 可跑）。
- 运行时：生成后自检回执（shapes 数、溢出警告）让模型重排，模式对齐 HtmlHarnessSmoke。

---

## 4. 分期建议（按 2026-10-17 拍板重排：html2pptx 提前）

| 期 | PR | 内容 | 里程碑含义 |
|----|----|------|-----------|
| 1（html2pptx 起步） | PR1 | Spike + `html2pptx@0.1` **截图模式**：slide HTML → webview_exec 渲染 → 逐页 PNG → 全幅图片页 pptx | 拿 HTML 排版效果与现有 pptx.js 版式**做对比**，验证视觉上限；同时沉淀 slide HTML 约定与默认 deck 模板 |
| | PR2 | `html2pptx@0.2` **DOM 几何提取器**：遍历 slide DOM 输出 {rect, computed style, text runs, 图片} JSON（截图模式并行保留，用作 fallback） | 原生转换的数据基座；提取器本身可独立测试（给定 HTML → 断言 JSON） |
| | PR3 | `html2pptx@1.0` **原生转换**：提取 JSON → 原生文本框/圆角矩形/图片的 OOXML（复用 pptx.js zip 底座）；复杂效果（渐变/阴影区域）局部截图兜底 | 终态：可编辑 + 高保真 |
| 1（配套同期） | PR4 | `image_search` / `image_download` 工具 + 能力卡（Tavily `include_images`，个人场景） | HTML deck 的 `<img>` 与 Word `addImage` 共用素材来源 |
| | PR5 | SKILL.md（新增 `html2pptx`，改写 `pptx`）/ coach / 路由卡更新 | 教模型写 slide HTML 的版式套路与配图节奏 |
| 2（按需） | PR6 | docx.js 封面/目录/页眉页脚/原生列表/表格样式 + docx SKILL.md | Word 基座补齐 |
| | PR7 | chart 包（echarts/mermaid → PNG，服务 docx；html2pptx 车道 HTML 内直接嵌 CDN 图表即可） | Word 的图表入口 |
| | PR8 | 主题包化上云 | 19 号文档机制 |
| 3（闭环） | PR9 | LibreOffice 视觉冒烟（CI Ubuntu）+ 溢出自检回执 | 质量兜底 |

每 PR 遵循仓库惯例：Java 侧改动带 JUnit；JS 包带魔数/结构校验测；`./scripts/ci-local.sh fast` 门禁。

**落地进度**：PR1 已完成——`html2pptx@0.1.0` 截图模式包（含 workspace 图片自动内联）、`html2pptx` skill 与能力卡、系统提示词路由、`Html2PptxScriptTest`（mock 编排 + 真 Chromium 端到端 + `HTML2PPTX_SAMPLE_DIR` 样例对比）、`doc/集成/HTML2PPTX.md`；顺手修复桌面 CDP 两个既有缺陷（weizhi-web 资产迁移后 `DesktopBootstrapAssets` 路径失效、分块回执双重投递 seq 重复）。PR2（DOM 几何提取器）未开始。

---

## 5. 已拍板决策

见 §0 决策记录。原「决策点待讨论」全部关闭。

---

## 6. 风险

- webview_exec 内动态加载 CDN 在双端的行为差异（Android WebView 版本、CSP）→ PR1 前先做 spike 验证；已拍板不考虑离线，但 Android WebView 旧版本兼容仍需实测。
- 截图依赖第三方库（snapdom / html-to-image / html2canvas）的 CSS 保真度与外链资源 CORS（jsdelivr 带 `access-control-allow-origin: *`，需实测字体/图片）。
- html2pptx 的 DOM→OOXML 映射边角（渐变、阴影、字体差异）注定长尾，需明确「支持的 CSS 子集」契约；复杂区域局部截图兜底。
- 一次 webview_exec 回传多页 PNG base64 可能超 64KB 分块传输上限（Android WebView 大字符串往返）→ 分批回传或 JPEG 压缩。
- Tavily 图片质量参差（小图、水印）→ image_search 返回尺寸元数据 + media_resize 兜底。
- 主题/版式上云后需保证 find_caps 卡与包版本的向后兼容（复用包注册表既有校验）。

---

## 7. 遗留项（记下来，之后找机会做）

| 项 | 说明 |
|----|------|
| CDN 自建镜像 | 把 echarts/mermaid/snapdom/字体等 CDN 资源镜像到自有服务器，复用 weizhi package registry 同款分发机制（`artifact-cdn/` 上传链路）；不着急，当前直接用 jsdelivr |
| 文生图 / 多模态生成后端 | 文生图、ASR、图生视频、文生视频等各类生成式后端接入；与 image_search 互补（照片类素材搜图，插画/图标类 SVG 自绘，中间态用文生图补）。接口分层见 [23-多模态模型能力接口设计.md](./23-多模态模型能力接口设计.md) |
| pptx.js 版式扩容 | 不再是主线；仅当 html2pptx 之外仍需快速轻量 deck 时按需补版式 |
| 地图类 | leaflet 瓦片图 / echarts geo 只能走截图或局部截图，放原生转换之后 |
