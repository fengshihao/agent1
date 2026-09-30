#!/usr/bin/env bash
# 从已安装的主 App 读出上次崩溃栈与启动轨迹（debug 包，无需 root）。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_ID="${AGENT1_APP_ID:-com.agent1.android}"
OUT="${1:-$ROOT_DIR/last_crash_report.txt}"

if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "错误：未检测到 adb device。" >&2
  exit 1
fi

echo "==> 读取 $APP_ID files/last_crash_report.txt 与 boot_trace.txt"
{
  echo "=== last_crash_report.txt ==="
  adb exec-out run-as "$APP_ID" cat files/last_crash_report.txt 2>/dev/null || true
  echo ""
  echo "=== boot_trace.txt ==="
  adb exec-out run-as "$APP_ID" cat files/boot_trace.txt 2>/dev/null || true
} >"$OUT" || true

if [[ -s "$OUT" ]]; then
  echo "已写入: $OUT"
  wc -c "$OUT"
  exit 0
fi

echo "私有目录无记录。抓取 logcat（Agent1Boot / CrashReporter / AndroidRuntime）…" >&2
adb logcat -d 2>/dev/null | rg -i 'Agent1Boot|CrashReporter|AndroidRuntime|FATAL' | tail -120 >"${OUT}.logcat.txt" || true
echo "已写入: ${OUT}.logcat.txt" >&2
exit 1
