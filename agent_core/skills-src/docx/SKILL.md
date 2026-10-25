---
name: docx
version: 1.0.0
description: 用户要 Word、docx、公文、报告、合同、说明书等文档时使用。用 run_js 调用 weizhi:docx / weizhi:docx-build；长文先写 Markdown 再转 docx；改已有文件用 readDocx + grep，不要手写 OOXML。
category: office.document
catalog_line: 写 Word / docx、Markdown 转 Word、改已有 docx 时用。
depends_weizhi: docx,docx-build
tags: docx,word,文档,报告,公文,合同,markdown
---

# 生成与修改 Word（docx）

## 开工

1. 先 **`load_skill`（`docx`）** 读本正文。
2. 按下方 **选路** 决定写法；需要 API 细节时再 `find_caps`（`docx.*` 四张卡：`markdownToDocx`、`create`、`readEdit`、`raw`）。
3. 用 **`run_js`** 一段脚本完成渲染/保存；产物写在 **当前会话 `workspace/`**（如 `out/报告.docx`）。
4. 用户要打开文件：Android 用 `android.intent.start({ action: "view", path })`；桌面告知绝对路径或让用户从工作区打开。

**禁止**：用 `write_file` 直接写二进制 `.docx`；不要凭空造 OOXML / zip；除非走 **raw 逃生舱**，否则不要改 `word/document.xml`。

---

## 选路（默认顺序）

| 场景 | 做法 | 原因 |
|------|------|------|
| 新文档、多章节、用户可能改稿 | **先 `write_file` 写 `*.md`**，再 `markdownToDocx` | Markdown 是可编辑源；与 PPT「先 deck.html」同理念 |
| 新文档、结构简单、表格/块列表明确 | **`buildDocx` / `buildDoc` + `renderDocx`** | 一次 `run_js` 出文件，不必中间 md |
| 用户给了已有 `.docx` 或要改上一版 | **`readDocx` → `textView` / `grep` → 改 → `save`** | Builder **不负责**编辑已有文件 |
| API 不够、页眉页脚等极端版式 | **`docx-raw` 解包改 XML + `validateDocx` + `packDocx`** | 最后手段；校验不保证 Word 100% 接受 |

不确定时：**默认 Markdown 路径**（`outline.md` 或 `report.md` → `out/report.docx`）。

---

## 路径 A：Markdown → Word（推荐长文）

1. 在 workspace 写 Markdown（标题 `#`…`###`、列表、GFM 表格、引用、代码块、`- [ ]` 任务、图片 `![](相对路径.png)`）。
2. 渲染：

```javascript
import { markdownToDocx } from "weizhi:docx";

export default markdownToDocx({
  inputPath: "report.md",           // 与 markdown 字符串二选一
  outputPath: "out/report.docx",
  title: "可选封面标题",
  defaultStyle: { font: "宋体", sizePt: 12, lineSpacing: 1.5 },
  headingStyles: {
    1: { sizePt: 22, bold: true },
    2: { sizePt: 16, bold: true },
    3: { sizePt: 14, bold: true },
  },
});
```

3. 用户要改内容：**改 `.md` 再跑一遍**；不要手改 docx 除非走路径 C。

等价：`documentFromMarkdown` + `renderDocx`（适合 md 已在内存、不想落盘中间文件）。

---

## 路径 B：从零程序化写（短报告、固定模板）

```javascript
import { buildDocx } from "weizhi:docx-build";

export default buildDocx(
  { title: "季度复盘", defaultStyle: { font: "宋体", sizePt: 12, lineSpacing: 1.5 } },
  (b) => {
    b.h1("摘要")
      .p("第一段正文。")
      .bullet("要点一")
      .bullet("要点二")
      .h2("数据")
      .table(["指标", "数值"], ["收入", "+12%"], ["留存", "持平"])
      .style({ bold: true })
      .p("这是加粗段落。");
  },
  "out/q3.docx"
);
```

**JSON 块列表**（适合工具传参）：

```javascript
import { buildDoc, renderDocx } from "weizhi:docx-build";

const doc = buildDoc({ title: "说明" }, (b) =>
  b.blocks([
    { type: "h1", text: "标题" },
    { type: "p", text: "正文" },
    { type: "table", rows: [["列A", "列B"], ["1", "2"]] },
    { type: "image", path: "assets/chart.png", width: 400, height: 300 },
  ])
);
export default renderDocx(doc.doc(), "out/from-blocks.docx");
```

Builder 块类型：`h1`/`h2`/`h3`、`p`、`bullet`、`task`（`checked`）、`quote`、`code`、`table`、`image`（workspace 相对路径）。

底层也可用 `Document.create` + `addHeading` / `addParagraph` / `addBullet` + `renderDocx`（与 Builder 等价，择一即可）。

---

## 路径 C：阅读并修改已有 docx

把 docx 当成「带 `blockIndex` 的块序列」（类似代码库的 read / grep / edit）：

```javascript
import { readDocx } from "weizhi:docx";

const doc = readDocx("in/contract.docx");
const view = doc.textView();                    // Agent 友好：[index] type: 正文
const { matches } = doc.grep("甲方");             // 定位块

doc.replaceInBlock(matches[0].blockIndex, "旧公司名", "新公司名");
doc.setBlockStyle(matches[0].blockIndex, { font: "宋体", sizePt: 14, bold: true });
// doc.replaceAll("短语", "新短语", { type: "paragraph" });
// doc.setBlockText(3, "整段重写（支持 **粗体** 等 inline）");

export default doc.save("out/contract-edited.docx");
```

| 能力 | 说明 |
|------|------|
| `plainText` / `textView` / `headings` / `filter` | 观察结构 |
| `grep` / `grepStyles` | 按正文或样式找块 |
| `replaceInBlock` / `replaceAll` / `setBlockText` | 改字 |
| `setBlockStyle` / `getBlockStyle` | 块级字体、字号、加粗、行距 |
| `toMarkdown()` | 有损导出，复杂版式会简化 |

**限制**：表格/图片块不要用 `setBlockText`；复杂页眉页脚、主题色、段前后距不支持；读回的图片可能是占位。改不动时再考虑路径 D。

---

## 路径 D：OOXML 逃生舱（`weizhi:docx-raw`）

仅当 Document / readDocx API 明显不够：

```javascript
import { unpackDocx, packDocx, validateDocx } from "weizhi:docx-raw";
import fs from "fs";

const { dir } = unpackDocx("in/report.docx", { workDir: "tmp/ooxml" });
const xmlPath = dir + "/word/document.xml";
let xml = fs.readFileSync(xmlPath).toString();
xml = xml.replace("OLD", "NEW");
fs.writeFileSync(xmlPath, xml);

const v = validateDocx({ dir });
if (!v.ok) throw new Error(JSON.stringify(v.errors));
export default packDocx(dir, "out/raw-edited.docx");
```

`validateDocx` 只查 zip 结构 + XML 良构，**不**保证 Word 排版语义正确。

---

## 样式与内容约定

- **中文正文默认**：`宋体` 12pt、行距 1.5；标题层次用 `headingStyles` 或 Markdown `#`（写入 Word Heading1–3）。
- **英文/混排**：可改 `defaultStyle.font`（如 `Calibri`），保持 `sizePt` 一致。
- **图片**：路径相对 workspace；Markdown 或 `addImage` / `block.image`。
- **交付**：回复里写清 `outputPath`、字节结果（`renderDocx` / `save` 返回的 `bytes`）；大文档说明源文件是 `.md` 还是单次 `buildDocx`。

---

## 与 PPT Skill 的分工

- 演示文稿、幻灯片 → **`pptx`** 或 **`html2pptx`**（设计感 deck）。
- 可打印报告、合同、说明、路书 Word 版 → **本 Skill**（`docx`）。

路书类任务若已用 `travel-itinerary`，其 Word 产物仍走本 Skill 的 Markdown 或 `buildDocx` 路径。

---

## 能力边界（用户问起再细说）

- **没有** docx → PDF（需外部转换）。
- **没有**「打开已有 docx 增量补丁」以外的第二种编辑模型；编辑一律 `readDocx` 块 API 或 raw。
- 人读 API 全文：`doc/集成/OFFICE_DOCX_API.md`（仓库文档，非 workspace）。
