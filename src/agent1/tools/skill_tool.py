"""skill tool: search/install/read/uninstall (aligned with java SkillTool, requires git for GitHub install)."""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import tempfile
import urllib.error
import urllib.request
from pathlib import Path
from typing import List, Optional

from agent1.skills.loader import ClaudeSkillLoader

GITHUB_SEARCH_API = "https://api.github.com/search/repositories"
DEFAULT_DEST_ROOT = ".claude/skills"
_GITHUB_SLUG = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
_GITHUB_URL = re.compile(r"^https?://github\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?:/.*)?$", re.I)
_DEFAULT_SEARCH_LIMIT = 5
_MAX_SEARCH_LIMIT = 10


def make_skill_tool(workspace_root: Path):
    root = workspace_root.resolve()
    loader = ClaudeSkillLoader()

    def skill(
        action: str,
        query: str = "",
        limit: int = _DEFAULT_SEARCH_LIMIT,
        source: str = "",
        skill_name: str = "",
        overwrite: bool = False,
        destination_root: str = DEFAULT_DEST_ROOT,
    ) -> str:
        """Manage Claude-style skills: search|install|read|uninstall.

        Args:
            action: search, install, read, or uninstall.
            query: GitHub search query when action=search.
            limit: Max search results (default 5, max 10).
            source: GitHub owner/repo, URL, or local path under workspace (install).
            skill_name: Target skill for read/uninstall; disambiguate on install.
            overwrite: Overwrite existing skill directory on install.
            destination_root: Install root relative to workspace (default .claude/skills).
        """
        act = (action or "").strip().lower()
        if not act:
            return "错误：action 不能为空，支持 search/install/read/uninstall"
        try:
            if act == "search":
                return _search(query, limit)
            if act == "install":
                return _install(
                    root,
                    source,
                    skill_name,
                    overwrite,
                    destination_root or DEFAULT_DEST_ROOT,
                )
            if act == "read":
                return _read_skill(root, loader, skill_name)
            if act == "uninstall":
                return _uninstall(root, loader, skill_name)
            return f"错误：不支持的 action={action}，支持 search/install/read/uninstall"
        except Exception as e:
            return f"错误：skill 工具执行失败: {e}"

    return skill


def _search(query: str, limit: int) -> str:
    q = (query or "").strip()
    if not q:
        return "错误：action=search 时 query 不能为空"
    lim = _DEFAULT_SEARCH_LIMIT if limit <= 0 else min(int(limit), _MAX_SEARCH_LIMIT)
    github_query = q + " (claude skill OR cursor skill OR SKILL.md) in:name,description,readme"
    from urllib.parse import quote

    url = f"{GITHUB_SEARCH_API}?q={quote(github_query)}&sort=stars&order=desc&per_page={lim}"
    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "agent1-python-skill-tool",
        },
        method="GET",
    )
    code = 0
    body = ""
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            code = resp.status
    except urllib.error.HTTPError as e:
        return f"错误：GitHub 搜索失败，status={e.code}"
    except Exception as e:
        return f"错误：GitHub 搜索失败: {e}"
    if code < 200 or code >= 300:
        return f"错误：GitHub 搜索失败，status={code}"
    data = json.loads(body)
    items = data.get("items") or []
    if not items:
        return "没有找到匹配的技能仓库。"
    lines: List[str] = ["搜索结果（可用于 action=install 的 source）:"]
    for idx, item in enumerate(items, start=1):
        full_name = (item.get("full_name") or "").strip()
        if not full_name:
            continue
        html_url = item.get("html_url") or ""
        desc = (item.get("description") or "").replace("\n", " ").strip()
        stars = item.get("stargazers_count") or 0
        lines.append(
            f"{idx}. {full_name} | stars={stars}" + (f" | {desc}" if desc else "") + f"\n   {html_url}"
        )
    lines.append("")
    lines.append('安装示例：skill action=install source="owner/repo"')
    return "\n".join(lines)


def _install(
    workspace: Path,
    source: str,
    requested_skill_name: str,
    overwrite: bool,
    destination_root_raw: str,
) -> str:
    src = (source or "").strip()
    if not src:
        return "错误：action=install 时 source 不能为空"
    dest_root_in = (destination_root_raw or DEFAULT_DEST_ROOT).strip() or DEFAULT_DEST_ROOT
    destination_root = _resolve_within_workspace(workspace, dest_root_in)
    if destination_root is None:
        return "错误：destination_root 超出工作区范围"

    temp_dir: Optional[Path] = None
    try:
        if _looks_like_github(src):
            temp_dir = Path(tempfile.mkdtemp(prefix="agent1_skill_install_"))
            slug = _normalize_github_slug(src)
            branch = _fetch_default_branch(slug)
            source_root = _git_clone(slug, branch, temp_dir / "repo")
        else:
            local = _resolve_within_workspace(workspace, src)
            if local is None:
                return "错误：本地 source 超出工作区范围"
            source_root = local
        if not source_root.is_dir():
            return f"错误：source 不是目录: {source_root}"

        skill_dirs = _detect_skill_directories(source_root)
        if not skill_dirs:
            return "错误：未在 source 中找到 SKILL.md"
        selected = _select_skill_directory(skill_dirs, requested_skill_name.strip())
        if selected is None:
            names = [p.name for p in skill_dirs]
            return "检测到多个 skill，请指定 skill_name: " + ", ".join(names)

        directory_name = _sanitize_directory_name(selected.name)
        if not directory_name:
            return "错误：skill 目录名非法"

        destination_root.mkdir(parents=True, exist_ok=True)
        target_dir = (destination_root / directory_name).resolve()
        if not str(target_dir).startswith(str(destination_root.resolve())):
            return "错误：目标路径非法"
        if target_dir.exists():
            if not overwrite:
                return f"错误：目标 skill 已存在，若要覆盖请设置 overwrite=true: {target_dir}"
            shutil.rmtree(target_dir, ignore_errors=True)
        shutil.copytree(selected, target_dir)
        rel = target_dir.relative_to(workspace)
        return (
            f"安装成功: {directory_name}\n路径: {rel}\n"
            f'可通过 skill(action=read, skill_name="{directory_name}") 读取。'
        )
    finally:
        if temp_dir and temp_dir.exists():
            shutil.rmtree(temp_dir, ignore_errors=True)


def _read_skill(workspace: Path, loader: ClaudeSkillLoader, skill_name: str) -> str:
    name = (skill_name or "").strip()
    if not name:
        return "错误：action=read 时 skill_name 不能为空"
    load_result = loader.load_from_project_root(workspace)
    matched = None
    for sk in load_result.skills:
        dir_name = sk.source_path.parent.name
        if sk.name.lower() == name.lower() or dir_name.lower() == name.lower():
            matched = sk
            break
    if matched is None:
        names = sorted({s.name for s in load_result.skills}, key=str.lower)
        hint = "\n当前未发现任何已安装 skill。" if not names else "\n当前可用 skill: " + ", ".join(names)
        return f"未找到 skill: {name}{hint}"
    parts = [f"skill: {matched.name}"]
    if matched.description:
        parts.append(f"description: {matched.description}")
    if matched.frontmatter:
        parts.append("frontmatter:")
        for k, v in matched.frontmatter.items():
            parts.append(f"- {k}: {v}")
    parts.append("\nSKILL.md content:")
    parts.append(matched.content.strip())
    return "\n".join(parts).strip()


def _uninstall(workspace: Path, loader: ClaudeSkillLoader, skill_name: str) -> str:
    name = (skill_name or "").strip()
    if not name:
        return "错误：action=uninstall 时 skill_name 不能为空"
    skills_root = (workspace / DEFAULT_DEST_ROOT).resolve()
    if not str(skills_root).startswith(str(workspace)):
        return "错误：skills 目录非法"
    if not skills_root.is_dir():
        return "当前没有可卸载的 skill（未找到 .claude/skills 目录）。"
    direct = (skills_root / name).resolve()
    if str(direct).startswith(str(skills_root)) and direct.is_dir():
        shutil.rmtree(direct, ignore_errors=True)
        return f"卸载成功: {name}"
    load_result = loader.load_from_project_root(workspace)
    for sk in load_result.skills:
        dir_name = sk.source_path.parent.name
        if sk.name.lower() == name.lower() or dir_name.lower() == name.lower():
            target = (skills_root / dir_name).resolve()
            if not str(target).startswith(str(skills_root)) or not target.is_dir():
                return f"未找到可卸载目录: {dir_name}"
            shutil.rmtree(target, ignore_errors=True)
            return f"卸载成功: {dir_name}"
    names = sorted({s.name for s in load_result.skills}, key=str.lower)
    hint = "\n当前没有已安装 skill。" if not names else "\n当前可用 skill: " + ", ".join(names)
    return f"未找到 skill: {name}{hint}"


def _resolve_within_workspace(workspace: Path, raw: str) -> Optional[Path]:
    p = Path(raw)
    resolved = p.resolve() if p.is_absolute() else (workspace / p).resolve()
    try:
        resolved.relative_to(workspace)
    except ValueError:
        return None
    return resolved


def _looks_like_github(source: str) -> bool:
    s = source.strip()
    return bool(_GITHUB_SLUG.match(s) or _GITHUB_URL.match(s))


def _normalize_github_slug(source: str) -> str:
    raw = source.strip()
    if _GITHUB_SLUG.match(raw):
        return raw
    m = _GITHUB_URL.match(raw)
    if not m:
        raise ValueError(f"不支持的 GitHub source: {source}")
    return m.group(1)


def _fetch_default_branch(slug: str) -> str:
    url = f"https://api.github.com/repos/{slug}"
    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "agent1-python-skill-tool",
        },
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        data = json.loads(resp.read().decode("utf-8", errors="replace"))
    branch = (data.get("default_branch") or "").strip()
    if not branch:
        raise OSError("无法识别默认分支")
    return branch


def _git_clone(slug: str, branch: str, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "git",
        "clone",
        "--depth",
        "1",
        "--branch",
        branch,
        f"https://github.com/{slug}.git",
        str(dest),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if proc.returncode != 0:
        out = (proc.stdout or "") + (proc.stderr or "")
        raise OSError(f"git clone 失败: {out.strip()}")
    return dest


def _detect_skill_directories(source_root: Path) -> List[Path]:
    found: List[Path] = []
    for f in source_root.rglob("SKILL.md"):
        if f.is_file():
            found.append(f.parent)
    return sorted(found, key=lambda p: str(p))


def _select_skill_directory(skill_dirs: List[Path], requested: str) -> Optional[Path]:
    if not skill_dirs:
        return None
    if not requested:
        return skill_dirs[0] if len(skill_dirs) == 1 else None
    for d in skill_dirs:
        if d.name.lower() == requested.lower():
            return d
    return None


def _sanitize_directory_name(name: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9._-]", "-", name.strip())
    normalized = re.sub(r"-{2,}", "-", normalized)
    return normalized.strip("-")