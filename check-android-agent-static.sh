#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Android 静态约束：分层 + 主线程不得同步调用阻塞型 ProductivityAgentGateway
# + agent_core 禁止 Files.readString/writeString（API 34，desugar 不覆盖）。
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
SCRIPTS="${REPO_ROOT}/android_agent/scripts"

python3 "${SCRIPTS}/check_android_layering.py"
python3 "${SCRIPTS}/check_android_main_thread_gateway.py" --self-test
python3 "${SCRIPTS}/check_android_main_thread_gateway.py"
bash "${REPO_ROOT}/scripts/check-android-files-api.sh"
if [[ "${AGENT1_SKIP_GRADLE:-}" == "1" ]]; then
  echo "skip :app:detekt (AGENT1_SKIP_GRADLE=1；Detekt 由 CI quality-static 执行)"
else
  "${REPO_ROOT}/android_agent/gradlew" --no-daemon -p "${REPO_ROOT}/android_agent" :app:detekt
fi
