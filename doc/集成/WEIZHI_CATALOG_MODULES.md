# Weizhi Package（替代 catalog scriptFolder）

Weizhi 主干已移除 **`setScriptFolder` / `import "leaf.js"`**，官方 JS 库统一为 **Weizhi Package**。

规范真源：[weizhi `docs/WEIZHI_PACKAGES.md`](https://github.com/fengshihao/weizhi/blob/master/docs/WEIZHI_PACKAGES.md)、[MODULE_LOADING.md](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)

## Agent1 约定

| 根 | 路径 / API |
|----|------------|
| `setFsRoot` | 会话 workspace |
| `setPackageRoot` | 桌面 / Android：`{agentRoot}/weizhi-home`（仅确保目录；包由 `WeizhiPackageSync.beforeEval` 从 registry 惰性安装） |

官方 Office / SVG 等 JS 包**不进 jar/APK**（没网连不上模型，离线预装没有意义）。首次 `run_js` 由 `WeizhiPackageSync.beforeEval` 按源里 `import "weizhi:<name>"` 从云端 registry（`OfficialCdn` / `AGENT1_CDN_HOST`）ensure。发布走本仓 `artifact-cdn` 的 `pack.mjs + upload.sh`。源码 / 运行时 / 通道分层见 [WEIZHI_PACKAGE_REGISTRY.md §3.1](./WEIZHI_PACKAGE_REGISTRY.md)。

集成测试用固化 fixture（`WeizhiTestPackages` / `WeizhiTestSkills`）。包/Skill 的 API 与工作流不在本仓，运行时靠 `find_caps` / `load_skill`。

**例外——video（issue #98 交付模型，[#101](https://github.com/fengshihao/agent1/issues/101) 升至 0.6.0）**：`weizhi:video`（**video@0.6.0**）与插件 SO（`libweizhivideo.so` / `libweizhitranscode.so`）强版本耦合，**不上云 registry**，随 `:weizhi-video` AAR 原子交付进 APK（SO 走 jniLibs、JS 包走 AAR assets，`WeizhiPackages.syncBundled` 自动安装）；引擎侧 `WeizhiVideo.install(engine)` 注册插件。桌面无 MediaCodec，`ensureNative` 报 unsupported 属预期。

写法：`import { exportVideo, probe, extractAudio } from "weizhi:video"`。

| 能力 | 版本 | 说明 |
|------|------|------|
| 包层 `probe` | 0.3.1 起（weizhi#39） | 元数据探测 |
| `extractFrame` / `extractFrames` / `extractAudio` | 0.4.0 起 | 经 agent1 `$tools.media_*` 宿主桥 |
| v1 `clips` / `slides` remux | 不变 | 掐头去尾、拼接；`reencode`/`filters` 走 transcode |
| v2 `tracks` 关键帧 + image `crossfade` | **0.6.0 / P5c** | 见下方 |

**时间线 v2（P5c，规格真源 [weizhi `docs/video-timeline-v2.md`](https://github.com/fengshihao/weizhi/blob/master/docs/video-timeline-v2.md)）**：

```js
await exportVideo({
  width: 1280, height: 720,
  tracks: [{
    type: "image",
    clips: [
      { src: "a.jpg", durationMs: 800 },
      {
        src: "b.jpg", startMs: 500, durationMs: 800,
        transform: { scale: [{ t: 0, v: 1 }, { t: 600, v: 1.15, interp: "ease" }] },
        transition: { type: "crossfade", durationMs: 300 }, // 须与前 clip 精确重叠 300ms
      },
    ],
  }],
  output: "out.mp4",
});
```

- `transform` 五元：`x` / `y`（归一化，±1 为画面边缘中心）、`scale`、`rotation`（度）、`opacity`（0–1）；每项可为常量或 `{t, v, interp?}` 数组（`linear`|`ease`，缺省 linear）。
- image 主轨相邻 clip 的 `transition: { type: 'crossfade', durationMs }` 须与前 clip **精确重叠 D**（`startMs_next = startMs_prev + durationMs_prev - D`）。**没有** `crossfadeMs` 字段；写了也不会生效，重叠会被报 `overlapping clips in tracks[n]`。
- 剧照/静图序列用 `type: "image"` 轨；`type: "video"` 轨只接 mp4 等视频 `src`（`inMs`/`outMs`），不要把图片塞进 video 轨。
- crossfade 与 clip 上自写的 `opacity` 关键帧**不能同时**用（引擎会合成淡化关键帧）。
- video 主轨支持 transform 常量/关键帧（缩放 letterbox 黑底、opacity 混合）；**video 轨不支持** `transition` crossfade（P5e）。
- **仍不支持**（P5d/P5e）：text 轨、audio gain 关键帧、video 轨 crossfade、时间轴空隙。

**给模型的写法**：`import { markdownToDocx } from "weizhi:docx"`、`import { renderPptx } from "weizhi:pptx"` 等。workspace 内协作用 `import "./helper.js"`。

## 官方 Skill（与 Package 分轨，2026-10 起）

- **Package** 只放 SDK 级 JS（`weizhi:<name>`）；**Skill** 为 `SKILL.md` + 可选脚本。上云用 `artifact-cdn/` 通用工具。**不**把能上云的 Skill / Package 打进 jar/APK。
- 运行时目录 `<agentRoot>/skills-home/`；`load_skill` / 云端 `SkillRegistryClient`（统一 `registry/stable.json` 里 `kind=skill`）安装 zip。
- `SkillsCapabilitySync` 写 `kind=skill` 卡（`source=skills_home`）；系统提示词注入 `skills/registry/stable.json` 渲染的目录；工作流：**先 `load_skill`，无匹配再 `find_caps`**。
- 遗留：`PackageSkills` 仍解析旧包 manifest 的 `"skill"` 字段（新官方包不再使用）。内置例外：`skill-creator`（classpath bundled）。

## 集成测

- `DocxOfficeIntegrationTest` / `PptxOfficeIntegrationTest` / `XlsxOfficeIntegrationTest`（`:weizhi-bridge:test`，需 `libweizhijni`；xlsx 从仓内 fixture 安装）
- `OfficeCatalogInstrumentedTest`（Android 真机，云端 registry 动态安装，需联网）
- `VideoExportInstrumentedTest`（Android 真机，AAR assets 离线装包：v1 remux 掐头去尾 + v2 image 主轨 scale 关键帧，无需联网）
- 剪辑工作流 Skill：`video-edit`（云端 registry + `load_skill`，不进 APK）

## 关联

- [WEIZHI.md](./WEIZHI.md)、[WEIZHI_PACKAGE_REGISTRY.md](./WEIZHI_PACKAGE_REGISTRY.md)
- [agent1#89](https://github.com/fengshihao/agent1/issues/89)、[agent1#98](https://github.com/fengshihao/agent1/issues/98)、[agent1#101](https://github.com/fengshihao/agent1/issues/101)
