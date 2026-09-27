#!/usr/bin/env bash
# V6 总控：Mock 全集（无 Key）+ DeepSeek UC 回归（有 Key，闲时）。
#
#   ./scripts/e2e-deepseek-v6.sh
#   E2E_DEEPSEEK_V6_SKIP_MOCK=1 ./scripts/e2e-deepseek-v6.sh   # 仅 LLM
#   E2E_DEEPSEEK_V6_SKIP_LLM=1 ./scripts/e2e-deepseek-v6.sh    # 仅 Mock
#
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd -P)"
cd "${REPO_ROOT}"

if [[ "${E2E_DEEPSEEK_V6_SKIP_MOCK:-}" != "1" ]]; then
  echo "==> V6 Mock / 集成（e2e-self-evolve-smoke）"
  "${SCRIPT_DIR}/e2e-self-evolve-smoke.sh"
fi

if [[ "${E2E_DEEPSEEK_V6_SKIP_LLM:-}" == "1" ]]; then
  echo "e2e-deepseek-v6: PASS（仅 Mock）"
  exit 0
fi

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "未设置 OPENAI_API_KEY，跳过 DeepSeek 层（Mock 已通过即 V6 Mock 完成）"
  exit 0
fi

echo ""
echo "==> V6 DeepSeek LLM（分层 UC，建议闲时）"
export E2E_DEEPSEEK_UCS="${E2E_DEEPSEEK_UCS:-01,11,02,12,03,04,05,06,08,09}"
export AGENT1_AGENT_ROOT="${AGENT1_AGENT_ROOT:-/tmp/agent1-deepseek-v6-$$}"
export AGENT1_MAX_TURNS_PER_RUN="${AGENT1_MAX_TURNS_PER_RUN:-6}"
export AGENT1_MAX_TOOL_CALLS_PER_RUN="${AGENT1_MAX_TOOL_CALLS_PER_RUN:-12}"
exec "${SCRIPT_DIR}/e2e-deepseek-uc-smoke.sh" "$@"
