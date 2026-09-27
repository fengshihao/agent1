#!/usr/bin/env bash
# Tier-4 DeepSeek LLM UC：Tier-3 + UC-09（promote 后 skill read）。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
export E2E_DEEPSEEK_UCS="${E2E_DEEPSEEK_UCS:-01,11,06,08,09}"
export AGENT1_MAX_TOOL_CALLS_PER_RUN="${AGENT1_MAX_TOOL_CALLS_PER_RUN:-12}"
exec "${SCRIPT_DIR}/e2e-deepseek-uc-smoke.sh" "$@"
