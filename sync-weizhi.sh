#!/usr/bin/env bash
# 拉取/更新「微智 weizhi」工程（作者自有仓库），供 Android / java-agent 集成编译。
#
# 用法：
#   export WEIZHI_GIT_URL='https://github.com/<you>/weizhi.git'   # 或 SSH URL
#   ./sync-weizhi.sh
#
# 默认克隆到本仓库内 agent1/weizhi（与 android_agent/../weizhi 路径一致）。
# 若已存在同级目录 ../weizhi，则不会重复克隆，仅尝试 git pull。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
IN_REPO="${REPO_ROOT}/weizhi"
SIBLING="$(cd "${REPO_ROOT}/.." && pwd)/weizhi"
URL="${WEIZHI_GIT_URL:-${WEIZHI_REPO_URL:-}}"

if [[ -z "${URL}" ]]; then
  echo "请设置 WEIZHI_GIT_URL（你的 weizhi 仓库 clone 地址）" >&2
  echo "示例: export WEIZHI_GIT_URL='git@github.com:you/weizhi.git'" >&2
  exit 1
fi

pick_target() {
  if [[ -d "${IN_REPO}/android" ]]; then
    echo "${IN_REPO}"
    return
  fi
  if [[ -d "${SIBLING}/android" ]]; then
    echo "${SIBLING}"
    return
  fi
  echo "${IN_REPO}"
}

TARGET="$(pick_target)"

if [[ ! -d "${TARGET}/.git" ]]; then
  echo "==> clone weizhi -> ${TARGET}"
  mkdir -p "$(dirname "${TARGET}")"
  git clone --depth 1 "${URL}" "${TARGET}"
else
  echo "==> pull weizhi @ ${TARGET}"
  git -C "${TARGET}" pull --ff-only
fi

if [[ ! -d "${TARGET}/android" ]]; then
  echo "错误：${TARGET} 下缺少 android/，请确认这是完整的 weizhi 工程根目录" >&2
  exit 1
fi

echo "==> 完成。Android 集成目录: ${TARGET}/android"
echo "    本地编译: cd android_agent && ./gradlew :app:assembleDebug"
