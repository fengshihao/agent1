#!/usr/bin/env bash
# agent_core 与 Android 源码会跑在 minSdk 26 上。
# Files.readString / Files.writeString 从 API 34 才有，core library desugar 不覆盖，
# API 26–33 上会 NoSuchMethodError。文本读写请用 com.agent1.javaagent.util.PathIo。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

PATTERN='Files\.(readString|writeString)[[:space:]]*\(|import static java\.nio\.file\.Files\.(readString|writeString)'

ROOTS=(
  "${REPO_ROOT}/agent_core/src/main"
  "${REPO_ROOT}/java_agent/weizhi-bridge/src/shared"
  "${REPO_ROOT}/android_agent/app/src/main"
  "${REPO_ROOT}/android_agent/app/src/weizhi"
)

hits=""
for root in "${ROOTS[@]}"; do
  if [[ ! -d "${root}" ]]; then
    continue
  fi
  found="$(grep -RInE --include='*.java' --include='*.kt' "${PATTERN}" "${root}" || true)"
  if [[ -n "${found}" ]]; then
    hits+="${found}"$'\n'
  fi
done

if [[ -n "${hits}" ]]; then
  echo "Android 不兼容：禁止 Files.readString/writeString（API 34+），请改用 PathIo"
  printf '%s' "${hits}"
  exit 1
fi

echo "ok: Android 路径未使用 Files.readString/writeString"
