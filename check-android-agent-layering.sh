#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# 作用（仓库根「薄」脚本）
#   检查 android_agent 内 Kotlin 分层与依赖约束。逻辑在
#   android_agent/scripts/check_android_layering.py；本文件仅定位仓库根并转调。
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
exec python3 "${REPO_ROOT}/android_agent/scripts/check_android_layering.py" "$@"
