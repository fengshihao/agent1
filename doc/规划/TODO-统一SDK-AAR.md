# TODO：统一 SDK 制品（单 AAR，含微智，UI 可选）

> 状态：**规格已对齐，工程未做**（2026-10-03）。本 session **只记录**，不实现打包与发布。  
> 关联：[Agent1-SDK愿景.md](./Agent1-SDK愿景.md)、[WEIZHI.md](../集成/WEIZHI.md)、[CLI与Android-Agent能力对照.md](../CLI与Android-Agent能力对照.md)。

## 产品规格（共识）

1. **体积口径**：对外描述「核心约 **2MB**」时 **包含微智 Weizhi**（QuickJS native + agent-tools 等），与 Agent1 编排层 **合并为一个交付叙事**；具体数字需在 **固定 ABI + release + R8** 下实测后写入 README 脚注（避免与「仅 `java-agent-core` JAR ~0.4MB」混淆）。
2. **制品形态**：**一个（或一个主）Android Library AAR**，三方 `implementation` 后即可获得当前 Agent1 **已实现的生产力能力环**（Session、workspace 工具、`execute_script`、grep/glob/zip/bash、Skill、MCP、`webview_exec`、`capability_search`、JSONL 等），**不要求**自带 Compose 对话 UI。
3. **UI 可选**：聊天/会话列表/模型设置等 **仅保留在 `android_agent` Demo**（或未来独立 `agent1-ui` 可选模块）；集成方自行接 UI，或只调 `ProductivityAgentHost` / 薄封装 Gateway。

## 当前实现情况（能力已有，制品未合包）

| 能力 | 代码位置 | 是否在「单 AAR」里 |
|------|----------|-------------------|
| ReAct、Session、workspace、事件、能力检索 | `agent_core` → 发布为 **JAR** `java-agent-core` | JAR 已有；**未**打进 AAR |
| Weizhi 脚本/MCP/WebView 装配（Java 侧） | `java_agent/weizhi-bridge` + 从 weizhi 同步的 MCP/WebView 源 | **未**发布；桌面/测试用 |
| Android 上 `ProductivityAgentHost` + Weizhi 全环 | `android_agent/app/src/weizhi/.../WeizhiHostLoader.kt`、`WeizhiAgentTools` 等 | 在 **Demo app** 源码集，非独立 library |
| 无 Weizhi 时的 Host | `ProductivityHostAssembly` → 裸 `ProductivityAgentHost` | 在 **Demo app** |
| 线程安全的 Android 门面（无 UI） | `ProductivityAgentGateway` | 在 **Demo app** `logic.business` |
| Compose 对话 UI | `android_agent/app/.../ui/**` | **不应**进 SDK AAR |

结论：**函数与装配逻辑大体已实现**，缺口是 **模块边界 + 依赖收敛 + Maven/AAR 发布**，不是从零写 Host。

## 目标架构（待实现）

```text
com.agent1:agent1-android-sdk  (android-library AAR, 对外主制品)
  ├── api: ProductivityAgentHost 等（来自 java-agent-core，可 fat 或 api 传递）
  ├── 嵌入/传递: Weizhi AAR + .so（per ABI）
  ├── 含: weizhi-bridge 的 Android 适用部分 + WeizhiHostLoader 同类装配
  ├── 含: agent-home 资源（或继续由 core JAR 携带）
  └── 公开入口建议:
        Agent1Sdk.createHost(context, agentRoot, config) → ProductivityAgentHost
        可选: Agent1AndroidGateway（从 Demo 抽出，无 Compose）

android_agent/app          → 仅 Demo + 可选 UI（依赖 agent1-android-sdk）
```

## 实现清单（专门 PR / 里程碑）

### P0 — 三方能接依赖

- [ ] 新建 `android_agent/agent1-sdk/`（或仓库根 `agent1-android-sdk/`）`com.android.library` 模块。
- [ ] 将 `ProductivityHostAssembly`、`WeizhiHostLoader`、`WeizhiAgentTools`、相关 `platform/` 装配 **迁出 `app`**，放入 SDK 模块（遵守分层规则）。
- [ ] SDK 模块 **api/implementation** 依赖：`java-agent-core`、Weizhi 各子模块（或预编译 Maven），合并 **weizhi-bridge** 中 Android 需要的类（避免 Demo 再直接依赖 bridge 源码）。
- [ ] `publish` 任务：发布 **`com.agent1:agent1-android-sdk:<version>`** AAR 到本地 Maven / 后续 Central；扩展 `publish-java-agent-core.sh` 或新脚本 `publish-agent1-android-sdk.sh`。
- [ ] **ProGuard / consumer-rules** 与 `WEIZHI_INTEGRATED` 单一真源（SDK 始终 integrated，Demo 仅开关 UI）。

### P1 — 规格与文档

- [ ] 在 CI 或 release 脚本中记录 **AAR + 传递 native** 体积（arm64-v8a 为默认口径），更新 README「~2MB（含微智）」脚注。
- [ ] 三方集成 Checklist：`agentRoot`、`Agent1Sdk.createHost`、订阅 `observeEvents`、不接 UI 的最小 Activity 示例。
- [ ] 更新 [Agent1-SDK愿景.md](./Agent1-SDK愿景.md) §6：将「BOM / core+bridge」改为以 **agent1-android-sdk** 为主制品。

### P2 — 可选拆分

- [ ] 独立 **`agent1-ui-compose`**（可选依赖）：从 Demo 抽会话列表/聊天/设置，供要默认 UI 的客户使用。
- [ ] 桌面仍用 JAR + weizhi-bridge；**不强行**与 Android AAR 同模块。

## 本 session 决策

- **不在本 session 实现** 合包与发布；避免与进行中的 README / Pages 文档 PR 搅在一起。
- 下一迭代用 **单独 `feature/agent1-android-sdk-aar`**（或类似）按上表 P0 推进。

## 参考命令（现状，非目标制品）

```bash
./publish-java-agent-core.sh   # 仅 java-agent-core JAR → local-maven
./sync-weizhi.sh && cd android_agent && ./gradlew :app:assembleDebug   # 全能力在 Demo APK，非单 AAR
```
