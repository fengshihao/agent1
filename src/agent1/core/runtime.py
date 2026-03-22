"""Wraps pydantic_ai.Agent with lifecycle events + usage limits (Java AgentRuntime parity)."""

from __future__ import annotations

import inspect
from pathlib import Path
from typing import Any, AsyncIterator, Callable, Dict, List, Optional

from agent1.core.events import (
    AgentEventType,
    message_update_payload,
    tool_end_payload,
    tool_start_payload,
)

try:
    from pydantic_ai.usage import UsageLimits
except ImportError:
    UsageLimits = None  # type: ignore[misc, assignment]

Listener = Callable[[AgentEventType, Any], None]


def _filter_run_kwargs(runner: Any, kwargs: Dict[str, Any]) -> Dict[str, Any]:
    try:
        sig = inspect.signature(runner)
        params = sig.parameters
    except (TypeError, ValueError):
        return dict(kwargs)
    out: Dict[str, Any] = {}
    for k, v in kwargs.items():
        if k in params:
            out[k] = v
    return out


class Agent1Runtime:
    """Thin shell: subscribe to AgentEventType, forward run_stream_events, optional memory catalog injection."""

    def __init__(
        self,
        agent: Any,
        *,
        memory_db: Optional[Path] = None,
        max_turns: int = 12,
        max_tool_calls: int = 24,
    ) -> None:
        self._agent = agent
        self._memory_db = memory_db
        self._listeners: List[Listener] = []
        self._usage_limits = _build_usage_limits(max_turns, max_tool_calls)

    @property
    def memory_db_path(self) -> Optional[Path]:
        return self._memory_db

    def subscribe(self, listener: Listener) -> Callable[[], None]:
        self._listeners.append(listener)

        def unsubscribe() -> None:
            if listener in self._listeners:
                self._listeners.remove(listener)

        return unsubscribe

    def _emit(self, event_type: AgentEventType, payload: Any = None) -> None:
        for fn in list(self._listeners):
            try:
                fn(event_type, payload)
            except Exception:
                pass

    async def run_stream_events(
        self,
        user_prompt: str,
        *,
        message_history: Any = None,
        usage: Any = None,
        inject_memory_catalog: bool = False,
    ) -> AsyncIterator[Any]:
        from agent1.core.memory_catalog import build_memory_catalog_section
        from pydantic_ai.messages import (
            FunctionToolCallEvent,
            FunctionToolResultEvent,
            PartDeltaEvent,
            TextPartDelta,
        )
        from pydantic_ai.run import AgentRunResultEvent

        extra: dict[str, Any] = {}
        if inject_memory_catalog and self._memory_db is not None:
            extra["instructions"] = build_memory_catalog_section(self._memory_db)

        run_kw: dict[str, Any] = dict(extra)
        if usage is not None:
            run_kw["usage"] = usage
        if message_history is not None:
            run_kw["message_history"] = message_history
        if self._usage_limits is not None:
            run_kw["usage_limits"] = self._usage_limits

        stream_kw = _filter_run_kwargs(self._agent.run_stream_events, run_kw)

        self._emit(AgentEventType.AGENT_START, None)
        self._emit(AgentEventType.TURN_START, None)
        self._emit(AgentEventType.MESSAGE_START, None)
        try:
            async for event in self._agent.run_stream_events(user_prompt, **stream_kw):
                self._map_stream_event(
                    event,
                    PartDeltaEvent,
                    TextPartDelta,
                    FunctionToolCallEvent,
                    FunctionToolResultEvent,
                    AgentRunResultEvent,
                )
                yield event
        except Exception as e:
            self._emit(AgentEventType.AGENT_ERROR, {"error": str(e)})
            raise
        finally:
            self._emit(AgentEventType.MESSAGE_END, None)
            self._emit(AgentEventType.TURN_END, None)
            self._emit(AgentEventType.AGENT_END, None)

    def run_sync(
        self,
        user_prompt: str,
        *,
        message_history: Any = None,
        usage: Any = None,
        inject_memory_catalog: bool = False,
    ) -> Any:
        from agent1.core.memory_catalog import build_memory_catalog_section

        extra: dict[str, Any] = {}
        if inject_memory_catalog and self._memory_db is not None:
            extra["instructions"] = build_memory_catalog_section(self._memory_db)

        run_kw: dict[str, Any] = dict(extra)
        if usage is not None:
            run_kw["usage"] = usage
        if message_history is not None:
            run_kw["message_history"] = message_history
        if self._usage_limits is not None:
            run_kw["usage_limits"] = self._usage_limits

        sync_kw = _filter_run_kwargs(self._agent.run_sync, run_kw)

        self._emit(AgentEventType.AGENT_START, None)
        self._emit(AgentEventType.TURN_START, None)
        self._emit(AgentEventType.MESSAGE_START, None)
        try:
            return self._agent.run_sync(user_prompt, **sync_kw)
        except Exception as e:
            self._emit(AgentEventType.AGENT_ERROR, {"error": str(e)})
            raise
        finally:
            self._emit(AgentEventType.MESSAGE_END, None)
            self._emit(AgentEventType.TURN_END, None)
            self._emit(AgentEventType.AGENT_END, None)

    def _map_stream_event(
        self,
        event: Any,
        PartDeltaEvent: type,
        TextPartDelta: type,
        FunctionToolCallEvent: type,
        FunctionToolResultEvent: type,
        AgentRunResultEvent: type,
    ) -> None:
        if isinstance(event, PartDeltaEvent) and isinstance(getattr(event, "delta", None), TextPartDelta):
            delta = event.delta.content_delta or ""
            if delta:
                self._emit(AgentEventType.MESSAGE_UPDATE, message_update_payload(delta))
        elif isinstance(event, FunctionToolCallEvent):
            part = event.part
            self._emit(
                AgentEventType.TOOL_EXECUTION_START,
                tool_start_payload(
                    part.tool_name,
                    getattr(part, "args", None),
                    part.tool_call_id,
                ),
            )
        elif isinstance(event, FunctionToolResultEvent):
            content = getattr(event.result, "content", None)
            text = content if isinstance(content, str) else str(content or "")
            self._emit(
                AgentEventType.TOOL_EXECUTION_END,
                tool_end_payload(event.tool_call_id, text, is_error=False),
            )
        elif isinstance(event, AgentRunResultEvent):
            self._emit(AgentEventType.MESSAGE_END, {"result": event.result})


def _build_usage_limits(max_turns: int, max_tool_calls: int) -> Any:
    if UsageLimits is None:
        return None
    # Align with java_agent: cap tool calls; leave model request headroom for tool rounds + final answer.
    req_limit = max(20, max_turns + 10) if max_turns > 0 else 50
    return UsageLimits(request_limit=req_limit, tool_calls_limit=max_tool_calls)
