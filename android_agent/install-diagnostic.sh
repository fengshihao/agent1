#!/usr/bin/env bash
# 编译并安装「Agent1 诊断」包（与主 App 并存，仅用于查看/复制崩溃日志）。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
APK="$ROOT_DIR/app/build/outputs/apk/diagnostic/debug/agent1-android-diagnostic-debug.apk"
APP_ID="com.agent1.android.diagnostic"
ACTIVITY=".CrashLogActivity"

if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "错误：未检测到 adb device。" >&2
  exit 1
fi

echo "==> 发布 java-agent-core"
( cd "$REPO_ROOT" && ./java_agent/bin/publish-core-and-verify-android )

echo "==> 编译 diagnostic debug APK"
( cd "$ROOT_DIR" && ./gradlew :app:assembleDiagnosticDebug )

echo "==> 安装诊断包（可与主 App 共存）"
adb install -r "$APK"

echo "==> 启动崩溃日志页"
adb shell am start -n "${APP_ID}/${ACTIVITY}"

echo "完成。主 App 崩溃后打开「Agent1 诊断」→ 复制全部，或到："
echo "  文件管理器 → 下载 → Agent1 → com.agent1.android → last_crash_report.txt"
