#!/usr/bin/env bash
# V6 总控：Mock 全集（无 Key）+ DeepSeek UC 回归（有 Key，闲时）。
#
#   ./scripts/e2e-deepseek-v6.sh
#   E2E_DEEPSEEK_V6_SKIP_MOCK=1 ./scripts/e2e-deepseek-v6.sh   # 仅 LLM
#   E2E_DEEPSEEK_V6_AGENT_ROOT=/tmp/…  可选；未设则每次 LLM 段用新目录（忽略环境里旧的 AGENT1_AGENT_ROOT）
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
# 避免环境里残留的 E2E_DEEPSEEK_UCS=08,09 等只跑子集；V6 专用变量优先
if [[ -n "${E2E_DEEPSEEK_V6_UCS:-}" ]]; then
  export E2E_DEEPSEEK_UCS="${E2E_DEEPSEEK_V6_UCS}"
else
  export E2E_DEEPSEEK_UCS="01,11,02,03,04,05,06,08,09"
fi
# UC-12 真实 LLM 不稳定，默认不含；需测：E2E_DEEPSEEK_V6_UCS=02,12 或 tier-v2-sandbox
if [[ -n "${E2E_DEEPSEEK_V6_AGENT_ROOT:-}" ]]; then
  export AGENT1_AGENT_ROOT="${E2E_DEEPSEEK_V6_AGENT_ROOT}"
elif [[ -z "${E2E_DEEPSEEK_V6_REUSE_AGENT_ROOT:-}" ]]; then
  export AGENT1_AGENT_ROOT="/tmp/agent1-deepseek-v6-$$"
fi
export AGENT1_AGENT_ROOT="${AGENT1_AGENT_ROOT:-/tmp/agent1-deepseek-v6-$$}"
export AGENT1_MAX_TURNS_PER_RUN="${AGENT1_MAX_TURNS_PER_RUN:-6}"
export AGENT1_MAX_TOOL_CALLS_PER_RUN="${AGENT1_MAX_TOOL_CALLS_PER_RUN:-12}"
exec "${SCRIPT_DIR}/e2e-deepseek-uc-smoke.sh" "$@"
