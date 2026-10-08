#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

cd "${TMP}"
git init -q
git config user.email "test@example.com"
git config user.name "Test"

for i in $(seq 1 12); do
  echo "c${i}" > "f${i}.txt"
  git add .
  git commit -q -m "commit ${i}"
  if [[ "${i}" -eq 2 ]]; then
    git tag v0.1.5
  fi
done

export AGENT1_RELEASE_GIT_ROOT="${TMP}"
export GITHUB_EVENT_NAME=push
export RELEASE_EVERY_N_COMMITS=10
export GITHUB_OUTPUT="${TMP}/out-push"
bash "${REPO_ROOT}/scripts/release/evaluate-release-trigger.sh"
grep -q 'should_release=true' "${GITHUB_OUTPUT}"
grep -q 'reason=commit_threshold' "${GITHUB_OUTPUT}"

export AGENT1_RELEASE_GIT_ROOT="${TMP}"
export GITHUB_EVENT_NAME=push
export GITHUB_OUTPUT="${TMP}/out-push2"
# 再打 2 个提交，距标签 12 个，仍满足 >=10
for i in 13 14; do
  echo "c${i}" > "f${i}.txt"
  git add .
  git commit -q -m "commit ${i}"
done
bash "${REPO_ROOT}/scripts/release/evaluate-release-trigger.sh"
grep -q 'should_release=true' "${GITHUB_OUTPUT}"

export AGENT1_RELEASE_GIT_ROOT="${TMP}"
export GITHUB_EVENT_NAME=push
export RELEASE_EVERY_N_COMMITS=20
export GITHUB_OUTPUT="${TMP}/out-push3"
bash "${REPO_ROOT}/scripts/release/evaluate-release-trigger.sh"
grep -q 'should_release=false' "${GITHUB_OUTPUT}"

export AGENT1_RELEASE_GIT_ROOT="${TMP}"
export GITHUB_EVENT_NAME=workflow_dispatch
export RELEASE_INPUT_FORCE=false
export GITHUB_OUTPUT="${TMP}/out-manual"
bash "${REPO_ROOT}/scripts/release/evaluate-release-trigger.sh"
grep -q 'should_release=true' "${GITHUB_OUTPUT}"

export AGENT1_RELEASE_GIT_ROOT="${TMP}"
export GITHUB_OUTPUT="${TMP}/out-ver"
bash "${REPO_ROOT}/scripts/release/compute-version.sh"
grep -q 'version_name=0.1.14' "${GITHUB_OUTPUT}"

echo "release script self-test OK"
