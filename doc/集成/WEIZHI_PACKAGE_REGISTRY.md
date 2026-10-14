# Weizhi 官方 Package Registry（方案）

> **状态：已实现（Agent1 #91 + Weizhi P3）**；默认 Registry 为微智官方 URL，可用 env / manifest 覆盖  
> 前置：[WEIZHI_CATALOG_MODULES.md](./WEIZHI_CATALOG_MODULES.md)（#89 已合：内置 `weizhi:<name>` + `setPackageRoot`）  
> 微智规格：[WEIZHI_PACKAGES.md](https://github.com/fengshihao/weizhi/blob/master/docs/WEIZHI_PACKAGES.md)（P3 远程目录仍为「未实现」）

## 1. 目标体验

| 阶段 | 模型感知 | 宿主行为 |
|------|----------|----------|
| **find_caps** | 能看到**官方 Registry** 中的能力包（含 `import` 示例），不要求已安装 | 拉取/缓存 Registry manifest，合并进能力索引 |
| **run_js** | 只写 `import … from "weizhi:<name>"`，**不**先调下载工具 | 执行前按需下载/校验/安装缺失包，再 `runJs`；成功不暴露下载细节 |
| **后台** | 无感 | 定期对照 Registry 更新**已安装**的 package |
| **失败** | 仅在 run_js 回执中看到明确错误（网络、sha256、未收录等） | 审计 events 可记 `package_fetch` / `package_install` |

原则：**不在 `import` 语句内联网**；透明安装在 **run_js / 引擎 open 前** 由宿主完成（与 `host.ensureNative` 的「显式异步」不同，Package 对 AI 完全透明）。

## 2. 非目标（本期方案）

- 脚本内任意 URL 装包；企业私有 Registry（微智 D5：仅官方目录，后续再议）。
- 在 `import` 解析器里嵌 HTTP（保持模块加载只读本地）。
- 要求模型调用 `package_install` 工具后再 `run_js`（可对开发者保留调试工具，非主路径）。
- 独立 NATIVE 插件目录 CDN 与 Package 双轨长期并存（**倾向** SO 随 Package zip 分发，见 §7）。

## 3. 两份数据

| 名称 | 位置 | 含义 |
|------|------|------|
| **官方 Registry** | HTTPS JSON（CDN/OSS） | 全网有哪些 `name`、当前推荐 `version`、`artifact.url`、`sha256`、能力摘要/entry |
| **本地安装索引** | `<weizhi-home>/packages-index.json` | 本机 `name → version`，引擎 `weizhi:<name>` 只读此状态 |

Registry **定期更新**（ETag / If-None-Match）；Agent1 缓存建议：`{agentRoot}/sync/weizhi-package-registry.json`（或 `sync/remote/` 子目录）。

内置包（APK assets / classpath）仍为**离线保底**；远程仅为增量与升级。

## 4. 统一下载流水线（Weizhi 实现）

在 `com.weizhi.packages.PackageInstaller` 之上增加 **Registry 客户端**（命名示例：`PackageRegistryClient` / `OfficialArtifactSync`）：

```
Registry URL（可配置，默认微智官方）
    → GET manifest（缓存 + 条件请求）
    → 对比本地 packages-index / 请求集（run_js 预扫或后台任务）
    → GET artifact zip
    → sha256 校验
    → PackageInstaller.installPackageZip(weizhiHome, …)
    → 更新 packages-index.json
```

与 Agent1 **CatalogSyncService**（skill/js_lib/native）**共用思路**（清单 + digest），但：

- 安装目标为 **`weizhi-home/packages/`**，不是 `shared/catalog/scripts/`。
- 能力检索走 **find_caps** 的 `weizhi_package`（或等价 kind），不是 `catalog_install` 装叶子 js。

**配置（Agent1 覆盖，Weizhi 提供默认 URL 常量）**：

- 环境变量：`AGENT1_WEIZHI_PACKAGE_REGISTRY_URL`（名称可最终与 weizhi 对齐）
- 或 `agent.manifest.json` 新字段（与 `AGENT1_CATALOG_MANIFEST_URL` 并列）

## 5. Agent1 集成点

### 5.1 find_caps

1. 启动 / 定时 / Host 初始化：`RegistryClient.fetchAndCache(agentRoot)`。
2. 将每条 package 写入能力索引（字段含 `id`、`title`、`summary`、`entry` 含 `weizhi:<name>`、`tags`、`platforms`）。
3. 与现有 `office-api-cards.jsonl` / seed 关系：**内置条目可保留**；Registry 为权威增量源，避免双份真源长期分叉。
4. 可选：命中里标注 `installed: true/false`（仅 UI/调试，不强制进模型提示）。

### 5.2 run_js 透明 ensure

在 **ExecuteScriptTool** 或 **WeizhiScriptEngineFactory.open** 之前：

1. 从脚本提取 `weizhi:<name>`（与 `WeizhiScriptToolInstaller` 同级轻量扫描即可）。
2. 对缺失（或策略要求升级的）name 调用 Weizhi `RegistryClient.ensureInstalled(weizhiHome, names)`。
3. 失败：直接返回 tool 错误，**不**进入 QuickJS。
4. 成功：`syncBundled` 保底 + `setPackageRoot` + `eval`。

可选：首次引擎报 `package not installed` 时宿主 **重试一次** sync（单次、带超时）。

### 5.3 后台更新

- `ProductivityAgentHost` 或系统 Job：对已安装 `name` 对照 Registry 检查新版本，策略可配置（仅 Wi‑Fi、仅充电等后置）。
- 不与每次 run_js 强制全量升级抢带宽。

### 5.4 超时（与透明装包）

**现状（#90）**：`run_js` 只有一个预算，经 `AGENT1_SCRIPT_TIMEOUT_MS`（默认 **600_000 ms**）传给 `WeizhiEngine.runJs`；`engineFactory.open()` 里的 `syncBundled` 是本地拷贝，**不计入**该超时，但也**没有**单独上限。

**透明远程装包后**：若把下载塞进同一个 `runJs` 超时，大 zip 或弱网会误杀正常脚本；若只在 `open()` 里下载且不设限，可能无限挂起。

**约定（实现 #91 时）**：

| 阶段 | 预算 | 默认 | 环境变量（建议） |
|------|------|------|------------------|
| Registry 拉清单（find_caps / 后台） | 短 | 30 s | `AGENT1_WEIZHI_REGISTRY_FETCH_TIMEOUT_MS` |
| **Package ensure**（run_js 前，按脚本 `weizhi:*`） | 独立 | **180_000 ms**（3 min） | `AGENT1_WEIZHI_PACKAGE_ENSURE_TIMEOUT_MS` |
| **QuickJS eval** | 独立，与现逻辑一致 | 600_000 ms | 沿用 `AGENT1_SCRIPT_TIMEOUT_MS` |

- ensure **只计网络+安装**；已安装且版本满足时 **0 ms 级返回**，不占满 ensure 预算。
- `CancellationToken` 取消 run_js 时，应同时中止进行中的 package 下载（与现有 cancel watcher 一致）。
- 失败文案区分：`package ensure timeout` vs `script timeout`，避免 AI 误改业务逻辑。
- **后台 periodic 升级**不走 run_js 超时，用 Job 自己的上限；失败只写日志/events，不打扰当前对话。

不推荐「简单把 `AGENT1_SCRIPT_TIMEOUT_MS` 整体加倍」作为长期方案；双预算更清晰，也便于单独调移动端 ensure 上限。

## 6. 安全与并发

- 仅信任 Registry **基址白名单** + 条目内 `url`/`sha256`（对齐 WEIZHI_PACKAGES D4/D5）。
- zip slip 与现有 `PackageInstaller` 一致。
- 同 `name` 并发安装：Weizhi 侧文件锁或单飞。

## 7. NATIVE / SO 演进（对齐讨论，非本期实现）

当前规格：`native_requires` → 独立 `host.ensureNative` + HOST_ABI 插件目录。

**目标形态**：SO 作为 **Package zip 内 `native/<abi>/`** + manifest `native` 段；安装 package 时展开/验签/dlopen，**逐步废弃**独立 NATIVE CDN。Registry 仍**一套 manifest**，条目只有 `kind: weizhi_package`。

## 8. 实施顺序（建议）

| 序 | 仓库 | 交付 |
|----|------|------|
| 1 | **weizhi** | Registry JSON schema + `PackageRegistryClient` + mock HTTP 测试 |
| 2 | **weizhi** | 文档：P3 从「未实现」改为 API 说明 |
| 3 | **agent1** | Registry 缓存 + find_caps 索引合并 |
| 4 | **agent1** | run_js `ensurePackagesForSource` + 错误文案 + events |
| 5 | **agent1** | 后台 periodic sync + env/manifest 配置 |
| 6 | 运维 | 官方 Registry 托管（可与 catalog CDN 同桶不同路径） |

## 9. 验收（整体）

- 离线：仅内置包时 find_caps 可见缓存/内置能力，run_js 可跑 docx/pptx。
- 在线：Registry 新增虚构测试包，find_caps 可见，run_js 首次自动安装后 `import` 成功且无多余 tool 步骤。
- 篡改 artifact：run_js 失败且错误含校验失败，不执行用户 JS。
- 后台：已装包 version 落后时，无对话情况下 index 可更新，下次 run_js 使用新版本。

## 10. 关联 Issue

- Agent1：[#91](https://github.com/fengshihao/agent1/issues/91)（find_caps + run_js 透明安装 + 后台更新）
- Weizhi：建议在 [fengshihao/weizhi](https://github.com/fengshihao/weizhi) 开 P3 `PackageRegistryClient` issue，与 #91 联调。
- 前置（已关闭）：[#89](https://github.com/fengshihao/agent1/issues/89) 内置 Package（PR #90）
