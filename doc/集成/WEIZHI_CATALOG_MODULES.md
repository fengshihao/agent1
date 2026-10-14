# Weizhi Package（替代 catalog scriptFolder）

Weizhi 主干已移除 **`setScriptFolder` / `import "leaf.js"`**，官方 JS 库统一为 **Weizhi Package**。

规范真源：[weizhi `docs/WEIZHI_PACKAGES.md`](https://github.com/fengshihao/weizhi/blob/master/docs/WEIZHI_PACKAGES.md)、[MODULE_LOADING.md](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)

## Agent1 约定

| 根 | 路径 / API |
|----|------------|
| `setFsRoot` | 会话 workspace |
| `setPackageRoot` | 桌面：`{agentRoot}/weizhi-home`（`WeizhiBundledPackages.syncFromClasspath`）；Android：`WeizhiPackages.syncBundled(Context)` → `filesDir/weizhi-home` |

内置 Office / SVG 脚本真源：`agent_core/.../agent-home/weizhi-packages/<name>@1.0.0/`；Android 同步副本：`android_agent/app/src/main/assets/weizhi-packages/`（`scripts/build-weizhi-bundled-packages.sh` 生成）。

**给模型的写法**：`import { markdownToDocx } from "weizhi:docx"`、`import { renderPptx } from "weizhi:pptx"` 等。workspace 内协作用 `import "./helper.js"`。

## 集成测

- `DocxOfficeIntegrationTest` / `PptxOfficeIntegrationTest`（`:weizhi-bridge:test`，需 `libweizhijni`）
- `OfficeCatalogInstrumentedTest`（Android，`assets/weizhi-packages`）

## 关联

- [WEIZHI.md](./WEIZHI.md)、[agent1#89](https://github.com/fengshihao/agent1/issues/89)
- [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、[WEIZHI_PPTX.md](./WEIZHI_PPTX.md)
