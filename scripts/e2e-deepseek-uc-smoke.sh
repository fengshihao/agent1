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
#   含 02/12/03/04/05 见 tier-v2-sandbox / tier-v3-weizhi 或 v6 总控
#   E2E_DEEPSEEK_FORCE=1       忽略北京时间高峰警告
#   E2E_DEEPSEEK_REPORT=path   脱敏报告输出路径
#   Tier-2（+UC-06 catalog）：./scripts/e2e-deepseek-tier2.sh
#   Tier-3（+UC-08 promote）：./scripts/e2e-deepseek-tier3.sh
#   Tier-4（+UC-09 skill read）：./scripts/e2e-deepseek-tier4.sh
#   Tier-V3 Weizhi（03,04,05）：./scripts/e2e-deepseek-tier-v3-weizhi.sh
#   V6 总控：./scripts/e2e-deepseek-v6.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"
# shellcheck disable=SC1091
source "${REPO_ROOT}/scripts/lib/catalog-sample-local-server.sh"
# shellcheck disable=SC1091
source "${REPO_ROOT}/scripts/lib/ensure-weizhi.sh"

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

cleanup() {
  catalog_sample_server_stop
}
trap cleanup EXIT

run_uc() {
  local id="$1"
  local prompt="$2"
  local max_turns="${3:-}"
  local saved_turns="${AGENT1_MAX_TURNS_PER_RUN}"
  if [[ -n "${max_turns}" ]]; then
    export AGENT1_MAX_TURNS_PER_RUN="${max_turns}"
  fi
  echo ""
  echo "======== UC-${id} ========"
  echo "prompt: ${prompt}"
  set +e
  local out
  out="$(./agent1 "${prompt}" 2>&1)"
  local code=$?
  set -e
  export AGENT1_MAX_TURNS_PER_RUN="${saved_turns}"
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

verify_uc06() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  local script="${AGENT1_AGENT_ROOT}/shared/catalog/scripts/sample-hello.js"
  if [[ ! -f "${script}" ]]; then
    echo "UC-06 验证失败: 未落盘 ${script}" >&2
    return 1
  fi
  if [[ ! -f "${events}" ]]; then
    echo "UC-06 验证失败: 无 events.jsonl" >&2
    return 1
  fi
  if ! grep -q '"tool_name":"catalog_install"' "${events}" \
    && ! grep -q '"tool_name":"catalog_sync_status"' "${events}"; then
    echo "UC-06 验证失败: events 中无 catalog_install/sync_status" >&2
    return 1
  fi
  echo "UC-06 验证: sample-hello.js 已安装"
  return 0
}

UC08_SKILL_DIR="e2e-tier3-skill"

verify_uc08() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  local skill="${AGENT1_AGENT_ROOT}/shared/local/skills/${UC08_SKILL_DIR}/SKILL.md"
  if [[ ! -f "${skill}" ]]; then
    echo "UC-08 验证失败: 未落盘 ${skill}" >&2
    return 1
  fi
  if [[ ! -f "${events}" ]]; then
    echo "UC-08 验证失败: 无 events.jsonl" >&2
    return 1
  fi
  if ! grep -q '"type":"promotion_completed"' "${events}"; then
    echo "UC-08 验证失败: events 无 promotion_completed" >&2
    return 1
  fi
  if ! grep -q '"tool_name":"promote_request"' "${events}"; then
    echo "UC-08 验证失败: events 无 promote_request" >&2
    return 1
  fi
  echo "UC-08 验证: shared/local/skills/${UC08_SKILL_DIR}/SKILL.md 已晋升"
  return 0
}

UC08_SKILL_BODY_MARKER="DeepSeek E2E tier3 晋升测试"

verify_uc09() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  local skill="${AGENT1_AGENT_ROOT}/shared/local/skills/${UC08_SKILL_DIR}/SKILL.md"
  if [[ ! -f "${skill}" ]]; then
    echo "UC-09 验证失败: 需先完成 UC-08（${skill} 不存在）" >&2
    return 1
  fi
  if [[ ! -f "${events}" ]]; then
    echo "UC-09 验证失败: 无 events.jsonl" >&2
    return 1
  fi
  if ! grep -q '"tool_name":"skill"' "${events}"; then
    echo "UC-09 验证失败: events 无 skill 工具调用" >&2
    return 1
  fi
  if ! grep -q "${UC08_SKILL_BODY_MARKER}" "${events}" \
    && ! grep -q "${UC08_SKILL_BODY_MARKER}" "${skill}"; then
    echo "UC-09 验证失败: 未见到 skill 正文标记" >&2
    return 1
  fi
  if ! grep -q 'source: local' "${events}" && ! grep -q 'source":"local' "${events}"; then
    echo "UC-09 验证失败: tool 回执未标明 local source" >&2
    return 1
  fi
  echo "UC-09 验证: skill ${UC08_SKILL_DIR} 已从 local 读取"
  return 0
}

verify_uc02() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  if [[ ! -f "${events}" ]]; then
    echo "UC-02 验证失败: 无 events" >&2
    return 1
  fi
  if grep -q 'file.large_write' "${events}"; then
    echo "UC-02 验证: Coach file.large_write 已触发"
    return 0
  fi
  echo "UC-02 验证失败: 未见 file.large_write coach" >&2
  return 1
}

verify_uc03() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  [[ -f "${events}" ]] || { echo "UC-03: 无 events" >&2; return 1; }
  grep -q '"tool_name":"run_js"' "${events}" || { echo "UC-03: 无 run_js" >&2; return 1; }
  echo "UC-03 验证: run_js 已调用（结果=3 请人工看 transcript）"
  return 0
}

verify_uc04() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  [[ -f "${events}" ]] || { echo "UC-04: 无 events" >&2; return 1; }
  if grep -qE 'userLine(\\"|"):[[:space:]]*5' "${events}"; then
    echo "UC-04 验证: 结构化错误含 userLine=5"
    return 0
  fi
  echo "UC-04 验证失败: events 未见 userLine=5（可能在 location 嵌套 JSON 内）" >&2
  return 1
}

verify_uc05() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  [[ -f "${events}" ]] || { echo "UC-05: 无 events" >&2; return 1; }
  if grep -q 'script.inline_long' "${events}" || grep -q 'script.fail_repeat' "${events}"; then
    echo "UC-05 验证: Coach 脚本类 hook 已触发"
    return 0
  fi
  echo "UC-05 验证失败: 未见 script.inline_long / fail_repeat" >&2
  return 1
}

verify_uc12() {
  local events="${AGENT1_AGENT_ROOT}/logs/events.jsonl"
  [[ -f "${events}" ]] || { echo "UC-12: 无 events" >&2; return 1; }
  if grep -q 'path.outside_attempt' "${events}"; then
    echo "UC-12 验证: path.outside_attempt coach"
    return 0
  fi
  if grep -q '路径超出工作区' "${events}"; then
    echo "UC-12 验证: 沙箱拒绝越权路径"
    return 0
  fi
  echo "UC-12 验证失败: 模型可能未发起越权 write（LLM 不稳定，请用 Mock UC-12）" >&2
  return 1
}

require_weizhi() {
  ensure_weizhi_for_agent1 "${REPO_ROOT}" || return 1
  if [[ ! -f "${AGENT1_WEIZHI_REPO}/build/libweizhijni.so" ]] \
    && [[ ! -f "${AGENT1_WEIZHI_REPO}/build/libweizhijni.dylib" ]]; then
    echo "Weizhi native 未构建" >&2
    return 1
  fi
  return 0
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
    02)
      export AGENT1_COACH_LARGE_WRITE_BYTES=5
      run_uc "02" \
        "请用 write_file 在 workspace 写入 big.txt，内容至少 30 个字符（例如重复数字）。不要写 shared/ 或 docs/system。" \
        "3" || fail=1
      verify_uc02 || fail=1
      ;;
    12)
      run_uc "12" \
        "请尝试 write_file，路径 ../shared/catalog/e2e-outside.txt，内容 test。若被拒绝请根据工具回执说明原因，不要多次改用其他越权路径。" \
        "3" || fail=1
      if ! verify_uc12; then
        echo "UC-12: LLM 未触发越权 write 时以 Mock ProductivityScriptedCoachTest 为准" >&2
        fail=1
      fi
      ;;
    03)
      require_weizhi || fail=1
      run_uc "03" \
        "请用 run_js 的 code 参数计算 1+2，把数值结果告诉我。" \
        "3" || fail=1
      verify_uc03 || fail=1
      ;;
    04)
      require_weizhi || fail=1
      run_uc "04" \
        "在 workspace 创建 bug.js：第1–4行 console.log(1)..(4)，第5行故意单独写 } 造成语法错，第6行 console.log(6)。先 write_file，再 run_js file=bug.js，确认错误 userLine 是否为 5。" \
        "6" || fail=1
      verify_uc04 || fail=1
      ;;
    05)
      require_weizhi || fail=1
      export AGENT1_COACH_SCRIPT_FAIL_REPEAT=2
      run_uc "05" \
        "请连续两次用 run_js 的 code 参数执行 bad();（不要用 file）。看第二次失败后是否出现 script.fail_repeat Coach。" \
        "5" || fail=1
      verify_uc05 || fail=1
      ;;
    06)
      catalog_sample_server_start "${REPO_ROOT}" || fail=1
      run_uc "06" \
        "请先 catalog_sync_status 查看 pending；若有 script.sample-hello 待安装，用 catalog_install 只装这一条。禁止 write_file 写入 shared/catalog。" \
        "5" || fail=1
      verify_uc06 || fail=1
      ;;
    08)
      run_uc "08" \
        "请把可复用 skill 沉淀到 shared/local：先用 write_file 在 workspace 创建 staging/skills/${UC08_SKILL_DIR}/SKILL.md，YAML frontmatter 含 name: ${UC08_SKILL_DIR}，正文写一句「DeepSeek E2E tier3 晋升测试」（不要包含 api key 字样）；再调用 promote_request。禁止 write_file 写入 shared/ 或 docs/system。" \
        "6" || fail=1
      verify_uc08 || fail=1
      ;;
    09)
      run_uc "09" \
        "local 里应该已有 skill「${UC08_SKILL_DIR}」（若还没有请先 promote）。请用 skill 工具 action=list，再 action=read skill_name=${UC08_SKILL_DIR}，确认 source 为 local 且正文含「${UC08_SKILL_BODY_MARKER}」。" \
        "5" || fail=1
      verify_uc09 || fail=1
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
