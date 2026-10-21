#!/usr/bin/env bash
# 被 agent1-ui source；查找本机 java 与缺省安装指引（国内链接优先）。

AGENT1_MIN_JAVA_MAJOR=17

java_install_hint() {
  cat >&2 <<EOF
未检测到可用的 Java ${AGENT1_MIN_JAVA_MAJOR} 或更高版本（OpenJDK 即可，不必 Oracle）。
已安装 Java 21 / 25 等新版也可以；低于 ${AGENT1_MIN_JAVA_MAJOR} 无法运行本程序（字节码要求）。

请先安装 JDK / JRE，任选其一：
  国内（推荐）腾讯 Kona JDK：
    https://kona.tencent.com/index.html#download
  国内  阿里 Dragonwell：
    https://dragonwell.aliyun.com/
  国际  Eclipse Temurin（非 Oracle）：
    https://adoptium.net/temurin/releases/?version=17

Ubuntu 也可：sudo apt install openjdk-17-jre  （或 openjdk-21-jre）
安装后重试，或设置 JAVA_HOME / JAVA17_BIN 指向 java 可执行文件。
EOF
}

# 依次尝试：JAVA17_BIN → JAVA_HOME → PATH → macOS java_home
find_java_bin() {
  if [[ -n "${JAVA17_BIN:-}" && -x "${JAVA17_BIN}" ]]; then
    echo "${JAVA17_BIN}"
    return 0
  fi
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    echo "${JAVA_HOME}/bin/java"
    return 0
  fi
  local path_java
  path_java="$(command -v java 2>/dev/null || true)"
  if [[ -n "${path_java}" ]]; then
    echo "${path_java}"
    return 0
  fi
  if [[ "$(uname -s)" == Darwin ]] && [[ -x /usr/libexec/java_home ]]; then
    local home
    home="$(/usr/libexec/java_home 2>/dev/null || true)"
    if [[ -n "${home}" && -x "${home}/bin/java" ]]; then
      echo "${home}/bin/java"
      return 0
    fi
  fi
  return 1
}

java_major_version() {
  local java_bin="$1"
  local ver_line major
  ver_line="$("${java_bin}" -version 2>&1 | head -1)"
  major="$(printf '%s\n' "${ver_line}" | sed -E 's/^.*version "([^"]+)".*$/\1/' | cut -d. -f1)"
  if [[ "${major}" == "1" ]]; then
    major="$(printf '%s\n' "${ver_line}" | sed -E 's/^.*version "([^"]+)".*$/\1/' | cut -d. -f2)"
  fi
  case "${major}" in
    ''|*[!0-9]*) major="0" ;;
  esac
  echo "${major}"
}
