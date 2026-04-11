"""Inject CLI arguments into SKILL.md content (java ClaudeSkillPromptRenderer parity)."""

from __future__ import annotations

import re
from typing import List, Optional

from agent1.skills.loader import ClaudeSkill

_ARG_INDEX = re.compile(r"\$ARGUMENTS\[(\d+)]")
_SHORT_ARG = re.compile(r"\$(\d+)")


def render_skill_prompt(skill: ClaudeSkill, raw_arguments: Optional[str]) -> str:
    content = skill.content
    args = (raw_arguments or "").strip()
    arg_list: List[str] = args.split() if args else []
    has_all = "$ARGUMENTS" in content

    rendered = content.replace("${CLAUDE_SKILL_DIR}", str(skill.source_path.parent))
    rendered = _replace_by_index(rendered, _ARG_INDEX, arg_list)
    rendered = _replace_by_index(rendered, _SHORT_ARG, arg_list)
    rendered = rendered.replace("$ARGUMENTS", args)

    if args and not has_all:
        rendered = rendered + "\n\nARGUMENTS: " + args
    return rendered


def _replace_by_index(text: str, pattern: "re.Pattern", args: List[str]) -> str:
    def repl(m: re.Match[str]) -> str:
        idx = int(m.group(1))
        return args[idx] if 0 <= idx < len(args) else ""

    return pattern.sub(repl, text)
