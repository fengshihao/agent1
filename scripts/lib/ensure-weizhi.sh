# shellcheck shell=bash
# 供 DeepSeek / V6 E2E 使用：确保 weizhi 源码与 libweizhijni 已构建。
ensure_weizhi_for_agent1() {
  local repo_root="${1:?repo root}"
  local weizhi="${AGENT1_WEIZHI_REPO:-${repo_root}/weizhi}"
  if [[ ! -d "${weizhi}/android" ]]; then
    (cd "${repo_root}" && ./sync-weizhi.sh)
  fi
  if [[ ! -f "${weizhi}/build/libweizhijni.so" ]] && [[ ! -f "${weizhi}/build/libweizhijni.dylib" ]]; then
    (cd "${weizhi}" && ./scripts/build.sh)
  fi
  export AGENT1_WEIZHI_REPO="${weizhi}"
}
