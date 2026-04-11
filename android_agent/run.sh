#!/usr/bin/env bash
# 一键：Debug 编译 → adb 覆盖安装 → 启动 MainActivity
# 用法：在仓库本目录执行  ./run.sh
# 依赖：已连接设备且开启 USB 调试；本机已配置 adb。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
APP_ID="com.dynamicui.demo"
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

echo "==> 2/4 编译 APK"
(
  cd "$ROOT_DIR"
  ./gradlew :app:assembleDebug
)

echo "==> 3/4 安装 APK"
adb install -r "$APK_PATH"

echo "==> 4/4 启动 App"
adb shell am start -n "${APP_ID}/${ACTIVITY}"

echo "==> 完成"
