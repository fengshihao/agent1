# Word（docx.js）— Agent1 脚本

**给人读的 API：** [OFFICE_DOCX_API.md](./OFFICE_DOCX_API.md)

脚本、调用卡和回归都在本仓库。Weizhi 提供 `runJs`、`fs`、`zip` 和 Package 加载。docx 以 `weizhi:docx` 安装，不附带裸 `docx.js`。

---

## 约定

| 项 | 位置 |
|----|------|
| 脚本真源 | 内容仓（官方默认 agent1-skills `packages-src/docx/` 等）；不进 jar/APK |
| 分发 | 云端 registry 动态安装（首次 `run_js` ensure） |
| 装进运行时 | `WeizhiPackageSync.beforeEval` → `weizhi-home/packages/<name>@<version>/` |
| 调用 | `run_js` 里 `import { markdownToDocx } from "weizhi:docx"`。无外层 `docx_*` 工具 |
| 调用卡 | 云端 package registry `cards[]`（打包输入 `artifact-cdn/office-api-cards.jsonl`） |
| 测试 | `DocxOfficeIntegrationTest`（需 `libweizhijni`） |
| 用户打开 docx | 脚本用 `android.intent.start({ action: "view", path })` |

改脚本时改内容仓真源，云端发布走 `artifact-cdn`（`pack.mjs --cards office-api-cards.jsonl` + `upload.sh`）。

## 系统提示

- 优先 `weizhi:docx`（`markdownToDocx` / read→grep→save）；高级 OOXML 用 `weizhi:docx-raw` + `validateDocx`。
- 不要使用已删除的 Java `host.office.*`。
