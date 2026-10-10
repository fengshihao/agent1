#!/usr/bin/env bash
# 本地复现 GitHub Actions CI（.github/workflows/ci.yml），push/PR 前跑，减少远端才红的循环。
#
# 用法：
#   ./scripts/ci-local.sh fast          # 默认：Java 静态 + java-test（Mock V6），约 2～5 分钟
#   ./scripts/ci-local.sh full          # static 全集 + java-test + Android assemble（需 SDK）
#   ./scripts/ci-local.sh static        # 等同 CI job quality-static
#   ./scripts/ci-local.sh java-test     # 等同 CI job java-test
#   ./scripts/ci-local.sh android       # 等同 CI job android-assemble-debug（不含 upload）
#
# 环境变量：
#   CI_LOCAL_SKIP_SYNC=1        不跑 sync-weizhi.sh
#   CI_LOCAL_SYNC_WEIZHI=1      fast 模式下也先 sync（默认仅 full/java-test/android 会 sync）
#   CI_LOCAL_SKIP_WEIZHI_TEST=1  跳过 weizhi/scripts/test.sh
#   WEIZHI_DIR=/path            覆盖 Weizhi 根目录（默认 $REPO_ROOT/weizhi）
#
# pre-push 示例：scripts/ci-local-pre-push-hook.example.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"

MODE="${1:-fast}"

usage() {
  sed -n '2,18p' "$0" | sed 's/^# \?//'
  exit "${2:-0}"
}

resolve_weizhi_dir() {
  local dir="${WEIZHI_DIR:-${REPO_ROOT}/weizhi}"
  if [[ ! -d "${dir}/android" ]] && [[ -d "${REPO_ROOT}/../weizhi/android" ]]; then
    dir="${REPO_ROOT}/../weizhi"
  fi
  printf '%s' "${dir}"
}

maybe_sync_weizhi() {
  if [[ "${CI_LOCAL_SKIP_SYNC:-}" == "1" ]]; then
    return 0
  fi
  chmod +x ./sync-weizhi.sh
  ./sync-weizhi.sh
}

run_quality_static() {
  echo ""
  echo "======== CI job: quality-static ========"
  bash "${REPO_ROOT}/check-java-agent-static.sh"
  bash "${REPO_ROOT}/check-android-agent-static.sh"
}

run_java_static_only() {
  echo ""
  echo "======== Java PMD + SpotBugs（fast 子集）========"
  bash "${REPO_ROOT}/check-java-agent-static.sh"
}

run_java_test() {
  echo ""
  echo "======== CI job: java-test ========"
  local weizhi_dir
  weizhi_dir="$(resolve_weizhi_dir)"
  export AGENT1_WEIZHI_REPO="${weizhi_dir}"

  if [[ -d "${weizhi_dir}" ]] && [[ -f "${weizhi_dir}/scripts/test.sh" ]]; then
    if [[ "${CI_LOCAL_SKIP_WEIZHI_TEST:-}" != "1" ]]; then
      echo "==> weizhi: WEIZHI_SKIP_ASAN=1 ./scripts/test.sh"
      (cd "${weizhi_dir}" && WEIZHI_SKIP_ASAN=1 ./scripts/test.sh)
    fi
    AGENT1_WEIZHI_REPO="${weizhi_dir}" E2E_DEEPSEEK_V6_SKIP_LLM=1 \
      "${REPO_ROOT}/scripts/e2e-deepseek-v6.sh"
  else
    echo "skip weizhi: 未找到 ${weizhi_dir}（可 ./sync-weizhi.sh 或 export WEIZHI_DIR=…）— 降级跑 :core:test + :cli:test（桥集成测试被跳过）"
    ./java_agent/gradlew --no-daemon -p java_agent :core:test :cli:test
  fi
}

run_android_assemble() {
  echo ""
  echo "======== CI job: android-assemble-debug ========"
  if [[ -z "${ANDROID_HOME:-}" ]] && [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    echo "跳过 Android assemble：未设置 ANDROID_HOME / ANDROID_SDK_ROOT" >&2
    echo "（装 Android SDK 后重试，或 macOS 上通常已有 Android Studio 环境）" >&2
    return 1
  fi
  ./java_agent/gradlew --no-daemon -p java_agent publishCoreToLocalRepo
  (
    cd "${REPO_ROOT}/android_agent"
    DASHSCOPE_API_KEY="" ./gradlew --no-daemon :app:testDebugUnitTest
    DASHSCOPE_API_KEY="" ./gradlew --no-daemon :app:assembleDebug
  )
  echo "APK: android_agent/app/build/outputs/apk/debug/agent1-android-debug.apk"
}

case "${MODE}" in
  help|-h|--help)
    usage
    ;;
  static)
    run_quality_static
    ;;
  java-test|java)
    maybe_sync_weizhi
    run_java_test
    ;;
  android)
    maybe_sync_weizhi
    run_android_assemble
    ;;
  full)
    maybe_sync_weizhi
    run_quality_static
    run_java_test
    run_android_assemble
    ;;
  fast)
    if [[ "${CI_LOCAL_SYNC_WEIZHI:-}" == "1" ]]; then
      maybe_sync_weizhi
    fi
    run_java_static_only
    run_java_test
    ;;
  *)
    echo "未知模式: ${MODE}" >&2
    usage 2>&1
    exit 2
    ;;
esac

echo ""
echo "ci-local (${MODE}): PASS"
