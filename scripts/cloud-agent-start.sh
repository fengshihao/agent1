#!/usr/bin/env bash
# 每次 Cloud Agent 启动时确认 JDK 17 与 weizhi 就绪。无常驻服务。
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="${JAVA_HOME}/bin:${PATH}"
export AGENT1_WEIZHI_REPO="${AGENT1_WEIZHI_REPO:-/weizhi}"

java -version
test -d "${AGENT1_WEIZHI_REPO}/android"
echo "agent1 ready: JAVA_HOME=${JAVA_HOME} AGENT1_WEIZHI_REPO=${AGENT1_WEIZHI_REPO}"
