#!/usr/bin/env bash
# 判断是否应执行 GitHub Release（写入 GITHUB_OUTPUT: should_release, reason, commits_since_tag）。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
GIT_ROOT="${AGENT1_RELEASE_GIT_ROOT:-${REPO_ROOT}}"
cd "${GIT_ROOT}"

EVENT_NAME="${GITHUB_EVENT_NAME:-}"
THRESHOLD="${RELEASE_EVERY_N_COMMITS:-10}"
if [[ ! "${THRESHOLD}" =~ ^[0-9]+$ ]] || (( THRESHOLD < 1 )); then
  THRESHOLD=10
fi

INPUT_FORCE="${RELEASE_INPUT_FORCE:-false}"
INPUT_DRY_RUN="${RELEASE_INPUT_DRY_RUN:-false}"

TAG_GLOB='v0.1.*'
LAST_TAG="$(git tag -l "${TAG_GLOB}" --sort=-v:refname 2>/dev/null | head -n1 || true)"
if [[ -z "${LAST_TAG}" ]]; then
  COMMITS_SINCE=999999
else
  COMMITS_SINCE="$(git rev-list --count "${LAST_TAG}..HEAD" 2>/dev/null || echo 0)"
fi

SHOULD=false
REASON=skipped

case "${EVENT_NAME}" in
  workflow_dispatch)
    SHOULD=true
    if [[ "${INPUT_FORCE}" == "true" ]]; then
      REASON=manual_force
    else
      REASON=manual
    fi
    ;;
  schedule)
    if (( COMMITS_SINCE >= 1 )); then
      SHOULD=true
      REASON=weekly
    else
      REASON=weekly_no_commits
    fi
    ;;
  push)
    if (( COMMITS_SINCE >= THRESHOLD )); then
      SHOULD=true
      REASON=commit_threshold
    else
      REASON=below_commit_threshold
    fi
    ;;
  *)
    REASON=unsupported_event
    ;;
esac

if [[ "${INPUT_DRY_RUN}" == "true" ]]; then
  SHOULD=false
  REASON="dry_run_${REASON}"
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "should_release=${SHOULD}"
    echo "reason=${REASON}"
    echo "commits_since_tag=${COMMITS_SINCE}"
    echo "commit_threshold=${THRESHOLD}"
    echo "last_tag=${LAST_TAG}"
  } >>"${GITHUB_OUTPUT}"
else
  echo "SHOULD_RELEASE=${SHOULD}"
  echo "REASON=${REASON}"
  echo "COMMITS_SINCE_TAG=${COMMITS_SINCE}"
  echo "COMMIT_THRESHOLD=${THRESHOLD}"
  echo "LAST_TAG=${LAST_TAG}"
fi
