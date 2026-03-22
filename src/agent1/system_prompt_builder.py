"""System prompt assembly (aligned with java SystemPromptBuilder)."""

from __future__ import annotations

import os
import platform
import shutil
import sys
from pathlib import Path
from typing import Iterable, List, Optional

from agent1.skills.loader import ClaudeSkill


class SystemPromptBuilder:
    def __init__(self) -> None:
        self._base = (
            "你是一个有帮助的 AI 助手，可以读取工作区文件、执行 bash 命令、Python 脚本，"
            "使用 memory 长期记忆，以及 skill 管理。"
            "需要读文件时用 read；执行命令或脚本时用 run_bash 或 run_python。"
            "当用户用自然语言表达 skill 相关意图时，自动使用 skill 工具。"
            "请使用 Markdown 格式回复。"
        )
        self._skills: List[ClaudeSkill] = []
        self._memory_db: Optional[Path] = None

    def base_prompt(self, text: str) -> SystemPromptBuilder:
        self._base = text or ""
        return self

    def memory_database_path(self, path: Optional[Path]) -> SystemPromptBuilder:
        self._memory_db = path
        return self

    def add_skill(self, skill: Optional[ClaudeSkill]) -> SystemPromptBuilder:
        if skill is not None:
            self._skills.append(skill)
        return self

    def add_all_skills(self, skills: Optional[Iterable[ClaudeSkill]]) -> SystemPromptBuilder:
        if skills:
            for s in skills:
                self.add_skill(s)
        return self

    def build(self) -> str:
        return "".join(
            [
                self._base,
                _runtime_env_section(),
                _run_bash_implementation_section(),
                _shell_pitfalls_section(),
                _skill_list_section(self._skills),
                _memory_section(self._memory_db),
            ]
        )


def _runtime_env_section() -> str:
    shell_hint = os.environ.get("SHELL") or os.environ.get("ComSpec") or "unknown"
    cwd = str(Path.cwd().resolve())
    os_name = platform.system()
    release = platform.release()
    machine = platform.machine() or "unknown"
    py = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"
    term = (os.environ.get("TERM") or "").strip() or "unknown"
    if os_name.lower() == "darwin":
        shell_pref = (
            "当前为 **macOS**：终端多为 zsh；生成 `run_bash` 时请按 **zsh 语法**（与下文「实际执行方式」一致）。"
        )
    elif os_name.lower() == "windows":
        shell_pref = "当前为 **Windows**：优先 bash（Git Bash）；否则 PowerShell / cmd（见下文）。"
        if shutil.which("bash"):
            shell_pref = (
                "当前为 **Windows** 且检测到 **bash**：`run_bash` 将优先走 bash；命令请用 bash 语法。"
            )
        elif shutil.which("powershell") or shutil.which("pwsh"):
            shell_pref = (
                "当前为 **Windows** 且无 bash：`run_bash` 可能走 PowerShell；请用 PowerShell 语法，并注意 `curl` 常为别名。"
            )
        else:
            shell_pref = "当前为 **Windows** 且无 bash/PowerShell：`run_bash` 可能走 cmd；请用 cmd 兼容语法。"
    else:
        shell_pref = "当前为 **Linux/Unix**：生成 `run_bash` 时请用 **bash** 语法（与下文「实际执行方式」一致）。"
    return (
        f"\n\n=== 运行环境（选用工具与命令语法）===\n"
        f"- OS: {os_name} {release} ({machine})\n"
        f"- Python: {py}\n"
        f"- Python 可执行文件: {sys.executable}\n"
        f"- 用户 Shell 环境变量提示: {shell_hint}\n"
        f"- TERM: {term}\n"
        f"- 工作目录 CWD: {cwd}\n"
        f"- 建议: {shell_pref}\n"
    )


def _run_bash_implementation_section() -> str:
    name = platform.system()
    if name == "Darwin":
        z = shutil.which("zsh")
        if z:
            return (
                "\n=== run_bash 在本机的实际执行方式 ===\n"
                f"使用 **`{z} -l -c \"<整行命令>\"`**（登录式 zsh），与 Java agent 的 RunBashTool 一致。"
                "若无 zsh，会回退 `bash -lc` 或 `/bin/sh -lc`。\n"
            )
        return (
            "\n=== run_bash 在本机的实际执行方式 ===\n"
            "未在 PATH 中发现 zsh，将回退 bash/sh；请避免依赖仅 zsh 独有的语法。\n"
        )
    if name == "Windows":
        if shutil.which("bash"):
            return (
                "\n=== run_bash 在本机的实际执行方式 ===\n"
                "使用 **`bash -lc`**（Git Bash 等）。\n"
            )
        if shutil.which("pwsh") or shutil.which("powershell"):
            exe = shutil.which("pwsh") or shutil.which("powershell")
            return (
                "\n=== run_bash 在本机的实际执行方式 ===\n"
                f"使用 PowerShell **`{exe} -NoProfile -Command`**。\n"
            )
        return (
            "\n=== run_bash 在本机的实际执行方式 ===\n"
            "使用 **cmd.exe `/d /s /c`**。\n"
        )
    bash = shutil.which("bash")
    if bash:
        return (
            "\n=== run_bash 在本机的实际执行方式 ===\n"
            f"使用 **`{bash}`** 且 **`shell=True`** 执行整行（bash 处理管道与重定向）。\n"
        )
    return (
        "\n=== run_bash 在本机的实际执行方式 ===\n"
        "未找到 bash，将使用系统默认 sh 行为；请尽量 POSIX 兼容。\n"
    )


def _shell_pitfalls_section() -> str:
    name = platform.system()
    core = (
        "\n=== Shell 易错点（减少无效重试）===\n"
        "- 含 **`?` `&` `*`** 的 **URL** 必须整体加 **引号**，否则 zsh/bash 会通配展开并报错（如 `no matches found`）。\n"
        "  示例：`curl -s 'wttr.in?2'`、`curl -s \"https://a.com?q=1\"`。\n"
        "- **`curl`** 只取正文请加 **`-s`**，否则进度条易混在输出里。\n"
        "- 调试网络可短期用 **`-v` / `--connect-timeout`**，确认后改回 `-s`。\n"
    )
    if name == "Darwin":
        return core + (
            "- **zsh**：`!` 可能触发历史扩展；复杂一行命令多用引号。\n"
            "- **管道**需要「任一步失败则失败」时，可在同一命令串内写 **`set -o pipefail`** 再写管道。\n"
        )
    if name == "Windows":
        w = (
            "- **Windows**：路径与引号规则与 Unix 不同；**cmd** 中 `&`、`|`、`%` 有特殊含义。\n"
            "- PowerShell 中需要原生 curl 时可写 **`curl.exe`**。\n"
        )
        if not shutil.which("bash"):
            w += "- 当前无 bash 时，**勿照搬**仅适用于 Unix 的示例命令。\n"
        return core + w
    return core + (
        "- **bash**：`?`、`*` 同样会路径名展开，URL 务必引号。\n"
        "- 需要 **pipefail** 时在命令串内 `set -o pipefail`（再写管道）。\n"
    )


def _skill_list_section(skills: List[ClaudeSkill]) -> str:
    if not skills:
        return ""
    lines = [
        "\n\n=== 可用 Skills ===\n",
        "你可以自主判断是否使用某个 skill。",
        "如果决定使用 skill，请先调用 skill 工具执行 read(skill_name)，再按读取内容执行。\n",
        "skill(action=read) 的返回文本会自动进入对话历史，这就是 skill 内容进入上下文的方式。\n",
        "用户会使用自然语言表达意图（如找、安装、读取、卸载 skill），你应将其自动映射为 "
        "skill(action=search/install/read/uninstall)。\n",
        "在读取 skill 内容之前，不要假设自己已经知道该 skill 的完整规则。\n",
        "注意：当用户发起新的具体请求时，在首次调用业务工具前仍需重新 read 一次对应 skill。\n\n",
        "执行流程（必须遵循）：\n",
        "1) 先识别用户意图：search / install / read / uninstall。\n",
        "2) 必要时先 search，再 install/read。\n",
        "3) 执行任务前，用 read(skill_name) 加载规则；若参数不足，按 skill 要求补充信息。\n\n",
        "可用 skill 列表：\n",
    ]
    for sk in skills:
        line = f"- {sk.name}"
        if sk.description:
            line += f": {sk.description}"
        lines.append(line + "\n")
    lines.append(
        "\n示例：当用户问天气时，先 read 对应 weather skill 的 SKILL.md，再决定是否调用 run_bash。"
    )
    return "".join(lines)


def _memory_section(memory_db: Optional[Path]) -> str:
    if memory_db is None:
        return ""
    abs_path = memory_db.resolve()
    cwd = Path.cwd().resolve()
    rel_display = str(abs_path)
    try:
        r = abs_path.relative_to(cwd)
        if not str(r).startswith(".."):
            rel_display = str(r)
    except ValueError:
        pass
    env = (os.environ.get("AGENT1_MEMORY_DB") or "").strip()
    env_line = (
        "当前由环境变量 AGENT1_MEMORY_DB 指向该文件。"
        if env
        else "默认路径见下；也可设置环境变量 AGENT1_MEMORY_DB 指向其它 .sqlite 文件。"
    )
    return (
        "\n\n=== 长期记忆（SQLite + memory 工具）===\n"
        "你可以通过 memory 工具持久化跨会话信息（偏好、结论、项目事实等），不要再用 run_bash + sqlite3 操作记忆库。\n"
        "系统会在每条用户消息后的首次模型请求附带「记忆库结构」快照；表内数据请用 memory(search/read) 查看。\n"
        f"{env_line}\n"
        f"- 数据库路径（内部维护，供你理解上下文）：{abs_path}\n"
        f"- 相对当前工作目录：{rel_display}\n"
        "- 固定表结构（memories）：id, created_at, type(data/code/txt/calen), keywords, summary（<100字）, content。\n"
        "- memory 工具 action：search/read/write；search 使用参数 query、mem_type（对应 type）、limit；"
        "read 使用 memory_id；write 使用 mem_type、keywords、summary、content。\n"
        "通用决策原则：若任务依赖历史偏好或事实，先 memory(search)；命中后 memory(read)；"
        "结束轮次前如有值得保留的信息可 memory(write)。\n"
        "搜索支持通配符 * ?，以及与或非 & || !；不写操作符时空格分词按 OR。\n"
    )
