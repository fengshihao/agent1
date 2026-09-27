# E2E 运行记录（样例）

## Mock / Scripted 集成测（优先，无 API Key）

Gradle 默认跑，类名 ↔ UC 映射：

| UC | 测试类 / 方法 |
|----|----------------|
| UC-02 | `ProductivityScriptedCoachTest#uc02LargeWriteAppendsLargeWriteCoachWhenThresholdLow` |
| UC-05 | `ProductivityScriptedCoachTest#uc05ScriptFailRepeatCoachAfterTwoInlineFailures` |
| UC-06 | `ProductivityScriptedCatalogTest#uc06InstallPendingCatalogItem` |
| UC-10 | `CatalogSyncServiceTest#uc10DigestChangeMarksUpdatedAndApplyRefetches` |
| UC-08 | `ProductivityScriptedPromoteTest#uc08PromoteStagingSkillToLocal` |
| UC-09 | `ProductivityScriptedSkillTest#uc09ReadLocalSkillAfterSeed` |
| 7.1 catalog Skill | `ProductivityScriptedCatalogSkillTest#readCatalogSkillViaSkillTool` + `AgentSkillLoaderTest#mergedCatalogBetweenProjectAndLocal` |
| UC-12 | `ProductivityScriptedCoachTest#uc12OutsideWriteAppendsPathOutsideCoach` |
| UC-04 | `ProductivityScriptedScriptLineTest#uc04SyntaxErrorReportsUserLineFive`（需 Weizhi native） |
| UC-07 | `CatalogSyncServiceNativeTest` + `WeizhiNativeCatalogIntegrationTest` + `ProductivityScriptedNativeCatalogTest#uc07ScriptedInstallNativeThenEnsureNative`（单次 execute_script 内 auto-install + ensureNative；Mock + echo_math，**无需 COS**） |
| 7.2 / 7.4 | `WeizhiCatalogScriptFolderIntegrationTest`；端到端故事见 [dev/catalog-sample/DEMO-7.4.md](../../../dev/catalog-sample/DEMO-7.4.md) |
| P.4 审计 | `AgentAuditEventsTest`；UC-06/08/Coach Scripted 测断言 `catalog_sync_*` / `promotion_*` / `coach_fired` |
| 读写环 | `ProductivityScriptedReadWriteTest` / `ProductivityScriptedToolFailureTest` |

```bash
env AGENT1_WEIZHI_REPO=/path/to/weizhi ./java_agent/gradlew -p java_agent :core:test :weizhi-bridge:test
```

---

真实 LLM 跑 [14-用户场景与验收用例](../14-用户场景与验收用例.md) 后，可在此存放 **脱敏** 片段：

- `events.jsonl` 摘录  
- transcript 关键轮次  
- 结论：通过 / 失败 / AI 行为待优化  

**勿提交 API Key。** 文件名建议：`YYYY-MM-DD_UC-04.md`。
