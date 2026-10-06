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
| 调用方式 | `execute_script` 里 `import { markdownToDocx } from "docx.js"`。无外层 `docx_*` 工具 |
| 系统提示 | 只指向 `capability_search`；调用卡来自微智 `docs/api-cards.jsonl`（打进 core 资源，不打 Markdown 手册） |
| 测试 | `DocxOfficeIntegrationTest`、Android `ChatTranscriptFormattingTest` |
| 用户打开 docx | 聊天附件仍走 `WorkspaceFileActions`；脚本用 `android.intent.start({ action: "view", path })`（`launchIntent = true`，FileProvider 已声明） |

## Issue #8 checklist（Agent1）

- [x] `setScriptFolder` 含 `docx.js` + `docx-raw.js`
- [x] workspace 与 `setFsRoot` 一致（既有 Weizhi 行为）
- [x] Word 走脚本 `import { markdownToDocx } from "docx.js"`（`validateDocx` 来自 `docx-raw.js`），不注册外层工具
- [x] 系统提示不写 Word 细则；`capability_search` 返回 `markdownToDocx` 等调用卡，APK 不带 `office-docx.md`
- [x] App 内打开/分享 workspace 附件（`.docx` 等）

## 系统提示 / 能力

- 优先 `docx.js`（`markdownToDocx` / read→grep→save）；高级 OOXML 用 `docx-raw.js` + `validateDocx`。
- **不要**使用已删除的 Java `host.office.*`。
- 脚本：`import` ES module，与 [weizhi office-js-api.md](https://github.com/fengshihao/weizhi/blob/master/docs/office-js-api.md) 一致。

## 构建

- `docx.js` / `docx-raw.js` 已提交在 `agent_core/src/main/resources/agent-home/catalog/scripts/`；运行时 `OfficeCatalogScripts` 还会从 `AGENT1_WEIZHI_REPO/assets/office/` 覆盖拷贝（若存在）。
- 本地需 `./sync-weizhi.sh` 且 `./weizhi/scripts/build.sh` 以跑 `:weizhi-bridge` 集成测。
