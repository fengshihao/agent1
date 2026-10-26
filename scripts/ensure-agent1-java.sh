#!/usr/bin/env bash
# 解析 Agent1 构建用的 JDK 17（Gradle toolchain / JAVA_HOME）。
# 用法：source scripts/ensure-agent1-java.sh   或   ./scripts/ensure-agent1-java.sh
set -euo pipefail

_ensure_agent1_java_repo_root() {
  if [[ -n "${REPO_ROOT:-}" ]]; then
    echo "${REPO_ROOT}"
    return
  fi
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
  echo "$(cd "${script_dir}/.." && pwd -P)"
}

# 供其它脚本 source；成功时 export JAVA_HOME/PATH，失败返回 1（不 exit）。
ensure_agent1_java() {
  if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "${JAVA_HOME}/bin/java" ]]; then
    if "${JAVA_HOME}/bin/java" -version 2>&1 | grep -qE 'version "17\.'; then
      return 0
    fi
  fi

  local root
  root="$(_ensure_agent1_java_repo_root)"
  local candidates=()
  if [[ -n "${AGENT1_JDK17_HOME:-}" ]]; then
    candidates+=("${AGENT1_JDK17_HOME}")
  fi
  candidates+=(
    "${HOME}/sdk/jdk-17"
    "${root}/../sdk/jdk-17"
    "/usr/lib/jvm/java-17-openjdk-amd64"
    "/usr/lib/jvm/java-17-openjdk"
  )

  local c resolved
  for c in "${candidates[@]}"; do
    [[ -z "${c}" ]] && continue
    if [[ ! -d "${c}" ]]; then
      continue
    fi
    resolved="$(cd "${c}" && pwd -P)"
    if [[ -x "${resolved}/bin/java" ]] \
      && "${resolved}/bin/java" -version 2>&1 | grep -qE 'version "17\.'; then
      export JAVA_HOME="${resolved}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      return 0
    fi
  done
  return 1
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  REPO_ROOT="$(_ensure_agent1_java_repo_root)"
  if ensure_agent1_java; then
    echo "JAVA_HOME=${JAVA_HOME}"
    java -version
  else
    echo "未找到 JDK 17。可选：" >&2
    echo "  1) macOS: 将 Temurin 17 解压到 ~/sdk/jdk-17（与 jdk-21 同级）" >&2
    echo "  2) Ubuntu: sudo apt install openjdk-17-jdk（Cloud VM 见 scripts/cloud-agent-install.sh）" >&2
    echo "  3) 依赖 Gradle 自动下载: org.gradle.java.installations.auto-download=true + foojay（java_agent/settings.gradle.kts）" >&2
    exit 1
  fi
fi
