# java_agent

Java 版状态化 Agent 内核（MVP），对齐 `pi-mono/packages/agent` 的核心能力：

- 状态化运行时（消息、工具、流式状态、错误状态）
- 事件流（agent/turn/message/tool 生命周期）
- OpenAI 兼容流式调用（可直连 OpenAI，也可连阿里 OpenAI 兼容接口）
- 工具调用循环（assistant tool call -> execute -> toolResult -> 下一轮）
- `continueRun()` 和 `abort()` / `waitForIdle()`

## 依赖策略

- `okhttp + okhttp-sse`
- `jackson-databind`
- `rxjava3`（用于多路流组合和事件桥接）
- 无 Spring / Reactor 依赖

## 构建与测试

```bash
gradle -p java_agent :core:test :cli:test
```

Java 版本要求：17（已在 `java_agent/build.gradle.kts` 与 `java_agent/gradle.properties` 固定）。

如果你的 `~/.gradle/gradle.properties` 里也配置了 `org.gradle.java.home`，可能会覆盖项目配置，可用下面命令强制本次构建走 17：

```bash
gradle -Dorg.gradle.java.home="/Users/fengshihao/.jdks/jdk-17.jdk/Contents/Home" -p java_agent test
```

## PC 最小示例

入口：`com.agent1.javaagent.examples.PcCliExample`

环境变量（OpenAI 或阿里兼容接口任选）：

- `OPENAI_API_KEY` 或 `DASHSCOPE_API_KEY`
- `OPENAI_BASE_URL` 或 `DASHSCOPE_BASE_URL`
- `OPENAI_MODEL`（可选）

## Java CLI（仿 Python agent.py）

入口：`com.agent1.javaagent.cli.JavaAgentCli`

支持：
- 单次模式：`gradle -p java_agent runJavaAgentCli --args="帮我查看当前目录文件"`
- 交互模式：`gradle -p java_agent runJavaAgentCli`
- 非流式：`gradle -p java_agent runJavaAgentCli --args="--no-stream 你好"`
- 快捷命令（仓库根目录）：`./java-agent` 或 `./java-agent --no-stream 你好`
- 标准入口（推荐）：`java_agent/bin/java-agent`

fat jar（可单独运行）：
- 构建：`gradle -p java_agent :cli:fatJar`
- 产物：`java_agent/cli/build/libs/cli-0.1.0-SNAPSHOT-all.jar`
- 运行单次：`java -jar java_agent/cli/build/libs/cli-0.1.0-SNAPSHOT-all.jar --no-stream 你好`
- 运行交互：`java -jar java_agent/cli/build/libs/cli-0.1.0-SNAPSHOT-all.jar`
- 目录内脚本（自动构建并优先使用本机 JDK17）：`java_agent/bin/java-agent --no-stream 你好`
  - 默认每次都会先执行 `fatJar`（Gradle 增量，源码变更会自动重建）
  - 如需跳过构建：`JAVA_AGENT_SKIP_BUILD=1 java_agent/bin/java-agent`

内置工具（当前按 mac 场景）：
- `run_bash`：按系统自动选择最合适 shell（mac 优先 zsh，Linux 优先 bash，Windows 自动回退）
- `run_python`：执行 Python 脚本（`script` 或 `file_path`，按系统自动选择 python 启动器）

必需环境变量（二选一）：
- `DASHSCOPE_API_KEY`（推荐，默认走阿里兼容端）
- `OPENAI_API_KEY`

可选环境变量：
- `ALIBABA_BASE_URL`（默认 `https://dashscope.aliyuncs.com/compatible-mode/v1`）
- `OPENAI_BASE_URL`
- `OPENAI_MODEL`（默认 `qwen3.5-flash`）
- `AGENT1_LOG_FILE`（覆盖 JSONL 日志路径，默认 `./logs/agent1.jsonl`）
- `AGENT1_MAX_CONTEXT_MESSAGES`（发往 LLM 的 user/assistant/tool 消息最多保留最近 N 条；`0` 或未设置表示不截断。内存中的完整历史仍保留在 `AgentState`，仅请求上下文缩短；配合 SQLite 记忆可把 N 调小）

CLI 输出增强：
- 彩色状态提示（可通过 `NO_COLOR=1` 关闭）
- 工具调用与结果预览（终端显示截断）
- JSONL 结构化日志（`run_started/model_request/model_text_delta/tool_call/tool_result/model_response/run_completed`）

Skill（Claude Code 风格）支持：
- 自动发现项目目录下 `.claude/skills/*/SKILL.md`
- 交互模式或单次模式可用 `/skill-name 参数` 手动触发
- 当前支持变量替换：`$ARGUMENTS`、`$ARGUMENTS[N]`、`$0/$1...`、`${CLAUDE_SKILL_DIR}`
- 示例：`/.claude/skills/shell-helper/SKILL.md`（调用示例：`/shell-helper 查看当前仓库状态`）

## Android 接入方式

`core` 模块可发布到本地 Maven 并由 Android 工程以 artifact 依赖接入。

发布 core artifact（本地仓库）：

```bash
gradle -p java_agent publishCoreToLocalRepo
```

默认发布路径：`java_agent/build/local-maven`（artifact: `com.agent1:java-agent-core:0.1.0-SNAPSHOT`）。

一键发布并验证 Android 编译：

```bash
java_agent/bin/publish-core-and-verify-android
```

最小接入步骤：

1. 创建 `OpenAiCompatibleClient`（`baseUrl` 指向 OpenAI 或阿里兼容端点）
2. 构造 `AgentRuntime` 并注册工具
3. 通过 `subscribe(...)` 或 `observeEvents()` 订阅事件流，把 `MESSAGE_UPDATE` delta 增量渲染到 UI（Compose/Views 均可）
4. 在 `onStop/onDestroy` 调用 `abort()`，并在适当时机调用 `close()`

更多架构与事件时序见：`docs/JAVA_AGENT_ARCHITECTURE.md`。
