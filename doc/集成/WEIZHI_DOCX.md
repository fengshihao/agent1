# Word（docx.js）— Agent1 脚本

**给人读的 API：** [OFFICE_DOCX_API.md](./OFFICE_DOCX_API.md)

脚本、调用卡和回归都在本仓库。Weizhi 提供 `runJs`、`fs`、`zip` 和 Package 加载。docx 以 `weizhi:docx` 安装，不附带裸 `docx.js`。

---

## 约定

| 项 | 位置 |
|----|------|
| 脚本真源 | `agent_core/.../agent-home/weizhi-packages/docx@1.0.0/`（及 `docx-raw`、`docx-build`） |
| Android 副本 | `android_agent/app/src/main/assets/weizhi-packages/`（脚本同步；APK 不重复打 classpath 副本） |
| 装进运行时 | 桌面 `WeizhiBundledPackages` 从 classpath 装到 `weizhi-home`；Android `WeizhiPackages.syncBundled` 从 assets 装 |
| 调用 | `run_js` 里 `import { markdownToDocx } from "weizhi:docx"`。无外层 `docx_*` 工具 |
| 调用卡 | `agent-home/capabilities/office-api-cards.jsonl`（`docx.*`） |
| 测试 | `DocxOfficeIntegrationTest`（需 `libweizhijni`） |
| 用户打开 docx | 脚本用 `android.intent.start({ action: "view", path })` |

改脚本时改 `weizhi-packages` 真源，跑 `scripts/build-weizhi-bundled-packages.sh` 同步 Android assets，并改 `office-api-cards.jsonl` 里对应 `entry`。

## 系统提示

- 优先 `weizhi:docx`（`markdownToDocx` / read→grep→save）；高级 OOXML 用 `weizhi:docx-raw` + `validateDocx`。
- 不要使用已删除的 Java `host.office.*`。
