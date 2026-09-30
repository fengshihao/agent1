#!/usr/bin/env bash
# 将 Agent1 bootstrap catalog 中的 office 脚本覆盖到已 clone 的 weizhi 树，
# 使 weizhi/scripts/test-jni.sh（OfficeTest）与 App 运行时 catalog 一致。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
CATALOG="${REPO_ROOT}/agent_core/src/main/resources/agent-home/catalog/scripts"
WEIZHI="${WEIZHI_DIR:-${AGENT1_WEIZHI_REPO:-${REPO_ROOT}/weizhi}}"

if [[ ! -d "${WEIZHI}/assets/office" ]]; then
  echo "skip overlay: no ${WEIZHI}/assets/office" >&2
  exit 0
fi

for name in docx.js docx-raw.js docx-build.js; do
  src="${CATALOG}/${name}"
  [[ -f "${src}" ]] || continue
  cp "${src}" "${WEIZHI}/assets/office/${name}"
  if [[ -d "${WEIZHI}/android/app/src/main/assets/office" ]]; then
    cp "${src}" "${WEIZHI}/android/app/src/main/assets/office/${name}"
  fi
done

echo "overlay office scripts: catalog -> ${WEIZHI}/assets/office"
