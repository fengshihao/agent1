"""SQLite schema snapshot for system prompt (parity with MemorySqliteCatalog)."""

from __future__ import annotations

import sqlite3
from pathlib import Path

_MAX_SECTION_CHARS = 6000
_HEADER = "=== 记忆库结构（自动生成）===\n"


def build_memory_catalog_section(db_path: Path) -> str:
    if db_path is None:
        return ""
    abs_path = db_path.resolve()
    if not abs_path.exists():
        return _HEADER + "（数据库文件尚未创建。）\n"
    lines: list[str] = []
    try:
        conn = sqlite3.connect(str(abs_path))
        try:
            cur = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
            )
            tables = [r[0] for r in cur.fetchall()]
            for table in tables:
                lines.append(f"- table: {table}")
                esc = table.replace("'", "''")
                for row in conn.execute(f"PRAGMA table_info('{esc}')"):
                    col_name, col_type, notnull, _, pk = row[1], row[2], row[3], row[4], row[5]
                    lines.append(f"  - {col_name} | {col_type} | notnull={notnull} | pk={pk}")
            if not lines:
                return _HEADER + "（当前尚无用户表。）\n"
            body = "\n".join(lines)
            if len(body) > _MAX_SECTION_CHARS:
                body = body[:_MAX_SECTION_CHARS] + "\n…（已截断）"
            return _HEADER + body + "\n"
        finally:
            conn.close()
    except Exception as e:
        return _HEADER + f"（读取失败: {e}）\n"
