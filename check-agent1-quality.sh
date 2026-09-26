#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# agent1 质量门禁：Java 静态检测 + Android 分层与主线程 Gateway 检查
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
cd "${REPO_ROOT}"

bash "${REPO_ROOT}/check-java-agent-static.sh"
bash "${REPO_ROOT}/check-android-agent-static.sh"
