#!/usr/bin/env bash
# 把 macOS 系统 HTTP/HTTPS 代理同步到当前 shell（git、curl、gh 等）。
# 浏览器走系统代理时，终端默认直连，访问 GitHub 常会超时。
# 禁用代理：AGENT1_NO_PROXY=1（会 unset 终端里的 http(s)_proxy，避免 Gradle 连死掉的 127.0.0.1:7890）。
# 若 shell 里已有 https_proxy 但代理未启动，会自动改直连并提示。
#
# JAVA_HOME：与 ~/.zprofile 一致，供 ./build-android-agent.sh 等非交互脚本使用。

agent1_clear_proxy_for_build() {
  unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY all_proxy ALL_PROXY
  export AGENT1_GRADLE_NEED_STOP=1
  local flags="-Djava.net.useSystemProxies=false -Dhttp.proxyHost= -Dhttps.proxyHost= -DsocksProxyHost="
  if [[ "${GRADLE_OPTS:-}" != *useSystemProxies=false* ]]; then
    if [[ -n "${GRADLE_OPTS:-}" ]]; then
      export GRADLE_OPTS="${GRADLE_OPTS} ${flags}"
    else
      export GRADLE_OPTS="${flags}"
    fi
  fi
}

agent1_effective_proxy_url() {
  if [[ -n "${https_proxy:-}" ]]; then
    echo "${https_proxy}"
    return 0
  fi
  if [[ -n "${HTTPS_PROXY:-}" ]]; then
    echo "${HTTPS_PROXY}"
    return 0
  fi
  if [[ -n "${http_proxy:-}" ]]; then
    echo "${http_proxy}"
    return 0
  fi
  if [[ -n "${HTTP_PROXY:-}" ]]; then
    echo "${HTTP_PROXY}"
    return 0
  fi
  return 1
}

agent1_proxy_health_ok() {
  local proxy_url="$1"
  curl -sS -o /dev/null --connect-timeout 2 -x "${proxy_url}" https://github.com 2>/dev/null
}

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
    agent1_clear_proxy_for_build
    return 0
  fi

  local existing=""
  if existing="$(agent1_effective_proxy_url 2>/dev/null)"; then
    if agent1_proxy_health_ok "${existing}"; then
      return 0
    fi
    echo "[agent1] 代理 ${existing} 不可用，Gradle 将直连（可启动 Clash，或 AGENT1_NO_PROXY=1）。" >&2
    agent1_clear_proxy_for_build
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
