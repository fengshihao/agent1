"""Bash 命令执行工具."""

import subprocess
import shlex


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
        result = subprocess.run(
            command,
            shell=True,
            capture_output=True,
            text=True,
            timeout=60,
        )
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
