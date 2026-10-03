# Agent1 SDK 愿景（讨论稿）

> 状态：**记录愿景，后续实现**（2026-09-30）。与 [自进化 Agent 规划](./自进化Agent/README.md)、[17-能力检索](./自进化Agent/17-能力检索-capability-search.md) 配套阅读。

## 1. 我们要卖给三方什么

**一个可嵌入的「编程型 ReAct 智能体」运行时**，而不是一个必须带固定 UI 的 App。

- **核心制品**：Maven / AAR **`java-agent-core`**（代码）+ 首次启动落地的 **`agentRoot`**（数据与能力目录）。
- **推荐执行引擎**：Weizhi QuickJS（`execute_script` + Caps + catalog 脚本）；宿主可按平台替换 `ScriptEngineFactory`。
- **开箱行为**：创建 `ProductivityAgentHost` → 会话 / Run / 工具循环 / JSONL 审计 / 默认 productivity 提示词 → 三方只需接 **LLM 客户端** 与（可选）**UI**。

三方 **不应** 需要理解 classpath 上的 `agent-home` 资源包名；那是 SDK 内嵌模板，经 `AgentHomeBootstrap.ensure(agentRoot)` 拷贝到磁盘。

## 2. 概念分层（避免 agent_core / agent-home / agentRoot 混淆）

| 名称 | 性质 | 三方集成时 |
|------|------|------------|
| **agent_core** | JAR/AAR 中的 **代码**（Runtime、Host、Tool、Prompt、Session） | `implementation("com.agent1:java-agent-core:…")` |
| **agent-home** | 打在 core 里的 **默认资源**（系统 doc、bootstrap 脚本等） | 无单独依赖；随 core 发布 |
| **agentRoot** | 设备上 **持久目录**（sessions、shared、catalog、docs、logs） | 传入 `ProductivityAgentHost(agentRoot, …)`，通常 `context.filesDir/...` |

**ReAct / tool call** 在 `AgentRuntime`；**编排入口** 在 `ProductivityAgentHost`。这不是与 agent-home 并列的第三个 SDK。

## 3. 产品形态：编程智能体优先

长期默认策略（与能力检索规划一致）：

1. **主路径**：模型写 **workspace 内 JS orchestrator**（`execute_script` file 模式），调用 Caps、`fs`、catalog 脚本、`host.ensureNative`、`$tools` 白名单等。
2. **Java Tool 极简**：工作区 I/O、脚本执行、用户澄清（`ask_user`）、**能力检索**、必要的 catalog/skill 只读与区外写入 API。
3. **能力不灌进系统提示**：提示词只给 **大类地图 + 必须先检索**；细节走 `capability_search` + `read_agent_doc` / skill 正文。

## 4. 三方最小集成面（目标 API，稳定后文档化）

```text
ProductivityAgentHost(
  agentRoot,
  AgentRuntimeConfig,
  LlmClient,
  ScriptEngineFactory?,      // 如 WeizhiAndroidScriptEngineFactory
  scriptToolBridge?,
  WorkspaceToolProvider?,    // 扩展工具，宜少
)
  → createSession / switchSession
  → runUserMessage(text) → runId
  → runtime().observeEvents()   // UI 订阅
  → abortActiveRun()
```

**宿主责任（非 core 内完成）**：

- LLM 鉴权与网络
- Android：`AndroidCaps.Session` 的 share / picker 等 UI 接线（见 Weizhi INTEGRATION_FOR_AI）
- 可选：聊天 UI、FileProvider 分享、附件解析（参考 `android_agent` Demo，非 SDK 必选）

## 5. 与 Weizhi 的关系

| 组件 | 职责 |
|------|------|
| **Agent1 SDK** | ReAct、会话、工作区、审计、提示词、工具编排、能力索引 |
| **Weizhi** | JS 运行时 + 平台 Caps + 部分 Agent 工具环（grep/bash/webview/MCP） |

Weizhi 的 `android.*` 等 **不是** LLM 的 `@Tool` 列表项；仅在 **脚本内** 可用。SDK 文档须写清这一边界。

## 6. 发布与版本（待办）

> **规格对齐（2026-10-03）**：Android 三方主制品目标为 **单 AAR（含微智）**、**不含对话 UI**；详见 [TODO-统一SDK-AAR.md](./TODO-统一SDK-AAR.md)。

- [ ] 主制品 **`com.agent1:agent1-android-sdk`**（android-library AAR：core + weizhi-bridge 装配 + Weizhi 传递依赖 / native）
- [ ] 保留 **`java-agent-core` JAR** 供桌面与纯 Java 宿主；版本与 Android SDK 对齐
- [ ] **agent-home 升级策略**：bootstrap `copyIfMissing` vs 版本迁移说明
- [ ] 三方集成 Checklist + **无 UI** 最小 Activity 示例
- [ ] 可选模块 **`agent1-ui-compose`**：Demo 聊天 UI，与 SDK 解耦
- [ ] 体积基线：release + R8 + arm64，**含微智**，写入 README（目标叙事约 2MB，以实测为准）

## 7. 不在首版 SDK 承诺内

- 完整聊天 UI、微信分享一键（需宿主 Activity + 文件 Intent）
- Cloud Agent 托管（与嵌入式 SDK 不同交付物）
- 向量能力检索（见 17 文档 Phase C 可选）

## 8. 关联文档

- [17-能力检索-capability-search.md](./自进化Agent/17-能力检索-capability-search.md) — 编程智能体下的能力发现
- [07-系统提示词与环境摘要.md](./自进化Agent/07-系统提示词与环境摘要.md) — 提示词分层
- [WEIZHI 集成入口](../集成/WEIZHI_DOCX.md) — docx / 脚本目录
- Android 参考：`ProductivityAgentGateway`、`WeizhiHostLoader`
