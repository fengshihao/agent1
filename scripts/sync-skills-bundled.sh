#!/usr/bin/env bash
# 从 agent1-skills 内容仓同步官方 Skill 到 classpath skills-bundled（桌面离线兜底）。
# Skill 真源请在 agent1-skills 编辑；Cursor 用根目录 agent1.code-workspace 同时打开两仓（见 docs/ai/WORKSPACE.md）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SRC_ROOT="${AGENT1_SKILLS_ROOT:-${ROOT}/../agent1-skills/skills-src}"
DEST="${ROOT}/agent_core/src/main/resources/agent-home/skills-bundled"

if [[ ! -d "${SRC_ROOT}" ]]; then
  echo "找不到 Skill 源目录: ${SRC_ROOT}" >&2
  echo "请 clone https://github.com/fengshihao/agent1-skills 到同级目录，或设置 AGENT1_SKILLS_ROOT" >&2
  exit 1
fi

rm -rf "${DEST}"
mkdir -p "${DEST}"
for dir in "${SRC_ROOT}"/*/; do
  name="$(basename "${dir}")"
  cp -R "${dir}" "${DEST}/${name}"
done

echo "已同步 $(find "${DEST}" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ') 个 Skill → ${DEST}"
