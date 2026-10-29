# Agent1 与微智 Weizhi

[Weizhi（之谓 · 微智）](https://github.com/fengshihao/weizhi) 是 Agent1 生产力路径的 **脚本引擎**：端上 **QuickJS**（C + JNI）、沙箱 `fs`、平台 **Caps**，以及脚本内的 `mcp.connect`。MCP 的服务器列表、缓存和模型侧检索在 Agent1。

给模型调用的 `grep` / `glob` / `zip_extract` / `zip_create` / `bash` 实现在 **`java-agent-core`**（`@Tool`，经 `AnnotatedTools` 收成运行时的 `AgentTool`）。`WeizhiWorkspaceTools` 与 Android `WeizhiAgentTools` 在集成 Weizhi 时注册它们。Skill 加载统一走宿主注册的 `load_skill`（按名读 SKILL.md 或其内资源；本地没有自动从官方 registry 安装），不再单独注册 `load_skill_through_path`。`webview_exec` 的执行体在 Agent1（桌面 CDP、Android 系统 WebView）。Weizhi 不提供模型工具模块。

Agent1 **不实现** JS 运行时和设备 Caps。二者通过 **`java_agent/weizhi-bridge`** 与 Android `app/src/weizhi/` 装配。

## 职责分界

| 层级 | 仓库 | 典型能力 |
|------|------|----------|
| 编排与模型工具 | **agent1** | `ProductivityAgentHost`、工作区读写、`grep` / `glob` / `zip` / `bash`、`load_skill`、`find_caps`、审计 |
| 脚本与端能力 | **weizhi** | `WeizhiEngine.runJs`、脚本内 `fs` / `$mcp` / Caps；`webview_exec` 的平台执行体由宿主接上 |

**重要边界**：Weizhi 的 `android.*` 等 **Caps 只在 QuickJS 脚本内可用**，不会逐个注册成 LLM 的 Java `@Tool`，以免提示词与权限面失控。模型应写脚本或在检索后调用已暴露的 Agent 工具。

## 获取与编译

```bash
./sync-weizhi.sh    # 默认克隆 https://github.com/fengshihao/weizhi.git → agent1/weizhi
```

| 变量 | 说明 |
|------|------|
| `WEIZHI_GIT_URL` | 使用 fork / 私有镜像 |
| `WEIZHI_SKIP_SYNC=1` | CI 或离线跳过拉取 |
| `AGENT1_WEIZHI_REPO` | 运行时指定 weizhi 根目录（桌面联调、测试脚本） |

**Android**

- **源码联编**（与 GitHub CI 默认一致）：`./sync-weizhi.sh` 后 `cd android_agent && ./gradlew :app:assembleDebug` → `BuildConfig.WEIZHI_INTEGRATED=true`
- **Maven 预编译**：[`android_agent/weizhi-prebuilt/README.md`](../../android_agent/weizhi-prebuilt/README.md)，适合无 NDK 环境

**桌面 CLI**

- 构建含 `weizhi-bridge` 的 CLI；`WeizhiJniBootstrap` 成功后才有完整脚本环
- `AGENT1_SCRIPT_ENGINE=off` 可关闭脚本引擎

**成品包（给内容仓 / 外部 JVM 测试）**

```bash
./publish-weizhi-bridge.sh
```

发布 `com.agent1:weizhi-bridge:0.1.0-SNAPSHOT` 到 `java_agent/build/local-maven` 与 `mavenLocal`（POM 带上 `java-agent-core`）。JAR 里含 Weizhi Java 与桥接代码，**不含** `libweizhijni`；跑脚本仍要本机 weizhi native。

## 进一步阅读

- Weizhi 集成指南：[weizhi `docs/INTEGRATION_FOR_AI.md`](https://github.com/fengshihao/weizhi/blob/master/docs/INTEGRATION_FOR_AI.md)
- Agent1 双端能力表：[CLI与Android-Agent能力对照.md](../CLI与Android-Agent能力对照.md)
- 路径策略（fs / import / 工具环 / Caps）：[WEIZHI_PATHS.md](./WEIZHI_PATHS.md)（[agent1#72](https://github.com/fengshihao/agent1/issues/72)）
- **Weizhi Package**（`import … from "weizhi:<name>"`，云端 registry 动态安装）：[WEIZHI_CATALOG_MODULES.md](./WEIZHI_CATALOG_MODULES.md)、[WEIZHI_GREP_GLOB_SANDBOX.md](./WEIZHI_GREP_GLOB_SANDBOX.md)
- **weizhi:video**（随 `:weizhi-video` AAR，video@0.6.0；v2 时间线关键帧 / crossfade）：[WEIZHI_CATALOG_MODULES.md](./WEIZHI_CATALOG_MODULES.md) video 例外段；规格真源 [weizhi `docs/video-timeline-v2.md`](https://github.com/fengshihao/weizhi/blob/master/docs/video-timeline-v2.md)（[agent1#101](https://github.com/fengshihao/agent1/issues/101)）
- **官方 Registry**（微智客户端已实现，发布在 `artifact-cdn/`）：[WEIZHI_PACKAGE_REGISTRY.md](./WEIZHI_PACKAGE_REGISTRY.md)
