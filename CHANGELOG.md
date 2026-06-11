# Changelog

All notable changes to this project are documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Removed
- **Python CLI** (`python_agent/` 及根目录安装脚本) 已从 `main` 移除；完整代码保留在分支 `archive/python-agent`。

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
