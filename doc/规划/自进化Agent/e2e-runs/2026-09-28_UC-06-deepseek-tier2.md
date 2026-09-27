# 2026-09-28 DeepSeek Tier-2（UC-01/11/06）

**脚本**：`./scripts/e2e-deepseek-tier2.sh`（本地 catalog-sample HTTP + DeepSeek Flash）  
**agentRoot**：`/tmp/agent1-tier2-test`（示例，无密钥）

## UC-06 — 通过

- **行为**：`catalog_sync_status` → `catalog_install` 仅 `script.sample-hello`
- **落盘**：`shared/catalog/scripts/sample-hello.js`
- **events**：含 `catalog_install` / `catalog_sync_status`

## 与 Tier-1

Tier-1 仍用 `./scripts/e2e-deepseek-uc-smoke.sh`（默认 01+11，更省 Token）。
