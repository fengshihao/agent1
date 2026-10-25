#!/usr/bin/env bash
# 把 macOS 系统 HTTP/HTTPS 代理同步到当前 shell（git、curl、gh 等）。
# 浏览器走系统代理时，终端默认直连，访问 GitHub 常会超时。
# 禁用：AGENT1_NO_PROXY=1  已设置 https_proxy/HTTPS_PROXY 时不覆盖。
#
# JAVA_HOME：与 ~/.zprofile 一致，供 ./build-android-agent.sh 等非交互脚本使用。

agent1_apply_java_env() {
  local jdk17="${HOME}/sdk/jdk-17"
  if [[ -d "${jdk17}" && -x "${jdk17}/bin/java" ]]; then
    export JAVA_HOME="${jdk17}"
    case ":${PATH}:" in
      *":${JAVA_HOME}/bin:"*) ;;
      *) export PATH="${JAVA_HOME}/bin:${PATH}" ;;
    esac
  fi
}

agent1_apply_proxy_env() {
  if [[ "${AGENT1_NO_PROXY:-}" == "1" ]]; then
    return 0
  fi
  if [[ -n "${https_proxy:-}" || -n "${HTTPS_PROXY:-}" ]]; then
    return 0
  fi

  local host="" port="" enabled="0"
  if [[ "$(uname -s)" == "Darwin" ]]; then
    enabled="$(scutil --proxy 2>/dev/null | awk -F': ' '/^  HTTPSEnable / {print $2; exit}')"
    host="$(scutil --proxy 2>/dev/null | awk -F': ' '/^  HTTPSProxy / {print $2; exit}')"
    port="$(scutil --proxy 2>/dev/null | awk -F': ' '/^  HTTPSPort / {print $2; exit}')"
  fi

  if [[ "${enabled}" != "1" || -z "${host}" || -z "${port}" ]]; then
    return 0
  fi

  local proxy_url="http://${host}:${port}"
  if ! curl -sS -o /dev/null --connect-timeout 2 -x "${proxy_url}" https://github.com 2>/dev/null; then
    return 0
  fi

  export http_proxy="${proxy_url}"
  export https_proxy="${proxy_url}"
  export HTTP_PROXY="${proxy_url}"
  export HTTPS_PROXY="${proxy_url}"
  export all_proxy="socks5://${host}:${port}"
  export ALL_PROXY="socks5://${host}:${port}"
}

agent1_apply_java_env
agent1_apply_proxy_env
