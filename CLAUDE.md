# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Agent1 is a multi-language agent system providing production-ready CLI agents with tool calling, observability, and cost control. It consists of three components:

- **Python CLI** (`src/agent1/`) — Pydantic AI + Qwen/DashScope, Rich terminal UI
- **Java Agent** (`java_agent/`) — OkHttp SSE + RxJava3, OpenAI-compatible client, no Spring
- **Android UI** (`agent_ui/android/`) — Jetpack Compose dynamic UI generation

Python and Java implementations maintain feature parity. Both share the same tool set, event model, and skill system.

## Build & Run Commands

### Python

```bash
uv sync                                                          # Install dependencies
PYTHONPATH=src python -m unittest discover -s tests -v           # Run all tests
PYTHONPATH=src python -m pytest tests/test_bash_tool.py -v       # Run single test file
uv run agent1 "prompt"                                           # Single run (streaming)
uv run agent1 "prompt" --no-stream                               # Single run (non-streaming)
uv run agent1                                                    # Interactive REPL mode
```

### Java Agent

```bash
gradle -p java_agent :core:test :cli:test                        # Run tests
gradle -p java_agent build                                       # Build all
gradle -p java_agent :cli:fatJar                                 # Build standalone fat jar
gradle -p java_agent runJavaAgentCli --args="prompt"             # Run CLI
```

Java source is in `java_agent/src/main/java` shared between `:core` and `:cli` subprojects via source set filtering (`cli/**` excluded from core). Java toolchain: JDK 17.

### Android

```bash
./java_agent/bin/publish-core-and-verify-android                 # Publish core to local Maven + verify
(cd agent_ui/android && ./run.sh)                                  # Demo: assembleDebug + adb install + 启动 App（需设备）
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

- **`Agent1Runtime`** (Python: `src/agent1/core/runtime.py`, Java: `java_agent/src/.../core/AgentRuntime.java`) — Thin event-emitting wrapper. Subscribes listeners, manages usage limits. Does NOT contain business logic.

- **`agent_factory.py`** — Wires everything: builds model, creates tools, system prompt, runtime. Entry point for CLI.

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

- For Android feature modules, package by layer: `com.xyz.<feature>.ui.view`, `com.xyz.<feature>.ui.viewmodel`, `com.xyz.<feature>.logic.business`, `com.xyz.<feature>.logic.data`.
- Dependency direction is one-way and downward only:
  - `ui.view -> ui.viewmodel, logic.business`
  - `ui.viewmodel -> logic.business`
  - `logic.business -> logic.data`
  - `logic.data` must not depend on upper layers.
- Any lower-to-upper dependency is forbidden (for example, `logic.* -> ui.*`).
- Android UI framework classes (`Activity`, `Fragment`, `View`, Compose APIs) are only allowed in `ui.*`; `logic.*` must not import or reference them.
- `ui.view` must not perform direct IO (file/network/database/thread management). IO belongs to `logic.data`; view code only forwards intent and renders state.
- `utils` is not a separate architecture layer:
  - Forbidden: generic cross-layer `*.utils` packages used as shortcuts.
  - Allowed: layer-owned utils only (`ui.utils`, `logic.business.utils`, `logic.data.utils`) and pure `common/foundation` helpers that do not depend on `ui.*` or `logic.*`.
- Exceptions require explicit annotation/comment and review approval.
