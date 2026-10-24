#!/usr/bin/env bash
# =============================================================================
# 作用
#   把 artifact-cdn/dist 拷到国优服务器的站点根。
#   先传全部 zip，再覆盖两份清单。不负责安装，也不区分 kind。
# 配置
#   同目录 cdn.local.env（勿提交）或环境变量：
#   ARTIFACT_CDN_BASE_URL、ARTIFACT_CDN_SSH、ARTIFACT_CDN_REMOTE_DIR
#   UPLOAD_DRY_RUN=1 只打印 scp，不连服务器。
# 站点根布局（与 dist 相同）
#   registry/stable.json
#   packages/registry/stable.json
#   web-libs/registry/stable.json
#   packages/<name>/<version>/<name>-<version>.zip
#   skills/<name>/<version>/<name>-<version>.zip
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

: "${ARTIFACT_CDN_BASE_URL:=https://agentone.guoyoutech.cn/}"
SSH_TARGET="${ARTIFACT_CDN_SSH:-root@49.232.7.38}"
REMOTE_DIR="${ARTIFACT_CDN_REMOTE_DIR:-/var/www/agentone}"

if [[ ! "${SSH_TARGET}" =~ ^[A-Za-z0-9._-]+@[A-Za-z0-9._-]+$ ]]; then
  echo "ARTIFACT_CDN_SSH 不合法" >&2
  exit 1
fi
if [[ "${REMOTE_DIR}" != /* || "${REMOTE_DIR}" == *".."* || "${REMOTE_DIR}" == */ ]]; then
  echo "ARTIFACT_CDN_REMOTE_DIR 必须是绝对路径，且不能包含 .. 或以 / 结尾" >&2
  exit 1
fi

GENERIC="${DIST}/registry/stable.json"
PROJECTION="${DIST}/packages/registry/stable.json"
SKILLS_CATALOG="${DIST}/skills/registry/stable.json"
WEB_LIBS="${DIST}/web-libs/registry/stable.json"
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

remote_path() {
  local rel="${1#"${DIST}/"}"
  printf '%s/%s' "${REMOTE_DIR}" "${rel}"
}

run_ssh() {
  if [[ "${UPLOAD_DRY_RUN:-}" == "1" ]]; then
    printf 'ssh %s %s\n' "${SSH_TARGET}" "$1"
    return 0
  fi
  ssh -o BatchMode=yes "${SSH_TARGET}" "$1"
}

run_scp() {
  local src="$1"
  local dest="$2"
  if [[ "${UPLOAD_DRY_RUN:-}" == "1" ]]; then
    printf 'scp %s %s:%s\n' "${src}" "${SSH_TARGET}" "${dest}"
    return 0
  fi
  scp -o BatchMode=yes "${src}" "${SSH_TARGET}:${dest}"
}

parents=""
for zip in "${zips[@]}"; do
  parent="$(dirname "$(remote_path "${zip}")")"
  parents="${parents} ${parent}"
done
parents="${parents} $(dirname "$(remote_path "${GENERIC}")") $(dirname "$(remote_path "${PROJECTION}")")"
if [[ -f "${SKILLS_CATALOG}" ]]; then
  parents="${parents} $(dirname "$(remote_path "${SKILLS_CATALOG}")")"
fi
if [[ -f "${WEB_LIBS}" ]]; then
  parents="${parents} $(dirname "$(remote_path "${WEB_LIBS}")")"
fi
# shellcheck disable=SC2086
run_ssh "mkdir -p ${parents}"

for zip in "${zips[@]}"; do
  run_scp "${zip}" "$(remote_path "${zip}")"
done
run_scp "${GENERIC}" "$(remote_path "${GENERIC}")"
run_scp "${PROJECTION}" "$(remote_path "${PROJECTION}")"
if [[ -f "${SKILLS_CATALOG}" ]]; then
  run_scp "${SKILLS_CATALOG}" "$(remote_path "${SKILLS_CATALOG}")"
fi
if [[ -f "${WEB_LIBS}" ]]; then
  run_scp "${WEB_LIBS}" "$(remote_path "${WEB_LIBS}")"
fi
