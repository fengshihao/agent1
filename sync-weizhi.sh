#!/usr/bin/env bash
# 拉取/更新「微智 weizhi」工程（作者自有仓库），供 Android / java-agent 集成编译。
#
# 用法：
#   ./sync-weizhi.sh
#   # 或指定 fork / 私有镜像：
#   export WEIZHI_GIT_URL='https://github.com/<you>/weizhi.git'
#
# 未设置 WEIZHI_GIT_URL 时默认克隆公开仓库 fengshihao/weizhi。
# ES module workspace→catalog 回退需 Weizhi master ≥ dd7904c（weizhi#10）；docx 标题层次需 ≥ cb3e889（weizhi#11）；
# webview_exec 自动落盘 tmp/webview_exec 需 ≥ 18d0786（weizhi#18）；fs.mkdirSync / writeFile 父目录需 ≥ df0e604（weizhi#27）；`git -C weizhi rev-parse HEAD` 可核对。
#
# 默认克隆到本仓库内 agent1/weizhi（与 android_agent/../weizhi 路径一致）。
# 若已存在同级目录 ../weizhi，则不会重复克隆，仅尝试 git pull。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
IN_REPO="${REPO_ROOT}/weizhi"
SIBLING="$(cd "${REPO_ROOT}/.." && pwd)/weizhi"
DEFAULT_WEIZHI_GIT_URL="https://github.com/fengshihao/weizhi.git"
URL="${WEIZHI_GIT_URL:-${WEIZHI_REPO_URL:-${DEFAULT_WEIZHI_GIT_URL}}}"

if [[ "${WEIZHI_SKIP_SYNC:-}" == "1" ]] || [[ "${WEIZHI_SKIP_SYNC:-}" == "true" ]]; then
  echo "跳过 weizhi sync（WEIZHI_SKIP_SYNC=${WEIZHI_SKIP_SYNC}）" >&2
  exit 0
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

apply_weizhi_patches() {
  local patch="${REPO_ROOT}/scripts/patches/weizhi-ndk-realpath-path-max.patch"
  local test_c="${TARGET}/tests/test_engine.c"
  if [[ ! -f "${patch}" ]] || [[ ! -f "${test_c}" ]]; then
    return 0
  fi
  if grep -q 'folder_real\[PATH_MAX\]' "${test_c}"; then
    echo "==> weizhi NDK realpath 补丁已应用，跳过"
    return 0
  fi
  echo "==> apply weizhi patch: NDK realpath 需 PATH_MAX 缓冲区（Android NDK fortify）"
  patch -p1 -d "${TARGET}" < "${patch}"
}

apply_weizhi_patches

PUBLISH_GRADLE="${TARGET}/android/weizhi-maven-publish.gradle"
AGENT_PUBLISH="${REPO_ROOT}/android_agent/weizhi-maven-publish.gradle"
if [[ -f "${PUBLISH_GRADLE}" ]]; then
  cp "${PUBLISH_GRADLE}" "${AGENT_PUBLISH}"
  echo "==> 已同步 ${AGENT_PUBLISH}（weizhi 子模块 Maven 脚本）"
fi

echo "==> 完成。Android 集成目录: ${TARGET}/android"
echo "    本地编译: cd android_agent && ./gradlew :app:assembleDebug"
