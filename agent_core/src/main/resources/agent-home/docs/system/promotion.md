# 晋升（promote）

将 **workspace/staging/** 中整理好的 skill 或 script 沉淀到 **shared/local/**。

创建 Skill 时 `capability_search`「skill-creator」。命中结果里就是元技能正文。

## 布局

```text
workspace/staging/skills/<name>/SKILL.md
workspace/staging/scripts/<name>.js   # 可选 <name>.meta.json
```

## SKILL.md

以 YAML frontmatter 开头。`name` 与目录名一致。`description` 写清何时使用。

```markdown
---
name: travel-planner
description: 用户要安排行程、交通或每日安排时使用。
---

步骤、输入和产出写在这里。
```

## API

- **promote_request**（可选 `note`）：规则扫描 → 自动审查通过 → 复制到 `shared/local/`。
- 工作 Agent 对 `shared/local` 与 `shared/catalog` 只读；写入走 `promote_request`，不要 `write_file`。
- 晋升后更新 **docs/capabilities/local.***.md**；审计写入 **logs/events.jsonl**（`promotion_completed` / `promotion_rejected`）。
