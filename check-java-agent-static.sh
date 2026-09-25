#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Java 静态检测：PMD + SpotBugs（:core / :cli，见 config/ 与 doc/代码质量硬性要求与静态检测.md）
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
cd "${REPO_ROOT}"

GRADLE=(./java_agent/gradlew --no-daemon -p java_agent)
if [ -d "${REPO_ROOT}/weizhi" ]; then
  "${GRADLE[@]}" :core:pmdMain :core:spotbugsMain :cli:pmdMain :cli:spotbugsMain
else
  echo "skip cli static analysis: no ./weizhi (cli depends on weizhi-bridge)"
  "${GRADLE[@]}" :core:pmdMain :core:spotbugsMain
fi
