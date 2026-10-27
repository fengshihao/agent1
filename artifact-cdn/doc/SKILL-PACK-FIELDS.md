# Skill 制品字段（pack-skill.mjs）

Skill 与 **Weizhi Package** 分轨：Package 只放 SDK 级 JS；Skill 放 **SKILL.md + 可选业务脚本**。业务脚本里可以 `import … from "weizhi:<name>"`，与主会话 `run_js` 相同——**不在 load_skill 时预装依赖**，在 **执行脚本前** `WeizhiPackageSync.beforeEval` 扫描 `weizhi:*` 再 ensure。

宿主侧（待实现，本文只定制品与清单）：

1. 启动拉 `skills/registry/stable.json` → 缓存 → **写入系统提示词 Skill 目录**（大类 + 名称 + 一句话）。
2. 工作流：**先 `load_skill`（无本地则下 zip）→ 按 SKILL 执行；无合适 Skill 再 `find_caps` 查 API/MCP/web_lib**。
3. `load_skill` 后若 SKILL 要求跑 `scripts/*.js`，该段源码与手写 `run_js` 一样走 beforeEval。

---

## 1. 源码目录（打包输入）

```text
skills-src/
  html2pptx/              # manifest.name 必须等于目录名
    manifest.json
    SKILL.md
    scripts/              # 可选
      deck-preflight.js
  travel-itinerary/
    manifest.json
    SKILL.md
    scripts/
      trip-pipeline.js
```

**禁止**：在 `weizhi-packages/<name>/manifest.json` 里再写 `"skill": "SKILL.md"`（迁移后删除）。

---

## 2. `manifest.json` 是否必要？和 `SKILL.md` YAML 怎么分工

**结论：不必两套重复元数据。**

| 信息 | 建议放哪里 | 原因 |
|------|------------|------|
| `name`、`version` | **极简 `manifest.json`**（或仅 registry 清单 + zip 内 frontmatter 校验） | 安装器、CDN 路径 `skills/<name>/<version>/…zip`、sha256 对齐 **要在不解压全文的情况下**知道版本；与 `weizhi_package` 安装链一致 |
| `description`（触发条件） | **`SKILL.md` frontmatter** | 今天 `AgentSkillLoader` 已只认 `name` / `description` / `tags`；`load_skill` 后给模型的正文也来自同一文件 |
| `category`、`catalog_line` | **`SKILL.md` frontmatter**（推荐） | 打包时 `pack-skill` **只读 frontmatter** 生成目录；不必在 manifest 再写一遍 |
| `depends_weizhi`、`depends_mcp` | **frontmatter 或 SKILL 正文** | 给人与 `load_skill` 后模型看；执行前仍靠 `beforeEval` 扫 `weizhi:*` |
| `title`、`summary` | 可省略；用 `description` + `catalog_line` 即可 | 避免三处同义句 |

**推荐极简 manifest（仅安装用）：**

```json
{
  "name": "travel-itinerary",
  "version": "1.0.0"
}
```

**推荐 frontmatter（目录 + 加载后元数据）：**

```yaml
---
name: travel-itinerary
version: 1.0.0
description: 用户要旅游路书、几日游攻略…时使用（load_skill 后可见）。
category: travel.roadbook
catalog_line: 规划旅游路书、自驾攻略、行程表时用。
tags: 旅游,行程,路书
depends_mcp: gaode
---
```

打包规则：`manifest.version` 与 frontmatter `version` **必须一致**；`name` 与目录名、frontmatter `name` 一致。不一致则 `pack-skill` 失败。

**不要**在 Skill 里写 package 专用字段：`entry`、`cards`、manifest 的 `"skill": "SKILL.md"`。

（下文表格为「若坚持全写 manifest」时的字段说明；实现时可改为只校验极简 manifest + 解析 frontmatter。）

### 示例：`html2pptx`（从 package 迁出后的 Skill）

```json
{
  "name": "html2pptx",
  "version": "1.0.0",
  "category": "office.presentation",
  "title": "设计感幻灯片（HTML deck）",
  "summary": "交付 deck.html 预览；用户未点导出或明说导出前不要 renderHtmlDeck。",
  "catalog_line": "要做有设计感的 PPT、网页幻灯片、deck 预览时用。",
  "depends_weizhi": ["html2pptx"],
  "platforms": ["any"]
}
```

### 示例：`travel-itinerary`

```json
{
  "name": "travel-itinerary",
  "version": "1.0.0",
  "category": "travel.roadbook",
  "title": "旅游路书",
  "summary": "几日游行程、自驾里程、高德地图链接、食宿表；不含机酒火车下单。",
  "catalog_line": "规划旅游路书、自驾攻略、行程表时用。",
  "depends_mcp": ["gaode"],
  "platforms": ["any"]
}
```

`travel-itinerary` 已完全 Skill 化：工具函数随 `scripts/` 走，**不再有 weizhi 包**。若业务脚本极薄，可 `depends_weizhi: []`，只在 SKILL 里写 `import weizhi:docx`。

---

## 3. zip 内容

与 Package 相同：zip 根即目录内容，无额外父目录。

| 路径 | 必填 |
|------|------|
| `manifest.json` | 是 |
| `SKILL.md` | 是 |
| `scripts/**` | 否 |
| `references/**` | 否 |

---

## 4. 云端目录文件 vs 系统提示词里长什么样

容易混的两层：

| 层 | 格式 | 谁用 |
|----|------|------|
| **CDN 文件** `dist/skills/registry/stable.json` | **JSON**（机器读） | 宿主启动拉取、缓存、校验；**不要**把整段 JSON 贴进系统提示词 |
| **系统提示词里的「Skill 目录」** | **自然语言 Markdown**（人/模型读） | `ProductivitySystemPromptBuilder` 把 JSON **渲染**成短文 |

模型看到的应是类似（示意）：

```markdown
## 官方 Skill 目录
有匹配任务时：先用 load_skill 加载对应 skill，按 SKILL 执行；没有合适 skill 再用 find_caps 查 API。

### office.presentation
- **html2pptx** — 要做有设计感的 PPT、网页幻灯片 deck 预览时用。
- **pptx** — 快速结构化幻灯片、直接出 pptx 时用。

### travel.roadbook
- **travel-itinerary** — 规划旅游路书、自驾攻略、行程表时用。
```

后期目录瘦身时，宿主可以只渲染 `name` + `catalog_line`（或更短的一句），JSON 里仍保留同样字段即可。

---

## 5. 清单：`dist/skills/registry/stable.json`（CDN 交付，非提示词原文）

由 `pack-skill.mjs` 从**各 skill 的 SKILL.md frontmatter**（或过渡期 manifest）聚合；`category` 不硬编码枚举。

```json
{
  "schema_version": 1,
  "channel": "stable",
  "generated_at": "2026-10-24",
  "categories": [
    {
      "id": "office.presentation",
      "skills": [
        {
          "name": "html2pptx",
          "line": "要做有设计感的 PPT、网页幻灯片、deck 预览时用。"
        },
        {
          "name": "pptx",
          "line": "快速结构化幻灯片、直接出 pptx 时用。"
        }
      ]
    },
    {
      "id": "travel.roadbook",
      "skills": [
        {
          "name": "travel-itinerary",
          "line": "规划旅游路书、自驾攻略、行程表时用。"
        }
      ]
    }
  ]
}
```

后期可把 `line` 压到更短，或只留 `name`（由宿主拼默认句）；schema 保持 `name` + `line` 即可。

可选扩展（未实现）：`category.title` 中文展示名，由 manifest 侧 `category_title` 或单独 `categories.json` 提供。

---

## 6. 统一真源：`dist/registry/stable.json` 中的 `kind: "skill"`

每条 artifact（与 `weizhi_package` 并列）：

```json
{
  "kind": "skill",
  "name": "travel-itinerary",
  "version": "1.0.0",
  "category": "travel.roadbook",
  "title": "旅游路书",
  "summary": "…",
  "catalog_line": "…",
  "depends_weizhi": ["travel-itinerary"],
  "artifact": {
    "url": "https://agentone.guoyoutech.cn/skills/travel-itinerary/1.0.0/travel-itinerary-1.0.0.zip",
    "sha256": "…",
    "size": 12345
  }
}
```

**不进** `dist/packages/registry/stable.json`（微智投影仍只有 package）。

---

## 7. 与 PPT 能力的对照（迁移目标）

| 能力 | Skill（剧本） | Package（代码） |
|------|----------------|-----------------|
| 快速 pptx | `pptx`（bundled → 云 skill） | `weizhi:pptx`、`pptx-build` |
| HTML deck | `html2pptx`（原包内 SKILL.md） | `weizhi:html2pptx`（仅 JS API） |
| 路书 | `travel-itinerary`（剧本 + `scripts/roadbook.js`） | 无（已并入 Skill） |

---

## 8. 打包命令

```bash
cd artifact-cdn
export ARTIFACT_CDN_BASE_URL=https://agentone.guoyoutech.cn/
node pack-skill.mjs --root ./skills-src --base-url "$ARTIFACT_CDN_BASE_URL"
```

上传后客户端默认 URL（建议，待 core 实现）：

- 目录：`…/skills/registry/stable.json`
- 安装：`com.agent1.javaagent.skill.SkillRegistryClient.ensureInstalled`（对称 `PackageRegistryClient`；`load_skill` 经 `SkillsRegistryInstall.ensure`）
