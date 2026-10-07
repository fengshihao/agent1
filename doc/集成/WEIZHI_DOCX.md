# Word（docx.js）— Agent1 脚本

**给人读的 API：** [OFFICE_DOCX_API.md](./OFFICE_DOCX_API.md)

脚本、调用卡和回归都在本仓库。Weizhi 只提供 `runJs`、`fs`、`zip` 和 `setScriptFolder`，不再附带 `docx.js`。

---

## 约定

| 项 | 位置 |
|----|------|
| 脚本真源 | `agent_core/src/main/resources/agent-home/catalog/scripts/docx.js`（及 `docx-raw.js`、`docx-build.js`） |
| Android 副本 | `android_agent/app/src/main/assets/office/`（须与 catalog 保持一致） |
| 装进 agentRoot | `OfficeCatalogScripts.ensure` 从 classpath 拷到 `shared/catalog/scripts/`；Android 再由 `AndroidOfficeCatalogSync` 从 APK assets 写入 |
| 调用 | `execute_script` 里 `import { markdownToDocx } from "docx.js"`。无外层 `docx_*` 工具 |
| 调用卡 | `agent-home/capabilities/office-api-cards.jsonl`（`docx.*`） |
| 测试 | `DocxOfficeIntegrationTest`（需 `libweizhijni`） |
| 用户打开 docx | 脚本用 `android.intent.start({ action: "view", path })` |

改脚本时同时改 catalog 与 `assets/office` 两份，并改 `office-api-cards.jsonl` 里对应 `entry`。

## 系统提示

- 优先 `docx.js`（`markdownToDocx` / read→grep→save）；高级 OOXML 用 `docx-raw.js` + `validateDocx`。
- 不要使用已删除的 Java `host.office.*`。
