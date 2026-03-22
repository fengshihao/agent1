"""memory tool: SQLite long-term memory (schema aligned with java MemoryTool)."""

from __future__ import annotations

import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Sequence, Tuple

_DEFAULT_LIMIT = 10
_MAX_LIMIT = 20

_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS memories (
    id TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('data','code','txt','calen')),
    keywords TEXT NOT NULL,
    summary TEXT NOT NULL,
    content TEXT NOT NULL
)
"""


def make_memory_tool(db_path: Path):
    path = db_path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    _init_database(path)

    def memory(
        action: str,
        memory_id: str = "",
        query: str = "",
        mem_type: str = "",
        keywords: str = "",
        summary: str = "",
        content: str = "",
        limit: int = _DEFAULT_LIMIT,
    ) -> str:
        """Search/read/write long-term memory in local SQLite.

        Args:
            action: One of search, read, write.
            memory_id: Required for read; optional for write (auto UUID if empty).
            query: Search text (id/keywords/summary/content). See system prompt for operators.
            mem_type: Filter type: data|code|txt|calen.
            keywords: Comma-separated keywords for write.
            summary: Short summary required for write (<100 chars).
            content: Full content for write.
            limit: search result cap (default 10, max 20).
        """
        act = (action or "").strip().lower()
        if act == "search":
            return _search(path, query, mem_type, limit)
        if act == "read":
            return _read(path, memory_id.strip())
        if act == "write":
            return _write(path, memory_id.strip(), mem_type, keywords, summary, content)
        return "错误：未知 action，支持 search/read/write"

    return memory


def _init_database(path: Path) -> None:
    conn = sqlite3.connect(str(path))
    try:
        conn.execute(_TABLE_SQL)
        conn.commit()
        try:
            conn.execute("ALTER TABLE memories ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
            conn.commit()
        except sqlite3.OperationalError:
            pass
        conn.execute("CREATE INDEX IF NOT EXISTS idx_memories_type ON memories(type)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_memories_created_at ON memories(created_at)")
        conn.commit()
    finally:
        conn.close()


def _search(db_path: Path, query: str, mem_type: str, limit: int) -> str:
    lim = _DEFAULT_LIMIT if limit <= 0 else min(int(limit), _MAX_LIMIT)
    type_filter = (mem_type or "").strip()
    clause, args = _build_query_clause(query)
    sql = (
        "SELECT id, created_at, type, keywords, summary FROM memories WHERE "
        + clause
        + " AND (? = '' OR type = ?) ORDER BY datetime(created_at) DESC LIMIT ?"
    )
    params: List[str | int] = list(args)
    params.extend([type_filter, type_filter, lim])
    try:
        conn = sqlite3.connect(str(db_path))
        try:
            rows = conn.execute(sql, params).fetchall()
        finally:
            conn.close()
    except Exception as e:
        return f"错误：memory.search 执行失败: {e}"
    if not rows:
        return "未找到匹配记忆。"
    lines = [
        f"- id={r[0]} | created_at={r[1]} | type={r[2]} | keywords={r[3]} | summary={r[4]}"
        for r in rows
    ]
    return f"搜索结果（最多 {lim} 条，建议先根据 summary 判断再 read）:\n" + "\n".join(lines)


def _read(db_path: Path, mem_id: str) -> str:
    if not mem_id:
        return "错误：read 需要 id"
    try:
        conn = sqlite3.connect(str(db_path))
        try:
            row = conn.execute(
                "SELECT id, created_at, type, keywords, summary, content FROM memories WHERE id = ?",
                (mem_id,),
            ).fetchone()
        finally:
            conn.close()
    except Exception as e:
        return f"错误：memory.read 执行失败: {e}"
    if not row:
        return f"未找到该记忆: {mem_id}"
    return (
        f"id: {row[0]}\ncreated_at: {row[1]}\ntype: {row[2]}\nkeywords: {row[3]}\n"
        f"summary: {row[4]}\ncontent:\n{row[5]}"
    )


def _write(
    db_path: Path,
    mem_id: str,
    mem_type: str,
    keywords: str,
    summary: str,
    content: str,
) -> str:
    t = (mem_type or "").strip()
    if t not in ("data", "code", "txt", "calen"):
        return "错误：write 需要合法 type（data/code/txt/calen）"
    summ = (summary or "").strip()
    if not summ:
        return "错误：write 需要 summary（<100字）"
    if len(summ) >= 100:
        return "错误：summary 需小于100字"
    cont = content or ""
    if not cont.strip():
        return "错误：write 需要 content"
    mid = mem_id or str(uuid.uuid4())
    created = datetime.now(timezone.utc).isoformat()
    try:
        conn = sqlite3.connect(str(db_path))
        try:
            conn.execute(
                "INSERT INTO memories(id, created_at, type, keywords, summary, content) VALUES (?,?,?,?,?,?)",
                (mid, created, t, (keywords or "").strip(), summ, cont),
            )
            conn.commit()
        finally:
            conn.close()
    except Exception as e:
        return f"错误：memory.write 执行失败: {e}"
    return f"写入成功: id={mid}"


def _build_query_clause(query: str) -> Tuple[str, Sequence[str]]:
    q = (query or "").strip()
    if not q:
        return "1=1", ()
    normalized = _normalize_query(q)
    or_parts = normalized.split("||")
    or_sql: List[str] = []
    args: List[str] = []
    for or_part in or_parts:
        trimmed_or = or_part.strip()
        if not trimmed_or:
            continue
        and_parts = trimmed_or.split("&")
        and_sql: List[str] = []
        for raw in and_parts:
            token = raw.strip()
            if not token:
                continue
            negate = token.startswith("!")
            if negate:
                token = token[1:].strip()
            if not token:
                continue
            like = _wildcard_to_sql_like(token)
            expr = (
                "(id LIKE ? ESCAPE '\\' OR keywords LIKE ? ESCAPE '\\' OR "
                "summary LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\')"
            )
            and_sql.append(f"NOT {expr}" if negate else expr)
            args.extend([like, like, like, like])
        if and_sql:
            or_sql.append("(" + " AND ".join(and_sql) + ")")
    if not or_sql:
        return "1=1", ()
    return "(" + " OR ".join(or_sql) + ")", args


def _normalize_query(query: str) -> str:
    q = query.strip()
    if "||" in q or "&" in q or "!" in q:
        return q
    parts = [p.strip() for p in q.split() if p.strip()]
    if not parts:
        return ""
    return " || ".join(parts)


def _wildcard_to_sql_like(raw: str) -> str:
    esc = raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_")
    if "%" in esc or "_" in esc:
        return esc
    return f"%{esc}%"
