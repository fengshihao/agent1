#!/usr/bin/env bash
# GitHub Actions runner：安装 Agent1 Android 构建所需 SDK/NDK（与 ci.yml 对齐）。
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME}}"

export ANDROID_HOME ANDROID_SDK_ROOT

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "ANDROID_HOME=${ANDROID_HOME}"
    echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"
  } >>"${GITHUB_ENV}"
fi

if [[ -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]]; then
  SDKM="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
  SDKM_BIN_DIR="${ANDROID_HOME}/cmdline-tools/latest/bin"
else
  SDKM="${ANDROID_HOME}/cmdline-tools/16.0/bin/sdkmanager"
  SDKM_BIN_DIR="${ANDROID_HOME}/cmdline-tools/16.0/bin"
fi

if [[ -n "${GITHUB_PATH:-}" ]]; then
  echo "${SDKM_BIN_DIR}" >>"${GITHUB_PATH}"
  echo "${ANDROID_HOME}/platform-tools" >>"${GITHUB_PATH}"
fi

yes | "${SDKM}" --licenses >/dev/null
"${SDKM}" "platforms;android-34" "platforms;android-35" "build-tools;34.0.0" "ndk;26.1.10909125"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/26.1.10909125" >>"${GITHUB_ENV}"
fi
