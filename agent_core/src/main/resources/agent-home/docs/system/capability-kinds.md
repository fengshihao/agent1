# 能力卡 kind 枚举与 source 约定

> `find_caps`（capability_search）与 capabilities.db 里每张卡都有 `kind`（能力形态）与
> `source`（来源）。本文是唯一枚举表；新增能力一律显式声明 kind（规则见仓库
> `AGENTS.md`「能力一律微智 Package 形态」，2026-10-17 起）。

## kind 枚举（6 个，不再新增除非评审通过）

| kind | 含义 | id 约定 | 典型卡 | source |
|------|------|---------|--------|--------|
| `builtin` | 宿主内置工具 / 常量能力 | `tool.*`、`agent.*`、`builtin.*` | `tool.media_resize`、`agent.promotion`、`builtin.fs` | `bundled:v1` |
| `bridge_tool` | 仅 run_js 内可用的 `$tools` 桥 | `bridge_tool.*` | `bridge_tool.webview_exec` | `bundled:v1` |
| `caps` | 微智原生 caps（Android 宿主能力） | `android.*` | `android.intent.start` | `weizhi` |
| `weizhi_package` | 微智 JS 包（run_js `import "weizhi:<name>"`） | `<pkg>.<export>`、`svg.*`、office 家族（`docx.*`/`pptx.*`/`html2pptx.*`） | `html2pptx.render`、`pptx.render` | `agent1`（本仓库包）/ `registry`（云装包）/ `weizhi`（weizhi 真源卡） |
| `skill` | SKILL 正文卡（`skill:<name>` 可加载） | `skill.<name>` | `skill.skill-creator`、`skill.html2pptx` | `bundled:v1`（内置 skill-creator）/ `skills_home`（官方 skills-home）/ `package_skill`（遗留随包，`PackageSkills`）/ `local` |
| `web_lib` | 前端 CDN 库卡（deck HTML `<script src>`） | `web_lib.*` | `web_lib.echarts` | `web_lib`（`WebLibRegistrySync`） |

## 静态 seed（`agent-home/capabilities/`，仅宿主自己的能力）

能上云的 Package / Skill 卡**不**进 classpath。Office / svg / image / audio 等卡随 package registry 下发。

| 文件 | kind 现状 |
|------|-----------|
| `search-index.seed.jsonl` | 宿主工具：`builtin` / `bridge_tool` / `skill.skill-creator`；例外 `media.video`（AAR 内置、不上云） |
| `weizhi-api-cards.jsonl` | 描述打进 APK 的 Weizhi caps。真源随 weizhi（`docs/api-cards.jsonl`），**无 kind 字段**，加载时按 id 前缀推断兜底（`android.*` → caps） |

动态来源（写入 capabilities.db，不在 seed 文件里）：

- 云端 registry 装包 → `kind: weizhi_package`、`source: registry`（`WeizhiPackageSync`）。
- 官方 skills-home → `kind: skill`、`source: skills_home`（`SkillsCapabilitySync`）。
- 遗留包内 SKILL.md → `source: package_skill`（`PackageSkills`，新包不再使用）。
- web_lib 注册表 → `kind: web_lib`、`source: web_lib`（`WebLibRegistrySync`，要求 id 以 `web_lib.` 开头）。
- Skill 目录（提示词注入，不进 find_caps）→ CDN `skills/registry/stable.json`（`SkillCatalogSync`）。

## 变更规则

1. 新卡**必须显式 `kind`**；`CapabilitySeedLoader.parseWeizhiCard` 显式优先、推断仅作
   weizhi-api-cards.jsonl 的过渡兜底。
2. 新增 Skill / 脚本 / 库类能力一律微智 Package 形态（kind 落 `weizhi_package` / `skill` /
   `web_lib` / `caps` 之一），不再往 `agent-home/skills` 加内置 skill（存量仅
   `skill-creator`）。Skill 目录 JSON 走 CDN `skills/registry/stable.json`，不进 jar/APK。
3. kind 是过滤参数（`find_caps kinds=[...]`）与检索权重输入：发错 kind 的卡检索不到
   或被过滤掉，等价于能力不可见。
