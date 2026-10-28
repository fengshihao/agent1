#!/usr/bin/env bash
# 准备 agent1.code-workspace 所需的同级仓：weizhi。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

"${ROOT}/scripts/ensure-weizhi-sibling.sh"

if [[ -d "${ROOT}/../weizhi/android" ]]; then
  "${ROOT}/sync-weizhi.sh"
fi

echo ""
echo "请在 Cursor 打开: ${ROOT}/agent1.code-workspace"
