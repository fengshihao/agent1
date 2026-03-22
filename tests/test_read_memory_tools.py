import tempfile
import unittest
from pathlib import Path

from agent1.tools.memory_tool import make_memory_tool
from agent1.tools.read_file_tool import make_read_tool


class TestReadTool(unittest.TestCase):
    def test_read_respects_workspace(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "a.txt").write_text("line1\nline2\n", encoding="utf-8")
            read = make_read_tool(root)
            out = read("a.txt")
            self.assertIn("line1", out)
            out2 = read("../../etc/passwd")
            self.assertIn("超出工作区", out2)


class TestMemoryTool(unittest.TestCase):
    def test_write_read_search(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / "m.sqlite"
            mem = make_memory_tool(db)
            self.assertIn("写入成功", mem("write", mem_type="txt", summary="hi", keywords="a", content="body"))
            self.assertIn("未找到", mem("read", memory_id="not-a-real-id"))
            # get id from search
            s = mem("search", query="hi")
            self.assertIn("id=", s)
            import re

            m = re.search(r"id=([a-f0-9-]{36})", s)
            self.assertIsNotNone(m)
            rid = m.group(1)
            self.assertIn("body", mem("read", memory_id=rid))
