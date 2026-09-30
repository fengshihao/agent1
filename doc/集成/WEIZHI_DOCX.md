# Weizhi Word（docx.js）— Agent1 集成入口

**真源文档（Weizhi 仓库）：**  
https://github.com/fengshihao/weizhi/blob/master/docs/AGENT1_DOCX_INTEGRATION.md  

**跟踪 Issue：** https://github.com/fengshihao/weizhi/issues/8  
**标题层次（weizhi#11 / PR #12 已合并 master → Agent1 #34）：** catalog 与 `assets/office/docx.js` 对齐 weizhi `assets/office/docx.js`；Markdown `#` 标题带默认 H1/H2/H3 字号与 `word/styles.xml`。

---

## Agent1 已做 / 约定

| 项 | 位置 |
|----|------|
| 拷贝 `docx.js` / `docx-raw.js` / `docx-build.js` 到 agentRoot | `OfficeCatalogScripts` → `shared/catalog/scripts/` |
| Weizhi `setScriptFolder` | 桌面 + Android（`AndroidOfficeCatalogSync`） |
| 工具 | `docx_markdown_to_word`、`docx_inspect`、`docx_read_grep_edit`、`docx_raw_edit` |
| 系统提示 | 用法摘要；**API** → `read_agent_doc` → `docs/system/office-docx.md` |
| 测试 | `DocxOfficeIntegrationTest`、Android `ChatTranscriptFormattingTest` |
| 用户打开 docx | `WorkspaceFileAttachments` + `WorkspaceFileActions`（FileProvider） |

## Issue #8 checklist（Agent1）

- [x] `setScriptFolder` 含 `docx.js` + `docx-raw.js`
- [x] workspace 与 `setFsRoot` 一致（既有 Weizhi 行为）
- [x] `@Tool` docx 四件套 + `validateDocx` 路径（raw 工具）
- [x] 系统提示：用法 + 指向 `office-docx.md`
- [x] App 内打开/分享 workspace 附件（`.docx` 等）

## 系统提示 / 能力

- 优先 `docx.js`（`markdownToDocx` / read→grep→save）；高级 OOXML 用 `docx-raw.js` + `validateDocx`。
- **不要**使用已删除的 Java `host.office.*`。
- 脚本：`import` ES module，与 [weizhi office-js-api.md](https://github.com/fengshihao/weizhi/blob/master/docs/office-js-api.md) 一致。

## 构建

- `docx.js` / `docx-raw.js` 已提交在 `agent_core/src/main/resources/agent-home/catalog/scripts/`；运行时 `OfficeCatalogScripts` 还会从 `AGENT1_WEIZHI_REPO/assets/office/` 覆盖拷贝（若存在）。
- 本地需 `./sync-weizhi.sh` 且 `./weizhi/scripts/build.sh` 以跑 `:weizhi-bridge` 集成测。
