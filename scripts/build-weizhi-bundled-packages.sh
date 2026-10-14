#!/usr/bin/env bash
# 从 agent-home/catalog/scripts 生成 Weizhi Package 树（agent_core resources + Android assets）。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SRC="${REPO_ROOT}/agent_core/src/main/resources/agent-home/catalog/scripts"
CORE_OUT="${REPO_ROOT}/agent_core/src/main/resources/agent-home/weizhi-packages"
ANDROID_OUT="${REPO_ROOT}/android_agent/app/src/main/assets/weizhi-packages"
VERSION="1.0.0"

declare -A ENTRY=(
  [docx]="docx.js"
  [docx-raw]="docx-raw.js"
  [docx-build]="docx-build.js"
  [pptx]="pptx.js"
  [pptx-build]="pptx-build.js"
  [svg-raster]="svg-raster.js"
)

rm -rf "${CORE_OUT}"
mkdir -p "${CORE_OUT}" "${ANDROID_OUT}"

for name in docx docx-raw docx-build pptx pptx-build svg-raster; do
  bundle="${name}@${VERSION}"
  dir="${CORE_OUT}/${bundle}"
  mkdir -p "${dir}"
  entry="${ENTRY[$name]}"
  cp "${SRC}/${entry}" "${dir}/${entry}"
  case "${name}" in
    docx-raw)
      sed -i 's|from "./docx.js"|from "weizhi:docx"|' "${dir}/${entry}"
      ;;
    docx-build)
      sed -i 's|from "./docx.js"|from "weizhi:docx"|' "${dir}/${entry}"
      ;;
    pptx-build)
      sed -i 's|from "./pptx.js"|from "weizhi:pptx"|' "${dir}/${entry}"
      ;;
  esac
  cat > "${dir}/manifest.json" <<EOF
{"name":"${name}","version":"${VERSION}","entry":"${entry}"}
EOF
done

rm -rf "${ANDROID_OUT}"/*
cp -a "${CORE_OUT}/." "${ANDROID_OUT}/"

echo "==> weizhi-packages @ ${VERSION} -> ${CORE_OUT}"
echo "==> synced Android assets -> ${ANDROID_OUT}"
