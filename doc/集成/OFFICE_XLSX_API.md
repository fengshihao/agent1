# xlsx.js — 工作簿模型 + 读写

真源：`agent_core/src/main/resources/agent-home/weizhi-packages/xlsx/`（及 `xlsx-build`；版本在 manifest）。

**交付**：云端 registry（`artifact-cdn` pack + upload）。**不**进 `WeizhiBundledPackages` 桌面预装列表；Android APK 本就不打 `weizhi-packages`。`run_js` 前 `WeizhiPackageSync.beforeEval` 按 `import "weizhi:xlsx"` ensure。

Agent 发现面：`office-api-cards.jsonl` 的 `xlsx.*`；Skill 真源 [agent1-skills `skills-src/xlsx`](https://github.com/fengshihao/agent1-skills)（`sync-skills-bundled` **跳过**，靠 `load_skill` 云装）。

## 加载

```javascript
import { Workbook, readXlsx, renderXlsx, csvToXlsx } from "weizhi:xlsx";
import { buildXlsx } from "weizhi:xlsx-build";
```

## 读与改（保真）

`readXlsx` 打开的工作簿 `save` 时走**保真改写**（1.2.0）：只替换被编辑单元格的 XML 与 mergeCells/dimension，未编辑格的样式索引、公式缓存、列宽、冻结窗格、原图表与其它 parts 逐字节保留。不支持：对已打开文件 `addChart`（报错）、新增/删除/重命名 sheet。

```javascript
const wb = readXlsx("in/sales.xlsx");
const sh = wb.sheet("明细"); // 或 sheet(0)
sh.summary();
sh.head(20);
sh.grep("华北");
sh.set("B2", 1200);
sh.setFormula("D2", "B2*(1+C2)"); // 可选第三参缓存值
sh.merge("E5:F6");
wb.save("out/sales-edited.xlsx");
```

## 新建

```javascript
buildXlsx("out/budget.xlsx", (b) => {
  b.sheet("汇总")
    .headers(["项目", "金额"])
    .rows([["差旅", 1000]])
    .formula("C2", "B2*1.13", 1130);
});
```

## 图表与合并单元格（1.1.0 起）

```javascript
// 原生 bar / line / pie 图表（写 chart + drawing parts，Excel 可编辑）
sh.addChart({
  type: "bar",
  title: "月度对比",
  anchor: "E2",              // 可选 to；默认 8 列 × 15 行
  series: [{ name: "销售额", categories: "A2:A4", values: "B2:B4" }],
});

// 合并单元格：merge / unmerge / getMerges；readXlsx 读回自动保留
sh.merge("A1:C1");
```

## 公式求值与自校验（1.3.0 起）

```javascript
sh.setFormula("D2", "B2*(1+C2)");  // 省略缓存值 → 自动求值写 <v>；失败降级（Excel 打开重算）
sh.evaluate("D2");                  // { ok, value, valueType } / { ok:false, error }
sh.evaluate("E1");                 // 循环引用、未支持函数等会给出明确 error
```

支持：算术/比较/`&`、同表区域引用、`SUM/AVERAGE/MIN/MAX/COUNT/COUNTA/PRODUCT/IF/IFERROR/AND/OR/NOT/ROUND/ABS/SQRT/POWER/MOD/INT/CONCAT/CONCATENATE/LEN/LEFT/RIGHT/MID/UPPER/LOWER/TRIM`。跨表引用与未支持函数降级。

## 图表读回（1.3.0 起）

```javascript
sh.getCharts();
// [{ type: "bar", title: "月度", source: "read",
//    series: [{ name, nameRef, categories, values }] }]
```

保真 save 后原图表 parts 逐字节保留；对已打开文件 `addChart` 仍会报错（用 `buildXlsx` 新建或 `xlsx-raw`）。

## 边界

- 仅 `.xlsx`
- 新建走全量生成（`Workbook.create` 无 sourceDir）；`readXlsx → save` 走保真 patch
- 公式写入、不求值；图表只支持从零 build
- 深度改 OOXML 用 `weizhi:xlsx-raw`：`unpackXlsx` / `packXlsx` / `validateXlsx`
