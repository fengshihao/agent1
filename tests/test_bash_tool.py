import subprocess
import unittest
from unittest.mock import patch

from agent1.tools.bash import _pick_windows_shell, run_bash


class TestWindowsShellPick(unittest.TestCase):
    def test_pick_windows_shell_prefers_bash(self) -> None:
        with patch("agent1.tools.bash.shutil.which") as mock_which:
            mock_which.side_effect = lambda name: {
                "bash": r"C:\Program Files\Git\bin\bash.exe",
                "powershell": r"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe",
                "pwsh": r"C:\Program Files\PowerShell\7\pwsh.exe",
            }.get(name)
            shell_kind, shell_path = _pick_windows_shell()

        self.assertEqual(shell_kind, "bash")
        self.assertEqual(shell_path, r"C:\Program Files\Git\bin\bash.exe")

    def test_pick_windows_shell_falls_back_to_cmd(self) -> None:
        with patch("agent1.tools.bash.shutil.which", return_value=None):
            with patch.dict("agent1.tools.bash.os.environ", {"ComSpec": r"C:\Windows\System32\cmd.exe"}, clear=True):
                shell_kind, shell_path = _pick_windows_shell()

        self.assertEqual(shell_kind, "cmd")
        self.assertEqual(shell_path, r"C:\Windows\System32\cmd.exe")


class TestRunBashOnWindows(unittest.TestCase):
    @patch("agent1.tools.bash.subprocess.run")
    @patch("agent1.tools.bash._pick_windows_shell", return_value=("bash", r"C:\Program Files\Git\bin\bash.exe"))
    @patch("agent1.tools.bash.os.name", "nt")
    def test_run_bash_uses_git_bash_when_available(self, _mock_pick, mock_run) -> None:
        mock_run.return_value = subprocess.CompletedProcess(args=[], returncode=0, stdout="ok\n", stderr="")

        output = run_bash("echo hello")

        self.assertEqual(output, "ok")
        mock_run.assert_called_once_with(
            [r"C:\Program Files\Git\bin\bash.exe", "-lc", "echo hello"],
            capture_output=True,
            text=True,
            timeout=60,
        )

    @patch("agent1.tools.bash.subprocess.run")
    @patch("agent1.tools.bash._pick_windows_shell", return_value=("powershell", r"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"))
    @patch("agent1.tools.bash.os.name", "nt")
    def test_run_bash_uses_powershell_when_no_bash(self, _mock_pick, mock_run) -> None:
        mock_run.return_value = subprocess.CompletedProcess(args=[], returncode=0, stdout="ps\n", stderr="")

        output = run_bash("Get-Date")

        self.assertEqual(output, "ps")
        mock_run.assert_called_once_with(
            [r"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe", "-NoProfile", "-Command", "Get-Date"],
            capture_output=True,
            text=True,
            timeout=60,
        )

    @patch("agent1.tools.bash.subprocess.run")
    @patch("agent1.tools.bash._pick_windows_shell", return_value=("cmd", None))
    @patch("agent1.tools.bash.os.name", "nt")
    def test_run_bash_falls_back_to_cmd(self, _mock_pick, mock_run) -> None:
        mock_run.return_value = subprocess.CompletedProcess(args=[], returncode=0, stdout="cmd\n", stderr="")

        output = run_bash("dir")

        self.assertEqual(output, "cmd")
        mock_run.assert_called_once_with(
            ["cmd", "/d", "/s", "/c", "dir"],
            capture_output=True,
            text=True,
            timeout=60,
        )

    def test_run_bash_empty_command(self) -> None:
        self.assertEqual(run_bash("   "), "错误：命令为空")


if __name__ == "__main__":
    unittest.main()
