# Agent1 桌面 Web UI（PowerShell）。用法：.\bin\agent1-ui.ps1
$ErrorActionPreference = "Stop"
$MinMajor = 17
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$Jar = Get-ChildItem -Path (Join-Path $Root "lib\cli-*-ui*.jar") -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $Jar) {
  Write-Error "未找到 lib\cli-*-ui*.jar"
}

function Show-InstallHint {
  Write-Host @"
未检测到可用的 Java $MinMajor 或更高版本（OpenJDK 即可，不必 Oracle）。
已安装 Java 21 / 25 等新版也可以；低于 $MinMajor 无法运行本程序。

请先安装 JDK / JRE，任选其一：
  国内（推荐）腾讯 Kona JDK：
    https://kona.tencent.com/index.html#download
  国内  阿里 Dragonwell：
    https://dragonwell.aliyun.com/
  国际  Eclipse Temurin（非 Oracle）：
    https://adoptium.net/temurin/releases/?version=17

安装后重试，或设置 `$env:JAVA_HOME / `$env:JAVA17_BIN。
"@ -ForegroundColor Yellow
}

function Find-JavaBin {
  if ($env:JAVA17_BIN -and (Test-Path -LiteralPath $env:JAVA17_BIN)) { return $env:JAVA17_BIN }
  if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path -LiteralPath $candidate) { return $candidate }
  }
  $cmd = Get-Command java -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  return $null
}

$javaBin = Find-JavaBin
if (-not $javaBin) {
  Show-InstallHint
  exit 1
}

$verLines = & $javaBin -version 2>&1
if ($LASTEXITCODE -ne 0) {
  Show-InstallHint
  exit 1
}
$verLine = ($verLines | Select-Object -First 1).ToString()
if ($verLine -match 'version "([^"]+)"') {
  $parts = $Matches[1].Split(".")
  $major = [int]$parts[0]
  if ($major -eq 1 -and $parts.Length -gt 1) { $major = [int]$parts[1] }
  if ($major -lt $MinMajor) {
    Write-Host "当前 Java 版本过低（需要 ${MinMajor}+，检测到 $major）：$verLine" -ForegroundColor Red
    Show-InstallHint
    exit 1
  }
}

& $javaBin -jar $Jar.FullName @args
