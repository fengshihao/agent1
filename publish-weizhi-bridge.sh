#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# 将 weizhi-bridge（及传递依赖 java-agent-core）发布到：
#   - java_agent/build/local-maven
#   - ~/.m2（mavenLocal）
# 供 agent1-skills 等内容仓用成品包跑测试，无需 includeBuild 源码联编。
# JAR 不含 libweizhijni；运行时仍需 weizhi native（AGENT1_WEIZHI_REPO / java.library.path）。
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
cd "${REPO_ROOT}"

if [[ ! -d "${REPO_ROOT}/weizhi/java" ]] && [[ ! -d "${REPO_ROOT}/../weizhi/java" ]]; then
  echo "==> 未找到 weizhi Java 源码，先 sync"
  ./sync-weizhi.sh
fi

echo "[java-agent] 发布 weizhi-bridge + java-agent-core ..."
./java_agent/gradlew --no-daemon -p java_agent publishWeizhiBridgeToLocalRepo

echo "完成：com.agent1:weizhi-bridge:0.1.0-SNAPSHOT"
echo "  ${REPO_ROOT}/java_agent/build/local-maven"
echo "  ~/.m2/repository/com/agent1/weizhi-bridge/"
