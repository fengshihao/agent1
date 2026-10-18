# Weizhi Package（替代 catalog scriptFolder）

Weizhi 主干已移除 **`setScriptFolder` / `import "leaf.js"`**，官方 JS 库统一为 **Weizhi Package**。

规范真源：[weizhi `docs/WEIZHI_PACKAGES.md`](https://github.com/fengshihao/weizhi/blob/master/docs/WEIZHI_PACKAGES.md)、[MODULE_LOADING.md](https://github.com/fengshihao/weizhi/blob/master/docs/MODULE_LOADING.md)

## Agent1 约定

| 根 | 路径 / API |
|----|------------|
| `setFsRoot` | 会话 workspace |
| `setPackageRoot` | 桌面：`{agentRoot}/weizhi-home`（`WeizhiBundledPackages.syncFromClasspath`）；Android：`WeizhiPackages.syncBundled(Context)` → `filesDir/weizhi-home` |

内置 Office / SVG 脚本真源：`agent_core/.../agent-home/weizhi-packages/<name>@<version>/`（桌面 classpath 安装；pptx 当前为 `1.1.0`）。**Android 不预装**（20-A 收口）：首次 `run_js` 由 `WeizhiPackageSync.beforeEval` 从云端 registry（`agentone.guoyoutech.cn`）动态安装，发布走 `artifact-cdn` 的 `pack.mjs + upload.sh`。

**给模型的写法**：`import { markdownToDocx } from "weizhi:docx"`、`import { renderPptx } from "weizhi:pptx"` 等。workspace 内协作用 `import "./helper.js"`。

## 随包 Skill（2026-10-17 起，T0 规范）

Skill 类能力**一律随微智包分发**，不再往 `agent-home/skills` 加内置 skill：

- 包 manifest 声明 `"skill": "SKILL.md"`，SKILL.md 随包打进 zip / assets（frontmatter `name` / `description` / `tags`）。
- 安装链路（桌面 `WeizhiBundledPackages.syncFromClasspath`、registry `WeizhiPackageSync.beforeEval`、Android `WeizhiBundledInstall`）装完包后由 `agent_core` 的 `PackageSkills` 整批写 `kind=skill` 能力卡（`source=package_skill`，id `skill.<name>`、entry `skill:<name>`）。
- `AgentSkillLoader.loadMerged` 在 bundled 与 project 之间加载包内 skill 正文（source 标 `package`）；local / catalog / project 同名可覆盖。
- 首例：`html2pptx@0.2.1`（`doc/集成/HTML2PPTX.md`）。存量 `pptx`、`skill-creator` 仍是内置 skill 例外。

## 集成测

- `DocxOfficeIntegrationTest` / `PptxOfficeIntegrationTest`（`:weizhi-bridge:test`，需 `libweizhijni`）
- `OfficeCatalogInstrumentedTest`（Android 真机，云端 registry 动态安装，需联网）

## 关联

- [WEIZHI.md](./WEIZHI.md)、[agent1#89](https://github.com/fengshihao/agent1/issues/89)
- [WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、[WEIZHI_PPTX.md](./WEIZHI_PPTX.md)
