#!/usr/bin/env bash
# 从 agent1-skills 同步桌面 classpath 预装的 Weizhi Package 到 agent-home/weizhi-packages。
# Package 真源请在 agent1-skills/packages-src 编辑；云端-only 包不同步（靠 registry + 单测读 packages-src）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SKILLS_REPO="$(cd "${AGENT1_SKILLS_REPO:-${ROOT}/../agent1-skills}" 2>/dev/null && pwd -P)" || SKILLS_REPO="${AGENT1_SKILLS_REPO:-${ROOT}/agent1-skills}"
SRC_ROOT="${AGENT1_SKILLS_PACKAGES_ROOT:-${SKILLS_REPO}/packages-src}"
DEST="${ROOT}/agent_core/src/main/resources/agent-home/weizhi-packages"

if [[ ! -d "${SRC_ROOT}" ]]; then
  echo "找不到 Package 源目录: ${SRC_ROOT}" >&2
  echo "请 clone https://github.com/fengshihao/agent1-skills（同级或 ${ROOT}/agent1-skills），或设置 AGENT1_SKILLS_PACKAGES_ROOT" >&2
  exit 1
fi

# 与 WeizhiBundledPackages.BUNDLED_PACKAGE_NAMES 对齐；云端-only 见 WeizhiSourcePackages.CLOUD_ONLY_PACKAGE_NAMES
BUNDLED_PACKAGES="${BUNDLED_WEIZHI_PACKAGES:-docx docx-raw docx-build pptx pptx-build svg-raster html2pptx image audio}"

mkdir -p "${DEST}"
for name in ${BUNDLED_PACKAGES}; do
  src="${SRC_ROOT}/${name}"
  if [[ ! -d "${src}" ]]; then
    echo "缺少 bundled 包源: ${src}" >&2
    exit 1
  fi
  rm -rf "${DEST}/${name}"
  cp -R "${src}" "${DEST}/${name}"
done

# 移除已迁出的云端-only 副本（若存在）
for name in xlsx xlsx-build xlsx-raw; do
  rm -rf "${DEST}/${name}"
done

if [[ ! -f "${DEST}/README.md" ]]; then
  cat >"${DEST}/README.md" <<'EOF'
# weizhi-packages（classpath 副本）

**真源**：[agent1-skills/packages-src](https://github.com/fengshihao/agent1-skills/tree/main/packages-src)。

本目录仅保留桌面 **bundled** 子集（`WeizhiBundledPackages`），由 `./scripts/sync-weizhi-packages-bundled.sh` 从 agent1-skills 同步。云端-only（如 `xlsx`）不在此目录；单测与联调读 `packages-src`。

发布：`./scripts/pack-official-packages.sh` → `artifact-cdn/upload.sh`。
EOF
fi

echo "已同步 ${BUNDLED_PACKAGES} → ${DEST}"
echo "云端-only（xlsx*）未拷贝；真源见 ${SRC_ROOT}"
