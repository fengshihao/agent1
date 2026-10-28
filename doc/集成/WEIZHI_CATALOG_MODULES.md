# Weizhi Package（替代 catalog scriptFolder）

Weizhi 主干已移除 **`setScriptFolder` / `import "leaf.js"`**，官方 JS 库统一为 **Weizhi Package**。

规范真源：[weizhi `docs/WEIZHI_PACKAGES.md`](https://github.com/fengshihao/weizhi/blob/master/docs/WEIZHI_PACKAGES.md)、[MODULE_LOADING.md](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)

## Agent1 约定

| 根 | 路径 / API |
|----|------------|
| `setFsRoot` | 会话 workspace |
| `setPackageRoot` | 桌面：`{agentRoot}/weizhi-home`（`WeizhiBundledPackages.syncFromClasspath`）；Android：`WeizhiPackages.syncBundled(Context)` → `filesDir/weizhi-home` |

内置 Office / SVG 脚本真源：`agent_core/.../agent-home/weizhi-packages/<name>/`（**仓内路径无版本**；版本只写在各包 `manifest.json`）。桌面 classpath 安装到 `weizhi-home/packages/<name>@<version>/`（见 `WeizhiBundledPackages`）。**Android 不预装**（20-A 收口）：首次 `run_js` 由 `WeizhiPackageSync.beforeEval` 从云端 registry（`agentone.guoyoutech.cn`）动态安装，发布走 `artifact-cdn` 的 `pack.mjs + upload.sh`。源码 / 运行时 / 通道分层见 [WEIZHI_PACKAGE_REGISTRY.md §3.1](./WEIZHI_PACKAGE_REGISTRY.md)。

**云端-only（xlsx）**：`weizhi:xlsx` / `weizhi:xlsx-build` / `xlsx-raw` / `chart` 为云端-only 包（`WeizhiBundledPackages.CLOUD_ONLY_PACKAGE_NAMES`），**不**列入桌面 `BUNDLED_PACKAGE_NAMES`；Skill `xlsx` 也不进 `skills-bundled`。生产两侧均靠 registry；集成测试用固化 fixture（`WeizhiTestPackages.installFromSource`）。API 见 [OFFICE_XLSX_API.md](./OFFICE_XLSX_API.md)。

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

- **Package** 只放 SDK 级 JS（`weizhi:<name>`）；**Skill** 为 `SKILL.md` + 可选脚本。内容源**可插拔**（官方默认 [agent1-skills](https://github.com/fengshihao/agent1-skills) `skills-src/`，可自建内容仓），上云用内容仓 pack 脚本 + 本仓 `artifact-cdn/` 通用工具；classpath `skills-bundled/` 为官方预置离线快照（手动维护）。
- 运行时目录 `<agentRoot>/skills-home/`；桌面 `SkillsBundledInstall`（随 `WeizhiBundledPackages.syncFromClasspath`）；`load_skill` / 云端 `SkillRegistryClient`（统一 `registry/stable.json` 里 `kind=skill`）安装 zip。
- `SkillsCapabilitySync` 写 `kind=skill` 卡（`source=skills_home`）；系统提示词注入 `skills/registry/stable.json` 渲染的目录；工作流：**先 `load_skill`，无匹配再 `find_caps`**。
- 遗留：`PackageSkills` 仍解析旧包 manifest 的 `"skill"` 字段（新官方包不再使用）。内置例外：`skill-creator`（classpath bundled）。

## 集成测

- `DocxOfficeIntegrationTest` / `PptxOfficeIntegrationTest` / `XlsxOfficeIntegrationTest`（`:weizhi-bridge:test`，需 `libweizhijni`；xlsx 从仓内真源安装）
- `OfficeCatalogInstrumentedTest`（Android 真机，云端 registry 动态安装，需联网）
- `VideoExportInstrumentedTest`（Android 真机，AAR assets 离线装包：v1 remux 掐头去尾 + v2 image 主轨 scale 关键帧，无需联网）
- 剪辑工作流 Skill：`video-edit`（官方内容源 [agent1-skills/skills-src/video-edit](https://github.com/fengshihao/agent1-skills)；内置进 APK 为 `skills-bundled/` 官方预置离线快照；云端走 registry）

## 关联

- [WEIZHI.md](./WEIZHI.md)、[agent1#89](https://github.com/fengshihao/agent1/issues/89)、[agent1#98](https://github.com/fengshihao/agent1/issues/98)、[agent1#101](https://github.com/fengshihao/agent1/issues/101)
- [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、[WEIZHI_PPTX.md](./WEIZHI_PPTX.md)
