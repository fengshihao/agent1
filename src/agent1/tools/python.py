"""Python 脚本执行工具."""

import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Callable, Optional


def make_run_python(workspace_root: Optional[Path] = None) -> Callable[..., str]:
    """When workspace_root is set, file_path must resolve inside it."""
    root = workspace_root.resolve() if workspace_root else None

    def run_python(script: Optional[str] = None, file_path: Optional[str] = None) -> str:
        return _run_python_impl(script, file_path, workspace_root=root)

    return run_python


def run_python(script: Optional[str] = None, file_path: Optional[str] = None) -> str:
    """Execute Python script (no workspace constraint). Prefer make_run_python(cwd) from CLI."""
    return _run_python_impl(script, file_path, workspace_root=None)


def _run_python_impl(
    script: Optional[str],
    file_path: Optional[str],
    *,
    workspace_root: Optional[Path],
) -> str:
    """执行 Python 脚本。必须提供 script 或 file_path 之一，不能同时提供。

    Args:
        script: Python 脚本的完整代码内容。当用户给出代码片段时使用此参数。
        file_path: 已存在的 .py 文件路径。当用户指定要运行某文件时使用此参数。

    Returns:
        执行输出（stdout 和 stderr）。
    """
    if script and file_path:
        return "错误：不能同时指定 script 和 file_path"
    if not script and not file_path:
        return "错误：必须提供 script（脚本内容）或 file_path（文件路径）"

    if file_path:
        p = Path(file_path)
        w = workspace_root.resolve() if workspace_root else None
        path = p.resolve() if p.is_absolute() else (w / p).resolve() if w is not None else p.resolve()
        if w is not None:
            try:
                path.relative_to(w)
            except ValueError:
                return f"错误：file_path 超出工作区范围: {file_path}"
        if not path.exists():
            return f"错误：文件不存在: {file_path}"
        if not path.is_file():
            return f"错误：不是文件: {file_path}"
        try:
            result = subprocess.run(
                [sys.executable, str(path)],
                capture_output=True,
                text=True,
                timeout=30,
                cwd=path.parent,
            )
        except Exception as e:
            return f"错误：{e!r}"
    else:
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".py", delete=False
        ) as f:
            f.write(script)
            tmp_path = f.name
        try:
            result = subprocess.run(
                [sys.executable, tmp_path],
                capture_output=True,
                text=True,
                timeout=30,
            )
        except Exception as e:
            return f"错误：{e!r}"
        finally:
            Path(tmp_path).unlink(missing_ok=True)

    output_parts = []
    if result.stdout:
        output_parts.append(result.stdout)
    if result.stderr:
        output_parts.append(f"[stderr]\n{result.stderr}")
    if not output_parts:
        output_parts.append(f"(退出码: {result.returncode})")
    return "\n".join(output_parts).strip() or f"(退出码: {result.returncode})"
