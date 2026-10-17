#!/usr/bin/env bash
# 本地 Gradle 默认只把已有 libweizhijni.so 拷进 APK（CI 才会先跑 build-android.sh）。
# Java 已声明 nativeSetPackageRoot、so 仍导出旧的 nativeSetScriptFolder 时，
# run_js → setPackageRoot 会抛 UnsatisfiedLinkError。
#
# 缺少符号，或 jni/src/java/include 新于 so 时，重编 arm64-v8a。已对齐则立即退出。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SYMBOL="Java_com_weizhi_WeizhiEngine_nativeSetPackageRoot"

resolve_weizhi_dir() {
  local dir="${WEIZHI_DIR:-${REPO_ROOT}/weizhi}"
  if [[ ! -d "${dir}/android" ]] && [[ -d "${REPO_ROOT}/../weizhi/android" ]]; then
    dir="${REPO_ROOT}/../weizhi"
  fi
  printf '%s\n' "${dir}"
}

WEIZHI="$(resolve_weizhi_dir)"
if [[ ! -d "${WEIZHI}/android" ]] || [[ ! -f "${WEIZHI}/scripts/build-android.sh" ]]; then
  echo "ensure-weizhi-android-jni: 未找到 weizhi 源码，跳过 native 重编"
  exit 0
fi

SO="${WEIZHI}/android/weizhi/src/main/jniLibs/arm64-v8a/libweizhijni.so"

has_symbol() {
  local so="$1"
  [[ -f "${so}" ]] || return 1
  if ! command -v nm >/dev/null 2>&1; then
    return 0
  fi
  # 不用 `nm | grep -q`：脚本开了 pipefail，GNU grep -q 命中即退出会让
  # nm 收到 SIGPIPE（141），把「已导出」误判成「未导出」。先完整捕获再判子串。
  local syms
  syms="$(nm -D --defined-only "${so}" 2>/dev/null)" || syms=""
  [[ "${syms}" == *"${SYMBOL}"* ]]
}

sources_newer() {
  local so="$1"
  [[ -f "${so}" ]] || return 0
  if [[ "${WEIZHI}/CMakeLists.txt" -nt "${so}" ]]; then
    return 0
  fi
  local newer
  newer="$(
    find "${WEIZHI}/jni" "${WEIZHI}/src" "${WEIZHI}/java" "${WEIZHI}/include" \
      -type f -newer "${so}" -print 2>/dev/null | head -n 1 || true
  )"
  [[ -n "${newer}" ]]
}

reason=""
if [[ ! -f "${SO}" ]]; then
  reason="缺少 ${SO}"
elif ! has_symbol "${SO}"; then
  reason="so 未导出 ${SYMBOL}（Java 已改调用，打包的仍是旧库）"
elif sources_newer "${SO}"; then
  reason="JNI/Java 源码新于 libweizhijni.so"
fi

if [[ -z "${reason}" ]]; then
  echo "ensure-weizhi-android-jni: libweizhijni.so 已包含 ${SYMBOL}"
  exit 0
fi

echo "ensure-weizhi-android-jni: ${reason}"
echo "ensure-weizhi-android-jni: ${WEIZHI}/scripts/build-android.sh arm64-v8a"
(
  cd "${WEIZHI}"
  ./scripts/build-android.sh arm64-v8a
)

if ! has_symbol "${SO}"; then
  echo "ensure-weizhi-android-jni: 重编后仍没有 ${SYMBOL}：${SO}" >&2
  if command -v nm >/dev/null 2>&1; then
    echo "  nm: $(nm --version 2>/dev/null | head -1)；dynsym 前 3 行：" >&2
    nm -D --defined-only "${SO}" 2>&1 | head -3 | sed 's/^/    /' >&2 || true
  fi
  exit 1
fi
echo "ensure-weizhi-android-jni: 已更新 ${SO}"
