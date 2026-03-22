"""向后兼容：请优先使用 agent_factory.create_workspace_agent。"""

from pathlib import Path

from agent1.agent_factory import create_workspace_agent

__all__ = ["create_workspace_agent", "get_default_bundle"]


def get_default_bundle():
    """构建当前工作区下的 Agent + Runtime + skills 映射（需已配置 API key）。"""
    return create_workspace_agent(Path.cwd())
