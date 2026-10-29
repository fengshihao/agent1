#!/usr/bin/env bash
# 仓库导出不得出现内部 CDN 域名 / 上传机 IP。生产值只放 gitignore 的 cdn.local.env。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"

NEEDLE_DOMAIN="$(printf '%s%s.%s' 'guoyou' 'tech' 'cn')"
NEEDLE_IP="$(printf '%s.%s.%s.%s' '49' '232' '7' '38')"

failed=0
if git grep -F -n -- "${NEEDLE_DOMAIN}" -- . >/tmp/agent1-cdn-leak-domain.txt; then
  echo "forbidden CDN domain leaked in tracked files:" >&2
  cat /tmp/agent1-cdn-leak-domain.txt >&2
  failed=1
fi
if git grep -F -n -- "${NEEDLE_IP}" -- . >/tmp/agent1-cdn-leak-ip.txt; then
  echo "forbidden CDN SSH host leaked in tracked files:" >&2
  cat /tmp/agent1-cdn-leak-ip.txt >&2
  failed=1
fi
rm -f /tmp/agent1-cdn-leak-domain.txt /tmp/agent1-cdn-leak-ip.txt
if [[ "${failed}" -ne 0 ]]; then
  echo "put production values in artifact-cdn/cdn.local.env (gitignored) or AGENT1_CDN_HOST" >&2
  exit 1
fi
echo "ok: no internal CDN host in tracked files"
