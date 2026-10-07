# PPT（pptx.js）— Agent1 脚本

**给人读的 API：** [OFFICE_PPTX_API.md](./OFFICE_PPTX_API.md)

脚本与 `pptx` Skill 在本仓库。Weizhi 不附带 `pptx.js`。

---

## 约定

| 项 | 位置 |
|----|------|
| 脚本真源 | `agent_core/.../catalog/scripts/pptx.js`、`pptx-build.js` |
| Android 副本 | `android_agent/app/src/main/assets/office/` |
| Skill | `agent-home/skills/pptx/SKILL.md`（随 core 内置） |
| 调用卡 | `office-api-cards.jsonl` 的 `pptx.render` / `pptx.build` |
| 调用 | `import { renderPptx } from "pptx.js"` 或 `buildPptx` from `pptx-build.js` |
| 测试 | `PptxOfficeIntegrationTest`（需 `libweizhijni`） |

与 Word 共用 `OfficeCatalogScripts`。见 [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)。
