# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Agent1 is a JVM-centric agent system: **Java CLI** on **macOS and Ubuntu**, plus **Android** as the primary mobile host. **Do not add or restore `python_agent/`** — historical snapshot only on branch `archive/python-agent`.

Components:

- **Java core** (`agent_core/`) + **Java CLI** (`java_agent/cli/`) — OkHttp SSE + RxJava3, OpenAI-compatible client, no Spring
- **Android Agent** (`android_agent/`) — Jetpack Compose dynamic UI demo; integrates published `java-agent-core`

Productivity path: `ProductivityCli` / `ProductivityAgentHost`, session store, workspace tools, `events.jsonl`. Classic path: `JavaAgentCli` with bash/python/skill tools.

## Build & Run Commands

### Java Agent

```bash
gradle -p java_agent :core:test :cli:test                        # Run tests
gradle -p java_agent build                                       # Build all
gradle -p java_agent :cli:fatJar                                 # Build standalone fat jar
./agent1 "hi"                                                    # Productivity CLI
./agent1 models                                                  # Qwen models / runtime
./run-java-agent "prompt"                                        # Classic CLI
AGENT1_USE_GRADLE=1 ./agent1 "hi"                                # Productivity via Gradle
```

Java core sources are in `agent_core/src/main/java`; CLI sources are in `java_agent/cli/src/main/java`. The `java_agent` Gradle build includes `:core` from `../agent_core` and `:cli` for the executable. Java toolchain: JDK 17.

Root helper scripts (Chinese comments in headers): `./agent1`, `./run-java-agent`, `./run-java-agent-gradle`, `./publish-java-agent-core.sh`, `./build-android-agent.sh`, `./check-android-agent-layering.sh`, `./check-android-agent-static.sh`, `./check-java-agent-static.sh`, `./check-agent1-quality.sh`（见 `doc/代码质量硬性要求与静态检测.md`）。

### Android

```bash
./publish-java-agent-core.sh
./build-android-agent.sh   # needs adb
```

## Required Environment Variables

```bash
export DASHSCOPE_API_KEY="your-key"     # or QWEN_API_KEY / ALIBABA_API_KEY / OPENAI_API_KEY
```

Optional: `QWEN_BASE_URL` / `ALIBABA_BASE_URL`, `QWEN_MODEL` / `OPENAI_MODEL`, `AGENT1_AGENT_ROOT`, `AGENT1_MAX_CONTEXT_TURNS`, `AGENT1_MAX_TURNS_PER_RUN`, `AGENT1_MAX_TOOL_CALLS_PER_RUN`, classic CLI: `AGENT1_LOG_FILE`, `AGENT1_MAX_CONTEXT_MESSAGES`.

## Architecture

### Core Flow

```
User Input → CLI Layer → AgentRuntime / ProductivityAgentHost → LLM (Qwen via DashScope)
                                  ↓
                          Tool Loop (workspace tools and/or run_bash / run_python / skill)
                                  ↓
                          Event Emitter → JSONL (events.jsonl or legacy agent1.jsonl on classic path)
```

### Key Components

- **`AgentRuntime`** (`agent_core/.../core/AgentRuntime.java`) — Event-emitting wrapper for classic tool loop.
- **`ProductivityAgentHost`** — Session + run orchestration for productivity CLI.
- **`SystemPromptBuilder` / `ProductivitySystemPromptBuilder`** — OS/shell/CWD and productivity persona.
- **Skills** — Claude Code-compatible skills from `.claude/skills/*/SKILL.md` (classic CLI).

Capability tracking: `doc/基础能力/`.

### Event System

Productivity path: structured JSONL via `EventJsonlWriter` / `AgentEventJsonlBridge`. Classic CLI may use `logs/agent1.jsonl`. Event types include `run_started`, `model_request`, `tool_call`, `usage`, `run_completed`, etc.

### Java Agent Structure

- `core/` subproject: published as `java-agent-core` for Android
- `cli/` subproject: `JavaAgentCli`, `ProductivityCli`, tools, skill loader

## Conventions

- Keep changes minimal and focused
- Tool changes must consider cross-platform behavior (**macOS / Linux Ubuntu** for supported desktop; classic bash tool still handles Windows where applicable)
- Model call chain changes must preserve JSONL log fields
- Branch naming: `feature/xxx` or `fix/xxx`; archives: `archive/*`
- Tool preview limits: args 220 chars, result 280 chars (aligned across tools)
- Runtime limits: see `AgentRuntimeDefaults`
- Default model: `qwen3.7-flash` (overridable via `QWEN_MODEL` / `OPENAI_MODEL`)

## Android Layering Rules (Mandatory)

- For Android feature modules, package by layer: `com.xyz.<feature>.ui.view`, `com.xyz.<feature>.ui.viewmodel`, `com.xyz.<feature>.ui.overlay`, `com.xyz.<feature>.logic.business`, `com.xyz.<feature>.logic.data`.
- **`logic.data` 的定位**：以**数据访问实现**为主——网络请求、本地数据库/文件持久化、CRUD、上传下载等。系统权限判断（如 `canDrawOverlays`）、纯进程绑定等**不属于**数据访问，应放在 `ui.viewmodel` / `logic.business` / `logic.business.platform` / `logic.business.entry`。
- Source paths under `src/main/java` must match the declared `package` (no package/directory drift).
- Foreground `Service` 宿主：`com.xyz.<feature>.logic.business.platform`；启动/绑定编排：`com.xyz.<feature>.logic.business.entry`。`logic.data` 仅可在同 feature 下依赖 `logic.business.platform`，不依赖其余 `logic.business` 编排类型。
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
