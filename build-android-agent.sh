#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# 作用（仓库根「薄」脚本）
#   Android 子工程一键：发布 java-agent-core → 编译 → adb 安装 → 启动 Demo。
#   默认 Debug；加 --release 走 R8 Release 包（需已连接 adb 设备）。
# =============================================================================

usage() {
  cat <<'EOF'
用法: ./build-android-agent.sh [--release]

  （默认）Debug：assembleDebug
  --release     Release（R8 + 独立签名，见 android_agent/run_release.sh）

等价于 android_agent/run_debug.sh 或 run_release.sh。
EOF
}

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
VARIANT=debug
EXTRA=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release)
      VARIANT=release
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    --)
      shift
      EXTRA+=("$@")
      break
      ;;
    *)
      echo "未知参数: $1（可用 --help）" >&2
      exit 1
      ;;
  esac
done

RUN_SCRIPT="${REPO_ROOT}/android_agent/run_${VARIANT}.sh"
if ((${#EXTRA[@]} > 0)); then
  exec bash "$RUN_SCRIPT" "${EXTRA[@]}"
else
  exec bash "$RUN_SCRIPT"
fi
