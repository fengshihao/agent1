"""Build pydantic_ai Agent + Agent1Runtime for a workspace (java_agent CLI parity)."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

from pydantic_ai import Agent
from pydantic_ai.models.openai import OpenAIChatModel
from pydantic_ai.providers.alibaba import AlibabaProvider

from agent1.core.runtime import Agent1Runtime
from agent1.skills.loader import ClaudeSkill, ClaudeSkillLoader
from agent1.system_prompt_builder import SystemPromptBuilder
from agent1.tools.run_bash import run_bash
from agent1.tools.memory_tool import make_memory_tool
from agent1.tools.run_python import make_run_python
from agent1.tools.read_file import make_read_tool
from agent1.tools.skill_tool import make_skill_tool


def _parse_positive_int(raw: Optional[str], default: int) -> int:
    if not raw or not str(raw).strip():
        return default
    try:
        v = int(str(raw).strip())
        return v if v > 0 else default
    except ValueError:
        return default


def resolve_memory_database_path(workspace_root: Path) -> Path:
    env = (os.environ.get("AGENT1_MEMORY_DB") or "").strip()
    if env:
        p = Path(env)
        return p if p.is_absolute() else (workspace_root / p).resolve()
    return (workspace_root / ".agent1" / "memory.sqlite").resolve()


def build_model() -> OpenAIChatModel:
    api_key = os.environ.get("DASHSCOPE_API_KEY") or os.environ.get("ALIBABA_API_KEY")
    if not api_key:
        raise RuntimeError("缺少 API key：请设置 DASHSCOPE_API_KEY 或 ALIBABA_API_KEY")
    base_url = os.environ.get(
        "ALIBABA_BASE_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
    )
    provider = AlibabaProvider(api_key=api_key, base_url=base_url)
    model_name = (os.environ.get("OPENAI_MODEL") or "qwen3.5-flash").strip()
    return OpenAIChatModel(model_name, provider=provider)


def create_workspace_agent(
    workspace_root: Optional[Path] = None,
) -> Tuple[Any, Agent1Runtime, Dict[str, ClaudeSkill]]:
    """Create Agent with read/memory/run_bash/run_python/skill tools and thin runtime shell."""
    root = (workspace_root or Path(".")).resolve()
    memory_db = resolve_memory_database_path(root)
    memory_db.parent.mkdir(parents=True, exist_ok=True)

    loader = ClaudeSkillLoader()
    skill_result = loader.load_from_project_root(root)

    system_prompt = (
        SystemPromptBuilder()
        .memory_database_path(memory_db)
        .add_all_skills(skill_result.skills)
        .build()
    )

    read_tool = make_read_tool(root)
    memory_tool = make_memory_tool(memory_db)
    run_py = make_run_python(root)
    skill_tool = make_skill_tool(root)

    agent = Agent(
        build_model(),
        output_type=str,
        system_prompt=system_prompt,
        tools=[read_tool, memory_tool, run_bash, run_py, skill_tool],
    )

    max_turns = _parse_positive_int(os.environ.get("AGENT1_MAX_TURNS_PER_RUN"), 12)
    max_tools = _parse_positive_int(os.environ.get("AGENT1_MAX_TOOL_CALLS_PER_RUN"), 24)

    runtime = Agent1Runtime(
        agent,
        memory_db=memory_db,
        max_turns=max_turns,
        max_tool_calls=max_tools,
    )
    skills_by_name: Dict[str, ClaudeSkill] = {s.name: s for s in skill_result.skills}
    return agent, runtime, skills_by_name
