---
name: travel-itinerary
description: 用户要旅游路书、几日游行程攻略、自驾环线、景点食宿表、路线图或高德导航链接时使用。关键词：旅游 行程 攻略 自驾 路书 几日游 地图 里程 食宿 itinerary。不含机票火车酒店下单预订。
tags: 旅游,行程,攻略,自驾,路书,地图,高德,里程,食宿,itinerary,travel
---

# 旅游路书（行程规划交付包）

## 范围（硬边界）

| 做 | 不做 |
|----|------|
| 逐日行程、驾驶里程/耗时、景点与三餐建议、住宿区域与参考价、预算表、示意图路线图、高德个人地图 URI | **机票 / 火车 / 酒店下单、锁价、支付、代订** |
| `web_search` 查门票/封路/旺季房价等**公开信息**，写入攻略并标注来源日期 | 假装能订到房或买到票 |

大交通（飞哪、坐哪趟车）只在路书里写**建议班次类型与时段**；用户自行在 OTA / 12306 / 酒店 App 预订。

## 开工协议

1. 信息不全时：先用 `ask_user` 对齐出发城市、日期、人数、预算档、自驾/公共交通、忌口或老人小孩约束，然后结束本轮。
2. 信息足够后：**第一个能力检索**用  
   `find_caps` query=`旅游 行程攻略 路书 自驾 travel-itinerary 高德 leaflet docx`  
   命中本 skill 后**严格按下列步骤**，不要另起一套即兴流程。
3. 需要算路、导航链接时：确认宿主已配置**高德 MCP**（`maps_direction_driving`、`maps_schema_personal_map`）。未配置则用文字里程估算并说明无法生成导航链接。

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
- 用 `buildLineList(waypoints)` 生成高德 `lineList` 所需相邻段。

### 2. 真实驾车里程（有高德 MCP）

```javascript
import { queryDrivingLegs, buildLineList, personalMapUri } from "weizhi:travel-itinerary";

const legs = [
  { name: "乌市机场→天池", origin: "87.4742,43.9071", destination: "88.1273,43.8830" },
  // …按 trip.json 逐段补齐
];
const distances = await queryDrivingLegs(legs);
// 写入 legs-distances.json，再汇总进 CSV / 攻略.md
```

### 3. 高德个人地图链接（原样给用户）

```javascript
const lineList = buildLineList([
  { name: "乌鲁木齐", lng: 87.6168, lat: 43.8256 },
  { name: "天山天池", lng: 88.1273, lat: 43.8830 },
  // …顺序与行程一致
]);
const uri = await personalMapUri({ orgName: "本次路书", lineList });
// 回复里直接展示 uri，不要改写为摘要
```

### 4. 示意图 `route_map.png`

- 优先 `renderSchematicMap({ waypoints, outputPath: "<trip-slug>/route_map.png" })`（canvas 示意图，不依赖地图瓦片）。
- 需要交互地图页：`find_caps` → `web_lib.leaflet` + `points.json` 薄壳，不要手写整页业务 HTML。

### 5. 事实与价格

- 门票、封路（如独库）、旺季房价：用 `web_search`，结论写入 `sources.md` 和攻略正文，带**检索日期**。
- 数值不确定时写区间并注明「出发前再核实」。

### 6. 文档

- 主文 `攻略.md`：线路概述、逐日、住宿策略、预算、风险预案。
- 要 Word：`find_caps` → `weizhi:docx` 的 `markdownToDocx`。

## 编排建议

- 同一段 `run_js`（`jobs/trip-build.js`）串行：读 `trip.json` → `queryDrivingLegs` → 写 JSON/CSV → `renderSchematicMap` → `personalMapUri`。
- 外层不要逐个工具试探；失败就改同一段脚本再跑。

## 对用户怎么说

用日常语言总结「去哪几天、每天住哪、有哪些表和图」；文件用 `[攻略](<trip-slug>/攻略.md)` 形式链接。导航链接单独一行给出，方便点开高德。
