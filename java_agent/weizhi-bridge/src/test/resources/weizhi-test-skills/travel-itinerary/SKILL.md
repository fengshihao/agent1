---
name: travel-itinerary
version: 1.6.0
description: 用户要几日自驾或行程规划，并希望带图片、可缩放路线图、精美可交互页面时使用。直接写单页 HTML。不含机酒下单。
category: travel.roadbook
catalog_line: 几日自驾：照片封面、按天分色的曲线路线图、目录和出发清单。直接写 HTML。
depends_mcp: gaode
tags: 旅游,行程,攻略,自驾,路书,地图,高德,百度,HTML,路线图,itinerary,travel
---

# 自驾行程页

用户要的是一页能打开的 HTML。头图、曲线路线、按天切换和出发清单都要做足。

出发城市、天数、季节缺了就 `ask_user` 问一轮再写。预算用档位 `single_choice`，不要用跨度很大的 `rating`。

## 本 Skill 自带资源（必须用）

zip 里有 `references/` 和 `scripts/`。先并行 `load_skill` 两份，读不到就报错并停，不要靠记忆手写导航函数或直线示意图凑合。

1. `load_skill`（path=`references/roadbook-sample.html`）读设计稿。照它的版式、配色、地图画法和动效写，把数据、图片、地点换成这次行程。设计稿就是验收标准：胶囊地名、白边粗曲线、按天配色、线上的公里徽章、指针式罗盘、路线流动动画。
2. `load_skill`（path=`scripts/roadbook.js`）读工具源码，`write_file` 到 `jobs/roadbook.js`，再在 `run_js` 里 import（`run_js` 只认 workspace 路径）：

```javascript
import { appRouteUri, queryDrivingLegs } from "./roadbook.js";

appRouteUri({ waypoints, mode: "drive" });
await queryDrivingLegs([{ name, origin: "lng,lat", destination: "lng,lat" }]);
```

- **要用**：`appRouteUri`（高德 App 导航）、`queryDrivingLegs`（真实公里/分钟，一段脚本传入多段，不要每段单独一轮工具）。
- **不要跑**：`scripts/build-roadbook.js`、`renderSchematicMap`。那是直线草稿示意图，不是本页要的曲线路书。

百度导航本包没有现成函数，用文末 `baiduRouteUri`，链到 `<a href>`。

## 一批做完，不要拆成十几轮

坐标、里程、搜图、下载都尽量各用**一轮**做完：

- 坐标：`find_caps` 后在**一段** `run_js` 里 `$mcp.gaode.maps_geo` / `maps_text_search`，`Promise.all` 一批点，不要一个点一轮。
- 里程：一次 `queryDrivingLegs` 传入当天所有路段。
- 图片：同一轮并行多次 `image_search`（每个景点一次调用即可并行）；下载用一段 `run_js`：`await $tools.files_download({ items:[{url, expect:"image", path}], dir })`，不要一张图一轮、不要 bash `cd`/`cp` 整理。
- 小改 HTML 用 `edit_file`，不要用 `run_js` 里的 Node `fs.readFileSync`。

## 页面结构

一个页面，顶部一条目录，点了滚到对应的节。不要把多天行程纵向堆成一长条，也不要拆成多个网页。

目录四项，滚过封面后吸顶：**路线**、**日程**、**花费**、**清单**。

- **路线**：全程曲线图。
- **日程**：一天一个 Tab。切天时整块内容横向滑入并淡入，不要瞬间换掉。
- **花费**：预算和门票。窄屏用卡片，不要硬挤表格。
- **清单**：出发前要带、要订、要查的勾选列表，勾选记在 `localStorage`。

## 头图和每天的图

第一屏的头图是照片，铺满，标题压在图上。不要用纯色或渐变顶替头图。封面选最出片的一张，不必是第一天。

每天的大图可以左右换。换图用交叉淡化加轻微位移或缩放，大约半秒，不要硬切。大水印、人物摆拍、和景点无关的图丢掉。

图片落到 `<slug>/assets/`，页面用相对路径。不要编 URL，不要外链图床。

## 路线图

按经纬度投影，北在上。地图底色用柔和渐变，空白处画两三条浅色等高线增加质感。

每天一条**平滑曲线**（多途经点用三次贝塞尔穿过，两点段带一点弧度），颜色各不相同，白色描边包住彩色主线，加轻投影，线要粗（8px 上下）。当前天的线上叠一层白色虚线流动动画。地名用圆角胶囊（白底、当天高亮反色）。每段路旁放**公里徽章**：当天颜色的小胶囊、白字「xx km」，用碰撞检测避免盖住地名。图的一角放指针式罗盘（北针用强调色），四向标 北/东/南/西。

图下是图例行：色点＋日期＋当天公里，点图例切到那一天。另留放大、缩小、看全程三个按钮。

单指拖动平移，双指缩放。点一个地名，跳到那一天，并把地图推近、高亮那天的曲线。

坐标没有就查高德。里程能查到就写真实公里，查不到就估算并在页面上注明。不要 Leaflet 瓦片，不要 jsDelivr 或 GSAP。

## 导航按钮

每天并排两个按钮，途经点都带上。装了哪个地图就点哪个。高德用 `appRouteUri`。百度用同一套高德坐标，声明 `coord_type=gcj02`，纬度在前，途经点最多 5 个：

```javascript
function baiduRouteUri(waypoints) {
  const enc = encodeURIComponent;
  const s = waypoints[0];
  const e = waypoints[waypoints.length - 1];
  const via = waypoints.slice(1, -1).slice(0, 5);
  let url = "baidumap://map/direction?coord_type=gcj02&mode=driving&src=andr.agent1.roadbook";
  url += "&origin=name:" + enc(s.name || "起点") + "|latlng:" + s.lat + "," + s.lng;
  url += "&destination=name:" + enc(e.name || "终点") + "|latlng:" + e.lat + "," + e.lng;
  if (via.length) {
    url += "&viaPoints=" + enc(JSON.stringify({
      viaPoints: via.map((p) => ({ name: p.name || "途经", lat: String(p.lat), lng: String(p.lng) }))
    }));
  }
  return url;
}
```

## 清单

按这次行程写具体项，不要只写「带好行李」。至少覆盖证件、预约、路况、衣物、车上备品、药品。每项一个勾选框。

## 交付

口语说清几天、每天到哪。给 `[路书](<slug>/路书.html)`。不做 Word、订票下单，也不要假装能订到。
