#!/usr/bin/env bash
# Tier-2 DeepSeek LLM UC：01 + 11 + 06（06 需本地 catalog 样例 HTTP）。
# 建议在 DeepSeek 闲时跑；需 OPENAI_API_KEY。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
export E2E_DEEPSEEK_UCS="${E2E_DEEPSEEK_UCS:-01,11,06}"
exec "${SCRIPT_DIR}/e2e-deepseek-uc-smoke.sh" "$@"
