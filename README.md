# Agent1

> A production-friendly CLI agent starter powered by Pydantic AI and Qwen.

[![Python](https://img.shields.io/badge/python-3.9%2B-blue.svg)](https://www.python.org/)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Package Manager](https://img.shields.io/badge/package%20manager-uv-purple.svg)](https://docs.astral.sh/uv/)
[![Model](https://img.shields.io/badge/model-Qwen3.5--plus-orange.svg)](https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope)

`agent1` 是一个轻量、可观测、跨平台的命令行智能体工程模板，默认接入阿里云 DashScope（Qwen3.5-plus），并提供工具调用、结构化日志、token 用量监控与预算保护能力。

## Why Agent1

- 开箱即用的 **CLI Agent**（单次/交互、流式/非流式）
- 内置 **工具调用**：Shell 命令与 Python 脚本执行
- 完整 **可观测性**：JSONL 事件日志 + run_id 追踪
- 强化 **成本控制**：每轮展示 token 用量，支持会话预算上限
- **跨平台适配**：macOS / Ubuntu / Windows
- **环境感知提示词**：自动将 OS / Python / Shell / CWD 注入系统提示词

## Table of Contents

- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Usage](#usage)
- [Architecture](#architecture)
- [Observability](#observability)
- [Cross-platform Behavior](#cross-platform-behavior)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Quick Start

### 1) Install dependencies

```bash
cd python_agent
uv sync
```

若你已处在仓库根目录、不想 `cd`，可使用：`uv sync --project python_agent`。

### 1.5) One-click install (from scratch)

Linux / Ubuntu / macOS / Windows Git Bash:

```bash
curl -fsSL https://raw.githubusercontent.com/fengshihao/agent1/refs/heads/master/install-python-agent.sh | sh
```

Windows PowerShell:

```powershell
irm https://raw.githubusercontent.com/fengshihao/agent1/refs/heads/master/install-python-agent.ps1 | iex
```

Notes:

- 安装入口为仓库根 `install-python-agent.sh` / `install-python-agent.ps1`（薄转调）；实现源码在 `python_agent/scripts/install.*`。
- The installer auto-installs `uv` if missing.
- If the current package index fails, the installer clears `UV_*` index env vars and retries with official PyPI (`https://pypi.org/simple`).
- Override install source via `AGENT1_GIT_URL` when needed（默认已带 `#subdirectory=python_agent`，指向本仓库中的 Python 包）。
- After install, reopen terminal if `agent1` is not found in `PATH`.
- If your network path serves stale raw cache, add a timestamp query to bypass cache:
  - `curl -fsSL "https://raw.githubusercontent.com/fengshihao/agent1/refs/heads/master/install-python-agent.sh?v=$(date +%s)" | sh`

### 2) Configure model credentials

```bash
export DASHSCOPE_API_KEY="your-api-key"
# or
export ALIBABA_API_KEY="your-api-key"
```

Optional (international endpoint):

```bash
export ALIBABA_BASE_URL="https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
```

### 3) Run

在 `python_agent` 目录下（与 `uv sync` 相同）：

```bash
cd python_agent

# streaming single-run
uv run agent1 "列出当前目录文件"

# non-streaming single-run
uv run agent1 "1+1等于几" --no-stream

# interactive session
uv run agent1
```

在仓库根目录下可等价执行：

```bash
uv run --project python_agent agent1 "列出当前目录文件"
```

若已通过安装脚本或 `uv tool install` 将 CLI 装到全局环境，可直接运行 `agent1`。

## Configuration

| Variable | Description | Default |
|---|---|---|
| `DASHSCOPE_API_KEY` / `ALIBABA_API_KEY` | Model API key (one required) | None |
| `ALIBABA_BASE_URL` | DashScope endpoint | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `AGENT1_LOG_FILE` | JSONL log file path | `logs/agent1.jsonl` |
| `AGENT1_MAX_TOTAL_TOKENS` | Session token budget guard | Unlimited |

## Usage

### Common workflows

```bash
cd python_agent

# ask a coding question
uv run agent1 "帮我写一个读取 JSON 文件并校验字段的 Python 脚本"

# force non-stream mode
uv run agent1 "解释一下这段日志" --no-stream
```

### Session controls

- `Ctrl + C` or `Ctrl + D`: exit interactive mode
- Budget exceeded (`AGENT1_MAX_TOTAL_TOKENS`): session stops automatically

## Architecture

```mermaid
flowchart TB
    User[User Input] --> CLI[CLI Layer]
    CLI --> Agent[Agent Runtime]
    Agent --> Model[Qwen via DashScope]
    Agent --> Tools[run_bash / run_python]
    CLI --> Logs[JSONL Logging]
    CLI --> Usage[Token Guard]
```

- CLI orchestrates user interaction, status updates, and output rendering
- Agent layer handles prompt, model invocation, and tool orchestration
- Observability layer writes structured events for debugging and auditing

See [Architecture Details](docs/ARCHITECTURE.md) and [Tech Stack](docs/TECH_STACK.md).

## Observability

### Runtime feedback

Terminal output includes:

- request lifecycle status
- tool call / tool result markers
- token usage table (per-run + session cumulative)

### Structured logs (JSONL)

- Default path: `logs/agent1.jsonl`
- Format: one JSON object per line

```bash
tail -f logs/agent1.jsonl
```

Windows PowerShell:

```powershell
Get-Content .\logs\agent1.jsonl -Wait
```

Key event types:

- `run_started`
- `model_request`
- `model_text_delta`
- `tool_call`
- `tool_result`
- `usage`
- `model_response`
- `run_completed` / `run_failed`

## Cross-platform Behavior

- `run_python` uses `sys.executable` to avoid `python` vs `python3` mismatch
- `run_bash` adapts by OS:
  - Windows: prefer `bash` (e.g., Git Bash), then fallback to `powershell` / `pwsh` / `cmd`
  - Linux/macOS: `bash` preferred, fallback to `sh`
- System prompt includes runtime context (OS, Python, Shell, CWD) to reduce invalid command generation

## Testing

Run unit tests:

```bash
cd python_agent
PYTHONPATH=src python -m unittest discover -s tests -v
```

The Windows shell adaptation tests are in `python_agent/tests/test_bash_tool.py`, covering:

- Git Bash preferred on Windows when available
- PowerShell fallback when bash is unavailable
- cmd fallback when neither bash nor PowerShell is available

## Project Structure

```text
<repo>/
├── python_agent/                 # Python CLI（Pydantic AI）
│   ├── pyproject.toml
│   ├── uv.lock
│   ├── src/agent1/
│   └── tests/
├── agent_core/                   # Java 核心库
├── java_agent/                   # Java CLI（Gradle 工程）
├── android_agent/                # Android 应用（Gradle 工程根）
├── install-python-agent.sh       # 根目录薄脚本：一键安装 Python CLI（→ python_agent/scripts）
├── install-python-agent.ps1
├── sync-python-agent.sh          # 根目录薄脚本：uv sync python_agent
├── build-android-agent.sh        # 根目录薄脚本：Android 编译安装启动（→ android_agent/run.sh）
├── check-android-agent-layering.sh  # 根目录薄脚本：Kotlin 分层检查（→ android_agent/scripts）
├── publish-java-agent-core.sh    # 根目录薄脚本：发布 core + 校验 Android 编译（→ java_agent/bin）
├── run-java-agent                # 根目录薄脚本：Java CLI fat-jar（→ java_agent/bin/java-agent）
├── run-java-agent-gradle         # 根目录薄脚本：Java CLI Gradle 直跑（→ runJavaAgentCli）
├── python_agent/scripts/        # install.sh / install.ps1（实现）
├── android_agent/scripts/       # check_android_layering.py（实现）
├── .github/
│   ├── workflows/ci.yml
│   └── ISSUE_TEMPLATE/
├── CHANGELOG.md
├── CONTRIBUTING.md
└── LICENSE
```

## Roadmap

- [ ] Add tool safety policies (allowlist / denylist / confirmation layer)
- [ ] Add unit + integration tests
- [ ] Add model fallback and retry strategy
- [ ] Add optional remote log sink integration

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## License

MIT License. See [LICENSE](LICENSE).

---

If this project helps you, a star is appreciated.
