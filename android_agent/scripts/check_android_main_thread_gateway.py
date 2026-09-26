#!/usr/bin/env python3
"""
Detect ProductivityAgentGateway calls that block the caller thread (Future.get)
from UI layers without withContext(Dispatchers.IO).

Usage:
  ./check-android-agent-main-thread.sh
  python android_agent/scripts/check_android_main_thread_gateway.py

Rules:
  - ui.view / ui.overlay: must not call blocking gateway methods at all.
  - ui.viewmodel: blocking gateway calls must occur while inside a
    withContext(Dispatchers.IO) { ... } brace region (including nested suspend helpers).
  - logic.data / logic.business: unrestricted (IO belongs here).

Gateway blocking methods are discovered from ProductivityAgentGateway.kt
(any public fun whose body delegates to execute { ... }).
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys
from dataclasses import dataclass
from typing import Iterable

PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)\s*$", re.MULTILINE)
FUN_SPLIT_RE = re.compile(r"\n(?=\s*(?:override\s+)?fun\s+)")
FUN_NAME_RE = re.compile(r"^(?:override\s+)?fun\s+(\w+)")
GATEWAY_CALL_RE = re.compile(r"\bgateway\.(\w+)\s*\(")
WITH_IO_RE = re.compile(r"withContext\s*\(\s*Dispatchers\.IO\s*\)")


@dataclass(frozen=True)
class KotlinFile:
    path: pathlib.Path
    package: str
    text: str


def parse_package(text: str) -> str:
    m = PACKAGE_RE.search(text)
    if not m:
        raise ValueError("missing package declaration")
    return m.group(1)


def ui_layer(package: str) -> str | None:
    if ".ui.viewmodel" in package:
        return "ui.viewmodel"
    if ".ui.view" in package:
        return "ui.view"
    if ".ui.overlay" in package:
        return "ui.overlay"
    return None


def discover_blocking_gateway_methods(gateway_path: pathlib.Path) -> frozenset[str]:
    text = gateway_path.read_text(encoding="utf-8")
    blocking: set[str] = set()
    for chunk in FUN_SPLIT_RE.split(text):
        name_m = FUN_NAME_RE.search(chunk.lstrip())
        if not name_m:
            continue
        name = name_m.group(1)
        if name in {"execute", "closeQuietly"}:
            continue
        if chunk.lstrip().startswith("private fun"):
            continue
        if re.search(r"=\s*execute\s*\{", chunk) or re.search(r"\bexecute\s*\{", chunk):
            blocking.add(name)
    if "override fun close()" in text:
        blocking.add("close")
    if not blocking:
        raise RuntimeError(f"no blocking gateway methods found in {gateway_path}")
    return frozenset(blocking)


def collect_kotlin_files(root: pathlib.Path) -> list[KotlinFile]:
    files: list[KotlinFile] = []
    for path in sorted(root.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        try:
            pkg = parse_package(text)
        except ValueError:
            continue
        files.append(KotlinFile(path=path, package=pkg, text=text))
    return files


FUN_HEADER_RE = re.compile(
    r"^\s*(?:(?:private|internal|protected|public|override)\s+)*fun\s+",
    re.MULTILINE,
)


def split_kotlin_functions(text: str) -> list[tuple[int, str]]:
    """Return (1-based start line, function chunk) for each top-level fun in a class."""
    matches = list(FUN_HEADER_RE.finditer(text))
    if not matches:
        return [(1, text)]
    chunks: list[tuple[int, str]] = []
    for i, m in enumerate(matches):
        start = m.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        start_line = text.count("\n", 0, start) + 1
        chunks.append((start_line, text[start:end]))
    return chunks


def _apply_braces(line: str, stack: list[str], pending_io: bool) -> tuple[list[str], bool]:
    for ch in line:
        if ch == "{":
            stack.append("io" if pending_io else "block")
            pending_io = False
        elif ch == "}" and stack:
            stack.pop()
    return stack, pending_io


def blocking_gateway_errors_in_chunk(
    chunk: str,
    chunk_start_line: int,
    path: pathlib.Path,
    blocking_methods: frozenset[str],
) -> list[str]:
    errors: list[str] = []
    stack: list[str] = []
    pending_io = False
    lines = chunk.splitlines()
    for offset, line in enumerate(lines):
        line_no = chunk_start_line + offset
        line_pending = pending_io or bool(WITH_IO_RE.search(line))
        for m in GATEWAY_CALL_RE.finditer(line):
            method = m.group(1)
            if method not in blocking_methods:
                continue
            probe_stack: list[str] = list(stack)
            probe_pending = line_pending
            prefix = line[: m.start()]
            if WITH_IO_RE.search(prefix):
                probe_pending = True
            probe_stack, probe_pending = _apply_braces(prefix, probe_stack, probe_pending)
            if not any(frame == "io" for frame in probe_stack):
                errors.append(
                    f"{path}:{line_no}: blocking gateway.{method}() must run inside "
                    "withContext(Dispatchers.IO) { ... } (ProductivityAgentGateway uses Future.get)."
                )
        if WITH_IO_RE.search(line):
            pending_io = True
        stack, pending_io = _apply_braces(line, stack, pending_io)
    return errors


def check_viewmodel_blocking_calls(
    file: KotlinFile,
    blocking_methods: frozenset[str],
) -> list[str]:
    errors: list[str] = []
    for start_line, chunk in split_kotlin_functions(file.text):
        errors.extend(
            blocking_gateway_errors_in_chunk(chunk, start_line, file.path, blocking_methods)
        )
    return errors


def check_view_blocking_calls(
    file: KotlinFile,
    blocking_methods: frozenset[str],
) -> list[str]:
    errors: list[str] = []
    lines = file.text.splitlines()
    for line_no, line in enumerate(lines, start=1):
        for m in GATEWAY_CALL_RE.finditer(line):
            method = m.group(1)
            if method in blocking_methods:
                errors.append(
                    f"{file.path}:{line_no}: ui layer must not call blocking gateway.{method}(); "
                    "pass data via navigation args or load in ViewModel on Dispatchers.IO."
                )
    return errors


def run(root: pathlib.Path, gateway_path: pathlib.Path) -> int:
    blocking = discover_blocking_gateway_methods(gateway_path)
    errors: list[str] = []
    for kt in collect_kotlin_files(root):
        layer = ui_layer(kt.package)
        if layer == "ui.viewmodel":
            errors.extend(check_viewmodel_blocking_calls(kt, blocking))
        elif layer in {"ui.view", "ui.overlay"}:
            errors.extend(check_view_blocking_calls(kt, blocking))

    if errors:
        print("Android main-thread gateway check failed:")
        for err in errors:
            print(f"- {err}")
        print(f"(blocking gateway methods: {', '.join(sorted(blocking))})")
        return 1

    print(
        "Android main-thread gateway check passed "
        f"({len(blocking)} blocking methods tracked)."
    )
    return 0


def self_test() -> None:
    blocking = frozenset({"loadTranscript", "listSessions"})
    sample_vm = KotlinFile(
        path=pathlib.Path("SampleVM.kt"),
        package="com.example.productivity.ui.viewmodel",
        text="""
        private suspend fun load() {
            val x = withContext(Dispatchers.IO) {
                gateway.loadTranscript("id")
            }
        }
        fun bad() {
            gateway.listSessions()
        }
        fun okInit() {
            viewModelScope.launch {
                load()
            }
        }
        """.strip(),
    )
    errs = check_viewmodel_blocking_calls(sample_vm, blocking)
    assert len(errs) == 1 and "listSessions" in errs[0]

    sample_view = KotlinFile(
        path=pathlib.Path("Nav.kt"),
        package="com.example.productivity.ui.view",
        text='val t = gateway.listSessions()',
    )
    errs = check_view_blocking_calls(sample_view, blocking)
    assert len(errs) == 1

    one_liner = KotlinFile(
        path=pathlib.Path("VM.kt"),
        package="com.example.productivity.ui.viewmodel",
        text="""
        fun refresh() {
            val sessions = withContext(Dispatchers.IO) { gateway.listSessions() }
        }
        """.strip(),
    )
    assert check_viewmodel_blocking_calls(one_liner, blocking) == []


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    repo = pathlib.Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Check UI layers for blocking gateway calls.")
    parser.add_argument(
        "--root",
        default=str(repo / "app" / "src" / "main" / "java"),
        help="Kotlin source root",
    )
    parser.add_argument(
        "--gateway",
        default=str(
            repo
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "dynamicui"
            / "demo"
            / "productivity"
            / "logic"
            / "business"
            / "ProductivityAgentGateway.kt"
        ),
        help="ProductivityAgentGateway.kt path",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run built-in assertions and exit",
    )
    return parser.parse_args(argv)


def main(argv: Iterable[str]) -> int:
    args = parse_args(argv)
    if args.self_test:
        self_test()
        print("self-test passed")
        return 0
    root = pathlib.Path(args.root)
    gateway = pathlib.Path(args.gateway)
    if not root.exists():
        print(f"Source root does not exist: {root}")
        return 2
    if not gateway.exists():
        print(f"Gateway file does not exist: {gateway}")
        return 2
    return run(root, gateway)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
