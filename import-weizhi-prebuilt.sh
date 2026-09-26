#!/usr/bin/env bash
# 将 weizhi 私有仓库 publish 出的 Maven 产物导入 android_agent/weizhi-prebuilt/
#
# 用法：
#   ./import-weizhi-prebuilt.sh /path/to/maven/repository/root
#   ./import-weizhi-prebuilt.sh /path/to/weizhi-android-maven.tgz
#   WEIZHI_PREBUILT_URL='https://...' ./import-weizhi-prebuilt.sh
#
# 导入完成后请编辑 android_agent/weizhi-prebuilt/coordinates.properties（可从 .example 复制）

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
TARGET="${REPO_ROOT}/android_agent/weizhi-prebuilt"
MAVEN_DIR="${TARGET}/maven"
EXAMPLE="${TARGET}/coordinates.properties.example"
PROPS="${TARGET}/coordinates.properties"

mkdir -p "${MAVEN_DIR}"

import_from_dir() {
  local src="$1"
  if [[ ! -d "${src}/com/weizhi" ]] && [[ ! -d "${src}/com" ]]; then
    echo "错误：${src} 不像 Maven 仓库根（缺少 com/weizhi 或 com/）" >&2
    exit 1
  fi
  echo "==> rsync Maven 产物 -> ${MAVEN_DIR}"
  rsync -a --delete "${src}/" "${MAVEN_DIR}/"
}

if [[ -n "${WEIZHI_PREBUILT_URL:-}" ]]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "${TMP}"' EXIT
  echo "==> 下载 ${WEIZHI_PREBUILT_URL}"
  curl -fsSL -o "${TMP}/bundle" "${WEIZHI_PREBUILT_URL}"
  if file "${TMP}/bundle" | grep -qi gzip; then
    mkdir -p "${TMP}/extract"
    tar -xzf "${TMP}/bundle" -C "${TMP}/extract"
    ROOT="$(find "${TMP}/extract" -type d -path '*/com/weizhi' 2>/dev/null | head -1 | sed 's|/com/weizhi||')"
    if [[ -z "${ROOT}" ]]; then
      ROOT="${TMP}/extract"
    fi
    import_from_dir "${ROOT}"
  else
    import_from_dir "${TMP}/bundle"
  fi
elif [[ $# -ge 1 ]]; then
  SRC="$1"
  if [[ -f "${SRC}" ]]; then
    TMP="$(mktemp -d)"
    trap 'rm -rf "${TMP}"' EXIT
    tar -xzf "${SRC}" -C "${TMP}"
    ROOT="$(find "${TMP}" -type d -path '*/com/weizhi' 2>/dev/null | head -1 | sed 's|/com/weizhi||')"
    [[ -n "${ROOT}" ]] || ROOT="${TMP}"
    import_from_dir "${ROOT}"
  elif [[ -d "${SRC}" ]]; then
    import_from_dir "${SRC}"
  else
    echo "不存在: ${SRC}" >&2
    exit 1
  fi
else
  echo "请传入 Maven 目录、tgz，或设置 WEIZHI_PREBUILT_URL" >&2
  exit 1
fi

if [[ ! -f "${PROPS}" ]]; then
  cp "${EXAMPLE}" "${PROPS}"
  echo "==> 已生成 ${PROPS}，请按 weizhi 实际 publish 的 group/version 修改"
fi

if [[ ! -d "${MAVEN_DIR}/com/weizhi" ]]; then
  echo "警告：${MAVEN_DIR} 下未找到 com/weizhi，请确认 weizhi 的 groupId 与 coordinates.properties 一致" >&2
fi

echo "==> 完成。下一步: cd android_agent && ./gradlew :app:assembleDebug"
