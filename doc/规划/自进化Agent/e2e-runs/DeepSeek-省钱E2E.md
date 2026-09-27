# DeepSeek 省钱 E2E（OpenAI 兼容）

> **勿把 API Key 写进仓库或文档。** 仅在 shell / CI Secret 里设置。Key 若曾在聊天里暴露，建议在控制台 **轮换**。

## 环境变量（示例）

```bash
export OPENAI_API_KEY="（仅本地，勿提交）"
export OPENAI_BASE_URL="https://api.deepseek.com"
export OPENAI_MODEL="deepseek-flash"   # 官方推荐 id（见下「模型名」）

export AGENT1_AGENT_ROOT="/tmp/agent1-e2e-$$"
export AGENT1_COACH=1

# 省钱：先小后大；结构问题用代码/工具修，不靠堆提示词
export AGENT1_MAX_TURNS_PER_RUN=4
export AGENT1_MAX_TOOL_CALLS_PER_RUN=8
export AGENT1_MAX_CONTEXT_TURNS=6
```

也可用 `QWEN_*` / `OPENAI_*` 别名（见 `EnvAgentRuntimeConfigLoader`）；**Base URL 必须用 `OPENAI_BASE_URL` 或 `QWEN_BASE_URL`** 指向 DeepSeek。

## 模型名（DeepSeek 官方说明摘要）

| 请求 `model` | 说明 |
|--------------|------|
| **`deepseek-flash`** | **E2E 与开发默认使用此 id** |
| `deepseek-v4-flash`、`deepseek-v4-flash-vision-exp` | 仍可调用，但后端已切到 **DeepSeek-V4.1-Flash**，按 **Flash 价格**计费；勿在新脚本里继续用旧名 |

Agent1 通过 `OPENAI_MODEL=deepseek-flash` 传入即可（`./agent1 models` 应显示 `model=deepseek-flash`）。

## 计费时段（省钱排期）

**空闲时段价格为高峰的一半**（DeepSeek 定价规则）：

- **高峰（北京时间）**：周一至周五（不含中国法定节假日）**9:00–12:00、14:00–18:00**
- **空闲**：其余时段，含 **周末及法定节假日全天**

建议：**大批量 UC / 回归 E2E** 尽量排在空闲时段；开发中仍可用小 `maxTurns` 在高峰做冒烟。Cloud Agent 跑 E2E 时可对照北京时间决定是否开跑长测。

## 限速与并发（官方摘要）

文档：[限速与隔离](https://api-docs.deepseek.com/zh-cn/quick_start/rate_limit)

| 模型 | 账号并发上限（未完成请求计 1） |
|------|--------------------------------|
| **deepseek-flash** | **2500** |
| deepseek-v4-pro | 500 |

- 超限返回 **HTTP 429**；与 API Key 个数无关，按**账号**计。
- 可选请求体 **`user_id`**（OpenAI 兼容需放 `extra_body`）：内容安全 / KVCache / 调度隔离；**勿含隐私**；格式 `[a-zA-Z0-9\-_]+`，最长 512。
- **保活**：等待推理期间非流式会收到空行、流式会收到 SSE `: keep-alive`；**10 分钟未开始推理**会断连。

### 对 Agent1 E2E 的含义

| 做法 | 原因 |
|------|------|
| UC **串行**跑，避免并行多个 `./agent1` | 单账号开发一般远低于 2500，但并行 Cloud/本地混跑可能叠加 |
| 遇 **429** 退避重试，记录到 `e2e-runs` | 区分「产品 bug」与「配额/并发」 |
| 单 Run 控制 `maxTurns` / 工具次数 | 缩短「未完成请求」占用并发的时间 |
| **（待实现）** 生产力路径传 `user_id=sessionId` | 便于 DeepSeek 侧隔离；当前 `OpenAiCompatibleClient` **尚未**传 `extra_body.user_id`，见 backlog |

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
