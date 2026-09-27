#!/usr/bin/env bash
# Tier-3 DeepSeek LLM UC：Tier-2 + UC-08（staging → promote_request → shared/local）。
# 建议在 DeepSeek 闲时跑；需 OPENAI_API_KEY。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
export E2E_DEEPSEEK_UCS="${E2E_DEEPSEEK_UCS:-01,11,06,08}"
export AGENT1_MAX_TOOL_CALLS_PER_RUN="${AGENT1_MAX_TOOL_CALLS_PER_RUN:-10}"
exec "${SCRIPT_DIR}/e2e-deepseek-uc-smoke.sh" "$@"
