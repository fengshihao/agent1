#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# 作用（仓库根「薄」脚本，与子工程脚本并列）
#   将 Python 命令行智能体 agent1 安装为 uv 全局工具（uv tool install）。
#   实际逻辑在 python_agent/scripts/install.sh；本文件仅定位仓库根并转调。
#   远程一键安装：curl -fsSL …/refs/heads/<branch>/install-python-agent.sh | sh
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
exec sh "${REPO_ROOT}/python_agent/scripts/install.sh" "$@"
