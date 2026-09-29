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

echo "==> 读取 $APP_ID 私有目录 last_crash_report.txt"
if adb exec-out run-as "$APP_ID" cat files/last_crash_report.txt >"$OUT" 2>/dev/null; then
  echo "已写入: $OUT"
  wc -c "$OUT"
  exit 0
fi

echo "run-as 失败（可能是 release 或未安装主包）。尝试从 Download/Agent1 拉取…" >&2
TMP="/sdcard/Download/Agent1/$APP_ID/last_crash_report.txt"
if adb shell "test -f '$TMP'" 2>/dev/null; then
  adb exec-out cat "$TMP" >"$OUT"
  echo "已从公共下载目录写入: $OUT"
  wc -c "$OUT"
  exit 0
fi

echo "未找到崩溃记录。请先触发一次崩溃，或安装 diagnostic 包查看。" >&2
exit 1
