---
name: travel-itinerary
version: 1.1.0
description: 用户要旅游路书、几日游行程攻略、自驾环线、景点食宿表、路线图，或要在手机上打开高德 App 看路线时使用。不含机票火车酒店下单预订。
category: travel.roadbook
catalog_line: 规划旅游路书、自驾攻略、行程食宿表，或生成手机上打开高德 App 的路线时用。
depends_weizhi: travel-itinerary
depends_mcp: gaode
tags: 旅游,行程,攻略,自驾,路书,地图,高德,里程,食宿,导航,itinerary,travel
---

# 旅游路书（行程规划交付包）

当前只考虑手机。给用户点的地图链接一律用 `appRouteUri`（`amapuri://`），点开进入高德 App。不要按桌面、iOS 再写一套 Skill。

## 范围（硬边界）

| 做 | 不做 |
|----|------|
| 逐日行程、驾驶里程/耗时、景点与三餐建议、住宿区域与参考价、预算表、示意图路线图、手机上打开高德 App 的路线链接 | **机票 / 火车 / 酒店下单、锁价、支付、代订** |
| `web_search` 查门票/封路/旺季房价等**公开信息**，写入攻略并标注来源日期 | 假装能订到房或买到票 |

大交通（飞哪、坐哪趟车）只在路书里写**建议班次类型与时段**；用户自行在 OTA / 12306 / 酒店 App 预订。

## 脚本能力（何时用）

`find_caps` 里这几项通常是同一张包卡，正文以本 Skill 为准。

| 函数 | 什么时候用 | 要不要高德 MCP |
|------|------------|----------------|
| `appRouteUri` | 把路线交给用户。回复里单独一行放返回的 `amapuri://`，手机点开进高德 App 路线规划 | 不要 |
| `queryDrivingLegs` | 攻略、CSV 里要写真实公里和分钟 | 要 `maps_direction_driving` |
| `renderSchematicMap` | 攻略里嵌一张不依赖瓦片的示意图 PNG | 不要（要 `webview_exec`） |
| `buildLineList` + `personalMapUri` | 用户明确要高德云端「专属地图」页时才用。返回的是 https，手机上常常打开网页，**不要**当作导航入口 | 要 `maps_schema_personal_map` |

## 开工协议

1. 信息不全时：先用 `ask_user` 对齐出发城市、日期、人数、预算档、自驾/公共交通、忌口或老人小孩约束，然后结束本轮。
2. 信息足够后：先 **`load_skill`（`travel-itinerary`）** 读本正文，**严格按下列步骤**；需要 API 时再 `find_caps`（高德 MCP、leaflet、docx 等）。不要另起一套即兴流程。
3. 给用户的导航链接用 `appRouteUri`，不依赖 MCP。只有要写真实里程时，才确认宿主已配置高德 MCP 的 `maps_direction_driving`；未配置则用文字估算并说明数字不是高德实测。

## 推荐目录（单次任务一个文件夹）

```text
<trip-slug>/
  trip.json           # 元信息 + 按天 waypoints（name,lng,lat,day）
  legs-distances.json # queryDrivingLegs 输出
  行程食宿表.csv
  攻略.md             # 主交付
  route_map.png       # 示意图（可选）
  路书速查.html       # 可打印速查（可选）
  sources.md          # web_search 来源摘要
```

## 工作流

### 1. 结构化路线

- 用 `trip.json` 写清每天落脚点与景点坐标（高德经纬度：`经度,纬度`）。

### 2. 手机上打开高德 App（主路径）

第一个点是起点，最后一个点是终点，中间是途经点，最多 16 个。一天一条，或一次连续驾驶一条；点更多就拆开，不要塞进一条链接。

```javascript
import { appRouteUri } from "weizhi:travel-itinerary";

const uri = appRouteUri({
  waypoints: [
    { name: "乌鲁木齐", lng: 87.6168, lat: 43.8256 },
    { name: "天山天池", lng: 88.1273, lat: 43.8830 }
  ]
});
// 回复里单独一行原文展示 uri。默认 amapuri://route/plan/。
// 仅在 amapuri 拉不起 App 时再传 scheme: "androidamap"。
```

自驾默认 `mode: "drive"`。公交、步行、骑行分别用 `"bus"`、`"walk"`、`"ride"`。

### 3. 真实驾车里程（有高德 MCP 时）

```javascript
import { queryDrivingLegs } from "weizhi:travel-itinerary";

const legs = [
  { name: "乌市机场→天池", origin: "87.4742,43.9071", destination: "88.1273,43.8830" }
];
const distances = await queryDrivingLegs(legs);
// 写入 legs-distances.json，再汇总进 CSV / 攻略.md
```

### 4. 示意图 `route_map.png`

- 优先 `renderSchematicMap({ waypoints, outputPath: "<trip-slug>/route_map.png" })`（canvas 示意图，不依赖地图瓦片）。
- 需要交互地图页：`find_caps` → `web_lib.leaflet` + `points.json` 薄壳，不要手写整页业务 HTML。

### 5. 事实与价格

- 门票、封路（如独库）、旺季房价：用 `web_search`，结论写入 `sources.md` 和攻略正文，带**检索日期**。
- 数值不确定时写区间并注明「出发前再核实」。

### 6. 文档

- 主文 `攻略.md`：线路概述、逐日、住宿策略、预算、风险预案。文中的导航链接同样用 `appRouteUri` 的原文。
- 要 Word：`find_caps` → `weizhi:docx` 的 `markdownToDocx`。

## 编排建议

- 同一段 `run_js`（`jobs/trip-build.js`）串行：读 `trip.json` → `appRouteUri`（按天）→ 有 MCP 再 `queryDrivingLegs` → 写 JSON/CSV → `renderSchematicMap`。
- 外层不要拆成多个工具接力；脚本里 API 报错时先 `find_caps` 查相关能力，按示例改脚本。

## 对用户怎么说

用日常语言总结「去哪几天、每天住哪、有哪些表和图」；文件用 `[攻略](<trip-slug>/攻略.md)` 形式链接。每条高德路线单独一行给出 `amapuri://…` 原文，方便在手机上点进高德 App。
