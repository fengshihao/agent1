"""Agent1：Pydantic AI + 与 java_agent 对齐的工具与薄运行时。"""

__version__ = "0.1.0"

__all__ = ["__version__", "create_workspace_agent", "Agent1Runtime", "AgentEventType"]


def __getattr__(name: str):
    if name == "create_workspace_agent":
        from agent1.agent_factory import create_workspace_agent

        return create_workspace_agent
    if name == "Agent1Runtime":
        from agent1.core.runtime import Agent1Runtime

        return Agent1Runtime
    if name == "AgentEventType":
        from agent1.core.events import AgentEventType

        return AgentEventType
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
