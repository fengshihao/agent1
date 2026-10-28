# chart.js — 图表 → PNG / SVG

`weizhi:chart` 走云端 registry 交付（不进 jar/APK）。集成测试 fixture：`weizhi-bridge/src/test/resources/weizhi-test-packages/chart/`。

Agent 发现面：云端 registry 写入 find_caps 的 `chart.render`。

## 加载

```javascript
import { renderChart } from "weizhi:chart";
export default await renderChart({
  lib: "echarts",   // "echarts"（统计图，spec）| "mermaid"（图示，text）
  spec: { xAxis: { type: "category", data: ["1月","2月","3月"] },
          yAxis: { type: "value" },
          series: [{ type: "bar", data: [10, 20, 15] }] },
  width: 800,       // 默认 800×400，100–4096
  height: 400,
  scale: 2,         // PNG 栅格倍率 1–3，默认 2
  format: "png",    // "png" | "svg"
  output: "out/趋势.png"
});
// { ok, outputPath, width(=width*scale), height, format, bytes }
```

## 两条「出图」车道

| 场景 | 用法 |
|---|---|
| **Excel 内可编辑图表** | `weizhi:xlsx` 的 `addChart`（原生 bar/line/pie，双击可改数据源） |
| **报告/文档插图**（docx `addImage`、pptx、xlsx 旁路交付） | 本包：echarts 统计图 / mermaid 流程图 → PNG/SVG |

## 机制与边界

- 渲染在 `webview_exec`（桌面 Chromium CDP / Android 系统 WebView）：页面内动态加载 CDN `echarts@5.5.1` / `mermaid@11.4.0`（与 `web-libs` 卡片版本一致）→ SSR SVG →（png）canvas 栅格 → 落盘校验 PNG 魔数
- **需要网络**；无 WebView 环境会明确报错
- spec 建议带 `animation: false`（SSR 不需要动画）
- mermaid 用 `text` + `format:"svg"` 可保矢量；中文标签直接支持

## 回归

`ChartScriptTest`（weizhi-bridge）：mock webview 覆盖编排/参数校验/双格式；`renderChartRealEchartsPngAndMermaidSvgViaCdp` 为真实 e2e（本机 Chromium + CDN，`AGENT1_CHROMIUM_PATH` 可指定浏览器）。
