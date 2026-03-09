# 技术栈说明

## 语言与运行时

- **Python 3.9+**
- 解释器管理与运行：**uv**

## AI 框架

- **Pydantic AI**
  - Agent 生命周期管理
  - Tool 调用编排
  - 流式事件接口（`run_stream_events`）

## 模型与 Provider

- **Qwen3.5-plus**（阿里云 DashScope）
- Provider：`AlibabaProvider`
- 模型 API：`OpenAIChatModel`

## CLI 与渲染

- **Rich**
  - Markdown 渲染
  - 实时输出
  - Token 用量表格

## 日志与观测

- **JSONL 文件日志**（本地）
  - 适配 shell 工具快速查看
  - 便于后续接入集中日志系统

## 构建与发布

- `pyproject.toml` + `hatchling`
- CLI 入口：`agent1 = "agent1.cli.main:main"`

## 为什么选这套栈

- Pydantic AI：上手快，事件与工具能力完善
- Rich：终端体验好，交互反馈清晰
- JSONL：简单稳妥，排障成本低
- uv：依赖管理和执行体验统一
