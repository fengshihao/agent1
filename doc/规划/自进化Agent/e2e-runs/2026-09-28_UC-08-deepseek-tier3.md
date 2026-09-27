# 2026-09-28 DeepSeek Tier-3（UC-08）

**脚本**：`./scripts/e2e-deepseek-tier3.sh`（含 Tier-2 的 01/11/06 + UC-08）  
**单测 UC-08**：`E2E_DEEPSEEK_UCS=08 ./scripts/e2e-deepseek-uc-smoke.sh`  
**agentRoot 样例**：`/tmp/agent1-tier3-uc08`

## UC-08 — 通过

- **run_id**：`b75a434addc3`
- **行为**：`write_file` → `staging/skills/e2e-tier3-skill/SKILL.md` → `promote_request`
- **落盘**：`shared/local/skills/e2e-tier3-skill/SKILL.md`
- **events**：`promotion_completed`、`tool_name=promote_request`

## 注意

Skill 正文勿含 `api key` / `sk-` 等字样，否则 `PromotionScanner` 会拒绝晋升。
