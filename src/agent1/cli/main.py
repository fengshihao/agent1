"""CLI 入口：单次/交互模式，Rich Markdown 渲染，流式输出与 JSONL 日志。"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import sys
import uuid
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from pydantic_ai.exceptions import ModelHTTPError
from pydantic_ai.messages import FunctionToolCallEvent, FunctionToolResultEvent, PartDeltaEvent, TextPartDelta
from pydantic_ai.run import AgentRunResultEvent
from pydantic_ai.usage import RunUsage
from rich.console import Console, Group
from rich.live import Live
from rich.markdown import Markdown

from agent1.agent_factory import create_workspace_agent
from agent1.core.runtime import Agent1Runtime
from agent1.logging_utils import get_log_path, write_jsonl_event
from agent1.skills.loader import ClaudeSkill
from agent1.skills.prompt_renderer import render_skill_prompt

_SKILL_CMD = re.compile(r"^/([A-Za-z0-9:_-]+)(?:\s+(.*))?$")

# 与 java_agent JavaAgentCli 中 TOOL_*_PREVIEW_LIMIT 对齐
_TOOL_ARG_PREVIEW_LIMIT = 220
_TOOL_TEXT_PREVIEW_LIMIT = 280


def _truncate(text: str, max_len: int) -> str:
    if text is None:
        return ""
    if len(text) <= max_len:
        return text
    return text[:max_len] + "...(truncated)"


def _tool_args_preview(tool_args: object) -> str:
    if tool_args is None:
        return ""
    if isinstance(tool_args, str):
        raw = tool_args.strip()
    elif isinstance(tool_args, dict):
        try:
            raw = json.dumps(tool_args, ensure_ascii=False, separators=(",", ":"))
        except (TypeError, ValueError):
            raw = str(tool_args)
    else:
        try:
            raw = json.dumps(tool_args, ensure_ascii=False, separators=(",", ":"))
        except (TypeError, ValueError):
            raw = str(tool_args)
    return _truncate(raw, _TOOL_ARG_PREVIEW_LIMIT)


def _print_tool_call_line(out: Console, tool_name: str, tool_args: object, tool_call_id: str) -> None:
    preview = _tool_args_preview(tool_args)
    out.print()
    out.print(f"[cyan][tool] 调用 {tool_name} args={preview}[/cyan]")


def _print_tool_result_line(
    out: Console,
    tool_call_id: str,
    result_text: str,
    *,
    is_error: bool = False,
    error_message: str = "",
) -> None:
    if is_error:
        msg = _truncate(error_message or result_text or "unknown", _TOOL_TEXT_PREVIEW_LIMIT)
        out.print(f"[red][tool] 失败 {tool_call_id}: {msg}[/red]")
    else:
        preview = _truncate(result_text or "", _TOOL_TEXT_PREVIEW_LIMIT)
        out.print(f"[green][tool] 完成 {tool_call_id} result={preview}[/green]")


def _print_tool_logs_from_message_suffix(out: Console, previous: Optional[List], current: List) -> None:
    """非流式 run_sync 后，从本轮新增的 ModelMessage 里还原工具调用/结果（对齐 Java 终端信息）。"""
    if not current:
        return
    start = len(previous or [])
    if start >= len(current):
        return
    for msg in current[start:]:
        parts = getattr(msg, "parts", None) or []
        for part in parts:
            pname = type(part).__name__
            tool_name = getattr(part, "tool_name", None)
            tool_call_id = getattr(part, "tool_call_id", None)
            if pname == "ToolCallPart" or (tool_name and tool_call_id):
                args = getattr(part, "args", None)
                if args is None and callable(getattr(part, "args_as_dict", None)):
                    try:
                        args = part.args_as_dict()
                    except Exception:
                        args = getattr(part, "arguments_json", None)
                _print_tool_call_line(out, str(tool_name), args, str(tool_call_id))
                continue
            if pname == "ToolReturnPart" or (
                tool_call_id and not tool_name and getattr(part, "content", None) is not None
            ):
                content = getattr(part, "content", None)
                text = content if isinstance(content, str) else str(content)
                err = bool(getattr(part, "is_error", False))
                em = str(getattr(part, "error_message", "") or "")
                _print_tool_result_line(out, str(tool_call_id), text, is_error=err, error_message=em)


def main(args: Optional[Sequence[str]] = None) -> None:
    """CLI 入口。"""
    parser = argparse.ArgumentParser(
        description="Agent1：read/memory/bash/python/skill 工具 + 与 java_agent 对齐的薄运行时"
    )
    parser.add_argument(
        "prompt",
        nargs="?",
        help="单次模式：直接传入问题。省略则进入交互模式。",
    )
    parser.add_argument(
        "--no-stream",
        action="store_true",
        help="禁用流式输出，等待完整响应后一次性显示。",
    )
    parsed = parser.parse_args(args)

    try:
        _, runtime, skills_by_name = create_workspace_agent(Path.cwd())
    except RuntimeError as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)

    _print_startup_hints(Console(), runtime)

    try:
        if parsed.prompt:
            if parsed.no_stream:
                _run_once_sync(parsed.prompt, runtime, skills_by_name)
            else:
                asyncio.run(_run_once_stream(parsed.prompt, runtime, skills_by_name))
        else:
            asyncio.run(_run_interactive(parsed.no_stream, runtime, skills_by_name))
    except KeyboardInterrupt:
        sys.exit(130)


def _print_startup_hints(console: Console, runtime: Agent1Runtime) -> None:
    mem = runtime.memory_db_path
    if mem is not None:
        console.print(f"[dim]长期记忆 SQLite: {mem}（AGENT1_MEMORY_DB 可覆盖）[/dim]")
    skills = list((Path.cwd() / ".claude" / "skills").glob("*/SKILL.md")) if (Path.cwd() / ".claude" / "skills").is_dir() else []
    console.print(f"[dim]发现本地 skill 目录: {len(skills)} 个 SKILL.md[/dim]")


def _resolve_cli_prompt(
    user_input: str, skills_by_name: Dict[str, ClaudeSkill], console: Console
) -> Tuple[str, Optional[str]]:
    trimmed = (user_input or "").strip()
    if not trimmed.startswith("/"):
        return user_input, None
    m = _SKILL_CMD.match(trimmed)
    if not m:
        return user_input, None
    command, raw_args = m.group(1), m.group(2) or ""
    sk = skills_by_name.get(command)
    if sk is None:
        console.print(f"[yellow][skill] 未找到: {command}，按普通消息处理[/yellow]")
        return user_input, None
    console.print(f"[cyan][skill] 使用 {command}[/cyan]")
    return render_skill_prompt(sk, raw_args), command


def _run_once_sync(user_input: str, runtime: Agent1Runtime, skills_by_name: Dict[str, ClaudeSkill]) -> None:
    """单次模式，非流式。"""
    console = Console()
    prompt, skill_name = _resolve_cli_prompt(user_input, skills_by_name, console)
    run_id = _new_run_id()
    log_path = get_log_path()
    console.print(f"[dim]运行中（run_id={run_id}）...[/dim]")

    write_jsonl_event(
        "run_started",
        run_id,
        mode="single_sync",
        prompt=prompt,
        user_input=user_input,
        skill_name=skill_name or "",
        log_file=str(log_path),
    )
    write_jsonl_event("model_request", run_id, input_prompt=prompt)

    try:
        result = runtime.run_sync(prompt, inject_memory_catalog=True)
        _print_tool_logs_from_message_suffix(console, None, list(result.all_messages()))
        output_text = result.output if isinstance(result.output, str) else str(result.output)
        _print_markdown(console, output_text)

        write_jsonl_event(
            "model_response",
            run_id,
            output=output_text,
            all_messages_count=len(result.all_messages()),
        )
        run_usage = _extract_usage_dict(result.usage())
        session_usage = _new_empty_usage()
        _merge_usage(session_usage, run_usage)
        _print_usage_table(console, run_usage, session_usage, run_id)
        write_jsonl_event("usage", run_id, scope="run", **run_usage)
        write_jsonl_event("usage", run_id, scope="session", **session_usage)
        write_jsonl_event("run_completed", run_id, status="ok")
        console.print(f"[dim]已完成。日志: {log_path}[/dim]")
    except Exception as e:
        write_jsonl_event("run_failed", run_id, error=repr(e))
        console.print(f"[red]{_friendly_error_message(e)}[/red]")
        console.print(f"[dim]日志: {log_path}[/dim]")
        sys.exit(1)


async def _run_once_stream(
    user_input: str, runtime: Agent1Runtime, skills_by_name: Dict[str, ClaudeSkill]
) -> None:
    """单次模式，流式输出。"""
    await _run_stream_direct(
        Console(), user_input, runtime, skills_by_name, session_usage=_new_empty_usage()
    )


async def _run_stream_direct(
    console: Console,
    user_input: str,
    runtime: Agent1Runtime,
    skills_by_name: Dict[str, ClaudeSkill],
    message_history: Optional[List] = None,
    session_usage: Optional[Dict[str, int]] = None,
) -> Tuple[str, List]:
    """流式执行。返回 (最终文本, 更新后的 message_history)。"""
    prompt, skill_name = _resolve_cli_prompt(user_input, skills_by_name, console)
    run_id = _new_run_id()
    log_path = get_log_path()
    kwargs = {"message_history": message_history} if message_history else {}

    write_jsonl_event(
        "run_started",
        run_id,
        mode="stream",
        prompt=prompt,
        user_input=user_input,
        skill_name=skill_name or "",
        message_history_count=len(message_history or []),
        log_file=str(log_path),
    )
    write_jsonl_event("model_request", run_id, input_prompt=prompt)

    console.print(f"[dim]正在连接模型（run_id={run_id}）...[/dim]")

    accumulated: List[str] = []
    final_result = None
    stream_error: Optional[Exception] = None
    stream_usage = RunUsage()
    live = Live(console=console, refresh_per_second=12)

    try:
        live.start()
        live.update(_build_stream_renderable("", _extract_usage_dict(stream_usage), dict(session_usage or _new_empty_usage()), run_id, "请求已发送"))
        async for event in runtime.run_stream_events(
            prompt, usage=stream_usage, inject_memory_catalog=True, **kwargs
        ):
            if isinstance(event, PartDeltaEvent) and isinstance(event.delta, TextPartDelta):
                delta = event.delta.content_delta or ""
                if delta:
                    accumulated.append(delta)
                    run_usage = _extract_usage_dict(stream_usage)
                    session_usage_live = dict(session_usage or _new_empty_usage())
                    _merge_usage(session_usage_live, run_usage)
                    live.update(
                        _build_stream_renderable(
                            "".join(accumulated), run_usage, session_usage_live, run_id, "流式生成中"
                        )
                    )
                    write_jsonl_event("model_text_delta", run_id, delta=delta)
            elif isinstance(event, FunctionToolCallEvent):
                tool_name = event.part.tool_name
                tool_args = event.part.args
                _print_tool_call_line(live.console, tool_name, tool_args, event.part.tool_call_id)
                run_usage = _extract_usage_dict(stream_usage)
                session_usage_live = dict(session_usage or _new_empty_usage())
                _merge_usage(session_usage_live, run_usage)
                live.update(
                    _build_stream_renderable(
                        "".join(accumulated), run_usage, session_usage_live, run_id, f"工具: {tool_name}"
                    )
                )
                write_jsonl_event(
                    "tool_call",
                    run_id,
                    tool_name=tool_name,
                    tool_args=tool_args,
                    tool_call_id=event.part.tool_call_id,
                )
            elif isinstance(event, FunctionToolResultEvent):
                res = event.result
                rcontent = getattr(res, "content", "")
                rtext = rcontent if isinstance(rcontent, str) else str(rcontent or "")
                is_err = bool(getattr(res, "is_error", False))
                err_msg = str(getattr(res, "error_message", "") or "")
                _print_tool_result_line(
                    live.console,
                    event.tool_call_id,
                    rtext,
                    is_error=is_err,
                    error_message=err_msg,
                )
                run_usage = _extract_usage_dict(stream_usage)
                session_usage_live = dict(session_usage or _new_empty_usage())
                _merge_usage(session_usage_live, run_usage)
                live.update(
                    _build_stream_renderable(
                        "".join(accumulated), run_usage, session_usage_live, run_id, "流式生成中"
                    )
                )
                write_jsonl_event(
                    "tool_result",
                    run_id,
                    tool_call_id=event.tool_call_id,
                    result=rtext,
                    is_error=is_err,
                    error_message=err_msg or None,
                )
            elif isinstance(event, AgentRunResultEvent):
                final_result = event.result
                run_usage = _extract_usage_dict(stream_usage)
                session_usage_live = dict(session_usage or _new_empty_usage())
                _merge_usage(session_usage_live, run_usage)
                live.update(
                    _build_stream_renderable(
                        "".join(accumulated), run_usage, session_usage_live, run_id, "模型响应完成"
                    )
                )
    except Exception as e:
        write_jsonl_event("run_failed", run_id, error=repr(e))
        console.print(f"[red]{_friendly_error_message(e)}[/red]")
        console.print(f"[dim]日志: {log_path}[/dim]")
        stream_error = e
    finally:
        live.stop()

    if stream_error is not None:
        return "", (message_history or [])

    text = "".join(accumulated)
    if not text and final_result and isinstance(final_result.output, str):
        _print_markdown(console, final_result.output)
        text = final_result.output

    if final_result is not None:
        run_usage = _extract_usage_dict(final_result.usage())
        if session_usage is not None:
            _merge_usage(session_usage, run_usage)
            write_jsonl_event("usage", run_id, scope="run", **run_usage)
            write_jsonl_event("usage", run_id, scope="session", **session_usage)
        write_jsonl_event(
            "model_response",
            run_id,
            output=text,
            all_messages_count=len(final_result.all_messages()),
        )
    write_jsonl_event("run_completed", run_id, status="ok")
    console.print(f"[dim]已完成。日志: {log_path}[/dim]")

    new_history = list(final_result.all_messages()) if final_result else (message_history or [])
    return text, new_history


def _print_markdown(console: Console, text: str) -> None:
    """使用 Rich 渲染 Markdown。"""
    if not text.strip():
        return
    console.print(Markdown(text))


async def _run_interactive(
    no_stream: bool, runtime: Agent1Runtime, skills_by_name: Dict[str, ClaudeSkill]
) -> None:
    """交互模式：REPL 多轮对话。"""
    console = Console()
    message_history: List = []
    session_usage = _new_empty_usage()
    log_path = get_log_path()

    console.print("[bold]Agent1 交互模式[/bold]（输入 /exit 退出；/技能名 触发 SKILL）")
    console.print(f"[dim]日志文件: {log_path}[/dim]\n")

    while True:
        try:
            user_input = console.input("[bold cyan]你> [/bold cyan]")
        except (EOFError, KeyboardInterrupt):
            break

        user_input = user_input.strip()
        if not user_input:
            continue
        if user_input.lower() in ("/exit", "/quit", "exit", "quit"):
            break

        if no_stream:
            run_id = _new_run_id()
            prompt, skill_name = _resolve_cli_prompt(user_input, skills_by_name, console)
            write_jsonl_event(
                "run_started",
                run_id,
                mode="interactive_sync",
                prompt=prompt,
                user_input=user_input,
                skill_name=skill_name or "",
                message_history_count=len(message_history),
                log_file=str(log_path),
            )
            write_jsonl_event("model_request", run_id, input_prompt=prompt)
            console.print(f"[dim]运行中（run_id={run_id}）...[/dim]")
            try:
                prev_msgs = list(message_history) if message_history else []
                result = runtime.run_sync(
                    prompt,
                    message_history=message_history if message_history else None,
                    inject_memory_catalog=True,
                )
                new_msgs = list(result.all_messages())
                _print_tool_logs_from_message_suffix(console, prev_msgs, new_msgs)
                message_history = new_msgs
                output_text = result.output if isinstance(result.output, str) else str(result.output)
                _print_markdown(console, output_text)
                run_usage = _extract_usage_dict(result.usage())
                _merge_usage(session_usage, run_usage)
                _print_usage_table(console, run_usage, session_usage, run_id)
                write_jsonl_event("usage", run_id, scope="run", **run_usage)
                write_jsonl_event("usage", run_id, scope="session", **session_usage)
                write_jsonl_event(
                    "model_response",
                    run_id,
                    output=output_text,
                    all_messages_count=len(message_history),
                )
                write_jsonl_event("run_completed", run_id, status="ok")
                if _is_usage_over_limit(session_usage):
                    console.print(
                        "[bold red]已达到会话 token 上限，停止继续对话。[/bold red]"
                    )
                    break
            except Exception as e:
                write_jsonl_event("run_failed", run_id, error=repr(e))
                console.print(f"[red]{_friendly_error_message(e)}[/red]")
        else:
            _, message_history = await _run_stream_direct(
                console,
                user_input,
                runtime,
                skills_by_name,
                message_history=message_history if message_history else None,
                session_usage=session_usage,
            )
            if _is_usage_over_limit(session_usage):
                console.print("[bold red]已达到会话 token 上限，停止继续对话。[/bold red]")
                break

    console.print("\n再见！")


def _new_run_id() -> str:
    return uuid.uuid4().hex[:12]


def _new_empty_usage() -> Dict[str, int]:
    return {"input_tokens": 0, "output_tokens": 0, "total_tokens": 0, "requests": 0}


def _extract_usage_dict(usage_obj: object) -> Dict[str, int]:
    input_tokens = int(getattr(usage_obj, "input_tokens", 0) or 0)
    output_tokens = int(getattr(usage_obj, "output_tokens", 0) or 0)
    requests = int(getattr(usage_obj, "requests", 0) or 0)
    total_tokens = int(getattr(usage_obj, "total_tokens", input_tokens + output_tokens) or (input_tokens + output_tokens))
    return {
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": total_tokens,
        "requests": requests,
    }


def _merge_usage(target: Dict[str, int], delta: Dict[str, int]) -> None:
    target["input_tokens"] += delta["input_tokens"]
    target["output_tokens"] += delta["output_tokens"]
    target["total_tokens"] += delta["total_tokens"]
    target["requests"] += delta["requests"]


def _print_usage_table(
    console: Console, run_usage: Dict[str, int], session_usage: Dict[str, int], run_id: str
) -> None:
    console.print(_format_usage_line("本次用量", run_usage))
    console.print(_format_usage_line("会话累计", session_usage))

    limit = _usage_limit()
    if limit is not None:
        ratio = session_usage["total_tokens"] / limit if limit > 0 else 1.0
        if ratio >= 1.0:
            console.print(
                f"[bold red]Token 上限: {_format_token_count(session_usage['total_tokens'])}/{_format_token_count(limit)} (100%)[/bold red]"
            )
        elif ratio >= 0.8:
            console.print(
                f"[yellow]Token 上限预警: {_format_token_count(session_usage['total_tokens'])}/{_format_token_count(limit)} ({ratio:.0%})[/yellow]"
            )
        else:
            console.print(
                f"[dim]Token 上限: {_format_token_count(session_usage['total_tokens'])}/{_format_token_count(limit)} ({ratio:.0%})[/dim]"
            )


def _build_stream_renderable(
    text: str, run_usage: Dict[str, int], session_usage: Dict[str, int], run_id: str, phase: str
) -> Group:
    """流式输出：正文 + 实时 token 用量（非表格）。"""
    body = Markdown(text) if text.strip() else Markdown("*正在生成...*")
    run_usage_line = _format_usage_line(phase, run_usage)
    session_usage_line = _format_usage_line("会话累计", session_usage)
    run_id_line = f"[dim]run_id={run_id}[/dim]"
    return Group(body, run_usage_line, session_usage_line, run_id_line)


def _format_usage_line(prefix: str, usage: Dict[str, int]) -> str:
    return (
        f"[dim]{prefix} | 请求 {usage['requests']} | 输入 {_format_token_count(usage['input_tokens'])} "
        f"| 输出 {_format_token_count(usage['output_tokens'])} | 总 {_format_token_count(usage['total_tokens'])}[/dim]"
    )


def _format_token_count(value: int) -> str:
    """将 token 数显示为 K/M 单位。"""
    if value >= 1_000_000:
        return f"{value / 1_000_000:.1f}M"
    if value >= 1_000:
        return f"{value / 1_000:.1f}K"
    return str(value)


def _friendly_error_message(error: Exception) -> str:
    """将常见模型错误转换为更友好的中文提示。"""
    if isinstance(error, ModelHTTPError):
        body = str(getattr(error, "body", "") or "")
        if error.status_code == 403 and "AllocationQuota.FreeTierOnly" in body:
            return (
                "模型请求被拒绝（403）：免费额度已用尽，且当前账号启用了“仅免费额度”模式。"
                "请在 DashScope 控制台关闭该模式或开通付费后重试。"
            )
        return f"模型请求失败（HTTP {error.status_code}）：{body or repr(error)}"
    return f"错误: {error!r}"


def _usage_limit() -> Optional[int]:
    raw = os.environ.get("AGENT1_MAX_TOTAL_TOKENS")
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        return None


def _is_usage_over_limit(session_usage: Dict[str, int]) -> bool:
    limit = _usage_limit()
    return limit is not None and session_usage["total_tokens"] >= limit

