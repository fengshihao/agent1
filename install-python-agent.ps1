$ErrorActionPreference = "Stop"

# =============================================================================
# 作用（仓库根「薄」脚本，与子工程脚本并列）
#   将 Python 命令行智能体 agent1 安装为 uv 全局工具。实际逻辑在
#   python_agent/scripts/install.ps1；本文件仅定位仓库根并转调。
# =============================================================================

$RepoRoot = $PSScriptRoot
$Target = [System.IO.Path]::Combine($RepoRoot, "python_agent", "scripts", "install.ps1")
if (-not (Test-Path -LiteralPath $Target)) {
    throw "找不到安装脚本: $Target"
}
& $Target @Args
