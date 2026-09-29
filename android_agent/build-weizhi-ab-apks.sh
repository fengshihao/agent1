#!/usr/bin/env bash
# 打两个可并存的主包，对比 weizhi 旧 JNI 与新 JNI（AAR Java 与 libweizhijni.so 必须同一次编译）。
#
# 旧：D4（runJs 文件名栈，改了 JNI）之前的 bcbb1f4
# 新：weizhi 当前 master
#
# 安装后桌面会出现两个图标（包名 com.agent1.android.wzold / .wznew）。
# 打开后先点「检查 Weizhi AAR/SO」，看 stamp 里 java_and_so_same_build，再点加载按钮。
# 若「初始化 WeizhiEngine」后进程直接消失、屏幕上没有异常文字，就是 native 不配套，不是普通 Java catch 能接住的。
#
# 依赖：仓库内或上一级已有 weizhi 克隆、Android NDK、JDK 17。
# 用法（仓库根或 android_agent 下均可）：
#   ./android_agent/build-weizhi-ab-apks.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
OLD_REF="${WEIZHI_OLD_REF:-bcbb1f48e7485e55d26f356783ab6d8fff3e4499}"
NEW_REF="${WEIZHI_NEW_REF:-master}"

if [[ -d "$REPO_ROOT/weizhi/android" ]]; then
  WEIZHI_DIR="$REPO_ROOT/weizhi"
elif [[ -d "$REPO_ROOT/../weizhi/android" ]]; then
  WEIZHI_DIR="$(cd "$REPO_ROOT/../weizhi" && pwd)"
else
  echo "未找到 weizhi。先在仓库根执行 ./sync-weizhi.sh" >&2
  exit 1
fi

OUT_DIR="$ROOT_DIR/build/weizhi-ab"
mkdir -p "$OUT_DIR"
PREV="$(git -C "$WEIZHI_DIR" rev-parse HEAD)"
restore() {
  git -C "$WEIZHI_DIR" checkout --detach "$PREV" >/dev/null 2>&1 || git -C "$WEIZHI_DIR" checkout "$PREV" || true
}
trap restore EXIT

build_one() {
  local label="$1"
  local ref="$2"
  echo "==> weizhi checkout $label ($ref)"
  git -C "$WEIZHI_DIR" fetch --depth 1 origin "$ref" || true
  git -C "$WEIZHI_DIR" checkout --detach "$ref"
  if [[ -x "$WEIZHI_DIR/scripts/build-android.sh" ]]; then
    echo "==> build libweizhijni.so ($label)"
    (cd "$WEIZHI_DIR" && ./scripts/build-android.sh arm64-v8a)
  fi
  echo "==> assemble app debug label=$label"
  (cd "$ROOT_DIR" && ./gradlew :app:assembleAppDebug -PweizhiProbeLabel="$label")
  local apk="$ROOT_DIR/app/build/outputs/apk/app/debug/agent1-android-app-debug.apk"
  cp "$apk" "$OUT_DIR/agent1-weizhi-${label}.apk"
  echo "    $OUT_DIR/agent1-weizhi-${label}.apk"
}

build_one old "$OLD_REF"
build_one new "$NEW_REF"

echo "==> 完成。两个包可同时安装："
echo "    adb install -r $OUT_DIR/agent1-weizhi-old.apk"
echo "    adb install -r $OUT_DIR/agent1-weizhi-new.apk"
