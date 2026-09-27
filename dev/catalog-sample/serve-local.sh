#!/usr/bin/env bash
# 本地静态 catalog 样例（阶段 5.7 / P.1 手测）：script + js_lib 两种 kind。
#
# 用法（终端 1）：
#   ./dev/catalog-sample/serve-local.sh
# 终端 2：
#   export AGENT1_CATALOG_MANIFEST_URL="http://127.0.0.1:8765/catalog-index.json"
#   export AGENT1_AGENT_ROOT=/tmp/agent1-catalog-$$
#   ./agent1 sync check
#   ./agent1 sync apply --ids script.sample-hello,lib.sample-inc

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PORT="${CATALOG_SAMPLE_PORT:-8765}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 python3" >&2
  exit 1
fi

echo "Serving ${ROOT} at http://127.0.0.1:${PORT}/"
echo "export AGENT1_CATALOG_MANIFEST_URL=\"http://127.0.0.1:${PORT}/catalog-index.json\""
echo "（manifest 内 baseUrl 需与对象 URL 前缀一致；本地测可改 catalog-index.json 的 baseUrl 为上述地址/）"
exec python3 -m http.server "${PORT}" --directory "${ROOT}"
