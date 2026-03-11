#!/bin/sh
set -eu

AGENT1_GIT_URL="${AGENT1_GIT_URL:-git+https://github.com/fengshihao/agent1.git}"

log() {
  printf '%s\n' "$*"
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

ensure_uv() {
  if has_cmd uv; then
    command -v uv
    return 0
  fi

  log "uv 未检测到，正在安装 uv..."
  if has_cmd curl; then
    curl -LsSf https://astral.sh/uv/install.sh | sh
  elif has_cmd wget; then
    wget -qO- https://astral.sh/uv/install.sh | sh
  else
    log "错误：未找到 curl 或 wget，无法自动安装 uv。"
    exit 1
  fi

  if has_cmd uv; then
    command -v uv
    return 0
  fi

  if [ -x "$HOME/.local/bin/uv" ]; then
    printf '%s\n' "$HOME/.local/bin/uv"
    return 0
  fi

  if [ -x "$HOME/.cargo/bin/uv" ]; then
    printf '%s\n' "$HOME/.cargo/bin/uv"
    return 0
  fi

  log "错误：uv 安装后仍不可用，请重开终端后重试。"
  exit 1
}

main() {
  log "安装 agent1（来源：${AGENT1_GIT_URL}）..."
  UV_BIN="$(ensure_uv)"
  if "$UV_BIN" tool install --force --from "$AGENT1_GIT_URL" agent1; then
    :
  else
    log "检测到当前索引源安装失败，正在回退到官方 PyPI 重试..."
    "$UV_BIN" tool install --force --default-index https://pypi.org/simple --from "$AGENT1_GIT_URL" agent1
  fi

  log ""
  log "安装完成。"
  log "如果当前终端找不到 agent1，请重开终端后再执行：agent1 --help"
  log "运行前请先配置模型密钥："
  log "  export DASHSCOPE_API_KEY=\"<your-api-key>\""
}

main "$@"
