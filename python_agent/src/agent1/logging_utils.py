"""JSONL 日志工具：每行一个 JSON 事件。"""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict


def _default_log_path() -> Path:
    path_str = os.environ.get("AGENT1_LOG_FILE")
    if path_str:
        return Path(path_str).expanduser().resolve()
    return Path.cwd() / "logs" / "agent1.jsonl"


def get_log_path() -> Path:
    """返回当前日志文件路径（可通过 AGENT1_LOG_FILE 覆盖）。"""
    return _default_log_path()


def write_jsonl_event(event_type: str, run_id: str, **fields: Any) -> Path:
    """写入一条 JSONL 日志事件。"""
    log_path = _default_log_path()
    log_path.parent.mkdir(parents=True, exist_ok=True)

    payload: Dict[str, Any] = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "event_type": event_type,
        "run_id": run_id,
        **fields,
    }

    with log_path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(payload, ensure_ascii=False, default=str))
        f.write("\n")
    return log_path
