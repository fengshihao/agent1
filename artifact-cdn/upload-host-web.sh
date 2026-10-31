#!/usr/bin/env bash
# =============================================================================
# 作用
#   只把 dist/host-web 拷到站点根。不传 packages / skills / web-libs。
#   全量发布仍用 ./upload.sh（它要求清单和 zip 都在）。
# 配置
#   同目录 cdn.local.env（勿提交）或环境变量：
#   AGENT1_CDN_BASE_URL / ARTIFACT_CDN_BASE_URL、ARTIFACT_CDN_SSH、ARTIFACT_CDN_REMOTE_DIR
#   UPLOAD_DRY_RUN=1 只打印 scp，不连服务器。
# 用法
#   cd artifact-cdn && npm run pack-host-web && ./upload-host-web.sh
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
DIST="${ARTIFACT_CDN_DIST:-${ROOT}/dist}"
ENV_FILE="${ROOT}/cdn.local.env"
HOST_WEB="${DIST}/host-web"

if [[ "${ARTIFACT_CDN_SKIP_LOCAL_ENV:-}" != "1" && -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

if [[ -z "${ARTIFACT_CDN_BASE_URL:-}" && -n "${AGENT1_CDN_BASE_URL:-}" ]]; then
  ARTIFACT_CDN_BASE_URL="${AGENT1_CDN_BASE_URL}"
fi
if [[ -z "${ARTIFACT_CDN_BASE_URL:-}" ]]; then
  echo "set AGENT1_CDN_BASE_URL or ARTIFACT_CDN_BASE_URL（或 artifact-cdn/cdn.local.env）" >&2
  exit 1
fi
if [[ -z "${ARTIFACT_CDN_SSH:-}" ]]; then
  echo "set ARTIFACT_CDN_SSH（或 artifact-cdn/cdn.local.env）" >&2
  exit 1
fi
SSH_TARGET="${ARTIFACT_CDN_SSH}"
REMOTE_DIR="${ARTIFACT_CDN_REMOTE_DIR:-/var/www/agentone}"

if [[ ! "${SSH_TARGET}" =~ ^[A-Za-z0-9._-]+@[A-Za-z0-9._-]+$ ]]; then
  echo "ARTIFACT_CDN_SSH 不合法" >&2
  exit 1
fi
if [[ "${REMOTE_DIR}" != /* || "${REMOTE_DIR}" == *".."* || "${REMOTE_DIR}" == */ ]]; then
  echo "ARTIFACT_CDN_REMOTE_DIR 必须是绝对路径，且不能包含 .. 或以 / 结尾" >&2
  exit 1
fi

REGISTRY="${HOST_WEB}/registry/stable.json"
if [[ ! -f "${REGISTRY}" ]]; then
  echo "缺少 ${REGISTRY}，先运行 npm run pack-host-web" >&2
  exit 1
fi

ALLOW_LOOPBACK=0
if [[ "${UPLOAD_ALLOW_LOOPBACK:-}" == "1" ]]; then
  ALLOW_LOOPBACK=1
fi
node --input-type=module - "${REGISTRY}" "${ARTIFACT_CDN_BASE_URL}" "${ALLOW_LOOPBACK}" <<'EOF'
import { readFile } from "node:fs/promises";
const [file, baseUrl, allowFlag] = process.argv.slice(2);
const prefix = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
const doc = JSON.parse(await readFile(file, "utf8"));
const urls = [];
(function walk(value) {
  if (typeof value === "string" && /^https?:\/\//.test(value)) urls.push(value);
  else if (value && typeof value === "object") {
    for (const child of Object.values(value)) walk(child);
  }
})(doc);
if (urls.length === 0) {
  throw new Error("host-web registry has no urls");
}
for (const artifactUrl of urls) {
  if (!artifactUrl.startsWith(prefix)) {
    throw new Error(`url base mismatch: ${artifactUrl}`);
  }
  const host = new URL(artifactUrl).hostname;
  if (allowFlag !== "1" && (host === "127.0.0.1" || host === "localhost")) {
    throw new Error(`refuse loopback url: ${artifactUrl}`);
  }
}
EOF

if grep -R -q -e '__JSZIP_URL__' -e '__DOCX_PREVIEW_URL__' "${HOST_WEB}"; then
  echo "host-web 里还有未烘焙的占位符" >&2
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

files=()
while IFS= read -r line; do
  [[ -n "${line}" ]] && files+=("${line}")
done < <(find "${HOST_WEB}" -type f | sort)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "dist/host-web 里没有文件" >&2
  exit 1
fi

parents=""
for file in "${files[@]}"; do
  parents="${parents} $(dirname "$(remote_path "${file}")")"
done
# shellcheck disable=SC2086
run_ssh "mkdir -p ${parents}"

for file in "${files[@]}"; do
  run_scp "${file}" "$(remote_path "${file}")"
done

if [[ "${UPLOAD_DRY_RUN:-}" != "1" ]]; then
  echo "uploaded ${#files[@]} host-web files"
fi
