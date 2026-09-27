#!/usr/bin/env bash
# REQ-090 / V6：自进化 Agent UC 子集回归（Mock + 集成测，无需 API Key）。
# 映射见 doc/规划/自进化Agent/e2e-runs/README.md
#
# 用法：
#   ./scripts/e2e-self-evolve-smoke.sh
#   AGENT1_WEIZHI_REPO=/path/to/weizhi ./scripts/e2e-self-evolve-smoke.sh
#
# 可选真实 LLM 冒烟（需 Key）：source scripts/e2e-deepseek-env.example.sh 后
#   ./agent1 "数据目录在哪？我能改 shared 吗？"   # UC-01

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"

WEIZHI="${AGENT1_WEIZHI_REPO:-${REPO_ROOT}/weizhi}"
export AGENT1_WEIZHI_REPO="${WEIZHI}"

if [[ ! -d "${WEIZHI}/android" ]]; then
  echo "==> weizhi 缺失，运行 sync-weizhi.sh"
  ./sync-weizhi.sh
fi

if [[ ! -f "${WEIZHI}/build/libweizhijni.so" ]] && [[ ! -f "${WEIZHI}/build/libweizhijni.dylib" ]]; then
  echo "==> 构建 weizhi native（首次较慢）"
  (cd "${WEIZHI}" && ./scripts/build.sh)
fi

GRADLE=(./java_agent/gradlew --no-daemon -p java_agent)

echo "==> :core 自进化 UC 子集"
"${GRADLE[@]}" :core:test \
  --tests 'com.agent1.javaagent.session.ProductivityScriptedCoachTest' \
  --tests 'com.agent1.javaagent.session.ProductivityScriptedCatalogTest' \
  --tests 'com.agent1.javaagent.session.ProductivityScriptedPromoteTest' \
  --tests 'com.agent1.javaagent.session.ProductivityScriptedSkillTest' \
  --tests 'com.agent1.javaagent.session.ProductivityScriptedCatalogSkillTest' \
  --tests 'com.agent1.javaagent.session.ProductivityScriptedReadWriteTest' \
  --tests 'com.agent1.javaagent.skill.AgentSkillLoaderTest' \
  --tests 'com.agent1.javaagent.catalog.sync.CatalogSyncServiceTest' \
  --tests 'com.agent1.javaagent.catalog.sync.CatalogSyncServiceNativeTest' \
  --tests 'com.agent1.javaagent.log.AgentAuditEventsTest' \
  --tests 'com.agent1.javaagent.tool.agent.CatalogSyncToolsTest' \
  --tests 'com.agent1.javaagent.promote.PromotionServiceTest'

echo "==> :weizhi-bridge UC-04 / UC-07 / 7.2"
"${GRADLE[@]}" :weizhi-bridge:test \
  --tests 'com.agent1.javaagent.weizhi.ProductivityScriptedScriptLineTest' \
  --tests 'com.agent1.javaagent.weizhi.ProductivityScriptedNativeCatalogTest' \
  --tests 'com.agent1.javaagent.weizhi.WeizhiNativeCatalogIntegrationTest' \
  --tests 'com.agent1.javaagent.weizhi.WeizhiCatalogScriptFolderIntegrationTest' \
  --tests 'com.agent1.javaagent.weizhi.WeizhiScriptEngineIntegrationTest.syntaxErrorReportsUserLineInFileMode'

echo "==> :cli"
"${GRADLE[@]}" :cli:test

echo ""
echo "e2e-self-evolve-smoke: PASS（Mock/集成 UC 子集）"
echo "真实 LLM：见 doc/规划/自进化Agent/e2e-runs/ 与 scripts/e2e-deepseek-env.example.sh"
