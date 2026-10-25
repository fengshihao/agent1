#!/usr/bin/env bash
# 墨览本地预览：用 molan studio 在浏览器中预览任意 Markdown 文件。
#
# 用法:
#   ./scripts/molan-preview.sh <markdown 文件> [更多 markdown 文件...]
# 环境变量:
#   MOLAN_REPO   molan 仓库路径（默认 ../molan，即与本仓库同级）
#   MOLAN_PORT   本地服务端口（默认 5517，避免与 molan 默认 5500 冲突）
#
# 原理: 用临时目录软链组合 molan studio 静态资源与目标 md 所在目录（挂到 /doc/），
#       起 apps/studio/serve.mjs（MOLAN_ROOT 指向组合目录），再打开
#       http://127.0.0.1:$PORT/?file=/doc/<文件名>。
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "用法: $0 <markdown 文件> [更多 markdown 文件...]" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOLAN_REPO="${MOLAN_REPO:-$SCRIPT_DIR/../../molan}"
MOLAN_PORT="${MOLAN_PORT:-5517}"

STUDIO_DIR="$MOLAN_REPO/apps/studio"
SERVE="$STUDIO_DIR/serve.mjs"
if [[ ! -f "$SERVE" ]]; then
  echo "错误: 找不到 $SERVE，请先 clone molan（https://github.com/fengshihao/molan）" >&2
  echo "      或设置 MOLAN_REPO 指向 molan 仓库路径。" >&2
  exit 1
fi

# vendor/vditor 是 gitignore 的（从 vditor npm 包同步）；缺失时自动准备
if [[ ! -f "$STUDIO_DIR/vendor/vditor/dist/method.min.js" ]]; then
  echo "首次运行：正在准备 molan vendor（pnpm install + build + sync-media）…"
  (cd "$MOLAN_REPO" \
    && PUPPETEER_SKIP_DOWNLOAD=1 PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=1 pnpm install \
    && pnpm build \
    && node apps/vscode-molan/scripts/sync-media.mjs) >&2 || {
    echo "错误: vendor 准备失败。可手动执行：" >&2
    echo "  cd $MOLAN_REPO && pnpm install && pnpm build && node apps/vscode-molan/scripts/sync-media.mjs" >&2
    exit 1
  }
fi

# 绝对路径 + 检查存在
MD_FILES=()
for f in "$@"; do
  abs="$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"
  [[ -f "$abs" ]] || { echo "错误: 文件不存在: $f" >&2; exit 1; }
  MD_FILES+=("$abs")
done

# 所有文件须在同一目录（/doc/ 单挂载点）；跨目录时复制到临时目录
DOC_DIR="$(dirname "${MD_FILES[0]}")"
COPY_TMP=""
for f in "${MD_FILES[@]}"; do
  if [[ "$(dirname "$f")" != "$DOC_DIR" ]]; then COPY_TMP=1; break; fi
done
if [[ -n "$COPY_TMP" ]]; then
  DOC_DIR="$(mktemp -d)"
  for f in "${MD_FILES[@]}"; do cp "$f" "$DOC_DIR/"; done
fi

COMPOSE_DIR="$(mktemp -d)"
cleanup() {
  [[ -n "${SERVE_PID:-}" ]] && kill "$SERVE_PID" 2>/dev/null || true
  rm -rf "$COMPOSE_DIR"
  [[ -n "$COPY_TMP" ]] && rm -rf "$DOC_DIR"
  return 0
}
trap cleanup EXIT

# 服务已在跑就直接复用（不做软链组合）
if curl -sf -o /dev/null "http://127.0.0.1:$MOLAN_PORT/index.html" 2>/dev/null; then
  echo "墨览服务已在运行: http://127.0.0.1:$MOLAN_PORT/"
else
  # 组合目录: 软链 studio 静态资源 + doc 挂载点
  (cd "$STUDIO_DIR" && find . -maxdepth 1 -mindepth 1 ! -name 'demo' ! -name 'serve.mjs' \
    -exec ln -s "$STUDIO_DIR/{}" "$COMPOSE_DIR/{}" \;)
  ln -s "$DOC_DIR" "$COMPOSE_DIR/doc"
  MOLAN_ROOT="$COMPOSE_DIR" MOLAN_SERVE_PORT="$MOLAN_PORT" MOLAN_BIND="127.0.0.1" \
    node "$SERVE" >/tmp/molan-preview-serve.log 2>&1 &
  SERVE_PID=$!
  for _ in $(seq 1 30); do
    curl -sf -o /dev/null "http://127.0.0.1:$MOLAN_PORT/index.html" && break
    sleep 0.2
  done
  if ! curl -sf -o /dev/null "http://127.0.0.1:$MOLAN_PORT/index.html"; then
    echo "错误: 本地服务启动失败，日志见 /tmp/molan-preview-serve.log" >&2
    exit 1
  fi
  echo "墨览服务已启动: http://127.0.0.1:$MOLAN_PORT/ (pid ${SERVE_PID}，退出本脚本即停止)"
fi

URL="http://127.0.0.1:$MOLAN_PORT/?file=/doc/$(basename "${MD_FILES[0]}")"
echo "预览: $URL"
open "$URL"

# 前台等待，Ctrl+C 退出并停服务（服务由本脚本持有才等待；复用模式直接返回）
if [[ -n "${SERVE_PID:-}" ]]; then
  echo "按 Ctrl+C 停止本地服务"
  wait "$SERVE_PID"
fi
