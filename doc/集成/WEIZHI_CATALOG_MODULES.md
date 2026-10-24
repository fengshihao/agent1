# Weizhi Package（替代 catalog scriptFolder）

Weizhi 主干已移除 **`setScriptFolder` / `import "leaf.js"`**，官方 JS 库统一为 **Weizhi Package**。

规范真源：[weizhi `docs/WEIZHI_PACKAGES.md`](https://github.com/fengshihao/weizhi/blob/master/docs/WEIZHI_PACKAGES.md)、[MODULE_LOADING.md](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)

## Agent1 约定

| 根 | 路径 / API |
|----|------------|
| `setFsRoot` | 会话 workspace |
| `setPackageRoot` | 桌面：`{agentRoot}/weizhi-home`（`WeizhiBundledPackages.syncFromClasspath`）；Android：`WeizhiPackages.syncBundled(Context)` → `filesDir/weizhi-home` |

内置 Office / SVG 脚本真源：`agent_core/.../agent-home/weizhi-packages/<name>/`（**仓内路径无版本**；版本只写在各包 `manifest.json`）。桌面 classpath 安装到 `weizhi-home/packages/<name>@<version>/`。**Android 不预装**（20-A 收口）：首次 `run_js` 由 `WeizhiPackageSync.beforeEval` 从云端 registry（`agentone.guoyoutech.cn`）动态安装，发布走 `artifact-cdn` 的 `pack.mjs + upload.sh`。源码 / 运行时 / 通道分层见 [WEIZHI_PACKAGE_REGISTRY.md §3.1](./WEIZHI_PACKAGE_REGISTRY.md)。

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
- image 主轨相邻 clip 的 `transition: { type: 'crossfade', durationMs }` 须与前 clip **精确重叠 D**。
- video 主轨支持 transform 常量/关键帧（缩放 letterbox 黑底、opacity 混合）。
- **仍不支持**（P5d/P5e）：text 轨、audio gain 关键帧、video 轨 crossfade、时间轴空隙。

**给模型的写法**：`import { markdownToDocx } from "weizhi:docx"`、`import { renderPptx } from "weizhi:pptx"` 等。workspace 内协作用 `import "./helper.js"`。

## 随包 Skill（2026-10-17 起，T0 规范）

Skill 类能力**一律随微智包分发**，不再往 `agent-home/skills` 加内置 skill：

- 包 manifest 声明 `"skill": "SKILL.md"`，SKILL.md 随包打进 zip / assets（frontmatter `name` / `description` / `tags`）。
- 安装链路（桌面 `WeizhiBundledPackages.syncFromClasspath`、registry `WeizhiPackageSync.beforeEval`、Android `WeizhiBundledInstall`）装完包后由 `agent_core` 的 `PackageSkills` 整批写 `kind=skill` 能力卡（`source=package_skill`，id `skill.<name>`、entry `skill:<name>`）。
- `AgentSkillLoader.loadMerged` 在 bundled 与 project 之间加载包内 skill 正文（source 标 `package`）；local / catalog / project 同名可覆盖。
- 首例：`html2pptx`（随包 SKILL，见 `doc/集成/HTML2PPTX.md`）。存量 `pptx`、`skill-creator` 仍是内置 skill 例外。

## 集成测

- `DocxOfficeIntegrationTest` / `PptxOfficeIntegrationTest`（`:weizhi-bridge:test`，需 `libweizhijni`）
- `OfficeCatalogInstrumentedTest`（Android 真机，云端 registry 动态安装，需联网）
- `VideoExportInstrumentedTest`（Android 真机，AAR assets 离线装包：v1 remux 掐头去尾 + v2 image 主轨 scale 关键帧，无需联网）

## 关联

- [WEIZHI.md](./WEIZHI.md)、[agent1#89](https://github.com/fengshihao/agent1/issues/89)、[agent1#98](https://github.com/fengshihao/agent1/issues/98)、[agent1#101](https://github.com/fengshihao/agent1/issues/101)
- [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、[WEIZHI_PPTX.md](./WEIZHI_PPTX.md)
