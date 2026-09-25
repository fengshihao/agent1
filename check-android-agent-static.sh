#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Android 静态约束：分层 + 主线程不得同步调用阻塞型 ProductivityAgentGateway。
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
SCRIPTS="${REPO_ROOT}/android_agent/scripts"

python3 "${SCRIPTS}/check_android_layering.py"
python3 "${SCRIPTS}/check_android_main_thread_gateway.py" --self-test
python3 "${SCRIPTS}/check_android_main_thread_gateway.py"
"${REPO_ROOT}/android_agent/gradlew" --no-daemon -p "${REPO_ROOT}/android_agent" :app:detekt
