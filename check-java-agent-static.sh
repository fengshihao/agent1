#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Java 静态检测：PMD + SpotBugs（:core / :cli，见 config/ 与 doc/代码质量硬性要求与静态检测.md）
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
cd "${REPO_ROOT}"

./java_agent/gradlew --no-daemon -p java_agent :core:pmdMain :core:spotbugsMain :cli:pmdMain :cli:spotbugsMain
