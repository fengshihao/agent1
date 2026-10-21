#!/usr/bin/env bash
set -euo pipefail

# 打「仅 Web UI」桌面 zip：不含 JRE；需用户本机 Java 17+。
# 用法：./scripts/package-desktop-ui.sh
# 输出：dist/agent1-desktop-ui-<os>-<arch>.zip

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd -P)"
JAVA_AGENT="${REPO_ROOT}/java_agent"
DIST="${REPO_ROOT}/dist"

detect_sqlite_classifier() {
  local os arch
  os="$(uname -s)"
  arch="$(uname -m)"
  case "${os}" in
    Darwin)
      # 一份 zip 同时含 Intel / Apple 芯片 SQLite native，避免 Gradle 增量打错架构。
      echo "macosx-universal"
      ;;
    Linux)
      case "${arch}" in
        x86_64) echo "linux-x86_64" ;;
        aarch64 | arm64) echo "linux-aarch_64" ;;
        *) echo "unsupported linux arch: ${arch}" >&2; return 1 ;;
      esac
      ;;
    MINGW* | MSYS* | CYGWIN*)
      echo "win-x86_64"
      ;;
    *)
      echo "unsupported OS: ${os}" >&2
      return 1
      ;;
  esac
}

SQLITE_CLASSIFIER="${AGENT1_SQLITE_CLASSIFIER:-$(detect_sqlite_classifier)}"
OS_LABEL="$(uname -s | tr '[:upper:]' '[:lower:]')"
if [[ "$(uname -s)" == Darwin ]]; then
  ARCH_LABEL="universal"
else
  ARCH_LABEL="$(uname -m)"
fi
STAGING="${DIST}/agent1-desktop-ui-${OS_LABEL}-${ARCH_LABEL}"
JAR_PATH="${JAVA_AGENT}/cli/build/libs/cli-0.1.0-SNAPSHOT-ui-${SQLITE_CLASSIFIER}.jar"
ZIP_PATH="${DIST}/agent1-desktop-ui-${OS_LABEL}-${ARCH_LABEL}.zip"

echo "==> sqlite classifier: ${SQLITE_CLASSIFIER}"
echo "==> Gradle uiFatJar (skip web-ui npm if AGENT1_SKIP_WEB_UI=1)"
(
  cd "${JAVA_AGENT}"
  ./gradlew --no-daemon ":cli:uiFatJar" "-Pagent1.sqliteClassifier=${SQLITE_CLASSIFIER}"
)

rm -rf "${STAGING}"
mkdir -p "${STAGING}/lib" "${STAGING}/bin"

cp "${JAR_PATH}" "${STAGING}/lib/"
cp "${SCRIPT_DIR}/desktop-ui-launcher/agent1-ui" "${STAGING}/bin/agent1-ui"
cp "${SCRIPT_DIR}/desktop-ui-launcher/java-install-hint.sh" "${STAGING}/bin/"
chmod +x "${STAGING}/bin/agent1-ui" "${STAGING}/bin/java-install-hint.sh"
cp "${SCRIPT_DIR}/desktop-ui-launcher/agent1-ui.cmd" "${STAGING}/bin/"
cp "${SCRIPT_DIR}/desktop-ui-launcher/agent1-ui.ps1" "${STAGING}/bin/"

cat > "${STAGING}/README.txt" <<EOF
Agent1 桌面 Web UI（仅浏览器壳，不含 JRE）

1. 安装 Java 17 或更高版本（OpenJDK 即可，不必 Oracle；mac/Win 默认不预装）：
   国内（推荐）腾讯 Kona JDK 17：https://kona.tencent.com/index.html#download
   国内  阿里 Dragonwell 17：https://dragonwell.aliyun.com/
   国际  Eclipse Temurin 17：https://adoptium.net/temurin/releases/?version=17
   Ubuntu 可：sudo apt install openjdk-17-jre

2. 启动：
   macOS / Linux（Bash）: ./bin/agent1-ui
   Windows CMD: bin\\agent1-ui.cmd
   Windows PowerShell: .\\bin\\agent1-ui.ps1

3. 浏览器打开终端里打印的地址（默认 http://127.0.0.1:17831 ）
   API Key 可在设置页填写，数据目录见启动日志。

官网：https://fengshihao.github.io/agent1/
EOF

rm -f "${ZIP_PATH}"
(
  cd "${DIST}"
  zip -rq "$(basename "${ZIP_PATH}")" "$(basename "${STAGING}")"
)

echo "==> ${ZIP_PATH}"
du -sh "${ZIP_PATH}" "${JAR_PATH}"
