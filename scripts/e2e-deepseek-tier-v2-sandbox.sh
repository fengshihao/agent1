#!/usr/bin/env bash
# Tier-V2 DeepSeek：UC-02 / UC-12（Coach + 沙箱；12 模型常拒写，失败时以 Mock 为准）。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
export E2E_DEEPSEEK_UCS="${E2E_DEEPSEEK_UCS:-02,12}"
exec "${SCRIPT_DIR}/e2e-deepseek-uc-smoke.sh" "$@"
