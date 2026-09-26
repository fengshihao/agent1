#!/usr/bin/env bash
# 强制停止 Demo（例如悬浮窗占满屏时无法操作其他 App）
set -euo pipefail
adb shell am force-stop com.agent1.android
echo "已强制停止 com.agent1.android"
