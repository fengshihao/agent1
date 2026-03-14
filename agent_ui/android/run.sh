#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
APP_ID="com.dynamicui.demo"
ACTIVITY=".MainActivity"

echo "==> 1/3 编译 APK"
"$ROOT_DIR/gradlew" :app:assembleDebug

echo "==> 2/3 安装 APK"
adb install -r "$APK_PATH"

echo "==> 3/3 启动 App"
adb shell am start -n "${APP_ID}/${ACTIVITY}"

echo "==> 完成"
