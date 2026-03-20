#!/usr/bin/env zsh
set -euo pipefail

echo "== cwd =="
pwd
echo

echo "== top files =="
ls
echo

echo "== git status short =="
git status --short || true
echo

echo "== recent commits =="
git log --oneline -n 5 || true
