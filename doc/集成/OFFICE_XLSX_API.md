# xlsx.js — 工作簿模型 + 读写

真源：`agent_core/src/main/resources/agent-home/weizhi-packages/xlsx/`（及 `xlsx-build`；版本在 manifest）。

**交付**：云端 registry（`artifact-cdn` pack + upload）。**不**进 `WeizhiBundledPackages` 桌面预装列表；Android APK 本就不打 `weizhi-packages`。`run_js` 前 `WeizhiPackageSync.beforeEval` 按 `import "weizhi:xlsx"` ensure。

Agent 发现面：`office-api-cards.jsonl` 的 `xlsx.*`；Skill 真源 [agent1-skills `skills-src/xlsx`](https://github.com/fengshihao/agent1-skills)（`sync-skills-bundled` **跳过**，靠 `load_skill` 云装）。

## 加载

```javascript
import { Workbook, readXlsx, renderXlsx, csvToXlsx } from "weizhi:xlsx";
import { buildXlsx } from "weizhi:xlsx-build";
```

## 读与改

```javascript
const wb = readXlsx("in/sales.xlsx");
const sh = wb.sheet("明细"); // 或 sheet(0)
sh.summary();
sh.head(20);
sh.grep("华北");
sh.set("B2", 1200);
sh.setFormula("D2", "B2*(1+C2)"); // 可选第三参缓存值
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

## 边界

- 仅 `.xlsx`
- `save` / `renderXlsx` **重写整包**（样式 / 原图表 / 宏可能丢失；单元格值、公式、合并单元格保留）
- 公式写入、不求值；图表**只写不读**，改已有文件时需重新 `addChart`
