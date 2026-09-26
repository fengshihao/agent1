#!/usr/bin/env bash
# 将 weizhi 私有仓库 publish 出的 Maven 产物导入 android_agent/weizhi-prebuilt/
#
# 用法：
#   ./import-weizhi-prebuilt.sh /path/to/maven/repository/root
#   ./import-weizhi-prebuilt.sh /path/to/weizhi-android-maven.tgz
#   WEIZHI_PREBUILT_URL='https://...' ./import-weizhi-prebuilt.sh
#
# WEIZHI_PREBUILT_URL 支持：
#   - 直链 tgz / gzip
#   - GitHub Actions artifact 页面 URL（需 WEIZHI_GITHUB_TOKEN / GH_TOKEN / GITHUB_TOKEN）
#   - https://api.github.com/repos/OWNER/REPO/actions/artifacts/ID/zip
#
# 导入完成后请编辑 android_agent/weizhi-prebuilt/coordinates.properties（可从 .example 复制）

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
TARGET="${REPO_ROOT}/android_agent/weizhi-prebuilt"
MAVEN_DIR="${TARGET}/maven"
EXAMPLE="${TARGET}/coordinates.properties.example"
PROPS="${TARGET}/coordinates.properties"

mkdir -p "${MAVEN_DIR}"

github_token() {
  if [[ -n "${WEIZHI_GITHUB_TOKEN:-}" ]]; then
    echo "${WEIZHI_GITHUB_TOKEN}"
  elif [[ -n "${GH_TOKEN:-}" ]]; then
    echo "${GH_TOKEN}"
  elif [[ -n "${GITHUB_TOKEN:-}" ]]; then
    echo "${GITHUB_TOKEN}"
  else
    echo ""
  fi
}

find_maven_repo_root() {
  local base="$1"
  local hit
  hit="$(find "${base}" -type d -path '*/com/weizhi' 2>/dev/null | head -1 || true)"
  if [[ -n "${hit}" ]]; then
    echo "${hit%/com/weizhi}"
    return 0
  fi
  echo ""
  return 1
}

import_from_dir() {
  local src="$1"
  if [[ ! -d "${src}/com/weizhi" ]] && [[ ! -d "${src}/com" ]]; then
    echo "错误：${src} 不像 Maven 仓库根（缺少 com/weizhi 或 com/）" >&2
    exit 1
  fi
  echo "==> 同步 Maven 产物 -> ${MAVEN_DIR}"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete "${src}/" "${MAVEN_DIR}/"
  else
    find "${MAVEN_DIR}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
    cp -a "${src}/." "${MAVEN_DIR}/"
  fi
}

extract_archive_to_temp() {
  local archive="$1"
  local dest="$2"
  mkdir -p "${dest}"
  if file -b "${archive}" | grep -qiE 'gzip|tar'; then
    tar -xzf "${archive}" -C "${dest}"
    return 0
  fi
  if file -b "${archive}" | grep -qi 'zip'; then
    unzip -q "${archive}" -d "${dest}"
    return 0
  fi
  echo "错误：不支持的归档格式: ${archive} ($(file -b "${archive}"))" >&2
  exit 1
}

import_from_tree() {
  local root="$1"
  local inner_tgz inner_root
  inner_tgz="$(find "${root}" -type f \( -name '*.tgz' -o -name '*.tar.gz' \) 2>/dev/null | head -1 || true)"
  if [[ -n "${inner_tgz}" ]]; then
    local nested
    nested="$(mktemp -d)"
    extract_archive_to_temp "${inner_tgz}" "${nested}"
    inner_root="$(find_maven_repo_root "${nested}" || true)"
    [[ -n "${inner_root}" ]] || inner_root="${nested}"
    import_from_dir "${inner_root}"
    rm -rf "${nested}"
    return 0
  fi
  inner_root="$(find_maven_repo_root "${root}" || true)"
  if [[ -n "${inner_root}" ]]; then
    import_from_dir "${inner_root}"
    return 0
  fi
  import_from_dir "${root}"
}

download_github_actions_artifact() {
  local url="$1"
  local out="$2"
  local token artifact_id owner repo api

  token="$(github_token)"
  if [[ -z "${token}" ]]; then
    echo "错误：下载 GitHub Actions artifact 需要 WEIZHI_GITHUB_TOKEN（或 GH_TOKEN / GITHUB_TOKEN），且 token 需能读 weizhi 仓库" >&2
    exit 1
  fi

  if [[ "${url}" =~ github\.com/([^/]+)/([^/]+)/actions/runs/[0-9]+/artifacts/([0-9]+) ]]; then
    owner="${BASH_REMATCH[1]}"
    repo="${BASH_REMATCH[2]}"
    artifact_id="${BASH_REMATCH[3]}"
  elif [[ "${url}" =~ api\.github\.com/repos/([^/]+)/([^/]+)/actions/artifacts/([0-9]+) ]]; then
    owner="${BASH_REMATCH[1]}"
    repo="${BASH_REMATCH[2]}"
    artifact_id="${BASH_REMATCH[3]}"
  else
    echo "错误：无法从 URL 解析 GitHub artifact: ${url}" >&2
    exit 1
  fi

  api="https://api.github.com/repos/${owner}/${repo}/actions/artifacts/${artifact_id}/zip"
  echo "==> 下载 GitHub Actions artifact ${artifact_id} (${owner}/${repo})"
  curl -fsSL \
    -H "Authorization: Bearer ${token}" \
    -H "Accept: application/vnd.github+json" \
    -o "${out}" \
    "${api}"
}

download_prebuilt_url() {
  local url="$1"
  local out="$2"

  if [[ "${url}" == *"github.com"*"/actions/"*"artifacts/"* ]] || [[ "${url}" == *"api.github.com/repos/"*"/actions/artifacts/"* ]]; then
    download_github_actions_artifact "${url}" "${out}"
    return 0
  fi

  echo "==> 下载 ${url}"
  local token
  token="$(github_token)"
  if [[ -n "${token}" ]]; then
    curl -fsSL -H "Authorization: Bearer ${token}" -o "${out}" "${url}"
  else
    curl -fsSL -o "${out}" "${url}"
  fi
}

if [[ -n "${WEIZHI_PREBUILT_URL:-}" ]]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "${TMP}"' EXIT
  BUNDLE="${TMP}/bundle"
  download_prebuilt_url "${WEIZHI_PREBUILT_URL}" "${BUNDLE}"
  EXTRACT="${TMP}/extract"
  if file -b "${BUNDLE}" | grep -qiE 'gzip|tar|zip'; then
    extract_archive_to_temp "${BUNDLE}" "${EXTRACT}"
    import_from_tree "${EXTRACT}"
  else
    import_from_dir "${BUNDLE}"
  fi
elif [[ $# -ge 1 ]]; then
  SRC="$1"
  if [[ -f "${SRC}" ]]; then
    TMP="$(mktemp -d)"
    trap 'rm -rf "${TMP}"' EXIT
    extract_archive_to_temp "${SRC}" "${TMP}"
    import_from_tree "${TMP}"
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
