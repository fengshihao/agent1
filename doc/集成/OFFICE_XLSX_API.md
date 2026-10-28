# xlsx.js — 工作簿模型 + 读写

`weizhi:xlsx` / `xlsx-build` / `xlsx-raw` 走云端 registry（`artifact-cdn` pack + upload）。`run_js` 前 `WeizhiPackageSync.beforeEval` 按 `import "weizhi:xlsx"` ensure。集成测试 fixture：`weizhi-bridge/src/test/resources/weizhi-test-packages/`。

Agent 发现面：云端 registry 写入 find_caps 的 `xlsx.*` 卡；Skill `xlsx` 靠 `load_skill` 经 registry 云装。

## 加载

```javascript
import { Workbook, readXlsx, renderXlsx, csvToXlsx } from "weizhi:xlsx";
import { buildXlsx } from "weizhi:xlsx-build";
```

## 读与改（保真）

`readXlsx` 打开的工作簿 `save` 时走**保真改写**（1.2.0）：只替换被编辑单元格的 XML 与 mergeCells/dimension，未编辑格的样式索引、公式缓存、列宽、冻结窗格、原图表与其它 parts 逐字节保留。不支持：对已打开文件 `addChart`（报错）、新增/删除/重命名 sheet。

**日期可读化（1.4.0，默认开启）**：日期在 xlsx 里是数字序列值 + 样式表 numFmt；`readXlsx` 按 style → numFmt 判定后还原成 `"2026-10-27"` / `"2026-10-27 14:30:00"` / 纯时间 `"06:00:00"`（`type:"date"`）。内置日期格式 ID（14-22/27-36/45-47/50-58）与自定义 formatCode 均识别；`{dates: false}` 回到原始序列数字。写日期暂不支持样式注入——需要原生日期格式时写入后由用户在 Excel 中设定格式，或走 `xlsx-raw`。

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

## 多级表头与透视表（1.5.0 起）

```javascript
// 中国式合并表头展开：每列完整标签 + 数据起始格（行数按纵向合并自动探测，可显式指定）
sh.effectiveHeaders();
// [{ col:"A", label:"上半年 > 销售额", path:["上半年","销售额"], dataRef:"A3", headerRows:2 }, ...]
sh.effectiveHeaders({ headerRows: 2 });

// 透视表结构读回（数据值本身已在普通单元格里）：
sh.getPivotTables();
// [{ name, dataCaption, fields:["区域","产品","季度","销售额"],
//    rowFields:["区域"], colFields:["季度"], pageFields:["产品"],
//    dataFields:[{name:"求和项:销售额", field:"销售额", subtotal:"sum"}] }]
```

## 边界

- 仅 `.xlsx`
- 新建走全量生成（`Workbook.create` 无 sourceDir）；`readXlsx → save` 走保真 patch
- 公式写入、不求值；图表只支持从零 build
- 深度改 OOXML 用 `weizhi:xlsx-raw`：`unpackXlsx` / `packXlsx` / `validateXlsx`
