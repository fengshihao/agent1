# DeepSeek 省钱 E2E（OpenAI 兼容）

> **勿把 API Key 写进仓库或文档。** 仅在 shell / CI Secret 里设置。Key 若曾在聊天里暴露，建议在控制台 **轮换**。

## 环境变量（示例）

```bash
export OPENAI_API_KEY="（仅本地，勿提交）"
export OPENAI_BASE_URL="https://api.deepseek.com"
export OPENAI_MODEL="deepseek-flash"   # 以 DeepSeek 控制台可用 id 为准

export AGENT1_AGENT_ROOT="/tmp/agent1-e2e-$$"
export AGENT1_COACH=1

# 省钱：先小后大；结构问题用代码/工具修，不靠堆提示词
export AGENT1_MAX_TURNS_PER_RUN=4
export AGENT1_MAX_TOOL_CALLS_PER_RUN=8
export AGENT1_MAX_CONTEXT_TURNS=6
```

也可用 `QWEN_*` / `OPENAI_*` 别名（见 `EnvAgentRuntimeConfigLoader`）；**Base URL 必须用 `OPENAI_BASE_URL` 或 `QWEN_BASE_URL`** 指向 DeepSeek。

## 验证配置（不耗模型）

```bash
./agent1 models
./agent1 tools
```

确认打印的 baseUrl、model、maxTurns 与 env 一致。

## 跑 UC 时

1. 单 UC 单命令：`./agent1 "用户原话"`，不要一上来长 REPL。  
2. 跑完必看：`./agent1 logs` 或 `tail "$AGENT1_AGENT_ROOT/logs/events.jsonl"`。  
3. 记录到 `e2e-runs/YYYY-MM-DD_UC-xx.md`（脱敏）。

## 日志里要优化的信号（改结构，非改提示词凑 case）

| 现象 | 优先改什么 |
|------|------------|
| 多轮重复 `read_file` 同一文档 | 提示词摘要 / `read_agent_doc` 索引（REQ-010～012） |
| 反复失败 tool 再盲试 | Coach、结构化 tool 错误（REQ-030、050） |
| 不该 read 却读 huge 文件 | Coach `file.large_write`、capabilities 摘要 |
| 模型轮次打满 | 降 maxTurns 同时修 tool 设计，避免靠加长 system 哄 |

## 与 Scripted LLM

开发期优先：`ScriptedLlmClient` / `ProductivityScripted*Test`（见 `java_agent/doc/mock-llm-automation.md`）跑通工具链，**再上真实 Key** 做 UC。
