#!/usr/bin/env bash
# Cloud Agent install：JDK 17、公开 weizhi 源码、Gradle 依赖缓存。
# 不跑测试、不装 Android SDK。单测在会话内执行；Debug APK 仍以 GitHub Actions 为准。
#
# 用法：bash scripts/cloud-agent-install.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"

JDK17_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
WEIZHI_DIR="/weizhi"
WEIZHI_URL="${WEIZHI_GIT_URL:-https://github.com/fengshihao/weizhi.git}"

if [[ ! -x "${JDK17_HOME}/bin/java" ]]; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk
fi

if ! java -version 2>&1 | grep -q 'version "17\.'; then
  sudo update-java-alternatives -s java-1.17.0-openjdk-amd64
fi
sudo ln -sfn java-1.17.0-openjdk-amd64 /usr/lib/jvm/default-java

sudo tee /etc/profile.d/agent1-java17.sh >/dev/null <<EOF
export JAVA_HOME=${JDK17_HOME}
export PATH="\${JAVA_HOME}/bin:\${PATH}"
export AGENT1_WEIZHI_REPO=${WEIZHI_DIR}
EOF
sudo chmod 644 /etc/profile.d/agent1-java17.sh

export JAVA_HOME="${JDK17_HOME}"
export PATH="${JAVA_HOME}/bin:${PATH}"
export AGENT1_WEIZHI_REPO="${WEIZHI_DIR}"

chmod +x \
  java_agent/gradlew \
  agent1 \
  run-java-agent \
  run-java-agent-gradle \
  sync-weizhi.sh \
  scripts/cloud-agent-verify.sh \
  scripts/cloud-agent-install.sh \
  check-android-agent-layering.sh \
  check-android-agent-static.sh \
  check-java-agent-static.sh

if [[ ! -d "${WEIZHI_DIR}/android" ]]; then
  sudo mkdir -p "${WEIZHI_DIR}"
  sudo chown "$(id -u):$(id -g)" "${WEIZHI_DIR}"
  git clone --depth 1 "${WEIZHI_URL}" "${WEIZHI_DIR}"
elif [[ -d "${WEIZHI_DIR}/.git" ]]; then
  git -C "${WEIZHI_DIR}" pull --ff-only || echo "weizhi 已存在，pull 未快进，继续使用现有检出"
fi

# 仓库内相对路径（./weizhi、脚本里的 REPO/weizhi）指向快照外的检出，避免 git checkout 清掉依赖。
ln -sfn "${WEIZHI_DIR}" "${REPO_ROOT}/weizhi"

mkdir -p "${HOME}/.gradle"
GRADLE_PROPS="${HOME}/.gradle/gradle.properties"
touch "${GRADLE_PROPS}"
grep -q '^org.gradle.jvmargs=' "${GRADLE_PROPS}" || echo 'org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' >> "${GRADLE_PROPS}"
grep -q '^weizhiRepo=' "${GRADLE_PROPS}" || echo "weizhiRepo=${WEIZHI_DIR}" >> "${GRADLE_PROPS}"

# 解析依赖并编译 CLI（增量、可重复）。不跑测试，不打 Android 包。
./java_agent/gradlew --no-daemon -p java_agent :core:compileJava :cli:compileJava --quiet
echo "==> cloud-agent-install 完成：$(java -version 2>&1 | head -1)，weizhi=${WEIZHI_DIR}"
