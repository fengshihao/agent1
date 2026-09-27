# V0–V6 阶段进度（对照 15-可验证需求）

> **Mock/集成** = `./scripts/e2e-self-evolve-smoke.sh` 与 Gradle 用例。  
> **DeepSeek LLM** = `./scripts/e2e-deepseek-*.sh`（需 `OPENAI_API_KEY`，闲时跑）。

| 分期 | 完成标志（规划） | Mock/集成 | DeepSeek LLM |
|------|------------------|-----------|--------------|
| **V0** | bootstrap 单测 | ✅ | — |
| **V1** | UC-01、UC-11 | ✅ | ✅ Tier-1（01,11） |
| **V2** | UC-02、UC-12 | ✅ `ProductivityScriptedCoachTest` | ✅ `e2e-deepseek-tier-v2-sandbox.sh`（12 不稳定→Mock） |
| **V3** | UC-03～05；**UC-04 门禁** | ✅ Weizhi 集成 + Scripted | ✅ `e2e-deepseek-tier-v3-weizhi.sh` |
| **V4** | UC-06、07、10 | ✅ Catalog/Native 测 | ✅ UC-06 Tier-2；UC-07/10 以 Mock/CLI 为主 |
| **V5** | UC-08、09（依赖 V3 Mock 门禁） | ✅ Promote/Skill 测 | ✅ Tier-3/4（08,09） |
| **V6** | UC-01～12 回归 + 演示 | ✅ `e2e-self-evolve-smoke.sh` | ✅ **`e2e-deepseek-v6.sh`** 编排 Mock + LLM 子集 |

## V3 是否「做完」？

- **工程能力（D4 / userLine / execute_script）**：✅ Mock 与 `WeizhiScriptEngineIntegrationTest` 已绿，视为 **REQ-052 集成过关**。
- **真实 LLM UC-04 全轮「AI 改对第 5 行」**：⚠️ 仍依赖 Tier-V3 脚本 + 人工扫 transcript；不比 Mock 更严时可记为 **V3 LLM 冒烟**。
- **结论**：**V3 代码与 Mock 门禁已完成**；LLM 层用 **Tier-V3** 补齐，不阻塞 V4/V5。

## V6 怎么跑

```bash
# 无 Key：Mock 全集
./scripts/e2e-self-evolve-smoke.sh

# 有 Key：Mock + DeepSeek 分层 UC（闲时）
export OPENAI_API_KEY='…'
./scripts/e2e-deepseek-v6.sh
```
