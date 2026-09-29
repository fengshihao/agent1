#!/usr/bin/env bash
# 复制到 .git/hooks/pre-push 并 chmod +x，在 git push 前跑与 CI 接近的 fast 门禁。
#
#   cp scripts/ci-local-pre-push-hook.example.sh .git/hooks/pre-push
#   chmod +x .git/hooks/pre-push
#
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"
exec "${ROOT}/scripts/ci-local.sh" fast
