#!/usr/bin/env bash
# 从已安装的主 App 读出上次崩溃栈（debug 包，无需 root）。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_ID="${AGENT1_APP_ID:-com.agent1.android}"
OUT="${1:-$ROOT_DIR/last_crash_report.txt}"

if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "错误：未检测到 adb device。" >&2
  exit 1
fi

echo "==> 读取 $APP_ID 私有目录 files/last_crash_report.txt"
if adb exec-out run-as "$APP_ID" cat files/last_crash_report.txt >"$OUT" 2>/dev/null; then
  echo "已写入: $OUT"
  wc -c "$OUT"
  exit 0
fi

echo "run-as 失败（可能是 release 包或未安装 debug 签名）。尝试 logcat 最近 FATAL…" >&2
adb logcat -d -t 200 '*:E' 2>/dev/null | tail -80 >"${OUT}.logcat-snippet.txt" || true
echo "已写入 logcat 片段: ${OUT}.logcat-snippet.txt（若无 Java 栈请人工搜 FATAL EXCEPTION）" >&2
exit 1
