#!/usr/bin/env bash
# 复制为本地脚本并填入 Key： cp scripts/e2e-deepseek-env.example.sh /tmp/my-e2e-env.sh && chmod +x /tmp/my-e2e-env.sh
# 勿 commit 含真实 Key 的文件。

set -euo pipefail

export OPENAI_API_KEY="${OPENAI_API_KEY:?set OPENAI_API_KEY in environment}"
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.deepseek.com}"
export OPENAI_MODEL="${OPENAI_MODEL:-deepseek-flash}"

export AGENT1_AGENT_ROOT="${AGENT1_AGENT_ROOT:-/tmp/agent1-e2e-$$}"
export AGENT1_COACH="${AGENT1_COACH:-1}"
export AGENT1_MAX_TURNS_PER_RUN="${AGENT1_MAX_TURNS_PER_RUN:-4}"
export AGENT1_MAX_TOOL_CALLS_PER_RUN="${AGENT1_MAX_TOOL_CALLS_PER_RUN:-8}"
export AGENT1_MAX_CONTEXT_TURNS="${AGENT1_MAX_CONTEXT_TURNS:-6}"

echo "AGENT1_AGENT_ROOT=$AGENT1_AGENT_ROOT"
echo "OPENAI_BASE_URL=$OPENAI_BASE_URL OPENAI_MODEL=$OPENAI_MODEL"
echo "limits: turns=$AGENT1_MAX_TURNS_PER_RUN tools=$AGENT1_MAX_TOOL_CALLS_PER_RUN contextTurns=$AGENT1_MAX_CONTEXT_TURNS"
