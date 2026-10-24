# 7.4 端到端故事（Mock / 本地，无需 COS）

> **现状**：故事 A 里的模型工具 `catalog_sync_status` / `catalog_install` 已删除。官方脚本改走 `weizhi:<name>`。`CatalogSyncService` 与 CLI `./agent1 sync` 仍在。

与 [12-catalog安装与AI按需拉取](../../doc/规划/自进化Agent/12-catalog安装与AI按需拉取.md) 对齐的**可重复**验收路径。

## 故事 A：官方 Package

`run_js` 里写 `import { markdownToDocx } from "weizhi:docx"`（以及 `weizhi:pptx`、`weizhi:svg-raster`）。宿主按云端清单安装到 `weizhi-home/packages/`。不要 `loadScript`，也不要把库镜像进 `shared/catalog/scripts`。

**自动化**：`OfficeRegistryInstallTest`、`DocxOfficeIntegrationTest`、`PptxOfficeIntegrationTest`。`CatalogSyncServiceTest` 只覆盖 CLI catalog 落盘，不再镜像 `js_lib`。

## 故事 B：native 插件（UC-07 + auto-install）

1. 同样配置 manifest（含 `native.echo_math.*` 条目；SO 来自本地 Weizhi `build/plugins/echo_math`，见 [CLOUD-E2E.md](./CLOUD-E2E.md)）。
2. 用户一句话：「用 echo_math 算 20+22」。
3. 单轮 `run_js`：

   ```javascript
   const p = await host.ensureNative("echo_math");
   JSON.stringify(p.add(20, 22));
   ```

   缺插件时 **同一 tool 调用内** auto-install + 重开引擎重试。

**自动化**：`ProductivityScriptedNativeCatalogTest#uc07ScriptedInstallNativeThenEnsureNative`。

## 故事 C：staging → promote（UC-08）

1. `write_file` → `staging/skills/.../SKILL.md`
2. `promote_request` → `shared/local/skills`
3. 下一轮 `find_caps` / `load_skill` 可读 local skill。

**自动化**：`ProductivityScriptedPromoteTest`。

## Gradle 一键（Weizhi 已构建）

```bash
export AGENT1_WEIZHI_REPO=/path/to/weizhi
./weizhi/scripts/build.sh   # echo_math 插件
./java_agent/gradlew -p java_agent :core:test :weizhi-bridge:test
```
