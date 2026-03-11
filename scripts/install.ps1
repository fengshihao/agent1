$ErrorActionPreference = "Stop"

$Agent1GitUrl = if ($env:AGENT1_GIT_URL) { $env:AGENT1_GIT_URL } else { "git+https://github.com/fengshihao/agent1.git" }

function Get-UvPath {
    $uvCmd = Get-Command uv -ErrorAction SilentlyContinue
    if ($uvCmd) {
        return $uvCmd.Source
    }

    Write-Host "uv 未检测到，正在安装 uv..."
    powershell -ExecutionPolicy Bypass -c "irm https://astral.sh/uv/install.ps1 | iex"

    $uvCmd = Get-Command uv -ErrorAction SilentlyContinue
    if ($uvCmd) {
        return $uvCmd.Source
    }

    $candidates = @(
        "$HOME\.local\bin\uv.exe",
        "$HOME\.cargo\bin\uv.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "uv 安装后仍不可用，请重开 PowerShell 后重试。"
}

Write-Host "安装 agent1（来源：$Agent1GitUrl）..."
$uv = Get-UvPath
try {
    & $uv tool install --force --from $Agent1GitUrl agent1
}
catch {
    Write-Host "检测到当前索引源安装失败，正在回退到官方 PyPI 重试..."
    & $uv tool install --force --default-index https://pypi.org/simple --from $Agent1GitUrl agent1
}

Write-Host ""
Write-Host "安装完成。"
Write-Host "如果当前终端找不到 agent1，请重开 PowerShell 后再执行：agent1 --help"
Write-Host "运行前请先配置模型密钥："
Write-Host '  $env:DASHSCOPE_API_KEY = "<your-api-key>"'
