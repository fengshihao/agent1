# Agent1

基于 Pydantic AI 的智能体，支持 bash/python 工具，RxPy 流式接口，Rich Markdown 命令行输出。

## 环境与依赖（uv）

使用 [uv](https://docs.astral.sh/uv/) 管理依赖和环境：

```bash
cd agent1
uv sync
```

配置环境变量（阿里云 DashScope）：
```bash
export DASHSCOPE_API_KEY='your-api-key'
# 或
export ALIBABA_API_KEY='your-api-key'
```

默认使用中国区端点。若使用国际区，设置：
```bash
export ALIBABA_BASE_URL='https://dashscope-intl.aliyuncs.com/compatible-mode/v1'
```

## 使用

**单次模式**（流式输出）：
```bash
uv run agent1 "列出当前目录的文件"
```

**单次模式**（非流式）：
```bash
uv run agent1 "1+1等于几" --no-stream
```

**交互模式**：
```bash
uv run agent1
# 进入 REPL，输入 /exit 退出
```

若已全局安装：`uv tool install -e .` 后可直接运行 `agent1`。

## 跨平台说明（macOS / Ubuntu / Windows）

- `run_python` 使用当前解释器（`sys.executable`），避免 Ubuntu 上无 `python` 命令的问题。
- `run_bash` 在不同系统会自动适配：
  - Windows：使用 PowerShell 执行
  - Linux/macOS：优先 `bash`，没有则回退系统 `sh`
- 系统提示词会自动注入当前环境信息（OS、Python、Shell、CWD），帮助模型按环境生成命令和代码。

## 运行反馈与日志

- 运行时会在终端显示关键状态（开始请求、工具调用、完成/失败）。
- 日志采用 **JSONL**（一行一个 JSON）格式。
- 默认日志文件：`logs/agent1.jsonl`
- 可通过环境变量自定义路径：

```bash
export AGENT1_LOG_FILE='/absolute/path/agent1.jsonl'
```

查看实时日志：

```bash
tail -f logs/agent1.jsonl
```

Windows PowerShell 可用：

```powershell
Get-Content .\logs\agent1.jsonl -Wait
```

关键日志事件：
- `run_started`
- `model_request`
- `model_text_delta`（流式）
- `tool_call`
- `tool_result`
- `usage`（本次/会话 token 用量）
- `model_response`
- `run_completed` / `run_failed`

Token 预算保护（防止异常消耗）：

```bash
export AGENT1_MAX_TOTAL_TOKENS=20000
```

- 每次调用后终端会显示“本次 + 会话累计” token 表格。
- 当会话累计接近上限会预警，达到上限会停止交互模式继续请求。

## 架构

- `agent.py` - Pydantic AI Agent，注册 bash、python 工具
- `agent_rx/` - 将 `run_stream_events` 转为 RxPy Observable
- `cli/` - 命令行入口，Rich Markdown 渲染

## 依赖

- pydantic-ai
- rx (RxPy)
- rich
