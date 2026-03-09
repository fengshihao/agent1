"""Pydantic AI 智能体：bash/python 工具，agent_rx 流式，Rich Markdown CLI."""

__version__ = "0.1.0"

__all__ = ["agent", "__version__"]


def __getattr__(name: str):
    if name == "agent":
        from agent1.agent import agent
        return agent
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
