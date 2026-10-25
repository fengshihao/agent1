# Changelog

All notable changes to this project are documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added
- **失败自动给「继续」入口**：上一轮 run 因网络 / SSE / API / 工具环异常收场、没有产出最终回答时，Android 与桌面 Web UI 都自动在输入条上方展示「继续」按钮（原先仅轮次或工具次数用尽时展示）。
- **重试兜底自愈**：新 run 启动前，自动清除 transcript 尾部连续的运行失败记录（「错误: …」）及其紧邻的「继续」重试指令并写回磁盘——连续重试失败不再堆积错误污染上下文，发给模型的是干净上下文。

### Removed
- **Python CLI 永久下线**：删除 `python_agent/` 与 `install-python-agent.*` / `sync-python-agent.sh`；只读快照见分支 `archive/python-agent`。

### Changed
- CI 改为在 **ubuntu-latest** 与 **macos-latest** 上运行 Java 测试。
- 产品方向明确为 **Java 桌面（macOS / Ubuntu）+ Android**，生产力能力在 `--productivity` 与 `doc/基础能力/` 跟踪。

### Documentation
- Aligned `java_agent/README.md` with the current CLI: removed obsolete `AGENT1_MEMORY_DB` / SQLite long-term memory tool description (feature no longer present).

## [0.1.0] - 2026-03-09

### Added
- Initial CLI agent based on Pydantic AI and Qwen (DashScope).
- Tooling support for shell command execution and Python script execution.
- Streaming and non-streaming interaction modes.
- JSONL structured logging for run lifecycle, tool calls, and model output.
- Token usage monitoring with per-run and session totals.
- Session-level token budget guard via `AGENT1_MAX_TOTAL_TOKENS`.
- Cross-platform command adaptation (PowerShell on Windows; bash/sh on Unix-like).
- Runtime environment context injection into system prompt.

### Documentation
- Reworked README with quick start, config matrix, observability, and roadmap.
- Added architecture documentation and technical stack documentation.
- Added contribution guide and GitHub issue/PR templates.
