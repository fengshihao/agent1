#!/usr/bin/env bash
# 真机/模拟器：webview_exec + canvas 绘制 PNG（不调用 DashScope / 不走 LLM 生图）。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/.." && pwd -P)"

if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "错误：未检测到 adb device。请连接真机或启动模拟器并开启 USB 调试。" >&2
  exit 1
fi

if [[ ! -d "$REPO_ROOT/weizhi/android" ]]; then
  echo "错误：缺少 weizhi 源码。请在仓库根执行 ./sync-weizhi.sh" >&2
  exit 1
fi

echo "==> 发布 java-agent-core"
(
  cd "$REPO_ROOT"
  ./publish-java-agent-core.sh
)

echo "==> 运行 WebViewCanvasDrawInstrumentedTest（canvas → PNG 落盘）"
(
  cd "$ROOT_DIR"
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.dynamicui.demo.WebViewCanvasDrawInstrumentedTest
)

echo "==> WebView 绘图连通测试通过"
