#!/usr/bin/env bash
# 在 agent1 侧调用 agent1-skills 的 pack-skill（产出写入 artifact-cdn/dist）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SKILLS_ROOT="$(cd "${AGENT1_SKILLS_ROOT:-${ROOT}/../agent1-skills}" && pwd -P)"
PACK="${SKILLS_ROOT}/scripts/pack-skill.mjs"
SRC="${SKILLS_ROOT}/skills-src"
OUT="${ROOT}/artifact-cdn/dist"
BASE="${ARTIFACT_CDN_BASE_URL:-https://agentone.guoyoutech.cn/}"

if [[ ! -f "${PACK}" ]]; then
  echo "找不到 ${PACK}，请 clone https://github.com/fengshihao/agent1-skills" >&2
  exit 1
fi

node "${PACK}" --root "${SRC}" --base-url "${BASE}" --out "${OUT}"
