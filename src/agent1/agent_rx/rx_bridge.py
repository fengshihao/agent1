"""将 agent.run_stream_events 桥接到 RxPy Observable."""

import asyncio
import functools
from typing import Any

import rx
from rx.disposable import Disposable


def _from_async_iterable(aiter_coro):
    """将 async iterable（协程返回的 async generator）转为 RxPy Observable。

    RxPy 无内置 from_async_iterable，使用 rx.create 实现。
    """
    def on_subscribe(observer, scheduler=None):
        loop = asyncio.get_event_loop()
        if loop.is_running():
            # 已在 async 上下文中，创建 task
            async def _consume():
                try:
                    async for item in aiter_coro:
                        observer.on_next(item)
                    observer.on_completed()
                except asyncio.CancelledError:
                    observer.on_completed()
                except Exception as e:
                    observer.on_error(e)

            task = asyncio.ensure_future(_consume())
            return Disposable(lambda: task.cancel())
        else:
            # 同步上下文，需要 run_until_complete
            async def _consume():
                try:
                    async for item in aiter_coro:
                        loop.call_soon_threadsafe(
                            functools.partial(observer.on_next, item)
                        )
                    loop.call_soon_threadsafe(observer.on_completed)
                except Exception as e:
                    loop.call_soon_threadsafe(
                        functools.partial(observer.on_error, e)
                    )

            task = asyncio.ensure_future(_consume())
            return Disposable(lambda: task.cancel())

    return rx.create(on_subscribe)


def agent_run_to_observable(agent, user_prompt: str, **kwargs: Any) -> rx.Observable:
    """将 Agent 的 run_stream_events 转为 RxPy Observable。

    Args:
        agent: Pydantic AI Agent 实例。
        user_prompt: 用户输入。
        **kwargs: 传递给 agent.run_stream_events 的额外参数（如 deps, message_history）。

    Returns:
        rx.Observable，发射 AgentStreamEvent | AgentRunResultEvent。
    """
    # run_stream_events 返回 async generator，可直接作为 async iterable
    return _from_async_iterable(agent.run_stream_events(user_prompt, **kwargs))
