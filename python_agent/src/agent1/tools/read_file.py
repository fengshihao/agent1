"""read tool: workspace-scoped file read (parity with java ReadFileTool)."""

from __future__ import annotations

from pathlib import Path

_DEFAULT_OFFSET = 1
_DEFAULT_LIMIT = 200
_MAX_LIMIT = 500


def make_read_tool(workspace_root: Path):
    root = workspace_root.resolve()

    def read(path: str, offset: int = _DEFAULT_OFFSET, limit: int = _DEFAULT_LIMIT) -> str:
        """Read a text file from workspace with optional line range.

        Args:
            path: File path, relative to workspace or absolute (must stay under workspace).
            offset: 1-based start line (default 1).
            limit: Max lines (default 200, max 500).
        """
        raw = (path or "").strip()
        if not raw:
            return "错误：path 不能为空"
        off = max(int(offset) if offset else _DEFAULT_OFFSET, 1)
        lim = int(limit) if limit else _DEFAULT_LIMIT
        if lim <= 0:
            lim = _DEFAULT_LIMIT
        lim = min(lim, _MAX_LIMIT)

        resolved = _resolve_path(root, raw)
        if resolved is None:
            return f"错误：路径超出工作区范围: {raw}"
        if not resolved.exists():
            return f"错误：文件不存在: {resolved}"
        if not resolved.is_file():
            return f"错误：不是普通文件: {resolved}"
        try:
            text = resolved.read_text(encoding="utf-8", errors="replace")
        except OSError as e:
            return f"错误：读取文件失败: {e}"
        lines = text.splitlines()
        if not lines:
            return f"PATH: {resolved}\nFile is empty."
        start_idx = min(off - 1, len(lines))
        end_idx = min(start_idx + lim, len(lines))
        parts = [f"PATH: {resolved}", f"RANGE: {start_idx + 1}-{end_idx} / {len(lines)}"]
        for i in range(start_idx, end_idx):
            parts.append(f"{i + 1}|{lines[i]}")
        if end_idx < len(lines):
            parts.append(f"... {len(lines) - end_idx} more lines not shown")
        return "\n".join(parts).strip()

    return read


def _resolve_path(workspace_root: Path, raw_path: str) -> Path | None:
    p = Path(raw_path)
    resolved = p.resolve() if p.is_absolute() else (workspace_root / p).resolve()
    try:
        resolved.relative_to(workspace_root)
    except ValueError:
        return None
    return resolved
