---
name: travel-itinerary
version: 1.3.2
description: 用户要几日自驾路书、行程攻略、带图带路线、可点开高德导航，或「生成一个好看能点的路书网页」时使用。主交付是单页 HTML；不含机酒火车下单、Word/docx。
category: travel.roadbook
catalog_line: 自驾几日游路书、交互式 HTML 行程页（路线图+图片+高德导航）时用。
depends_mcp: gaode
tags: 旅游,行程,攻略,自驾,路书,地图,高德,HTML,路线图,itinerary,travel
---

# 自驾路书 → 可交互 HTML

交付一个手机可开的单页 **`<trip-slug>/路书.html`**。不做 Word/CSV/机酒下单；不订不了的别假装能订。禁用 Leaflet。

信息不够（去哪、几天、出发城市）先 `ask_user` 问一轮，否则直接开工。API 签名拿不准就 `find_caps`，按示例写。

## 目录

```text
<trip-slug>/
  trip.json     # days[].{title, waypoints[{name,lng,lat}], images[{path,sourcePage}]}
  assets/       # 配图（已下载到本地，不外链图床）
  路书.html     # 主交付
```

经纬度用高德格式 `经度,纬度`。缺坐标 → 高德 MCP geocode。

## 工具脚本（本 Skill 自带）

`scripts/roadbook.js` 提供 `appRouteUri`、`queryDrivingLegs`、`renderSchematicMap`。`run_js` 只认 workspace 路径，先落盘再 import：

```javascript
// load_skill(path="scripts/roadbook.js") 读源码 → write_file 到 jobs/roadbook.js
import { appRouteUri, queryDrivingLegs, renderSchematicMap } from "./roadbook.js";

appRouteUri({ waypoints, mode: "drive" });   // amapuri://，手机进高德 App；途经点≤16
await queryDrivingLegs([{ name, origin, destination }]); // 真实公里/分钟，需高德 MCP；有折线就用折线
await renderSchematicMap({ waypoints, outputPath });     // PNG 示意图，需 webview_exec
```

MCP 未配置时里程用估算并注明「非高德实测」。

## 流程（一段 run_js 串完）

1. 写 `trip.json`（按天 waypoints）。
2. `queryDrivingLegs` 汇总每天里程时间。
3. 配图：`image_search` 搜景点图 → `$tools.files_download({ items:[{url, expect:"image"}], dir:"<trip-slug>/assets" })`；`trip.json` 记 `sourcePage`，页面图注标来源。搜不到就占位，**不要编 URL**。门票/封路等事实用 `web_search` 并标注检索日期。
4. 每天 `appRouteUri` → 「用高德导航」按钮。
5. 生成 `路书.html`。

## 页面要求

- 按天分块（Tab 或手风琴）：当天里程耗时、景点食宿、1～3 张 `assets/` 配图（`loading="lazy"`）、当天路线图、导航按钮。
- 路线图三选一：**① 内嵌 SVG（默认）**：waypoints/折线投影成 `<polyline>`，滚轮/双指缩放 `viewBox`；**② 高德 Web/静态图**：需要真实底图时 `find_caps` 拿高德 JS API 或静态图用法；**③ `renderSchematicMap` PNG** 作缩略图。`maps_schema_personal_map` 只在用户点名要云端地图页时用。
- 顶部行程总览（天数、总里程）；配色偏旅行杂志感，别素成 Markdown 导出样。

## 收尾

口语总结几天、每天去哪；给 `[路书](<trip-slug>/路书.html)`，需要时贴当天 `amapuri://` 一行。改行程就改 `trip.json` 再生成。
