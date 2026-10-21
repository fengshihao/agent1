@echo off
setlocal EnableExtensions
set "MIN_MAJOR=17"
set "ROOT=%~dp0.."
for %%F in ("%ROOT%\lib\cli-*-ui*.jar") do set "JAR=%%~fF"
if not defined JAR (
  echo 错误: 未找到 lib\cli-*-ui.jar
  exit /b 1
)

set "JAVA_BIN="
if defined JAVA17_BIN set "JAVA_BIN=%JAVA17_BIN%"
if not defined JAVA_BIN if defined JAVA_HOME set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_BIN (
  for /f "delims=" %%J in ('where java 2^>nul') do (
    set "JAVA_BIN=%%J"
    goto :found_java
  )
)
:found_java
if not defined JAVA_BIN (
  call :install_hint
  exit /b 1
)

"%JAVA_BIN%" -version >nul 2>&1
if errorlevel 1 (
  call :install_hint
  exit /b 1
)

for /f "tokens=3" %%V in ('"%JAVA_BIN%" -version 2^>^&1 ^| findstr /i "version"') do set "VER=%%V"
set "VER=%VER:"=%"
for /f "tokens=1 delims=." %%M in ("%VER%") do set "MAJOR=%%M"
if "%MAJOR%"=="1" for /f "tokens=2 delims=." %%M in ("%VER%") do set "MAJOR=%%M"
if %MAJOR% LSS %MIN_MAJOR% (
  echo 当前 Java 版本过低（需要 %MIN_MAJOR%+，检测到 %MAJOR%）。
  "%JAVA_BIN%" -version 2>&1 | findstr /i "version"
  call :install_hint
  exit /b 1
)

"%JAVA_BIN%" -jar "%JAR%" %*
exit /b %ERRORLEVEL%

:install_hint
echo 未检测到可用的 Java %MIN_MAJOR% 或更高版本（OpenJDK 即可，不必 Oracle）。
echo 已安装 Java 21 / 25 等新版也可以；低于 %MIN_MAJOR% 无法运行本程序。
echo.
echo 请先安装 JDK / JRE，任选其一：
echo   国内（推荐）腾讯 Kona JDK：
echo     https://kona.tencent.com/index.html#download
echo   国内  阿里 Dragonwell：
echo     https://dragonwell.aliyun.com/
echo   国际  Eclipse Temurin（非 Oracle）：
echo     https://adoptium.net/temurin/releases/?version=17
echo.
echo 安装后重试，或设置 JAVA_HOME / JAVA17_BIN。
exit /b 1
