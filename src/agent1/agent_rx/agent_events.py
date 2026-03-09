"""Agent 流式事件类型与过滤辅助函数."""

from pydantic_ai import AgentRunResultEvent, AgentStreamEvent
from pydantic_ai.messages import (
    FunctionToolCallEvent,
    FunctionToolResultEvent,
    PartDeltaEvent,
    TextPartDelta,
)

# 联合类型，便于下游消费
AgentRxEvent = AgentStreamEvent | AgentRunResultEvent


def is_text_delta_event(event: AgentRxEvent) -> bool:
    """判断是否为文本增量事件（用于 Markdown 渲染）。"""
    if isinstance(event, PartDeltaEvent):
        return isinstance(event.delta, TextPartDelta)
    return False


def extract_text_delta(event: AgentRxEvent) -> str:
    """从 PartDeltaEvent 中提取文本增量。非文本事件返回空字符串。"""
    if isinstance(event, PartDeltaEvent) and isinstance(event.delta, TextPartDelta):
        return event.delta.content_delta or ""
    return ""


def is_tool_call_event(event: AgentRxEvent) -> bool:
    """判断是否为工具调用事件。"""
    return isinstance(event, FunctionToolCallEvent)


def is_tool_result_event(event: AgentRxEvent) -> bool:
    """判断是否为工具结果事件。"""
    return isinstance(event, FunctionToolResultEvent)


def is_final_result_event(event: AgentRxEvent) -> bool:
    """判断是否为最终结果事件。"""
    return isinstance(event, AgentRunResultEvent)
