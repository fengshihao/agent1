# AGENTS.md — Agent1 机器契约

> 本文件优先于聊天里的口头约定。人类「一句话开工」入口：[docs/ai/START.md](docs/ai/START.md)。

## 项目是什么

**Agent1** 是面向 **Android / 嵌入式 JVM 宿主** 的轻量 **编程型生产力智能体**：`java-agent-core`（约 3MB 量级）提供 ReAct、会话、工作区、JSONL 审计；主路径为 **JS 编排**（`execute_script`）、**能力检索**（`capability_search`）、Skill / MCP / WebView 扩展。`android_agent` 为参考宿主；macOS / Ubuntu 上 `./agent1` 用于联调与 CI。

- **推荐产品路径**：`ProductivityAgentHost` / `ProductivityCli`（`--productivity`）
- **桌面经典 CLI**（`JavaAgentCli`）仅作开发辅助，新能力以生产力路径为准

## 默认允许改的路径

| 路径 | 用途 |
|------|------|
| `agent_core/**` | 核心运行时、工具、会话、事件 |
| `java_agent/**` | CLI、Gradle、Java 单测 |
| `android_agent/**` | Android Demo、Compose、分层包结构 |
| `doc/**`、`docs/**` | 人类文档与 AI 契约 |
| `scripts/**`、`.github/**`、`.cursor/**` | CI、安装、规则 |
| 根目录脚本与 `README.md`、`AGENTS.md`、`CONTRIBUTING.md` | 入口与契约 |

## 开工步骤（强制）

1. 读本文件 + [docs/ai/START.md](docs/ai/START.md) + [docs/ai/CHECKLIST.md](docs/ai/CHECKLIST.md)
2. **一个 PR 一件事**
3. 改 Java 行为 → 必须有/更新 **JUnit 单测**，并跑：
   ```bash
   ./java_agent/gradlew --no-daemon -p java_agent :core:test :cli:test
   ```
4. PR 前跑与 CI 同源的检查：
   ```bash
   ./scripts/ci-local.sh fast
   ```
   动 Android 组装链或大量 Android 代码时：
   ```bash
   ./scripts/ci-local.sh full
   ```
5. 按 [docs/ai/PR_PLAYBOOK.md](docs/ai/PR_PLAYBOOK.md) 开 PR

Cloud Agent 环境无 Android SDK 时：Java 单测 + `check-java-agent-static.sh` 可在 VM 内跑；**Android assemble 以 GitHub Actions 为准**（见 [doc/cloud-agent.md](doc/cloud-agent.md)）。

## 检查命令（与 CI 对齐）

| 命令 | 何时 |
|------|------|
| `./java_agent/gradlew -p java_agent :core:test :cli:test` | 任何 Java 逻辑变更 |
| `./check-java-agent-static.sh` | Java PMD + SpotBugs |
| `./check-android-agent-static.sh` | Android 分层、Detekt、主线程 Gateway |
| `./check-agent1-quality.sh` | 上述静态检查合集 |
| `./scripts/ci-local.sh fast` | 日常 PR 门禁 |
| `./scripts/ci-local.sh full` | 含 `android-assemble-debug` 等价步骤 |
| `./scripts/cloud-agent-verify.sh` | 无 Gradle 的轻量校验 |

**禁止**在检查未通过时 push 或标记 PR 可合并。

## 代码与架构规则

- 保持改动 **最小且聚焦**
- 桌面官方支持 **macOS 与 Ubuntu**；工具改动需考虑跨平台（经典 bash 仍兼顾 Windows 时勿破坏）
- 模型调用链改动须保持 **JSONL 事件字段** 不回退
- **Android 分层**（强制）：见 [`.cursor/rules/android-layering.mdc`](.cursor/rules/android-layering.mdc)
  - `ui.view` 不做直接 IO；IO 在 `logic.data`
  - 包路径与目录一致；禁止下层依赖 `ui.*`
  - 新交互能力：**先 API 级自测**（状态机、事件顺序、取消路径），UI 测次之
- 新增行为同步更新 **README** / **doc/** 相关篇

## Commit / PR 约定

- Commit：中文或英文均可，说明**为什么**；一行主题，必要时正文补充
- 分支：`feature/xxx` 或 `fix/xxx`（Cloud Agent 可用 `cursor/xxx-dea4`）
- PR 正文必须含：**摘要**、**用户可见变化**、**Test plan**（贴出跑过的命令与结果）
- 若由 AI 辅助，写明读过 **AGENTS.md** 与 **CHECKLIST**

## 安全

- 禁止提交 API Key、token、私钥、`.env` 实值
- 安全问题勿在公开 issue 贴利用细节

## 语言

- 文档默认 **中文**；README 含 **English** 小节
- 用户可见 Android 字符串与注释风格与现有模块保持一致
