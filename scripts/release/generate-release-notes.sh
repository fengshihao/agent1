#!/usr/bin/env bash
# 生成 GitHub Release 说明（stdout）。参数：VERSION_NAME TAG_NAME
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
cd "${REPO_ROOT}"

VERSION_NAME="${1:?VERSION_NAME}"
TAG_NAME="${2:?TAG_NAME}"
REASON="${3:-}"

LAST_TAG="$(git tag -l 'v0.1.*' --sort=-v:refname 2>/dev/null | head -n1 || true)"
RANGE="${LAST_TAG}..HEAD"
if [[ -z "${LAST_TAG}" ]]; then
  RANGE="HEAD"
fi

SHORT_SHA="$(git rev-parse --short HEAD)"
VERSION_CODE=$((10000 + "${VERSION_NAME#0.1.}"))

cat <<EOF
## ${VERSION_NAME}

自动发版（\`${TAG_NAME}\` → \`${SHORT_SHA}\`）。

EOF

if [[ -n "${REASON}" ]]; then
  echo "- **触发原因**：\`${REASON}\`"
  echo ""
fi

if [[ -n "${LAST_TAG}" ]]; then
  echo "自 [${LAST_TAG}](https://github.com/${GITHUB_REPOSITORY:-fengshihao/agent1}/releases/tag/${LAST_TAG}) 以来的提交："
else
  echo "包含提交："
fi

echo ""
echo '```'
git log --oneline --no-decorate "${RANGE}" 2>/dev/null | head -n 40 || true
echo '```'

if [[ "$(git rev-list --count "${RANGE}" 2>/dev/null || echo 0)" -gt 40 ]]; then
  echo ""
  echo "_（仅展示最近 40 条，完整历史见 Compare。）_"
fi

cat <<EOF

### 安装包

- 附带 **\`agent1-android-release.apk\`**（R8 Release，**不含** DashScope / Qwen API Key）
- APK **versionName** \`${VERSION_NAME}\`、**versionCode** \`${VERSION_CODE}\`（与 Git 标签一致）
- CI 使用共用 debug 签名；与本地 \`release.keystore\` 包签名不同，覆盖安装前可能需要先卸载旧包

### 其他

- 官网：[fengshihao.github.io/agent1](https://fengshihao.github.io/agent1/)
EOF
