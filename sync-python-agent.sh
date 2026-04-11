#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# 作用（仓库根「薄」脚本）
#   为子工程 python_agent 执行 uv sync（安装/锁定依赖与本地可编辑包）。
#   等价于在 python_agent 目录内运行 uv sync；额外参数会原样传给 uv。
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
exec uv sync --project "${REPO_ROOT}/python_agent" "$@"
