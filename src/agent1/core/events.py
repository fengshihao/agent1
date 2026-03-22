"""Lifecycle events (names aligned with java_agent AgentEventType)."""

from __future__ import annotations

from enum import Enum, auto
from typing import Any, Optional


class AgentEventType(Enum):
    AGENT_START = auto()
    TURN_START = auto()
    MESSAGE_START = auto()
    MESSAGE_UPDATE = auto()
    MESSAGE_END = auto()
    TOOL_EXECUTION_START = auto()
    TOOL_EXECUTION_UPDATE = auto()
    TOOL_EXECUTION_END = auto()
    TURN_END = auto()
    AGENT_END = auto()
    AGENT_ERROR = auto()


def tool_start_payload(tool_name: str, tool_args: Any, tool_call_id: str) -> dict[str, Any]:
    return {"tool_name": tool_name, "tool_args": tool_args, "tool_call_id": tool_call_id}


def tool_end_payload(
    tool_call_id: str, result: str, *, is_error: bool = False, error_message: Optional[str] = None
) -> dict[str, Any]:
    return {
        "tool_call_id": tool_call_id,
        "result": result,
        "is_error": is_error,
        "error_message": error_message or "",
    }


def message_update_payload(delta: str) -> dict[str, str]:
    return {"delta": delta}
