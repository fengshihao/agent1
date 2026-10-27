#!/usr/bin/env bash
# 将 weizhi 克隆到与 agent1 同级目录（默认 ../weizhi），供 agent1.code-workspace 第三根目录。
# Android / ci-local 也会识别 ../weizhi（见 android_agent/settings.gradle.kts、scripts/ci-local.sh）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SIBLING="${ROOT}/../weizhi"
IN_REPO="${ROOT}/weizhi"
URL="${WEIZHI_GIT_URL:-${WEIZHI_REPO_URL:-https://github.com/fengshihao/weizhi.git}}"

if [[ -d "${SIBLING}/.git" ]] && [[ -d "${SIBLING}/android" ]]; then
  echo "weizhi 已存在: ${SIBLING}"
  exit 0
fi

if [[ -d "${IN_REPO}/.git" ]] && [[ -d "${IN_REPO}/android" ]]; then
  echo "已存在 agent1/weizhi（仓内路径）。多根 workspace 期望同级 ../weizhi。" >&2
  echo "可选：mv ${IN_REPO} ${SIBLING} 后重新打开 agent1.code-workspace" >&2
  exit 0
fi

if [[ -e "${SIBLING}" ]]; then
  echo "路径已存在但不是完整 weizhi 仓库: ${SIBLING}" >&2
  exit 1
fi

git clone --depth 1 "${URL}" "${SIBLING}"
echo "已克隆 weizhi → ${SIBLING}"
echo "建议执行: cd ${ROOT} && ./sync-weizhi.sh   # 打 agent1 侧补丁并同步 Gradle 脚本"
