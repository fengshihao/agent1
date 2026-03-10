"""Bash 命令执行工具."""

import os
import shutil
import subprocess
from typing import Optional, Tuple


def _pick_windows_shell() -> Tuple[str, Optional[str]]:
    """选择 Windows 可用的 shell。

    返回:
        (shell_kind, executable_path)
        shell_kind: "bash" | "powershell" | "pwsh" | "cmd"
    """
    bash_path = shutil.which("bash")
    if bash_path:
        return "bash", bash_path

    powershell_path = shutil.which("powershell")
    if powershell_path:
        return "powershell", powershell_path

    pwsh_path = shutil.which("pwsh")
    if pwsh_path:
        return "pwsh", pwsh_path

    cmd_path = os.environ.get("ComSpec") or shutil.which("cmd")
    return "cmd", cmd_path


def run_bash(command: str) -> str:
    """执行 bash/shell 命令。

    Args:
        command: 要执行的 shell 命令，支持多行。例如: "ls -la", "echo hello"。

    Returns:
        执行结果（stdout 和 stderr 的合并输出）。
    """
    command = command.strip()
    if not command:
        return "错误：命令为空"

    try:
        # Windows 优先 Git Bash；否则按 powershell/pwsh/cmd 回退。
        # 类 Unix 优先 bash，找不到则回退系统 sh。
        if os.name == "nt":
            shell_kind, shell_path = _pick_windows_shell()
            if shell_kind == "bash" and shell_path:
                result = subprocess.run(
                    [shell_path, "-lc", command],
                    capture_output=True,
                    text=True,
                    timeout=60,
                )
            elif shell_kind in ("powershell", "pwsh"):
                executable = shell_path or shell_kind
                result = subprocess.run(
                    [executable, "-NoProfile", "-Command", command],
                    capture_output=True,
                    text=True,
                    timeout=60,
                )
            else:
                executable = shell_path or "cmd"
                result = subprocess.run(
                    [executable, "/d", "/s", "/c", command],
                    capture_output=True,
                    text=True,
                    timeout=60,
                )
        else:
            bash_path = shutil.which("bash")
            run_kwargs = {
                "args": command,
                "shell": True,
                "capture_output": True,
                "text": True,
                "timeout": 60,
            }
            if bash_path:
                run_kwargs["executable"] = bash_path
            result = subprocess.run(**run_kwargs)
        output_parts = []
        if result.stdout:
            output_parts.append(result.stdout)
        if result.stderr:
            output_parts.append(f"[stderr]\n{result.stderr}")
        if not output_parts:
            output_parts.append(f"(退出码: {result.returncode})")
        return "\n".join(output_parts).strip() or f"(退出码: {result.returncode})"
    except subprocess.TimeoutExpired:
        return "错误：命令执行超时（60秒）"
    except Exception as e:
        return f"错误：{e!r}"
