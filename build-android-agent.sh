#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# 作用（仓库根「薄」脚本）
#   Android 子工程一键流程：发布 java-agent-core → assembleDebug → adb 安装 → 启动 Demo。
#   转调 android_agent/run.sh（需已连接 adb 设备、本机有 adb）。
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
exec bash "${REPO_ROOT}/android_agent/run.sh" "$@"
