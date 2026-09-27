#!/usr/bin/env bash
# DeepSeek Flash 真实 LLM UC 冒烟（需 OPENAI_API_KEY，勿写入仓库）。
# 文档：doc/规划/自进化Agent/e2e-runs/DeepSeek-省钱E2E.md
#
# 用法：
#   export OPENAI_API_KEY='…'   # 或 Cursor Cloud Environment Secret
#   source scripts/e2e-deepseek-env.example.sh   # 可选：limits + baseUrl
#   ./scripts/e2e-deepseek-uc-smoke.sh
#
# 可选：
#   E2E_DEEPSEEK_UCS=01,11     默认 01,11（省钱）
#   E2E_DEEPSEEK_FORCE=1       忽略北京时间高峰警告
#   E2E_DEEPSEEK_REPORT=path   脱敏报告输出路径

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "错误: 请设置 OPENAI_API_KEY（勿 commit 到 git）" >&2
  exit 1
fi

export OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.deepseek.com}"
export OPENAI_MODEL="${OPENAI_MODEL:-deepseek-flash}"
export AGENT1_AGENT_ROOT="${AGENT1_AGENT_ROOT:-/tmp/agent1-deepseek-uc-$$}"
export AGENT1_COACH="${AGENT1_COACH:-1}"
export AGENT1_MAX_TURNS_PER_RUN="${AGENT1_MAX_TURNS_PER_RUN:-3}"
export AGENT1_MAX_TOOL_CALLS_PER_RUN="${AGENT1_MAX_TOOL_CALLS_PER_RUN:-8}"
export AGENT1_MAX_CONTEXT_TURNS="${AGENT1_MAX_CONTEXT_TURNS:-4}"

beijing_peak_warning() {
  if [[ "${E2E_DEEPSEEK_FORCE:-}" == "1" ]]; then
    return 0
  fi
  local dow hour
  dow="$(TZ=Asia/Shanghai date +%u)"   # 1=Mon .. 7=Sun
  hour="$(TZ=Asia/Shanghai date +%H)"
  if [[ "$dow" -ge 1 && "$dow" -le 5 ]]; then
    if { [[ "$hour" -ge 9 && "$hour" -lt 12 ]] || [[ "$hour" -ge 14 && "$hour" -lt 18 ]]; }; then
      echo "警告: 当前为北京时间工作日高峰（DeepSeek 单价较高）。" >&2
      echo "      大批量回归请改闲时（周末/夜间），或 export E2E_DEEPSEEK_FORCE=1 继续。" >&2
      exit 2
    fi
  fi
}

beijing_peak_warning

UC_LIST="${E2E_DEEPSEEK_UCS:-01,11}"
REPORT="${E2E_DEEPSEEK_REPORT:-}"

if [[ -f "${REPO_ROOT}/java_agent/cli/build/libs/cli-0.1.0-SNAPSHOT-all.jar" ]]; then
  export JAVA_AGENT_SKIP_BUILD=1
fi

mkdir -p "${AGENT1_AGENT_ROOT}"

run_uc() {
  local id="$1"
  local prompt="$2"
  echo ""
  echo "======== UC-${id} ========"
  echo "prompt: ${prompt}"
  set +e
  local out
  out="$(./agent1 "${prompt}" 2>&1)"
  local code=$?
  set -e
  echo "${out}"
  echo "exit_code=${code}"
  if [[ -n "${REPORT}" ]]; then
    {
      echo "## UC-${id}"
      echo "- time: $(date -u +%Y-%m-%dT%H:%MZ)"
      echo "- model: ${OPENAI_MODEL}"
      echo "- agentRoot: ${AGENT1_AGENT_ROOT}"
      echo "- exit: ${code}"
      echo '```'
      echo "${out}" | tail -40
      echo '```'
      echo ""
    } >> "${REPORT}"
  fi
  return "${code}"
}

fail=0

IFS=',' read -ra UCS <<< "${UC_LIST}"
for uc in "${UCS[@]}"; do
  uc="$(echo "$uc" | tr -d ' ')"
  case "$uc" in
    01)
      run_uc "01" "数据目录在哪？我能不能用 write_file 在 shared/catalog 里新建文件？" || fail=1
      ;;
    11)
      run_uc "11" "请读环境手册里 catalog 安装说明，告诉我安装流程要点，不要写 docs/system。" || fail=1
      ;;
    *)
      echo "跳过未知 UC: ${uc}" >&2
      ;;
  esac
done

echo ""
echo "events: ${AGENT1_AGENT_ROOT}/logs/events.jsonl"
if [[ "${fail}" -eq 0 ]]; then
  echo "e2e-deepseek-uc-smoke: PASS"
else
  echo "e2e-deepseek-uc-smoke: FAIL（见上方 exit_code）" >&2
  exit 1
fi
