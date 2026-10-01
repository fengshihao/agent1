#!/usr/bin/env bash
# Cloud Agent 本地校验：不调用 Gradle，避免 ~/.gradle 占满小盘。
# 编译、单测、Detekt、APK 联编见 GitHub Actions：.github/workflows/ci.yml
#
# 用法：./scripts/cloud-agent-verify.sh
# 推送/开 PR 后请确认 CI 三项 job 均绿：java-test、quality-static、android-assemble-debug
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"

echo "==> agent1 Cloud Agent 校验（无 Gradle）"
echo "    完整构建请在 GitHub Actions / 本机 ./scripts/ci-local.sh 执行"
echo ""

export AGENT1_SKIP_GRADLE=1

bash "${REPO_ROOT}/check-android-agent-layering.sh"
python3 "${REPO_ROOT}/android_agent/scripts/check_android_main_thread_gateway.py" --self-test
python3 "${REPO_ROOT}/android_agent/scripts/check_android_main_thread_gateway.py"
bash "${REPO_ROOT}/scripts/check-android-files-api.sh"

echo ""
echo "==> 通过：Android 分层 + 主线程 Gateway + Files API"
echo "==> 未运行：Java PMD/SpotBugs、Detekt、:core:test、assembleDebug（由 CI 负责）"
echo "==> 推送后检查 PR CI：java-test | quality-static | android-assemble-debug"
