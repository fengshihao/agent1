# Weizhi 官方 Package Registry（方案）

> **状态：微智下载客户端已实现。桌面和 Android 在执行含 `weizhi:` 的脚本前调用 `ensureInstalled`，并把清单里的当前卡片写入 find_caps（`source=registry`）。**  
> 官方清单：`https://agentone.guoyoutech.cn/packages/registry/stable.json`（发布目录见仓库 `artifact-cdn/`）。  
> 每个 `name` 在清单里只有当前一行。换版本是改这一行；find_caps 不保留上一版的卡片。  
> 前置：[WEIZHI_CATALOG_MODULES.md](./WEIZHI_CATALOG_MODULES.md)（#89 已合：内置 `weizhi:<name>` + `setPackageRoot`）  
> 微智规格：[WEIZHI_PACKAGES.md](https://github.com/fengshihao/weizhi/blob/master/docs/WEIZHI_PACKAGES.md)

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

能上云的 Package / Skill **不**进 jar/APK；生产全靠 registry 动态安装。

## 3.1 源码布局、版本与通道（约定）

三层分开，避免「改 skill 就要复制整棵 `@x.y.z` 目录、测一下就推生产清单」：

| 层 | 路径 / 形态 | 版本出现在哪 |
|----|-------------|--------------|
| **源码（内容仓）** | 官方默认 agent1-skills `packages-src/<name>/`（可插拔；**路径不含版本**） | 仅 `manifest.json` 的 `version` |
| **运行时安装** | `<weizhi-home>/packages/<name>@<version>/` | 安装目录名带版本（多版本可并存、只升不降） |
| **云端制品** | `packages/<name>/<version>/<name>-<version>.zip` | 对象路径带版本；**已上传 zip 只增不改** |

日常开发：在无版本源码目录里改文件并 commit（与普通代码相同）；准备发布时改 `manifest.version`（或由发布流程注入），再 `pack` → 可选上传。

**通道（channel，预留）**——与源码布局正交，后续落地，现网可继续直推 stable：

| 通道 | 用途 | 客户端如何指过去 |
|------|------|------------------|
| 本地 fixture | 仅集成测试 `weizhi-test-packages/` | 测试安装，不进生产产物 |
| `staging` | 真机 / 灰度，不进现网指针 | `AGENT1_WEIZHI_PACKAGE_REGISTRY_URL` 指向 staging 清单（已支持）；日后可加 `CHANNEL` 配置少拼 URL |
| `stable` | 现网 | 默认 `…/packages/registry/stable.json` |

目标节奏：`改内容仓源码 → 集成测试 fixture / 本地 registry 测 → pack --channel staging 真机测 → 验收后再写 stable`。  
预发布 semver（`x.y.z-rc.N` / `-dev`）、清单文件名按 channel 拆分（`registry/<channel>.json`）、以及 `min_client_version` 见 [artifact-cdn/README.md](../../artifact-cdn/README.md) TODO——**实现通道时不要把版本重新绑回源码目录名**。

`artifact-cdn/pack.mjs`：源目录名为 `<name>`，身份以 manifest 为准；仍兼容旧的 `<name>@<version>` 目录（须与 manifest 一致）。`--channel` 写入清单 JSON 的 `channel` 字段；清单文件名目前仍落 `stable.json`（按 channel 分文件属后续项）。

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
- 能力检索走 **find_caps** 的 `weizhi_package`（或等价 kind）。模型工具 `catalog_install` / `catalog_sync_status` 已删除；官方包在 `run_js` 前安装。桌面 `./agent1 sync` 仍服务 native 等 catalog 条目。

**配置（Agent1 覆盖，Weizhi 提供默认 URL 常量）**：

- 环境变量：`AGENT1_WEIZHI_PACKAGE_REGISTRY_URL`（**已实现**，`WeizhiPackageSync.registryUrl()`）
- 系统属性：`agent1.weizhi.package.registry.url`（测试用，同上优先级在环境变量之后）
- 或 `agent.manifest.json` 新字段（与 `AGENT1_CATALOG_MANIFEST_URL` 并列，未实现）

## 5. Agent1 集成点

已落地：`WeizhiPackageSync` 在桌面和 Android 的 `evalForAgent` 之前扫描 `weizhi:`，调用 `PackageRegistryClient.ensureInstalled`。同一份 `packages/registry/stable.json` 里的 `cards`（没有 cards 时用 `capability`）整批替换 find_caps 中 `source=registry` 的行。内置种子保留；同 id 时清单行覆盖种子。每个 name 只有清单上的当前版本。`AGENT1_WEIZHI_PACKAGE_REGISTRY=off` 关闭。这些包已经装在本地且网络失败时，脚本继续跑。

**只升不降**（2026-10 修复）：本地已装版本高于清单版本时保留本地，不发生「线上滞后把内置新包回退」——版本按 x.y.z 三段数字比较，任一侧解析失败回退旧的「同步清单版本」行为。自定义 registry 地址时 zip 白名单自动加入该地址 host，清单缓存写 `registry-cache-custom.json` 不污染生产缓存。

**本地/测试不必上云**：`WeizhiPackageSyncLocalRegistryTest` 用回环 `HttpServer` 自建 registry（manifest + zip 全本地），通过系统属性指过去即可完整走 ensure 流程；上云灰度时也可用 `--channel staging` 打出独立清单再配 `AGENT1_WEIZHI_PACKAGE_REGISTRY_URL` 指向测试地址。

### 5.1 find_caps

1. 启动 / 定时 / Host 初始化：`RegistryClient.fetchAndCache(agentRoot)`。
2. 将每条 package 写入能力索引（字段含 `id`、`title`、`summary`、`entry` 含 `weizhi:<name>`、`tags`、`platforms`）。
3. Package 调用卡只在 registry 清单的 `cards[]`（打包时 `--cards artifact-cdn/office-api-cards.jsonl`）。classpath seed **不含**云端包卡；`WeizhiPackageSync.refreshIndex` 写入 `source=registry`。
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
- Weizhi：[#35](https://github.com/fengshihao/weizhi/issues/35)（P3 官方 Registry manifest + `PackageRegistryClient` + `ensureInstalled`，与 #91 联调）
- 前置（已关闭）：[#89](https://github.com/fengshihao/agent1/issues/89) 内置 Package（PR #90）
