# Changelog

All notable changes to this project are documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Removed
- **`python_agent`** 及根目录 Python 安装/同步脚本；CLI 以 **Java** 为唯一维护实现。
- GitHub **CI** 中的 Python 多版本矩阵；改为 **JDK 17 + `java_agent` 单测**。

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
