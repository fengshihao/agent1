---
name: skill-creator
description: 用户要新建、改写或沉淀一条 Skill 时使用。写出 SKILL.md，放到 workspace/staging/skills，再 promote_request。
---

# Skill Creator

这是内置的元技能，用来创建另一条 Skill。没有单独的 create 工具：写好文件后调用 `promote_request`。

## 流程

1. 确认三件事：技能名、何时触发、步骤。缺任何一件时用 `ask_user`，不要先写文件。
2. 技能名只用小写字母、数字和短横线，例如 `travel-planner`。目录名与 `name` 相同。
3. 用 `write_file` 写入 `workspace/staging/skills/<name>/SKILL.md`。
4. 调用 `promote_request`。成功后 `capability_search` 技能名，结果里的正文就是晋升后的内容。
5. 改已有技能：先 `capability_search` 读出现有正文，再把新版本写到同名 staging 目录并再次 `promote_request`（同名覆盖 `shared/local`）。

不要 `write_file` 到 `shared/` 或 `docs/`。不要把密钥、token 写进文件。

## SKILL.md 格式

`description` 写触发条件（用户会说什么、什么情境该用），不要写实现摘要。

```markdown
---
name: travel-planner
description: 用户要安排行程、交通或每日安排时使用。
---

# 步骤

1. 确认目的地、日期和约束。
2. 在 workspace 里写出行程并给出文件路径。
```

正文只保留执行步骤和必要输入。背景说明放短。一条技能做一件事。
