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

# 云端-only Skill：不进 classpath skills-bundled（APK / 桌面离线兜底）。
# load_skill 经 registry 安装。空格分隔；可用环境变量覆盖。
CLOUD_ONLY_SKILLS="${CLOUD_ONLY_SKILLS:-xlsx}"

is_cloud_only() {
  local needle="$1"
  local item
  for item in ${CLOUD_ONLY_SKILLS}; do
    if [[ "${item}" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

rm -rf "${DEST}"
mkdir -p "${DEST}"
skipped=()
for dir in "${SRC_ROOT}"/*/; do
  name="$(basename "${dir}")"
  if is_cloud_only "${name}"; then
    skipped+=("${name}")
    continue
  fi
  cp -R "${dir}" "${DEST}/${name}"
done

echo "已同步 $(find "${DEST}" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ') 个 Skill → ${DEST}"
if ((${#skipped[@]} > 0)); then
  echo "云端-only 已跳过（不进 bundled）: ${skipped[*]}"
fi
