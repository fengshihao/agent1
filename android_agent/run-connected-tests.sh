#!/usr/bin/env bash
# 在已连接的真机/模拟器上跑 androidTest（Espresso/Compose，不依赖 DashScope 网络）。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/.." && pwd -P)"

if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "错误：未检测到 adb device。请连接真机并开启 USB 调试。" >&2
  exit 1
fi

echo "==> 发布 java-agent-core（Android 依赖）"
(
  cd "$REPO_ROOT"
  ./publish-java-agent-core.sh
)

echo "==> 编译并安装 androidTest + 运行 MainActivitySmokeTest"
(
  cd "$ROOT_DIR"
  ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agent1.android.MainActivitySmokeTest
)

echo "==> 连通测试完成"
