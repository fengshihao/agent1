---
name: video-edit
version: 1.0.0
description: 用户要剪视频、拼片段、加片头剧照、抽帧/抽音轨、导出 mp4 时使用（Android 宿主 weizhi:video）。桌面无 MediaCodec 不适用。
category: media.video
catalog_line: 剪视频、拼片、片头剧照、抽帧抽音轨、导出 mp4 时用（Android）。
depends_weizhi: video
tags: video,视频,剪辑,exportVideo,抽帧,抽音轨,片头,crossfade,时间线,mp4
---

# 视频剪辑（weizhi:video）

## 范围

| 做 | 不做 |
|----|------|
| `probe`、抽帧/抽音轨、`exportVideo` 导出 | 用 `webview_exec` 解码整段 mp4 |
| v1 remux：掐头去尾、无淡化拼接 | 假装支持桌面转码（桌面无本包） |

## 开工协议

1. 先 **`load_skill`（`video-edit`）** 读本正文。
2. 写 `exportVideo` 或复杂时间线前：**`find_caps`** 一次（query 含 `exportVideo weizhi:video` 或具体能力），按返回的**调用示例**写，不要猜字段名。
3. 脚本放进 `jobs/*.js`，用 `run_js` 的 `file` 执行；可 `timeout_ms` 加大（上限 600000）。
4. **脚本里 API 报错**（如 `bad argument`、`unsupported`）：再 **`find_caps`** 核对签名后改脚本。

## 选路

| 任务 | API |
|------|-----|
| 只裁一段、多段首尾相接、不重编码 | v1 `exportVideo({ clips: [{ path, startMs?, endMs? }], output })` |
| 多轨、剧照片头、Ken Burns、交叉淡化 | v2 `exportVideo({ width, height, tracks, output })` |

## 时间线 v2（要点）

- **剧照 / 图片**：`type: "image"` 轨，`durationMs` 必填。
- **mp4 片段**：`type: "video"` 轨，`inMs` / `outMs`；不要把图片塞进 video 轨。
- **交叉淡化**（仅 image 轨）：后一个 clip 写 `transition: { type: "crossfade", durationMs: D }`，且  
  `startMs_next = startMs_prev + durationMs_prev - D`（重叠恰好 **D** 毫秒）。**没有** `crossfadeMs` 字段。
- 同一轨上未声明 `transition` 的 clip **不能时间重叠**；报错 `overlapping clips` 即检查上式与 `transition`。
- `transition` 与 clip 自写的 **opacity 关键帧不能同时用**。
- 音轨：`type: "audio"` 单独一条 track。

示例（两图淡化 + 再接视频）：

```javascript
import { exportVideo } from "weizhi:video";

const D = 300;
await exportVideo({
  width: 720,
  height: 1280,
  tracks: [
    {
      type: "image",
      clips: [
        { src: "a.jpg", startMs: 0, durationMs: 1000 },
        {
          src: "b.jpg",
          startMs: 1000 - D,
          durationMs: 1000,
          transition: { type: "crossfade", durationMs: D },
        },
      ],
    },
    {
      type: "video",
      clips: [{ src: "clip.mp4", startMs: 1700, inMs: 0, outMs: 5000 }],
    },
  ],
  output: "out/final.mp4",
});
```

## 推荐流程

1. `probe` 源视频时长与分辨率。
2. 需要封面帧：`extractFrame` / `extractFrames`（见 `find_caps`）。
3. 复杂片：可先 v1 导出中间 `part1.mp4`、`part2.mp4`，再 v1 或 v2 拼接；避免在一轨上硬叠未声明淡化的片段。
4. 配乐：单独 `audio` 轨，`outMs` 对齐画面总长。

## 编排

- 一段 `jobs/render.js` 串行完成探测、导出、复核（可再 `probe` 成片）。
- 外层不要用多个工具代替一整段脚本。

## 对用户怎么说

说明成片文件名与时长；用 `[成片](out/xxx.mp4)` 链接。不堆栈术语。
