# html2pptx.js — HTML 自由版式生成 PPT（v0.1 截图模式）

真源脚本：`agent_core/src/main/resources/agent-home/weizhi-packages/html2pptx@0.2.0/html2pptx.js`。Android 副本：`android_agent/app/src/main/assets/weizhi-packages/`。QuickJS **ES module**，截图在 `webview_exec`（桌面 CDP Chromium / Android 系统 WebView）里完成。Agent 检索见 `office-api-cards.jsonl` 的 `html2pptx.render`；使用说明（skill 正文）**随包分发**：包内 `SKILL.md`（manifest 的 `skill` 字段声明），安装后由 `PackageSkills` 写入 `kind=skill` 能力卡（`source=package_skill`），`find_caps` 查 html2pptx 会同时命中包卡与 skill 卡，正文由 `AgentSkillLoader` 从已安装包加载。

定位：`pptx.js`（[OFFICE_PPTX_API.md](OFFICE_PPTX_API.md)）负责快速结构化 deck；`html2pptx` 负责**版式自由**——整页 slide HTML 用 CSS 排版，渐变、阴影、卡片、大数字、图文混排、内嵌图表（echarts/mermaid，CDN）都可以。v0.1 为截图模式：每页导出为一张全幅图片页（观感优先、不可二次编辑）；原生可编辑转换是既定方向（见 [规划 20 号文档](../规划/自进化Agent/20-Office排版升级与视觉素材管线.md)）。

## 用法

```javascript
import { renderHtmlDeck } from "weizhi:html2pptx";

export default await renderHtmlDeck({
  htmlPath: "deck.html",   // 或 html: "<html>…</html>"
  output: "out/deck.pptx",
  width: 1280,             // 页 CSS 尺寸，默认 1280×720（16:9）
  height: 720,
  scale: 2,                // 截图像素倍数，默认 2（1280×720 → 2560×1440）
  format: "png",           // png | jpg
  quality: 0.92            // 仅 jpg
});
// { ok: true, path, slides, width, height, bytes }
```

## HTML 约定

- 每页一个 `<section class="slide">`，CSS 尺寸 = `width`×`height`；无 `.slide` 元素时报错。
- 字体：系统栈（`PingFang SC` / `Microsoft YaHei` / `Arial`）或 CDN webfont。
- 图片：workspace 相对路径（png/jpg/gif/svg/webp，≤8MB）**自动内联**为 data URI（srcdoc 无 base URL，不内联则加载不到）；`https://`、`data:` 原样保留。加载失败的图会报 `图片加载失败: <src>`。
- 图表：`<script src>` 引 web_lib 卡里的 CDN 地址即可；选项务必 `animation: false`，容器写死像素宽高。

## 流程

1. QuickJS 读取 HTML，内联 workspace 图片，写入（或复用）输入文件。
2. `webview_exec`：iframe srcdoc 渲染 → 注入 html-to-image（jsdelivr）→ 等字体与图片就绪 → 逐页 `toPng/toJpeg`（含 `pixelRatio: scale`）→ 分块回传 base64 JSON。
3. QuickJS 校验魔数（PNG/JPEG）、每页写入 `ppt/media/`，生成图片页 OOXML（每页一个全幅 `p:pic`），`zip` 打包成 pptx。
4. 临时输入/回执文件清理；`tmp/html2pptx-render-*` 组包暂存目录保留（与 pptx.js 一致）。

## 限制

- 需要网络（CDN 加载截图库和图表库；几何提取本身不需要 CDN）；截图库 CDN 自建镜像是遗留项。
- 截图模式产物每页是整页图片，不可在 PowerPoint 里二次编辑。
- `webview_exec` 需桌面 Chromium 或 Android 系统 WebView。
- 最多 40 页；页尺寸 320–4096px；scale 1–3。

## extractDeckGeometry（内部，服务原生转换）

同一 iframe 渲染后遍历 `.slide` 内可见元素，输出几何 JSON（截图/提取共用 deck 装载段，提取**不注入** html-to-image）：

```javascript
import { extractDeckGeometry } from "weizhi:html2pptx";

export default await extractDeckGeometry({
  htmlPath: "deck.html"        // 或 html 内联（此时 output 必填）
  // output: "geo.json"        // 默认 <htmlPath 词干>-geometry.json
});
// { ok: true, path, slides, elements }
```

输出 `{version, generator, slides:[{slideW, slideH, bg?, bgImage?, elements:[…]}]}`；元素 `{tag, cls?, rect{x,y,w,h}（相对 slide 左上）, font{size,weight,color,align,lineHeight,family}, bg?, bgImage?, border?, radius?, shadow?, textRuns[], imgSrc?}`。纯结构容器（无背景/边框/圆角/阴影/直接文本/图片）跳过；`img/canvas/svg` 的 data URI 超过 1MB 时省略并标 `imgOmitted: true`（由后续截图补图）。CDP 端到端快照 fixture 落 `weizhi-bridge/src/test/resources/html2pptx/extract-deck-geometry-fixture.json`（`HTML2PPTX_UPDATE_FIXTURES=1` 重生成）。`renderHtmlDeck` 行为不变。

## geometryToPptx（内部，T3 原生生成器）

几何 JSON → **原生可编辑** pptx，纯 QuickJS（fs + zip），不需要 webview/网络：

```javascript
import { geometryToPptx } from "weizhi:html2pptx";

export default await geometryToPptx({
  geometryPath: "deck-geometry.json"   // 或直接传 geometry 对象（此时 output 必填）
  // output: "out/deck.pptx"           // 默认 <词干去掉 -geometry>.pptx
});
// { ok, path, slides, shapes, pictures, needsScreenshot, bytes }
```

映射规则（20-A T3 落地）：

- 文本块 → `p:sp` 文本框：EMU = px×9525、字号 px×0.75 磅（88px→66pt）、粗细 ≥600 记 bold、对齐 start/center/end→l/ctr/r、行距 lineHeight/size→`spcPct`、字体族拆 latin（首个）+ ea（CJK 族名），bodyPr 零内边距顶对齐。
- 带背景/圆角/边框的块 → `roundRect`（adj = 圆角/min(w,h)×100000，封顶 50000；四角不等取最大近似）；纯色 `solidFill`。
- 渐变：linear → `gradFill` 多 stops（CSS 角度→OOXML `ang=(θ-90°)×60000`）；radial 退化为首色 alpha 纯色近似（如封面 deco）。
- 边框：四边同宽同色 → `a:ln`；否则逐边细色条（按圆角内缩，卡片彩色上边框走这条）。
- 阴影：解析 `rgba() x y blur` → `outerShdw` 近似，同时记入 `needsScreenshot`（T4 区域截图补真）。
- 图片：`img/canvas` 的 PNG data URI → `p:pic`；svg data URI → svg 媒体件 + `asvg:svgBlip` 扩展 + 1×1 透明 PNG 兜底（老 PowerPoint/WPS 显示兜底件）；`https://`、`imgOmitted`、不支持的 mime 不出图片，记 `needsScreenshot`。
- 去重：行内元素（em/span/svg text）文本已是祖先 textRuns 子串且 rect 被包含时跳过，避免同一文字画两遍（extractor 会把行内元素同时作为独立元素下发）。
- 返回的 `needsScreenshot:[{slide,x,y,w,h,reason}]` 是 T4「区域截图补图」的输入。

## 错误

常见失败：`HTML 里没有 class="slide" 的页元素`、`图片加载失败: <src>`、`第 N 页截图失败: …`、`html-to-image 未加载（CDN 不可达？）`、`第 N 页不是有效的 PNG/JPEG`。

## 测试

- `Html2PptxScriptTest`（需本机 `libweizhijni`）：mock `webview_exec` 的编排/组包/错误路径 + 真 Chromium 端到端 + `HTML2PPTX_SAMPLE_DIR` 样例对比渲染（同主题同时产出 html2pptx 版与 pptx.js 版）。
- `Html2PptxExtractTest`（fixture 基线部分无需 Chrome/JNI）：提取器 CDP 端到端 + 几何 fixture 快照对比。
- `Html2PptxNativeGeneratorTest`（需 `libweizhijni`，无 Chrome/webview）：fixture → 原生 pptx，断言 slide XML 的 `p:sp`/`roundRect`/`gradFill`/`svgBlip` 与去重、错误路径、`needsScreenshot` 记录。
- `CdpWebViewHtmlCaptureTest`：CDP 链路 spike（iframe + CDN 库 + 分块回传）。
