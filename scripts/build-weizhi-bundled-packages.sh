#!/usr/bin/env bash
# 把 classpath 内置包同步到 Android assets。真源是 agent-home/weizhi-packages。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE_OUT="${REPO_ROOT}/agent_core/src/main/resources/agent-home/weizhi-packages"
ANDROID_OUT="${REPO_ROOT}/android_agent/app/src/main/assets/weizhi-packages"

if [[ ! -d "${CORE_OUT}" ]]; then
  echo "missing ${CORE_OUT}" >&2
  exit 1
fi

rm -rf "${ANDROID_OUT}"
mkdir -p "${ANDROID_OUT}"
cp -a "${CORE_OUT}/." "${ANDROID_OUT}/"

echo "==> synced Android assets -> ${ANDROID_OUT}"
