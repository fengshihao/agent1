# Word 预览图表

> 状态：**设计** · 范围：docx 预览里把 `c:chart` 画出来 · 不做 DrawingML 组合图形

## 问题

`docx-preview` 只画 `pic` 图片。正文里的图表是 `a:graphicData` 指向 `word/charts/chartN.xml`，库直接丢掉，页面上留下一块空白。示例文档里的那张是分组柱状图，分类和数值已经缓存在图表 XML 里，没有外挂工作簿。

另开一个 WebView 画完再截图不采用。预览页本身就是 WebView，截图在系统 WebView 上经常是空白，还要来回传图。

## 决策

- 在现有 docx 预览页里，用 **ECharts** 画到图表占位的位置。
- 数据只读图表部件里的 **缓存**（`c:numCache` / `c:strCache`）。没有缓存、只有公式或外链工作簿的图，占位上写「图表数据未嵌入」，不发请求去拉。
- ECharts 与 `docx-preview` 一样走 host-web CDN，不打进 APK。
- 第一期只做平面图：柱状 / 条形、折线、面积、饼图、散点、组合图（同一图里不同序列类型）、K 线（`stockChart`）。
- 三维图不进第一期。`echarts-gl` 只能近似，对不齐 Word 的立体柱和墙面。

## 数据从哪来

渲染前壳子里已经有整份 docx 的字节，JSZip 也在。

1. 读 `word/charts/chartN.xml`，以及 `word/_rels/document.xml.rels` 里 `chart` 关系。
2. 每条 `c:ser` 取：
   - 名称：`c:tx`
   - 分类：`c:cat` 的 `strCache` 或 `numCache`
   - 值：`c:val` 的 `numCache`
   - 图表类型：父节点 `c:barChart` / `c:lineChart` / `c:pieChart` / `c:scatterChart` / `c:areaChart` / `c:stockChart`
3. 柱状方向看 `c:barDir`（`col` 竖柱，`bar` 横条），分组看 `c:grouping`（`clustered` / `stacked` / `percentStacked`）。
4. 占位尺寸用 `wp:extent` 的宽高（EMU 转成 px），不要用图表自己的默认大小。

## 画到哪

`docx-preview` 对图表不会生成 `img`。渲染结束后，按文档顺序找空的图表占位（有尺寸、没有图片、对应上面解析出的第 n 张图），放一个同尺寸的容器，`echarts.init` 后 `setOption`。

页面已经按屏宽缩放。图表画在页内，跟着整页一起缩放，不再单独做一次截图。

## 类型对应

| Word | ECharts | 第一期 |
|------|---------|--------|
| `barChart` + `barDir=col` | `bar` | 做 |
| `barChart` + `barDir=bar` | `bar` + `yAxis` 为类目 | 做 |
| `grouping=stacked` / `percentStacked` | `stack` / 百分比 | 做 |
| `lineChart` | `line` | 做 |
| `areaChart` | `line` + `areaStyle` | 做 |
| `pieChart` / `doughnutChart` | `pie` | 做 |
| `scatterChart` | `scatter` | 做 |
| 多个 `*Chart` 并列 | 同一 `option.series` 里混类型 | 做 |
| `stockChart` | `candlestick` | 做 |
| `surfaceChart` / `bar3DChart` 等三维 | `echarts-gl` | 不做 |

坐标轴标题、图例有缓存文本就带上。渐变、三维光照、数据表（`c:dTable`）先不画。

## 不做

- DrawingML 组合图形（`wordprocessingGroup`）、自定义形状路径。那不是图表。
- 把图表栅格化成 PNG 再塞回文档。
- 没有缓存数值时自己算 Excel 公式。
