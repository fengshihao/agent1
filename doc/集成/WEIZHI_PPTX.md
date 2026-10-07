# Weizhi PPT（pptx.js）— Agent1 集成入口

**真源文档（Weizhi 仓库）：**  
https://github.com/fengshihao/weizhi/blob/master/docs/pptx-js-api.md  

**调用卡真源：** weizhi `docs/api-cards.jsonl` → Agent1 `weizhi-api-cards.jsonl`（`pptx.render` / `pptx.build`）

---

## Agent1 已做 / 约定

| 项 | 位置 |
|----|------|
| 拷贝 `pptx.js` / `pptx-build.js` 到 agentRoot | `OfficeCatalogScripts` → `shared/catalog/scripts/`；APK `assets/office/` |
| 调用方式 | `execute_script` 里 `import { renderPptx } from "pptx.js"` 或 `buildPptx` from `pptx-build.js` |
| 系统提示 | 只指向 `capability_search`；细则在调用卡，不打独立 Markdown 手册 |
| 测试 | `PptxOfficeIntegrationTest`（需 `libweizhijni`） |

## 构建

- 脚本已提交在 `agent_core/src/main/resources/agent-home/catalog/scripts/`；本地可 `./sync-weizhi.sh` 后从 `AGENT1_WEIZHI_REPO/assets/office/` 覆盖（若 catalog 目录里尚无非空副本）。
- 与 docx 相同：见 [WEIZHI_DOCX.md](./WEIZHI_DOCX.md) 的 Weizhi JNI 构建说明。
