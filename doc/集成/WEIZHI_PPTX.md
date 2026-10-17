# PPT（pptx.js）— Agent1 脚本

**给人读的 API：** [OFFICE_PPTX_API.md](./OFFICE_PPTX_API.md)

脚本与 `pptx` Skill 在本仓库。Weizhi 不附带 `pptx.js`。

---

## 约定

| 项 | 位置 |
|----|------|
| 脚本真源 | `agent_core/.../weizhi-packages/pptx@1.1.0/`、`pptx-build@1.0.0/` |
| Android 副本 | `android_agent/app/src/main/assets/weizhi-packages/` |
| Skill | `agent-home/skills/pptx/SKILL.md`（随 core 内置） |
| 调用卡 | `office-api-cards.jsonl` 的 `pptx.render` / `pptx.build` |
| 调用 | `import { renderPptx } from "weizhi:pptx"` 或 `buildPptx` from `weizhi:pptx-build` |
| 测试 | `PptxOfficeIntegrationTest`（需 `libweizhijni`） |

与 Word 一样走 Weizhi Package。见 [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)。
