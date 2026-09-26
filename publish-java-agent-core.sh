#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# 作用（仓库根「薄」脚本）
#   将 java-agent-core 发布到本仓库本地 Maven，并对 Android 模块做一次 compileDebugKotlin 校验。
#   转调 java_agent/bin/publish-core-and-verify-android（Gradle 任务编排在其内部）。
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
exec bash "${REPO_ROOT}/java_agent/bin/publish-core-and-verify-android" "$@"
