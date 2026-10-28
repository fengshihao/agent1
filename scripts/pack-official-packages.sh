#!/usr/bin/env bash
# 在 agent1 侧调用 agent1-skills 的 pack-package（产出写入 artifact-cdn/dist）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SKILLS_REPO="$(cd "${AGENT1_SKILLS_REPO:-${ROOT}/../agent1-skills}" 2>/dev/null && pwd -P)" || SKILLS_REPO="${AGENT1_SKILLS_REPO:-${ROOT}/agent1-skills}"
PACK="${SKILLS_REPO}/scripts/pack-package.mjs"
SRC="${SKILLS_REPO}/packages-src"
OUT="${ROOT}/artifact-cdn/dist"
BASE="${ARTIFACT_CDN_BASE_URL:-https://agentone.guoyoutech.cn/}"
CARDS="${ROOT}/agent_core/src/main/resources/agent-home/capabilities/office-api-cards.jsonl"

if [[ ! -f "${PACK}" ]]; then
  echo "找不到 ${PACK}，请 clone https://github.com/fengshihao/agent1-skills" >&2
  exit 1
fi

node "${PACK}" --root "${SRC}" --base-url "${BASE}" --out "${OUT}" --cards "${CARDS}"
