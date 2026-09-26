#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
exec python3 "${REPO_ROOT}/android_agent/scripts/check_android_main_thread_gateway.py" "$@"
