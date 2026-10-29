#!/usr/bin/env bash
# 载入本地 CDN 配置（artifact-cdn/cdn.local.env，已 gitignore）。
# 给 AGENT1_CDN_BASE_URL 一个全局入口；打包脚本仍可读 ARTIFACT_CDN_BASE_URL。
# 用法：source scripts/cdn-env.sh

if [[ -n "${_AGENT1_CDN_ENV_LOADED:-}" ]]; then
  return 0 2>/dev/null || true
fi
_AGENT1_CDN_ENV_LOADED=1

_AGENT1_CDN_ENV_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
_AGENT1_CDN_ENV_FILE="${_AGENT1_CDN_ENV_ROOT}/artifact-cdn/cdn.local.env"
if [[ -f "${_AGENT1_CDN_ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${_AGENT1_CDN_ENV_FILE}"
  set +a
fi
if [[ -z "${AGENT1_CDN_BASE_URL:-}" && -n "${ARTIFACT_CDN_BASE_URL:-}" ]]; then
  export AGENT1_CDN_BASE_URL="${ARTIFACT_CDN_BASE_URL}"
fi
if [[ -z "${ARTIFACT_CDN_BASE_URL:-}" && -n "${AGENT1_CDN_BASE_URL:-}" ]]; then
  export ARTIFACT_CDN_BASE_URL="${AGENT1_CDN_BASE_URL}"
fi
if [[ -z "${AGENT1_CDN_HOST:-}" && -n "${AGENT1_CDN_BASE_URL:-}" ]]; then
  export AGENT1_CDN_HOST="${AGENT1_CDN_BASE_URL#*://}"
  AGENT1_CDN_HOST="${AGENT1_CDN_HOST%%/*}"
fi
unset _AGENT1_CDN_ENV_ROOT _AGENT1_CDN_ENV_FILE
