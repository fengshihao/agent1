# Agent1

轻量、可观测、可扩展的命令行智能体工程模板，基于 **Pydantic AI** + **Qwen**，支持工具调用（Shell / Python）、流式输出、JSONL 事件日志和 Token 用量保护。

## 项目定位

- 面向个人开发者/小团队的 Agent CLI 基础骨架
- 强调“可运行 + 可观测 + 可维护”
- 默认接入阿里云 DashScope（Qwen3.5-plus）

## 核心能力

- **CLI 交互**：单次问答、交互式会话、流式/非流式两种模式
- **工具调用**：`run_bash` / `run_python` 工具
- **可观测性**：JSONL 日志（每行一个事件），关键步骤可追踪
- **成本控制**：每次请求显示 token 用量，支持会话级 token 上限
- **跨平台适配**：macOS / Ubuntu / Windows（PowerShell）
- **环境感知提示词**：自动注入 OS / Python / Shell / CWD，提升生成命令正确率

## 技术栈

- Python 3.9+
- [Pydantic AI](https://ai.pydantic.dev/)
- [Rich](https://rich.readthedocs.io/)
- DashScope / Qwen（通过 Pydantic AI Alibaba Provider）
- 包管理与运行：`uv`

详细见：[技术栈说明](docs/TECH_STACK.md)

## 快速开始

### 1) 安装依赖

```bash
cd agent1
uv sync
```

### 2) 配置模型环境变量

```bash
export DASHSCOPE_API_KEY='your-api-key'
# 或
export ALIBABA_API_KEY='your-api-key'
```

默认使用中国区端点；若使用国际区：

```bash
export ALIBABA_BASE_URL='https://dashscope-intl.aliyuncs.com/compatible-mode/v1'
```

### 3) 运行

```bash
# 单次（流式）
uv run agent1 "列出当前目录文件"

# 单次（非流式）
uv run agent1 "1+1等于几" --no-stream

# 交互模式
uv run agent1
```

若已全局安装：`uv tool install -e .` 后可直接运行 `agent1`。

## 配置项

| 变量名 | 作用 | 默认值 |
|---|---|---|
| `DASHSCOPE_API_KEY` / `ALIBABA_API_KEY` | 模型 API Key | 无（必填其一） |
| `ALIBABA_BASE_URL` | DashScope 端点 | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `AGENT1_LOG_FILE` | JSONL 日志文件路径 | `logs/agent1.jsonl` |
| `AGENT1_MAX_TOTAL_TOKENS` | 会话 token 上限 | 不限制 |

## 日志与可观测性

### 终端反馈

运行时会显示：

- 请求开始/结束
- 工具调用与返回
- token 用量表（本次 + 会话累计）

### JSONL 日志

- 默认路径：`logs/agent1.jsonl`
- 格式：一行一个 JSON 事件

查看日志：

```bash
tail -f logs/agent1.jsonl
```

Windows PowerShell：

```powershell
Get-Content .\logs\agent1.jsonl -Wait
```

关键事件：

- `run_started`
- `model_request`
- `model_text_delta`
- `tool_call`
- `tool_result`
- `usage`
- `model_response`
- `run_completed` / `run_failed`

## 项目结构

```text
agent1/
├── src/agent1/
│   ├── agent.py              # Agent 初始化、模型配置、系统提示词
│   ├── cli/main.py           # CLI 入口、会话流程、用量展示
│   ├── tools/                # 工具实现（run_bash / run_python）
│   ├── logging_utils.py      # JSONL 日志写入
│   └── __init__.py
├── docs/
│   ├── ARCHITECTURE.md       # 架构说明
│   └── TECH_STACK.md         # 技术栈说明
├── pyproject.toml
└── README.md
```

详细见：[架构设计说明](docs/ARCHITECTURE.md)
详细变更见：[Changelog](CHANGELOG.md)；开源协议见：[MIT License](LICENSE)。

## 跨平台说明

- `run_python` 使用 `sys.executable` 启动 Python 子进程，避免系统命令差异
- `run_bash` 自动适配：
  - Windows：PowerShell
  - Linux/macOS：优先 bash，回退 sh
- 系统提示词会注入运行环境信息，模型可据此生成更稳妥的命令

## Roadmap

- [ ] 增加工具白名单/黑名单与安全策略
- [ ] 增加测试（单元测试 + 集成测试）
- [ ] 增加多模型切换策略与回退策略

## 贡献

欢迎提交 Issue / PR。请先阅读：[贡献指南](CONTRIBUTING.md)

---
如果这个项目对你有帮助，欢迎 star ⭐
