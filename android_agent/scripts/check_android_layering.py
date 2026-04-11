#!/usr/bin/env python3
"""
Check Android package layering constraints for Kotlin source files.

Usage:
  ./check-android-agent-layering.sh                 # 仓库根薄脚本（推荐）
  python android_agent/scripts/check_android_layering.py
  python android_agent/scripts/check_android_layering.py --root android_agent/app/src/main/java
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys
from dataclasses import dataclass
from typing import Iterable


PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)\s*$", re.MULTILINE)
IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z0-9_.*]+)\s*$", re.MULTILINE)

UI_FRAMEWORK_PREFIXES = (
    "android.app.",
    "androidx.fragment.",
    "android.view.View",
    "androidx.compose.",
)

VIEW_IO_IMPORT_PREFIXES = (
    "okhttp3.",
    "retrofit2.",
    "java.net.",
    "java.io.",
    "kotlin.io.",
    "android.database.",
    "androidx.room.",
)

VIEW_IO_KEYWORDS = (
    "Executors.",
    "runBlocking",
    ".execute(",
)

# 避免误报方法名如 hideOnMainThread()
VIEW_IO_THREAD_START_PATTERN = re.compile(r"\bThread\s*\(")

ALLOWED_LAYERED_UTILS = (
    ".ui.utils",
    ".logic.business.utils",
    ".logic.data.utils",
)

COMMON_FOUNDATION_SEGMENTS = (".common", ".foundation")

# Foreground Service 等 Android 宿主：放在 business 下此子包；脚本允许其 import android.app.*。
LOGIC_BUSINESS_PLATFORM_MARKER = ".logic.business.platform"

ALLOWED_EDGE = {
    # overlay：系统浮窗等，可与 viewmodel 互引用，并可依赖 logic（含 data，例如 ASR transport）
    "ui.view": {"ui.view", "ui.viewmodel", "ui.overlay", "logic.business"},
    "ui.viewmodel": {"ui.viewmodel", "ui.view", "ui.overlay", "logic.business"},
    "ui.overlay": {"ui.overlay", "ui.viewmodel", "ui.view", "logic.business", "logic.data"},
    "logic.business": {"logic.business", "logic.data"},
    "logic.data": {"logic.data"},
}


@dataclass(frozen=True)
class KotlinFile:
    path: pathlib.Path
    package: str
    imports: tuple[str, ...]
    text: str

    @property
    def layer(self) -> str | None:
        return detect_layer(self.package)

    @property
    def feature(self) -> str | None:
        return detect_feature(self.package)


def detect_layer(package: str) -> str | None:
    # 必须先匹配更长后缀：否则 ".ui.view" 会误命中 "...ui.viewmodel"。
    if ".ui.viewmodel" in package:
        return "ui.viewmodel"
    if ".ui.overlay" in package:
        return "ui.overlay"
    if ".ui.view" in package:
        return "ui.view"
    if ".logic.business" in package:
        return "logic.business"
    if ".logic.data" in package:
        return "logic.data"
    return None


def detect_feature(package: str) -> str | None:
    parts = package.split(".")
    for start in range(0, max(0, len(parts) - 2)):
        if parts[start + 2] in {"ui", "logic"}:
            return ".".join(parts[: start + 2])
    return None


def is_common_or_foundation(package: str) -> bool:
    return any(segment in package for segment in COMMON_FOUNDATION_SEGMENTS)


def is_logic_business_platform_host(package: str) -> bool:
    return LOGIC_BUSINESS_PLATFORM_MARKER in package


def is_forbidden_generic_utils(package: str) -> bool:
    if ".utils" not in package:
        return False
    if any(ok in package for ok in ALLOWED_LAYERED_UTILS):
        return False
    if is_common_or_foundation(package):
        return False
    return True


def parse_kotlin_file(path: pathlib.Path) -> KotlinFile | None:
    text = path.read_text(encoding="utf-8")
    package_match = PACKAGE_RE.search(text)
    if not package_match:
        return None
    package_name = package_match.group(1).strip()
    imports = tuple(m.group(1).strip() for m in IMPORT_RE.finditer(text))
    return KotlinFile(path=path, package=package_name, imports=imports, text=text)


def collect_files(root: pathlib.Path) -> list[KotlinFile]:
    files: list[KotlinFile] = []
    for path in root.rglob("*.kt"):
        parsed = parse_kotlin_file(path)
        if parsed is not None:
            files.append(parsed)
    return files


def check_package_dir_alignment(file: KotlinFile, java_root: pathlib.Path) -> str | None:
    """Ensure .../src/main/java/<package path>/<File>.kt matches declared package."""
    root = java_root.resolve()
    try:
        rel_parent = file.path.resolve().parent.relative_to(root)
    except ValueError:
        return None
    expected = tuple(file.package.split("."))
    actual = rel_parent.parts
    if actual != expected:
        return (
            f"{file.path}: directory does not match package '{file.package}'; "
            f"expected parent .../{'/'.join(expected)}"
        )
    return None


def check_edge(source: KotlinFile, target_pkg: str) -> str | None:
    source_layer = source.layer
    if source_layer is None:
        return None

    target_layer = detect_layer(target_pkg)
    if target_layer is None:
        return None

    source_feature = source.feature
    target_feature = detect_feature(target_pkg)
    if source_feature is None or target_feature is None:
        return None

    if source_feature != target_feature:
        if source_layer.startswith("ui") and target_layer.startswith("ui"):
            return (
                f"cross-feature UI dependency is forbidden: "
                f"{source.package} -> {target_pkg}"
            )
        return None

    allowed = ALLOWED_EDGE[source_layer]
    if target_layer not in allowed:
        # 同 feature 下，logic.data 仅允许引用 business.platform 宿主类（bind/startForeground 等），不开放对整个 business 的依赖。
        if (
            source_layer == "logic.data"
            and target_layer == "logic.business"
            and LOGIC_BUSINESS_PLATFORM_MARKER in target_pkg
            and source_feature == target_feature
        ):
            return None
        return f"forbidden dependency direction: {source.package} -> {target_pkg}"
    return None


def check_file(file: KotlinFile, all_packages: set[str]) -> list[str]:
    errors: list[str] = []
    layer = file.layer
    pkg = file.package

    if is_forbidden_generic_utils(pkg):
        errors.append(
            f"{file.path}: forbidden generic utils package '{pkg}'. "
            "Use layer-owned utils or common/foundation pure helpers."
        )

    if is_common_or_foundation(pkg):
        for imp in file.imports:
            if ".ui." in imp or ".logic." in imp:
                errors.append(
                    f"{file.path}: common/foundation package must not depend on "
                    f"feature implementation '{imp}'."
                )

    if layer in {"logic.business", "logic.data"}:
        for imp in file.imports:
            if imp.startswith(UI_FRAMEWORK_PREFIXES):
                if layer == "logic.business" and is_logic_business_platform_host(pkg):
                    continue
                errors.append(
                    f"{file.path}: logic layer must not import UI framework API '{imp}'."
                )

    if layer in {"ui.view", "ui.overlay"}:
        for imp in file.imports:
            if imp.startswith(VIEW_IO_IMPORT_PREFIXES):
                errors.append(
                    f"{file.path}: {layer} must not import direct IO API '{imp}'."
                )
        for kw in VIEW_IO_KEYWORDS:
            if kw in file.text:
                errors.append(
                    f"{file.path}: {layer} must not contain blocking/thread IO pattern '{kw}'."
                )
        if VIEW_IO_THREAD_START_PATTERN.search(file.text):
            errors.append(
                f"{file.path}: {layer} must not start raw Thread( for background work."
            )

    for imp in file.imports:
        # imports with wildcard still preserve package prefix for layer checks
        imp_pkg = imp[:-2] if imp.endswith(".*") else imp

        # Try exact package first; then progressively strip class names.
        candidate_pkgs = [imp_pkg]
        parts = imp_pkg.split(".")
        if len(parts) > 1:
            for i in range(len(parts) - 1, 0, -1):
                candidate_pkgs.append(".".join(parts[:i]))

        target = next((c for c in candidate_pkgs if c in all_packages), None)
        if not target:
            continue

        edge_error = check_edge(file, target)
        if edge_error:
            errors.append(f"{file.path}: {edge_error}")

    return errors


def run(root: pathlib.Path) -> int:
    files = collect_files(root)
    all_packages = {f.package for f in files}

    errors: list[str] = []
    for f in files:
        align_err = check_package_dir_alignment(f, root)
        if align_err:
            errors.append(align_err)
        errors.extend(check_file(f, all_packages))

    if errors:
        print("Android layering check failed:")
        for err in errors:
            print(f"- {err}")
        return 1

    print("Android layering check passed.")
    return 0


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check Android layering constraints.")
    default_root = (
        pathlib.Path(__file__).resolve().parent.parent
        / "app"
        / "src"
        / "main"
        / "java"
    )
    parser.add_argument(
        "--root",
        default=str(default_root),
        help="Kotlin source root directory (default: this repo's android_agent/app/.../java)",
    )
    return parser.parse_args(argv)


def main(argv: Iterable[str]) -> int:
    args = parse_args(argv)
    root = pathlib.Path(args.root)
    if not root.exists():
        print(f"Source root does not exist: {root}")
        return 2
    return run(root)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
