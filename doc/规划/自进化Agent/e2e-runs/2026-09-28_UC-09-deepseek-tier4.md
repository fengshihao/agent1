# 2026-09-28 DeepSeek Tier-4（UC-09）

**脚本**：`./scripts/e2e-deepseek-tier4.sh`（Tier-3 + UC-09）  
**快速**：`E2E_DEEPSEEK_UCS=08,09 ./scripts/e2e-deepseek-uc-smoke.sh`（同一 agentRoot 先 promote 再 read）

## UC-09 — 通过

- **前置**：UC-08 已写入 `shared/local/skills/e2e-tier3-skill/`
- **行为**：`skill` list → read；回执 `source: local` + 正文「DeepSeek E2E tier3 晋升测试」
- **events**：含 `tool_name=skill`

## V5 闭环

Tier-4 覆盖规划 **UC-08 + UC-09** 真实 LLM 路径（Mock 仍见 `ProductivityScriptedPromoteTest` / `ProductivityScriptedSkillTest`）。
