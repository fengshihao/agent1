# Agent1 与微智 Weizhi

[Weizhi（之谓 · 微智）](https://github.com/fengshihao/weizhi) 是 Agent1 生产力路径的 **脚本与工具执行引擎**：端上 **QuickJS**（C + JNI）、沙箱 `fs`、平台 **Caps**，以及 **`:agent-tools`** 工具环（grep / glob / zip / bash、`execute_script`、Skill、WebView）。MCP 客户端在 Weizhi 引擎内（`mcp.connect`），配置与能力索引由 Agent1 负责。

Agent1 **`java-agent-core`** 负责 LLM 对话、Session、工作区 Java Tool、`capability_search`、JSONL 审计；**不重复实现** JS 运行时与设备能力桥。二者通过 **`java_agent/weizhi-bridge`** 与 Android `app/src/weizhi/` 装配。

## 职责分界

| 层级 | 仓库 | 典型能力 |
|------|------|----------|
| 编排与数据 | **agent1** | `ProductivityAgentHost`、`read_file`…、`chat_history`、`read_agent_doc`、`capability_search`、晋升 / catalog 数据面 |
| 执行与端能力 | **weizhi** | `WeizhiEngine.runJs`、`execute_script`、`$tools.*`、`$mcp.*`、`webview_exec`、office/catalog 脚本、`caps`（如 `android.files.*`） |

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

## 进一步阅读

- Weizhi 集成指南：[weizhi `docs/INTEGRATION_FOR_AI.md`](https://github.com/fengshihao/weizhi/blob/master/docs/INTEGRATION_FOR_AI.md)
- Agent1 双端能力表：[CLI与Android-Agent能力对照.md](../CLI与Android-Agent能力对照.md)
- 路径策略（fs / import / 工具环 / Caps）：[WEIZHI_PATHS.md](./WEIZHI_PATHS.md)（[agent1#72](https://github.com/fengshihao/agent1/issues/72)）
- Catalog / docx / pptx / grep 沙箱：[WEIZHI_CATALOG_MODULES.md](./WEIZHI_CATALOG_MODULES.md)、[WEIZHI_DOCX.md](./WEIZHI_DOCX.md)、[WEIZHI_PPTX.md](./WEIZHI_PPTX.md)、[WEIZHI_GREP_GLOB_SANDBOX.md](./WEIZHI_GREP_GLOB_SANDBOX.md)
