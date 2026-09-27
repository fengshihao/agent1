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
| UC-12 | `ProductivityScriptedCoachTest#uc12OutsideWriteAppendsPathOutsideCoach` |
| UC-04 | `ProductivityScriptedScriptLineTest#uc04SyntaxErrorReportsUserLineFive`（需 Weizhi native） |
| UC-07 | `CatalogSyncServiceNativeTest#uc07SyncNativeEchoMathFiles` + `WeizhiNativeCatalogIntegrationTest#uc07EnsureNativeFromCatalogNativeDir` + `ProductivityScriptedNativeCatalogTest#uc07ScriptedInstallNativeThenEnsureNative`（Mock HTTP + 本地 echo_math，**无需 COS**） |
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
