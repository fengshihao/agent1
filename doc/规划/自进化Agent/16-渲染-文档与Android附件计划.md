# 16 — 渲染、文档产出与 Android 聊天附件（分步计划）

> 承接用户方向：去掉 Android 内置 canvas-draw skill；**SVG → WebView 栅格化 PNG**；Markdown→DOC；Mermaid / Three.js 等 **CDN 缓存 + 双运行时**；聊天里 **工作区文件可点打开 + 系统分享**。  
> **每条交付必须带自动化测试**（JVM 单测 / Android instrumented / 仓库脚本，不依赖真实 LLM）。

> **Weizhi Word（docx.js）**：[weizhi#8](https://github.com/fengshihao/weizhi/issues/8) → 真源 [AGENT1_DOCX_INTEGRATION.md](https://github.com/fengshihao/weizhi/blob/master/docs/AGENT1_DOCX_INTEGRATION.md)；Agent1 入口 [`doc/集成/WEIZHI_DOCX.md`](../../集成/WEIZHI_DOCX.md)。**不再**规划 Java `host.office.*`。

关联：`android_agent/.../WorkspaceMarkdown.kt`、`ChatTranscriptFormatting.kt`、`WeizhiAgentTools.kt`、`dev/catalog-sample/`、REQ-040～052（脚本）、REQ-060（catalog/js_lib）。

---

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| **双车道** | **QuickJS**（`execute_script` file/code）：无 DOM、偏计算/文本/调用 `$tools`。**WebView**（`webview_exec`）：DOM、Canvas、SVG、Mermaid、Three.js、Markdown 排版渲染。 |
| **库不落 APK 胖包** | 常用库走 **CDN 下载 → agentRoot 本地缓存**，WebView HTML 用 `file://` 或 inline importmap；QuickJS 只加载 **纯 JS、无 DOM** 的 bundle（或预置在 `shared/catalog/libs/qjs`）。 |
| **Skill 不硬编码 canvas 教程** | 删除 `assets/agent_skills/webview-canvas-draw`；改为 **仓库级 skill + docs/system** 描述「SVG 模板 + 栅格化 API」。 |
| **Android 分层** | UI 只渲染与发 Intent；路径解析、`FileProvider`、MIME 映射放在 `logic.business`（可 `platform` 子包）；禁止在 `ui.view` 里直接 IO。 |
| **与 catalog 对齐** | 可复用 `kind: js_lib` / 新 `kind: web_asset`（若需要）+ `catalog_install`；E2E 继续用 `dev/catalog-sample` mock HTTP。 |

---

## 2. 目标架构（CDN 与缓存）

```text
agentRoot/
  shared/vendor/web/          # WebView 用：按 libId/version 存 .js/.css（CDN 拉取，sha256 校验）
  shared/vendor/qjs/          # QuickJS 用：纯 JS 模块（无 document/window）
  shared/catalog/scripts/     # 官方/用户安装的 .js 流程（md→doc、svg→png 编排）
  sessions/<id>/workspace/    # 用户产物：.svg .png .docx .md …
```

**AI 使用流程（规划）**

1. 需要 Mermaid / Three / 某库 → 调用 **`vendor_fetch`**（新工具，或扩展 `catalog_install` + manifest 条目）→ 写入 `shared/vendor/...`。
2. **WebView**：`webview_exec` 内嵌 HTML 模板，`script src` 指向缓存路径（或 `loadHtml` + baseUrl）。
3. **QuickJS**：`execute_script` `file=transform/md-to-doc.js`，内部 `require` 指向 `shared/vendor/qjs/...`（需 Weizhi 模块加载约定，与现有 `js_lib` 搜索路径统一，见 7.2）。

**CDN 策略**

- 允许列表（manifest 或 `docs/system/trusted-cdn.md`）：如 `cdn.jsdelivr.net`、`unpkg.com` 等；**禁止**任意 URL（安全）。
- 每条缓存记录：`url`、`digest`、`license`（可选）、`runtime: web|qjs`。

**SVG → PNG（核心能力）**

- 用户/AI 先在 workspace 写 **`.svg`**（可指定 `width`/`height` viewBox）。
- `webview_exec` 使用 **固定模板**（非让模型手写 canvas 几何）：
  - 加载 workspace SVG → `<img>` 或 inline SVG → canvas.drawImage → `toDataURL('image/png')` → `output_path`。
- 参数：`svg_path`、`output_path`、`width`、`height`（默认读 SVG viewBox）。

---

## 3. 分阶段交付（按顺序做）

### Phase A — 清理与基线（1 PR）

| 做 | 验 |
|----|-----|
| 删除 `android_agent/.../assets/agent_skills/webview-canvas-draw/` | `./check-android-agent-static.sh` |
| 更新 `webview-draw-e2e.md`、`QUICKSTART.md`（不再引用该 skill） | 文档 grep 无 `webview-canvas-draw` |
| 保留/改写 instrumented 测为 **canvas 低层 smoke** 或移到 **Phase B SVG 测** | `:app:connectedDebugAndroidTest` 或 CI 现有 android job |

**REQ-101（建议编号）**：Android 不再内置 canvas-draw skill。

---

### Phase B — SVG → PNG WebView 管线（CLI + Android  parity）

| 做 | 验 |
|----|-----|
| **Weizhi / agent-tools**：`webview_exec` 支持可选 **`template=svg_to_png`** + 参数（或独立工具 `rasterize_svg`，二选一，优先模板减 prompt 体积） | JVM/Weizhi 单测：给定最小 SVG → PNG  magic bytes |
| 仓库 `dev/catalog-sample/scripts/svg-to-png.js` **或** 内建模板仅 C++ / Java 侧 | `./scripts/e2e-self-evolve-smoke` 可不加 LLM |
| 新 skill：`.claude/skills/raster-svg/`（**project**，非 Android asset）描述流程 | Mock：Scripted + webview 假引擎 或 desktop CDP 测 |
| Android：`run-webview-draw-test.sh` 改为 **SVG 输入 + 指定 512×512** | adb instrumented PASS |

**REQ-102**：workspace SVG + 指定尺寸 → PNG 文件存在且可解码。

---

### Phase C — JS _vendor 缓存与运行时分类

| 做 | 验 |
|----|-----|
| `agent.manifest.json` 增加 `vendor.trustedCdnHosts` + 默认空 | Bootstrap 单测 |
| 新工具 **`vendor_fetch`**（`logic` 在 core：`VendorCacheService`） | 单测：mock HTTP → sha256 落盘；拒绝非白名单 host |
| 文档 `docs/system/js-runtimes.md`：**web vs qjs** 对照表（Mermaid/Three → web；纯 markdown 解析尝试 qjs） | `read_agent_doc` 测 |
| catalog manifest 样例条目：`js_lib.mermaid`、`js_lib.three`（可选，与 vendor_fetch 二选一或并存） | 扩展 `catalog-sample` + `CatalogSyncServiceTest` |

**REQ-103**：白名单 CDN 下载一次后，第二次命中本地；events 记 `vendor_fetch` / `catalog_install`。

---

### Phase D — Mermaid / Three.js 场景脚本

| 做 | 验 |
|----|-----|
| `shared/catalog/scripts/` 或 workspace 模板：`render-mermaid.js`（WebView HTML + 缓存 mermaid） | instrumented / desktop webview：输出 png 或 svg |
| `render-three-preview.js`（可选，输出 png 截图） | 同上，标记 `@LargeTest` 可 CI nightly |
| Skill：`diagram-mermaid-webview`（project） | 无 LLM Scripted：tool 链调用脚本路径 |

**REQ-104**：给定 `.mmd` 或 markdown fenced mermaid → 产出图片路径。

---

### Phase E — Markdown → DOC（.docx）

| 做 | 验 |
|----|-----|
| **首选 WebView 车道**：HTML（Markdown→HTML 用 marked 等 **vendor 缓存**）→ print / html-docx-js 或服务端式 lib 在 WebView 内生成 blob → 写 workspace（base64 或 binary 工具） | 单测：最小 md → docx zip 魔数 `PK` |
| **备选 QuickJS**：仅当选用 **纯 JS、无 DOM** 的 docx 生成库时走 file 脚本 | 与 Phase C 分类一致 |
| CLI `./agent1` 与 Android 共用脚本路径约定 | `ProductivityScripted*` 或新 `MdToDocIntegrationTest` |

**REQ-105**：`input.md` + `output.docx`；events 有 `tool_call`；文件可被 Android **打开**（Phase F）。

---

### Phase F — Android 聊天：文件链接 + 打开 + 分享

| 做 | 验 |
|----|-----|
| **`WorkspaceMarkdown`**：除 `ImageTransformer` 外，增加 **LinkTransformer / 自定义 Markdown 扩展**：识别 `[label](path)` 且 path 为工作区相对路径 → `ClickableText` | `ChatTranscriptFormattingTest` + Compose UI test |
| **`WorkspaceAttachment`** 数据类：`relativePath`、`displayName`、`mimeType` | ViewModel 从 assistant markdown + tool `outputPath` 聚合 |
| **`OpenWorkspaceFileUseCase`**（`logic.business`）：`FileProvider` + `ACTION_VIEW` + MIME（docx、pdf、png…） | instrumented：临时 workspace 文件 → 断言 Intent 已 fire（或 resolveActivity 非空） |
| **分享**：同 `DiagnosticExportAction` 模式，`ACTION_SEND` + `ClipData` | instrumented 或 Robolectric Intent 断言 |
| UI：链接旁 **分享图标**（仅 attachment 行） | 截图/E2E 可选 |

**REQ-106**：助手消息含 `[报告](out/report.docx)` → 点击调起 WPS/Word；分享按钮出现系统 chooser。

**MIME 映射（初版）**

| 扩展名 | MIME |
|--------|------|
| docx | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| pdf | `application/pdf` |
| png/jpg | image/* |
| svg | `image/svg+xml` |
| md | `text/markdown` |

---

## 4. 自动化测试矩阵（必须齐）

| 层级 | 命令 / 类 | 覆盖 Phase |
|------|-----------|------------|
| JVM core | `:core:test` VendorCache、Bootstrap manifest | C |
| JVM weizhi-bridge | SVG 栅格、js_lib path | B, D |
| Android unit | `ChatTranscriptFormattingTest`、路径解析 | F |
| Android instrumented | `SvgRasterInstrumentedTest`、`OpenDocxInstrumentedTest` | B, E, F |
| 仓库脚本 | 更新 `run-webview-draw-test.sh` → `run-svg-raster-test.sh` | B |
| Mock 回归 | `./scripts/e2e-self-evolve-smoke.sh` 增 1～2 Gradle 测 | B, E |
| **禁止** | 本计划 Phase 不新增 DeepSeek Burn；LLM 仅可选 smoke 文档 | — |

---

## 5. 与现有代码的衔接点

| 模块 | 改动方向 |
|------|----------|
| `WeizhiHostLoader.kt` | 去掉仅 canvas 的 prompt；改为「附件用 markdown 链接 + 图片用 `![]()`」 |
| `WeizhiAgentTools.kt` | 后续可注册 `vendor_fetch` 适配器（若放 Weizhi 侧） |
| `ChatTranscriptFormatting.kt` | 扩展非图片 `outputPath`（docx）→ attachment 列表 |
| `ProductivityScreens.kt` / `WorkspaceMarkdown.kt` | 链接点击、分享按钮 |
| `SessionWorkspacePaths.kt` | 已有 `resolveFile`；扩展 MIME 与 `FileProvider` root |
| Desktop CLI | `WeizhiWorkspaceTools` 同步 SVG 模板行为，保证 **UC 一致** |

---

## 6. 建议 PR 切分（一步一步）

1. **PR-1 Phase A**：删 asset skill + 文档 + 测试改名  
2. **PR-2 Phase B**：SVG→PNG 模板 + Android/CLI 脚本测  
3. **PR-3 Phase C**：vendor_fetch + 白名单 + 单测  
4. **PR-4 Phase D**：Mermaid（+ 可选 Three）脚本 + catalog 样例  
5. **PR-5 Phase E**：md→docx 脚本 + 魔数单测  
6. **PR-6 Phase F**：Markdown 链接 UI + 打开 + 分享 + instrumented  

每 PR 在描述里写 `Phase X / REQ-10x`，CI 必须绿。

---

## 7. 风险与决策点（实施前确认）

| 项 | 选项 |
|----|------|
| docx 生成库 | WebView + html-docx（重 DOM） vs 服务端纯 JS（QuickJS）— 建议 **先 WebView** 与 Mermaid 同车道 |
| Three.js | 体积大、慢 — 建议 **Phase D 可选**，CI 标 `@LargeTest` |
| CDN 离线 | 仅缓存后可用；需 UI 提示「先 vendor_fetch」 |
| 打开 docx | 依赖用户设备安装 WPS/Office；无 app 时 Toast「未找到应用」 |
| FileProvider paths | 仅暴露 `sessions/.../workspace`，不暴露整个 agentRoot |

---

## 8. 文档索引

| 读者 | 文档 |
|------|------|
| 实施者 | 本文 Phase A–F |
| E2E | `android_agent/doc/webview-draw-e2e.md`（Phase A 后重写为 svg-raster） |
| 能力 | 新增 `docs/system/js-runtimes.md`（Phase C） |
| REQ 登记 | 合并到 `15-可验证需求.md` 时追加 REQ-101～106 |
