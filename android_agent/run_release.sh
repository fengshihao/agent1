#!/usr/bin/env bash
# 一键：Release（R8 混淆 + 资源压缩）编译 → adb 覆盖安装 → 启动 MainActivity
# 用法：在 android_agent 目录  ./run_release.sh
#       或在仓库根目录  ./build-android-agent.sh --release
# 签名与 Debug 相同，使用仓库内 debug.keystore。独立 release 签名以后再接。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/agent1-android-release.apk"
MAPPING="$ROOT_DIR/app/build/outputs/mapping/release/mapping.txt"
APP_ID="com.agent1.android"
ACTIVITY=".MainActivity"

if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "错误：未检测到可用设备。请连接手机并执行 adb devices 确认状态为 device。" >&2
  exit 1
fi

echo "==> 1/4 发布 java-agent-core 到本地 Maven"
(
  cd "$REPO_ROOT"
  ./java_agent/bin/publish-core-and-verify-android
)

echo "==> 2/4 编译 Release APK（minify + shrinkResources）"
(
  cd "$ROOT_DIR"
  ./gradlew :app:assembleRelease
)

if [[ ! -f "$APK_PATH" ]]; then
  echo "错误：未找到 APK: $APK_PATH" >&2
  exit 1
fi

if [[ -f "$MAPPING" ]]; then
  echo "    ProGuard mapping: $MAPPING"
fi

echo "==> 3/4 安装 APK（与 Debug 同一把 debug.keystore，可覆盖安装）"
if ! adb install -r -d "$APK_PATH"; then
  echo "提示：覆盖失败时可先 adb uninstall $APP_ID 再重试。" >&2
  exit 1
fi

echo "==> 4/4 启动 App"
adb shell am start -n "${APP_ID}/${ACTIVITY}"

echo "==> 完成"
