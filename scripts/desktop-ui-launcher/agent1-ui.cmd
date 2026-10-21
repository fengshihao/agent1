@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "MIN_MAJOR=17"

rem Must cd /d so drive letter and .. resolve correctly (zip may live on any drive)
cd /d "%~dp0.."
if errorlevel 1 (
  echo ERROR: cannot cd to install directory: %~dp0..
  exit /b 1
)
set "ROOT=%CD%"

set "JAR="
for %%F in ("%ROOT%\lib\cli-*-ui*.jar") do (
  set "JAR=%%~fF"
  goto :jar_ok
)
echo ERROR: no lib\cli-*-ui*.jar under %ROOT%
exit /b 1

:jar_ok
set "JAVA_BIN="
if defined JAVA17_BIN set "JAVA_BIN=%JAVA17_BIN%"
if not defined JAVA_BIN (
  if defined JAVA_HOME set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_BIN (
  for /f "delims=" %%J in ('where java 2^>nul') do (
    set "JAVA_BIN=%%J"
    goto :found_java
  )
)
:found_java
if not defined JAVA_BIN goto :install_hint

"%JAVA_BIN%" -version >nul 2>&1
if errorlevel 1 goto :install_hint

for /f "tokens=3" %%V in ('"%JAVA_BIN%" -version 2^>^&1 ^| findstr /i version') do set "VER=%%V"
set "VER=!VER:"=!"
for /f "tokens=1 delims=." %%M in ("!VER!") do set "MAJOR=%%M"
if "!MAJOR!"=="1" for /f "tokens=2 delims=." %%M in ("!VER!") do set "MAJOR=%%M"
if !MAJOR! LSS %MIN_MAJOR% (
  echo Java too old: need %MIN_MAJOR%+, got !MAJOR!
  "%JAVA_BIN%" -version 2>&1 | findstr /i version
  goto :install_hint
)

"%JAVA_BIN%" -jar "%JAR%" %*
exit /b !ERRORLEVEL!

:install_hint
echo.
echo Need Java %MIN_MAJOR% or newer (OpenJDK OK, not Oracle-only).
echo Java 21 / 25 also work.
echo.
echo China - Tencent Kona: https://kona.tencent.com/index.html#download
echo China - Dragonwell: https://dragonwell.aliyun.com/
echo Intl  - Temurin 17: https://adoptium.net/temurin/releases/?version=17
echo.
echo Then set JAVA_HOME or JAVA17_BIN if java is not on PATH.
exit /b 1
