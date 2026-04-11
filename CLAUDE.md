# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Agent1 is a multi-language agent system providing production-ready CLI agents with tool calling, observability, and cost control. It consists of three components:

- **Python CLI** (`python_agent/src/agent1/`) — Pydantic AI + Qwen/DashScope, Rich terminal UI
- **Java core** (`agent_core/`) + **Java CLI** (`java_agent/cli/`) — OkHttp SSE + RxJava3, OpenAI-compatible client, no Spring
- **Android Agent** (`android_agent/`) — Jetpack Compose dynamic UI generation

Python and Java implementations maintain feature parity. Both share the same tool set, event model, and skill system.

## Build & Run Commands

### Python

```bash
cd python_agent && uv sync                                       # Install dependencies
cd python_agent && PYTHONPATH=src python -m unittest discover -s tests -v   # Run all tests
cd python_agent && PYTHONPATH=src python -m pytest tests/test_bash_tool.py -v  # Single test file
cd python_agent && uv run agent1 "prompt"                       # Single run (streaming)
cd python_agent && uv run agent1 "prompt" --no-stream            # Single run (non-streaming)
cd python_agent && uv run agent1                               # Interactive REPL mode
# 在仓库根目录也可：uv run --project python_agent agent1 "prompt"
```

仓库根还有薄脚本（均中文注释在文件头）：`./sync-python-agent.sh`、`./install-python-agent.sh` / `install-python-agent.ps1`、`./run-java-agent`、`./run-java-agent-gradle`、`./publish-java-agent-core.sh`、`./build-android-agent.sh`、`./check-android-agent-layering.sh`。

### Java Agent

```bash
gradle -p java_agent :core:test :cli:test                        # Run tests
gradle -p java_agent build                                       # Build all
gradle -p java_agent :cli:fatJar                                 # Build standalone fat jar
gradle -p java_agent runJavaAgentCli --args="prompt"             # Run CLI
```

Java core sources are in `agent_core/src/main/java`; CLI sources are in `java_agent/cli/src/main/java`. The `java_agent` Gradle build includes `:core` from `../agent_core` and `:cli` for the executable. Java toolchain: JDK 17.

### Android

```bash
./publish-java-agent-core.sh                                       # 根目录薄脚本 → java_agent/bin（发布 core + 校验 Android 编译）
./build-android-agent.sh                                           # 根目录薄脚本 → android_agent/run.sh（编译安装并启动 Demo，需 adb）
```

## Required Environment Variables

```bash
export DASHSCOPE_API_KEY="your-key"     # or ALIBABA_API_KEY
```

Optional: `ALIBABA_BASE_URL`, `AGENT1_LOG_FILE`, `AGENT1_MAX_TOTAL_TOKENS`, `AGENT1_MAX_TURNS_PER_RUN`, `AGENT1_MAX_TOOL_CALLS_PER_RUN`, `OPENAI_MODEL`.

## Architecture

### Core Flow (both Python & Java)

```
User Input → CLI Layer → AgentRuntime (thin shell) → LLM (Qwen via DashScope)
                                  ↓
                          Tool Loop (run_bash / run_python / read_file / skill)
                                  ↓
                          Event Emitter → JSONL logs + terminal UI updates
```

### Key Components

- **`Agent1Runtime`** (Python: `python_agent/src/agent1/core/runtime.py`, Java: `agent_core/src/main/java/.../core/AgentRuntime.java`) — Thin event-emitting wrapper. Subscribes listeners, manages usage limits. Does NOT contain business logic.

- **`agent_factory.py`** (`python_agent/src/agent1/agent_factory.py`) — Wires everything: builds model, creates tools, system prompt, runtime. Entry point for CLI.

- **System Prompt** — `SystemPromptBuilder` auto-injects OS/Python/Shell/CWD context + skill descriptions.

- **Skills System** — Claude Code-compatible skills from `.claude/skills/*/SKILL.md`. Variables: `$ARGUMENTS`, `$0/$1...`, `${CLAUDE_SKILL_DIR}`. Invoked via `/skill-name args` in interactive mode.

### Event System

All operations emit structured JSONL events to `logs/agent1.jsonl`. Event types: `run_started`, `model_request`, `model_text_delta`, `tool_call`, `tool_result`, `usage`, `model_response`, `run_completed`/`run_failed`. Each event carries a `run_id`.

### Java Agent Structure

- `core/` subproject: `AgentRuntime`, `AgentState`, LLM client — published as `java-agent-core` to local Maven for Android consumption
- `cli/` subproject: `JavaAgentCli`, tool implementations, skill loader — builds fat jar

## Conventions

- Keep changes minimal and focused
- Tool changes must consider cross-platform behavior (macOS / Linux / Windows)
- Model call chain changes must preserve JSONL log fields
- Branch naming: `feature/xxx` or `fix/xxx`
- Python tool timeout defaults: `run_bash` 60s, `run_python` 30s
- Tool preview limits (Python & Java aligned): args 220 chars, result 280 chars
- Runtime limits: default 12 turns/run, 24 tool calls/run
- Default model: `qwen3.5-flash` (overridable via `OPENAI_MODEL`)

## Android Layering Rules (Mandatory)

- For Android feature modules, package by layer: `com.xyz.<feature>.ui.view`, `com.xyz.<feature>.ui.viewmodel`, `com.xyz.<feature>.ui.overlay`, `com.xyz.<feature>.logic.business`, `com.xyz.<feature>.logic.data`.
- **`logic.data` 的定位**：以**数据访问实现**为主——网络请求、本地数据库/文件持久化、CRUD、上传下载等。系统权限判断（如 `canDrawOverlays`）、纯进程绑定等**不属于**数据访问，应放在 `ui.viewmodel` / `logic.business` / `logic.business.platform` / `logic.business.entry`（如 `PetPlatformGateway`）等更合适的位置。demo 里 `pet.logic.data` 仍有历史混放，可逐步迁出。
- Source paths under `src/main/java` must match the declared `package` (no package/directory drift).
- Foreground `Service` 宿主：`com.xyz.<feature>.logic.business.platform`；启动/绑定编排：`com.xyz.<feature>.logic.business.entry`（`PetPlatformGateway`）。`logic.data` 仅可在同 feature 下依赖 `logic.business.platform`（例如 ASR 等），不依赖其余 `logic.business` 编排类型。
- Dependency direction is one-way and downward only:
  - `ui.view -> ui.viewmodel, ui.overlay, logic.business`
  - `ui.viewmodel -> ui.view, ui.overlay, logic.business`
  - `ui.overlay -> ui.viewmodel, ui.view, logic.business, logic.data`
  - `logic.business -> logic.data`
  - `logic.data` must not depend on upper layers.
- Any lower-to-upper dependency is forbidden (for example, `logic.* -> ui.*`).
- Android UI framework classes (`Activity`, `Fragment`, `View`, Compose APIs) are only allowed in `ui.*`; `logic.*` must not import or reference them.
- `ui.view` must not perform direct IO (file/network/database/thread management). IO belongs to `logic.data`; view code only forwards intent and renders state.
- `utils` is not a separate architecture layer:
  - Forbidden: generic cross-layer `*.utils` packages used as shortcuts.
  - Allowed: layer-owned utils only (`ui.utils`, `logic.business.utils`, `logic.data.utils`) and pure `common/foundation` helpers that do not depend on `ui.*` or `logic.*`.
- Exceptions require explicit annotation/comment and review approval.
