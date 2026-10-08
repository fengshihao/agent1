#!/usr/bin/env bash
# 构建 android_agent Release APK；要求已 publishCoreToLocalRepo，且勿注入 API Key。
# 可选环境变量：VERSION_NAME、VERSION_CODE（发版 workflow 注入，与 Git 标签对齐）。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
cd "${REPO_ROOT}/android_agent"

export DASHSCOPE_API_KEY=""
export QWEN_API_KEY=""

./gradlew --no-daemon :app:assembleRelease

cfg=app/build/generated/source/buildConfig/release/com/agent1/android/BuildConfig.java
if grep -E '(QWEN_API_KEY|DASHSCOPE_API_KEY) = "[^"]' "${cfg}"; then
  echo "Release BuildConfig 写入了 API Key，拒绝继续" >&2
  exit 1
fi

APK=app/build/outputs/apk/release/agent1-android-release.apk
if [[ ! -f "${APK}" ]]; then
  echo "未找到 ${APK}" >&2
  exit 1
fi

echo "Release BuildConfig API keys are empty"
if [[ -d ../weizhi/android ]]; then
  unzip -l "${APK}" | grep 'lib/arm64-v8a/libweizhijni.so'
fi

if [[ -n "${VERSION_NAME:-}" ]] || [[ -n "${VERSION_CODE:-}" ]]; then
  echo "Built ${APK} (VERSION_NAME=${VERSION_NAME:-<gradle>} VERSION_CODE=${VERSION_CODE:-<gradle>})"
fi
