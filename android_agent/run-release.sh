#!/usr/bin/env bash
# 一键：Release（R8 混淆 + 资源压缩）编译 → adb 覆盖安装 → 启动 MainActivity
# 用法：在 android_agent 目录执行  ./run-release.sh
# 首次会自动 ./bin/init-release-keystore 生成本地 release 签名（与 debug 分离）。

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

if [[ ! -f "$ROOT_DIR/release.keystore" ]] || [[ ! -f "$ROOT_DIR/release-signing.properties" ]]; then
  echo "==> 0/4 初始化 Release 签名（release.keystore）"
  "$ROOT_DIR/bin/init-release-keystore"
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

echo "==> 3/4 安装 APK（Release 签名，与 Debug 不同包签名时需先卸载旧包）"
if ! adb install -r -d "$APK_PATH"; then
  echo "提示：若此前装的是 Debug 包，请先 adb uninstall $APP_ID 再重试。" >&2
  exit 1
fi

echo "==> 4/4 启动 App"
adb shell am start -n "${APP_ID}/${ACTIVITY}"

echo "==> 完成"
