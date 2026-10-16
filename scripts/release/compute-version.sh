#!/usr/bin/env bash
# 计算下一版 GitHub Release 的 versionName / versionCode（与发版 workflow 注入 Gradle 一致）。
# 规则：patch = max(git 提交总数, 上一 v0.1.* 标签 patch + 1)，保证单调递增且与标签对齐。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
GIT_ROOT="${AGENT1_RELEASE_GIT_ROOT:-${REPO_ROOT}}"
cd "${GIT_ROOT}"

TAG_GLOB='v0.1.*'
LAST_TAG="$(git tag -l "${TAG_GLOB}" --sort=-v:refname 2>/dev/null | head -n1 || true)"
COMMIT_COUNT="$(git rev-list --count HEAD)"

LAST_PATCH=0
if [[ -n "${LAST_TAG}" ]]; then
  LAST_PATCH="${LAST_TAG#v0.1.}"
  if [[ ! "${LAST_PATCH}" =~ ^[0-9]+$ ]]; then
    echo "无法解析标签 patch: ${LAST_TAG}" >&2
    exit 1
  fi
fi

if [[ ! "${COMMIT_COUNT}" =~ ^[0-9]+$ ]]; then
  echo "无法解析提交计数: ${COMMIT_COUNT}" >&2
  exit 1
fi

NEXT_PATCH="${COMMIT_COUNT}"
if (( NEXT_PATCH <= LAST_PATCH )); then
  NEXT_PATCH=$((LAST_PATCH + 1))
fi

VERSION_NAME="0.1.${NEXT_PATCH}"
VERSION_CODE=$((10000 + NEXT_PATCH))
TAG_NAME="v${VERSION_NAME}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "version_name=${VERSION_NAME}"
    echo "version_code=${VERSION_CODE}"
    echo "tag_name=${TAG_NAME}"
    echo "last_tag=${LAST_TAG}"
    echo "commit_count=${COMMIT_COUNT}"
  } >>"${GITHUB_OUTPUT}"
else
  echo "VERSION_NAME=${VERSION_NAME}"
  echo "VERSION_CODE=${VERSION_CODE}"
  echo "TAG_NAME=${TAG_NAME}"
  echo "LAST_TAG=${LAST_TAG}"
  echo "COMMIT_COUNT=${COMMIT_COUNT}"
fi
