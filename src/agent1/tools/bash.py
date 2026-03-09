"""Bash 命令执行工具."""

import os
import shutil
import subprocess


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
        # Windows 默认使用 PowerShell；类 Unix 优先 bash，找不到则回退系统 sh。
        if os.name == "nt":
            result = subprocess.run(
                ["powershell", "-NoProfile", "-Command", command],
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
