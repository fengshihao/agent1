#!/usr/bin/env bash
# Tier-V3 DeepSeek：UC-03 / UC-04 / UC-05（需 Weizhi native + execute_script）。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
export E2E_DEEPSEEK_UCS="${E2E_DEEPSEEK_UCS:-03,04,05}"
export AGENT1_MAX_TURNS_PER_RUN="${AGENT1_MAX_TURNS_PER_RUN:-6}"
export AGENT1_MAX_TOOL_CALLS_PER_RUN="${AGENT1_MAX_TOOL_CALLS_PER_RUN:-10}"
exec "${SCRIPT_DIR}/e2e-deepseek-uc-smoke.sh" "$@"
