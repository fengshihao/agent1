# PPT（pptx.js）— Agent1 脚本

**给人读的 API：** [OFFICE_PPTX_API.md](./OFFICE_PPTX_API.md)

脚本与 `pptx` Skill 在本仓库。Weizhi 不附带 `pptx.js`。

---

## 约定

| 项 | 位置 |
|----|------|
| 脚本真源 | `agent_core/.../weizhi-packages/pptx/`、`pptx-build/`（版本在各自 manifest） |
| Android 分发 | 云端 registry 动态安装（APK 不预装，20-A 收口） |
| Skill | `agent-home/skills/pptx/SKILL.md`（随 core 内置） |
| 调用卡 | `office-api-cards.jsonl` 的 `pptx.render` / `pptx.build` |
| 调用 | `import { renderPptx } from "weizhi:pptx"` 或 `buildPptx` from `weizhi:pptx-build` |
| 测试 | `PptxOfficeIntegrationTest`（需 `libweizhijni`） |

与 Word 一样走 Weizhi Package。见 [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)。
