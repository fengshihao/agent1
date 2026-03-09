"""Pydantic AI Agent 定义与工具注册."""

import os
import platform
import sys
from pathlib import Path

from pydantic_ai import Agent
from pydantic_ai.models.openai import OpenAIChatModel
from pydantic_ai.providers.alibaba import AlibabaProvider

from agent1.tools.bash import run_bash
from agent1.tools.python import run_python

# 阿里云 Qwen 3.5 Plus
# 环境变量: DASHSCOPE_API_KEY 或 ALIBABA_API_KEY（必填）
# 中国区(默认): https://dashscope.aliyuncs.com/compatible-mode/v1
# 国际区: ALIBABA_BASE_URL=https://dashscope-intl.aliyuncs.com/compatible-mode/v1
_api_key = os.environ.get("DASHSCOPE_API_KEY") or os.environ.get("ALIBABA_API_KEY")
_base_url = os.environ.get(
    "ALIBABA_BASE_URL",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",  # 中国区默认
)

_provider = AlibabaProvider(api_key=_api_key, base_url=_base_url)
_model = OpenAIChatModel("qwen3.5-plus", provider=_provider)


def _runtime_env_prompt() -> str:
    """构造运行环境信息，注入到系统提示词。"""
    shell_hint = os.environ.get("SHELL") or os.environ.get("ComSpec") or "unknown"
    return (
        "当前运行环境信息如下，请据此选择命令和代码写法："
        f"OS={platform.system()} {platform.release()}，"
        f"Python={platform.python_version()} (executable={sys.executable})，"
        f"Shell={shell_hint}，"
        f"CWD={Path.cwd()}。"
        "在 Windows 上优先使用 PowerShell 风格命令；在 Linux/macOS 上优先使用 bash 命令。"
    )


agent = Agent(
    _model,
    output_type=str,
    system_prompt=(
        "你是一个有帮助的 AI 助手，可以执行 bash 命令和 Python 脚本。"
        "当用户需要执行命令或脚本时，使用 run_bash 或 run_python 工具。"
        "回复时使用 Markdown 格式（标题、加粗、表格等）以便在终端中更好展示。"
        + _runtime_env_prompt()
    ),
    tools=[run_bash, run_python],
)
