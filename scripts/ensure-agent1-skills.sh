#!/usr/bin/env bash
# 将 agent1-skills 克隆到与 agent1 同级目录（默认 ../agent1-skills），供多根 workspace 与 sync 脚本使用。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
DEST="$(cd "${AGENT1_SKILLS_ROOT:-${ROOT}/../agent1-skills}" 2>/dev/null && pwd -P)" || DEST="${AGENT1_SKILLS_ROOT:-${ROOT}/../agent1-skills}"
REPO="${AGENT1_SKILLS_GIT_URL:-https://github.com/fengshihao/agent1-skills.git}"

if [[ -d "${DEST}/.git" ]]; then
  echo "agent1-skills 已存在: ${DEST}"
  exit 0
fi

if [[ -e "${DEST}" ]]; then
  echo "路径已存在但不是 git 仓库: ${DEST}" >&2
  exit 1
fi

git clone "${REPO}" "${DEST}"
echo "已克隆 agent1-skills → ${DEST}"
echo "在 Cursor 中打开: ${ROOT}/agent1.code-workspace"
