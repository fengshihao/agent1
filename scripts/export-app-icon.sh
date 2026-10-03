#!/usr/bin/env bash
# 从 android_agent ic_launcher 矢量几何导出 PNG（与 App 图标一致）。
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${REPO_ROOT}"

python3 << PY
from pathlib import Path

REPO_ROOT = Path("${REPO_ROOT}")

try:
    from PIL import Image, ImageDraw
except ImportError:
    raise SystemExit("需要 Pillow：pip install pillow")

def render(size: int) -> Image.Image:
    s = size / 108.0
    img = Image.new("RGBA", (size, size), (0x1C, 0x19, 0x14, 255))
    d = ImageDraw.Draw(img)
    cx, cy = size / 2, size / 2
    d.ellipse((cx - 30 * s, cy - 30 * s, cx + 30 * s, cy + 30 * s), fill=(0xF4, 0xEF, 0xE6, 255))
    d.ellipse((cx - 17 * s, cy - 17 * s, cx + 17 * s, cy + 17 * s), fill=(0xD4, 0x77, 0x3B, 255))
    d.rounded_rectangle((44.5 * s, 47 * s, 63.5 * s, 52 * s), radius=2.5 * s, fill=(0xFF, 0xF8, 0xF1, 255))
    d.rounded_rectangle((44.5 * s, 56 * s, 54.5 * s, 61 * s), radius=2.5 * s, fill=(0xFF, 0xF8, 0xF1, 255))
    return img

targets = [
    ("docs/assets/app-icon.png", 512),
    ("docs/assets/app-icon-192.png", 192),
    ("site/assets/icon.png", 512),
    ("site/assets/favicon.png", 192),
    ("site/assets/apple-touch-icon.png", 180),
]
for rel, sz in targets:
    path = Path(REPO_ROOT) / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    render(sz).save(path, "PNG")
    print(f"wrote {rel} ({sz}px)")
PY

echo "源矢量：android_agent/app/src/main/res/drawable/ic_launcher_foreground.xml"
