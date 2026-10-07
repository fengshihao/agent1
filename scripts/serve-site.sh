#!/usr/bin/env bash
# 本地预览官网。静态文件与 GitHub Pages 发布的 site/ 相同。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-4173}"
URL="http://127.0.0.1:${PORT}/"

cd "${ROOT}/site"
python3 -m http.server "${PORT}" >/tmp/agent1-site.log 2>&1 &
pid=$!
cleanup() { kill "${pid}" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

for _ in 1 2 3 4 5 6 7 8 9 10; do
  if curl -fsS -o /dev/null "${URL}"; then
    break
  fi
  sleep 0.15
done

echo "Agent1 site  ${URL}"
echo "停止预览：Ctrl-C"

if [[ "$(uname -s)" == "Darwin" ]]; then
  open "${URL}" || true
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "${URL}" || true
fi

wait "${pid}"
