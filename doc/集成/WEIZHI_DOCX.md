# Weizhi Word（docx.js）— Agent1 集成入口

**真源文档（Weizhi 仓库）：**  
https://github.com/fengshihao/weizhi/blob/master/docs/AGENT1_DOCX_INTEGRATION.md  

**跟踪 Issue：** https://github.com/fengshihao/weizhi/issues/8  

---

## Agent1 已做 / 约定

| 项 | 位置 |
|----|------|
| 拷贝 `docx.js` / `docx-raw.js` 到 agentRoot | `OfficeCatalogScripts` → `shared/catalog/scripts/`（bootstrap + Weizhi `assets/office` 或 classpath） |
| Weizhi `setScriptFolder` | `WeizhiScriptEngineFactory`（桌面）；`WeizhiAndroidScriptEngineFactory` + `AndroidOfficeCatalogSync`（APK assets） |
| 工具 | `docx_markdown_to_word`（`DocxMarkdownToWordTool`），Weizhi 脚本启用且 office 就绪时注册 |
| 测试 | `DocxOfficeIntegrationTest`（`:weizhi-bridge:test`，需 libweizhijni） |
| 用户打开 docx | App 层 FileProvider + `ACTION_VIEW`（见 `16-渲染-文档与Android附件计划.md` Phase F，待做） |

## 系统提示 / 能力

- 优先 `docx.js`（`markdownToDocx` / read→grep→save）；高级 OOXML 用 `docx-raw.js` + `validateDocx`。
- **不要**使用已删除的 Java `host.office.*`。
- 脚本：`import` ES module，与 [weizhi office-js-api.md](https://github.com/fengshihao/weizhi/blob/master/docs/office-js-api.md) 一致。

## 构建

- `weizhi-bridge` 编译前会从 `weizhi/assets/office/` 同步 JS 到 `agent_core/.../resources/agent-home/catalog/scripts/`（`syncOfficeScripts`）。
- 本地需 `./sync-weizhi.sh` 且 `./weizhi/scripts/build.sh` 以跑集成测。
