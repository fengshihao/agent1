#!/usr/bin/env bash
# =============================================================================
# 作用
#   把 artifact-cdn/dist 上传到已有的阿里云 OSS / CDN 前缀。
#   先传全部 zip，再覆盖两份清单。不负责安装，也不区分 kind。
# 配置
#   同目录 cdn.local.env（勿提交）或环境变量：
#   ARTIFACT_CDN_BASE_URL、OSS_BUCKET、可选 OSS_PREFIX / OSS_ENDPOINT / OSSUTIL_CONFIG
#   UPLOAD_DRY_RUN=1 只打印 ossutil 命令。
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
DIST="${ARTIFACT_CDN_DIST:-${ROOT}/dist}"
ENV_FILE="${ROOT}/cdn.local.env"

if [[ "${ARTIFACT_CDN_SKIP_LOCAL_ENV:-}" != "1" && -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

: "${OSS_BUCKET:?设置 OSS_BUCKET（cdn.local.env 或环境变量）}"
: "${ARTIFACT_CDN_BASE_URL:?设置 ARTIFACT_CDN_BASE_URL，且必须与 pack --base-url 一致}"

if [[ ! "${OSS_BUCKET}" =~ ^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$ ]]; then
  echo "OSS_BUCKET 不合法" >&2
  exit 1
fi

PREFIX="${OSS_PREFIX:-}"
PREFIX="${PREFIX#/}"
PREFIX="${PREFIX%/}"
if [[ -n "${PREFIX}" && ! "${PREFIX}" =~ ^[A-Za-z0-9._/-]+$ ]]; then
  echo "OSS_PREFIX 不合法" >&2
  exit 1
fi
if [[ "${PREFIX}" == *".."* ]]; then
  echo "OSS_PREFIX 不能包含 .." >&2
  exit 1
fi

GENERIC="${DIST}/registry/stable.json"
PROJECTION="${DIST}/packages/registry/stable.json"
if [[ ! -f "${GENERIC}" || ! -f "${PROJECTION}" ]]; then
  echo "缺少清单，先在该 dist 上运行 pack" >&2
  exit 1
fi

ALLOW_LOOPBACK=0
if [[ "${UPLOAD_ALLOW_LOOPBACK:-}" == "1" ]]; then
  ALLOW_LOOPBACK=1
fi
node "${ROOT}/check-urls.mjs" "${GENERIC}" "${ARTIFACT_CDN_BASE_URL}" "${ALLOW_LOOPBACK}"
node "${ROOT}/check-urls.mjs" "${PROJECTION}" "${ARTIFACT_CDN_BASE_URL}" "${ALLOW_LOOPBACK}"

zips=()
while IFS= read -r line; do
  [[ -n "${line}" ]] && zips+=("${line}")
done < <(find "${DIST}" -type f -name '*.zip' | sort)

if [[ ${#zips[@]} -eq 0 ]]; then
  echo "dist 里没有 zip，先运行 pack" >&2
  exit 1
fi

object_key() {
  local rel="${1#"${DIST}/"}"
  if [[ -n "${PREFIX}" ]]; then
    printf '%s/%s' "${PREFIX}" "${rel}"
  else
    printf '%s' "${rel}"
  fi
}

run_ossutil() {
  if [[ "${UPLOAD_DRY_RUN:-}" == "1" ]]; then
    printf 'ossutil'
    local arg
    for arg in "$@"; do
      printf ' %s' "${arg}"
    done
    printf '\n'
    return 0
  fi
  if ! command -v ossutil >/dev/null 2>&1; then
    echo "未找到 ossutil。先在本机安装并 ossutil config，不要把密钥写进仓库。" >&2
    exit 1
  fi
  ossutil "$@"
}

copy_object() {
  local src="$1"
  local key
  key="$(object_key "${src}")"
  if [[ -n "${OSSUTIL_CONFIG:-}" && -n "${OSS_ENDPOINT:-}" ]]; then
    run_ossutil -c "${OSSUTIL_CONFIG}" -e "${OSS_ENDPOINT}" cp -f "${src}" "oss://${OSS_BUCKET}/${key}"
  elif [[ -n "${OSSUTIL_CONFIG:-}" ]]; then
    run_ossutil -c "${OSSUTIL_CONFIG}" cp -f "${src}" "oss://${OSS_BUCKET}/${key}"
  elif [[ -n "${OSS_ENDPOINT:-}" ]]; then
    run_ossutil -e "${OSS_ENDPOINT}" cp -f "${src}" "oss://${OSS_BUCKET}/${key}"
  else
    run_ossutil cp -f "${src}" "oss://${OSS_BUCKET}/${key}"
  fi
}

for zip in "${zips[@]}"; do
  copy_object "${zip}"
done
copy_object "${GENERIC}"
copy_object "${PROJECTION}"
