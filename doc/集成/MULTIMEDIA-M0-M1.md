# 多媒体 M0 / M1（Android 优先）

> **策略**：探测、抽帧、缩放是 **PC 与 Android 共用** 的能力，放在 **Agent1**（同一 `media.js`，两端各接后端）。**不**加进微智 `android.media`。Android 用系统 Media / Bitmap，**不**在 APK 内捆绑 FFmpeg。桌面后端以后用 FFmpeg；差异见 §6。  
> **数据源**：模型只认 **workspace 相对路径**（含 `imports/`、`accessible-files.json` 登记路径）。  
> **关联**：[CLI 与 Android Agent 能力对照](../CLI与Android-Agent能力对照.md)、[17-Android 宿主能力与 Session 可访问文件](../规划/自进化Agent/17-Android宿主能力与Session可访问文件.md)。

---

## 1. 目标

| 阶段 | 范围 | 实现 |
|------|------|------|
| **M0** | 图片（≤ 常规手机照） | **`media-image.js`**：灰度 / 缩放 / **catalog LUT**；WebView 执行；**无内置 preset 名** |
| **M1** | 图 / 视频 / 音频 **探测 + 抽帧 + 大图缩放** | Agent1：`media.js` 调宿主工具。Android 后端在 `android_agent`；桌面后端后做 |
| **未做（记录）** | 桌面转码、整段视频重编码、FFmpeg 滤镜链 | 文档 + 后续 `native.media_ffmpeg` 或 `bash ffmpeg` |

---

## 2. API 形状：FFmpeg **启发式**，非全量兼容

**原则**：参数名、操作语义尽量贴近 FFmpeg，方便模型从预训练知识迁移；**不**承诺支持完整 CLI / 滤镜表达式。

### 2.1 统一脚本入口（`media.js`）

```javascript
import { probe, run } from "media.js";

const info = await probe("imports/clip.mp4");
await run({
  inputs: ["imports/photo.jpg"],
  outputs: ["out/bw.jpg"],
  filters: "format=gray",           // 或 lut: "<catalog-id>"，见 §2.3
});
await run({
  inputs: ["imports/clip.mp4"],
  outputs: ["out/frame.jpg"],
  ss: "00:00:03.000",               // 与 ffmpeg -ss 同形（字符串或秒数）
  vframes: 1,
});
```

- **`probe(path)`** → `{ format, durationMs, width, height, videoCodec?, audioCodec?, sizeBytes }`（能取则取）。
- **`run(opts)`** → 调 Agent1 宿主工具（脚本内 `$tools`，与 `webview_exec` 同类）。图片滤镜分支到 **`media-image.js`**（WebView）。

### 2.2 放在哪

| 能力 | 归宿 | 原因 |
|------|------|------|
| `probe` / 抽帧 / 缩放 | **Agent1** | PC 和 Android 都要用。同一脚本入口，后端按平台换 |
| Android 后端 | **`android_agent`** | `MediaMetadataRetriever`、`BitmapFactory`，只服务上面的共用入口 |
| 桌面后端 | **`java_agent`（后做）** | FFmpeg。现在只记差异，不实现 |
| 微智 `android.media.resize` | **已删除**（[weizhi#32](https://github.com/fengshihao/weizhi/issues/32)） | 缩放由 Agent1 `media_resize` 承担 |

微智继续放 **端上脚本运行时**，以及只在某一系统上成立的宿主动作（如 `android.intent`、分享、选目录）。探测和抽帧不是这类动作。

| 方法（`media.js` / `$tools`） | FFmpeg 类比 | Android 后端 |
|------|-------------|------|
| `media_probe({ path })` | `ffprobe` 子集 | `AndroidMediaInspector`：`MediaMetadataRetriever`；图片读不出时用 `BitmapFactory` 看宽高。音频只报时长和 MIME |
| `media_extract_frame({ path, output, ss })` | `-ss` + `-vframes 1` | `getFrameAtTime`。纯音频返回「没有画面」 |
| `media_resize({ path, output, maxEdge })` | `-vf scale` | Android：`BitmapImageResizer`（`BitmapFactory`）。桌面调用返回「未实现」 |
| 截取音轨、整段转码 | **不做** | 引导系统剪辑 App；桌面以后再接 FFmpeg |

**limits（Native 路径，与 WebView 无关）**：

- 单次任务 **wall-clock timeout**（如 120s）、输出文件 **≤ 可用存储**；**不**沿用 WebView 20MB `input_path` 上限。
- 可选软限：单文件输入 ≤ 512MB（可配置），避免 OOM；超大文件先 `probe` 再拒绝并提示用户剪短。

### 2.3 调色 / 滤镜（可选，**仅 catalog 白名单 URL**）

**产品决策**：不维护 Agent1 内置 preset。扩展滤镜只来自 **catalog 索引里的 LUT（`.cube` 等）**；**禁止** `read_url`、脚本 `fetch` 或用户链接拉任意 LUT。

**AI 流程**：

1. `find_caps("LUT 胶片 调色")` → 命中 `kind: lut` 卡片（含 **id、tags、entry**）。
2. 若本地未安装 → `catalog_sync_status` / **`catalog_install`**（只安装 manifest 里 **trusted source** 的条目）。
3. LUT 落盘到 **`shared/catalog/...`** 或 sync 约定目录（**非** workspace 手 write）。
4. `applyLut({ input, output, lutId: "<id>" })` 或 `run({ ..., lut: "<id>" })` — 执行器 **只接受 id**，内部解析为 catalog 已安装路径；**不接受** http(s) 字符串或 workspace 内自写 `.cube`。

**卡片字段（seed / 云 manifest，`filter-lut-cards.jsonl`）**：

| 字段 | 说明 |
|------|------|
| `id` | 全局唯一，如 `lut.cinematic-teal-07` |
| `kind` | `lut` |
| `title` / `summary` / `tags` | 供 `find_caps` |
| `url` | **仅**维护者 bucket / 内置 CDN 前缀下的对象 URL（与 [trusted-sources](../规划/自进化Agent/08-云端资源同步.md) 一致） |
| `license` | SPDX 或摘要（索引展示用） |
| `entry` | 可复制调用示例（`lutId` 固定写 id） |

**默认（多数用户）**：只做 `format=gray`、`scale`（M1 Native 或 M0 WebView），**不**走 LUT。

**Coach**：检测到 `fetch(` / `read_url` 下载 LUT、或 `applyLut` 传入 URL → 提示改用 **`find_caps` + `catalog_install` + lutId**。

---

### 2.4 M0 基础图处理（无 LUT）

- **`filters`** 小子集：`format=gray`；可选 **CSS filter 白名单**（MDN 属性名，如 `grayscale(1)`、`contrast(1.1)`），由固定 WebView 模板解析，**禁止**模型每轮手写整页 Canvas。
- 大图缩放优先 **M1 `scaleImage`**，避免整图进 WebView `input_path`。

---

## 3. 20MB 限制说明（为何 Native 应绕开）

| 层级 | 限制 | 原因 |
|------|------|------|
| **QuickJS `fs`** | Weizhi `fsIoBytes` 等配额 | 脚本 VM 内存 / IO 预算 |
| **`webview_exec` `input_path`** | **20MB** | 文件读入后 **Base64 经 bridge 注入 WebView**（`WebViewTask` SR15）；不是「视频编解码需要 20MB」 |
| **`webview_exec` `writeFile`** | **20MB/次** | 同上，回写信封解码 |
| **Agent1 媒体工具（M1）** | **不应套用 20MB** | 宿主按 Path 读写，字节不进入 QuickJS / 不进 WebView 注入 |

结论：61MB 视频在 **imports/** 里可以存在；卡住的是 **WebView 抽帧**，不是 workspace 不能存大文件。

---

## 4. WebView 写文件

**已有能力**（`WebViewExecHost`）：脚本内 **`writeFile(相对路径, 数据)`**，宿主解码 Base64 后写入 **workspace 沙箱**（单次 ≤20MB）。

- 不是「WebView 没有写盘权限」，而是 **故意不暴露任意 Path**，只通过宿主信封写相对路径。
- M0 图片应优先 **`writeFile` 写二进制 JPG**，避免把 base64 再读进 QuickJS 手写解码（今日会话痛点）。

超过 20MB 的抽帧和缩放走 **Agent1 媒体工具**（按路径读写），不放大 WebView 注入上限。

---

## 5. 数据源（给 AI 的稳定规则）

**当前实现**：用户选文件 → **整份复制**到 `workspace/imports/` → 登记 `accessible-files.json` → 系统提示列出相对路径。（**无**「大文件仅 URI、小文件 import」分支。）

**对 AI 的契约（建议保持）**：

1. 只使用环境段与 **`accessible-files.json`** 中的 **相对路径**。
2. 输出写到 workspace（如 `out/`、`artifacts/`），交付用 `![](path)`。
3. **区外 URI / SAF** 不作为 M0/M1 默认路径。若以后要支持，单独写成文档里的路径规则，不塞进 `android.media`。

**外部源**：

- **用户照片 / 视频**：仅 workspace 路径（见上）。
- **LUT / 滤镜资源**：**仅 catalog 白名单 URL**（sync apply 下载）；**不得**用 `read_url` 或任意 URL 绕过索引。
- 其他公开网页正文仍可用 **`read_url`**（与 LUT 无关）。

---

## 6. 平台差异（Android vs 桌面）

| 能力 | Android（M0+M1） | 桌面（后续） |
|------|------------------|--------------|
| 图片 gray / CSS 白名单 | `media-image.js` + WebView | 同脚本 + CDP Chromium |
| 图片 LUT | `applyLut(lutId)` + catalog 本地文件 | 同索引；桌面可额外 FFmpeg `lut3d`（未做） |
| 大图 scale | Agent1 `media_resize`，Android 用 Bitmap | 同一工具名；桌面后端以后换 FFmpeg（当前返回未实现） |
| 音视频探测 | Agent1 `media_probe`，Android 用 `MediaMetadataRetriever` | 同一工具名；桌面返回未实现 |
| 视频抽帧 | Agent1 `media_extract_frame`，Android 用 `getFrameAtTime` | 同一工具名；桌面以后换 `ffmpeg -ss -vframes 1`（当前返回未实现） |
| 截取音轨 / 转码 | 不支持（引导系统 App / 剪短） | FFmpeg |
| APK / 依赖体积 | 无 FFmpeg | 可选系统 ffmpeg |

**更新约定**：本表随实现变更；CLI 对照总表见 [CLI与Android-Agent能力对照.md](../CLI与Android-Agent能力对照.md)。

---

## 7. 交付清单（工程）

- [ ] `agent-home/catalog/scripts/media.js` + `media-image.js`（含 `applyLut(lutId)`，校验 id ∈ 已安装 catalog）
- [ ] `capabilities/filter-lut-cards.jsonl` + `find_caps` 索引；manifest **trusted URL** 仅维护者源
- [x] Agent1 `media_resize`：Android `BitmapImageResizer` + 能力卡。桌面返回「未实现」
- [x] Agent1 `media_probe` / `media_extract_frame`：Android `AndroidMediaInspector`。桌面返回「未实现」。输入软上限 512MB
- [ ] Coach：见 §8，**先记文档，不实现**
- [ ] 验收：61MB MP4 在 imports → `media_extract_frame` → JPG 展示

---

## 8. Coach（已定稿，尚未实现）

触发后在工具回执末尾追加一条 `[coach] hookId: …`。同一轮只挂一条，不与已有的 `script.fail_repeat`、`webview.async_syntax` 叠两段。LUT 相关的 Coach 等 catalog 有白名单条目后再写。

| hookId | 何时触发 | 告诉模型什么 |
|--------|----------|--------------|
| `media.use_resize` | `run_js` 源码或报错出现 `android.media`、`media.resize`、`image_resize`、`resizeRgba` | 缩放用 `$tools.media_resize`。微智接口已删除，不要 `Object.keys(android.media)` |
| `media.desktop_unimplemented` | `media_resize` / `media_probe` / `media_extract_frame` 回执含「未实现」 | 桌面还没有后端。不要改走 WebView 整文件，也不要再找微智 |
| `media.not_video` | `media_resize` 报不是图片，且路径是 mp4/mov/webm 等 | 视频用 `media_probe` 与 `media_extract_frame`。不要把整文件塞进 `webview_exec` 的 `input_path` |
| `webview.input_too_large` | `webview_exec` 失败且文本为超过 20MB / input too large | 按路径用 `media_resize` 或 `media_extract_frame`。不要重试把更大的文件注进 WebView |
| `webview.manual_b64` | `run_js` 在读 `tmp/*.b64`，或源码手写 base64 解码 | 图片用 WebView 的 `writeFile`，宿主会解码。不要在 QuickJS 里解码 |
