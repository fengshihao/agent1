import tempfile
import unittest
from pathlib import Path

from agent1.tools.read_file import make_read_tool


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
