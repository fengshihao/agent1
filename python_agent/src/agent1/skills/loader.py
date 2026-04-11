"""Load .claude/skills/*/SKILL.md (aligned with java ClaudeSkillLoader)."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List


@dataclass
class ClaudeSkill:
    name: str
    description: str
    content: str
    source_path: Path
    frontmatter: Dict[str, str] = field(default_factory=dict)


@dataclass
class SkillLoadResult:
    skills: List[ClaudeSkill]
    warnings: List[str]


class ClaudeSkillLoader:
    def load_from_project_root(self, project_root: Path) -> SkillLoadResult:
        skills_root = project_root / ".claude" / "skills"
        return self.load_from_skills_root(skills_root)

    def load_from_skills_root(self, skills_root: Path) -> SkillLoadResult:
        skills: List[ClaudeSkill] = []
        warnings: List[str] = []
        if not skills_root.is_dir():
            return SkillLoadResult(skills, warnings)
        for skill_dir in sorted([p for p in skills_root.iterdir() if p.is_dir()], key=lambda p: p.name):
            skill_file = skill_dir / "SKILL.md"
            if not skill_file.is_file():
                continue
            try:
                raw = skill_file.read_text(encoding="utf-8", errors="replace")
                parsed = self.parse_skill(raw, skill_dir.name)
                skills.append(
                    ClaudeSkill(
                        name=parsed[0],
                        description=parsed[1],
                        content=parsed[2],
                        source_path=skill_file,
                        frontmatter=parsed[3],
                    )
                )
            except Exception as e:
                warnings.append(f"解析 skill 失败({skill_file}): {e}")
        return SkillLoadResult(skills, warnings)

    def parse_skill(self, raw: str, fallback_name: str) -> tuple[str, str, str, Dict[str, str]]:
        fm, body = _split_frontmatter(raw)
        frontmatter = _parse_frontmatter(fm)
        name = (frontmatter.get("name") or "").strip() or fallback_name
        description = (frontmatter.get("description") or "").strip()
        return name, description, body, frontmatter


def _split_frontmatter(raw: str) -> tuple[str, str]:
    normalized = raw.replace("\r\n", "\n")
    if not normalized.startswith("---\n"):
        return "", normalized
    second = normalized.find("\n---\n", 4)
    if second < 0:
        raise ValueError("frontmatter 未闭合")
    fm = normalized[4:second]
    body = normalized[second + len("\n---\n") :]
    return fm, body


def _parse_frontmatter(text: str) -> Dict[str, str]:
    """Minimal key: value lines (enough for name/description in SKILL.md)."""
    values: Dict[str, str] = {}
    for line in text.splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        if ":" in s:
            k, _, v = s.partition(":")
            values[k.strip().lower()] = v.strip()
    return values
