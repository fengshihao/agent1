# 7.4 端到端故事（Mock / 本地，无需 COS）

与 [12-catalog安装与AI按需拉取](../../doc/规划/自进化Agent/12-catalog安装与AI按需拉取.md) 对齐的**可重复**验收路径。

## 故事 A：catalog 脚本 + js_lib（UC-06 + 7.2）

1. 配置 `agent.manifest.json` → `catalog.manifestUrl`（或 MockWebServer / 静态样例，见 [README](./README.md)）。
2. LLM 或手测：`catalog_sync_status` → `catalog_install`（`script.demo` + `lib.demo`）。
3. `execute_script` 运行 workspace 内脚本，例如：

   ```javascript
   loadScript("demo-lib.js");
   JSON.stringify(inc(41));
   ```

   宿主已将 `shared/catalog/scripts` 设为 Weizhi `scriptFolder`；`js_lib` 装完后会**镜像叶子文件**到同目录。

**自动化**：`CatalogSyncServiceTest`、`WeizhiCatalogScriptFolderIntegrationTest`。

## 故事 B：native 插件（UC-07 + auto-install）

1. 同样配置 manifest（含 `native.echo_math.*` 条目；SO 来自本地 Weizhi `build/plugins/echo_math`，见 [CLOUD-E2E.md](./CLOUD-E2E.md)）。
2. 用户一句话：「用 echo_math 算 20+22」。
3. 单轮 `execute_script`：

   ```javascript
   const p = await host.ensureNative("echo_math");
   JSON.stringify(p.add(20, 22));
   ```

   缺插件时 **同一 tool 调用内** auto-install + 重开引擎重试；无需再调 `catalog_install`。

**自动化**：`ProductivityScriptedNativeCatalogTest#uc07ScriptedInstallNativeThenEnsureNative`。

## 故事 C：staging → promote（UC-08）

1. `write_file` → `staging/skills/.../SKILL.md`
2. `promote_request` → `shared/local/skills`
3. 下轮 `skill(action=read)` 可读 local skill。

**自动化**：`ProductivityScriptedPromoteTest`。

## Gradle 一键（Weizhi 已构建）

```bash
export AGENT1_WEIZHI_REPO=/path/to/weizhi
./weizhi/scripts/build.sh   # echo_math 插件
./java_agent/gradlew -p java_agent :core:test :weizhi-bridge:test
```
