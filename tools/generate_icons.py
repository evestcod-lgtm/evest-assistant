#!/usr/bin/env python3
"""
Generates Android launcher icons (all mipmap densities, square + round)
from a single source PNG.

Usage:
    python3 tools/generate_icons.py icon/icon.png
"""
import sys
import os
from PIL import Image, ImageDraw, ImageOps

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

RES_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")


def make_round(img: Image.Image) -> Image.Image:
    size = img.size
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, size[0], size[1]], fill=255)
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 generate_icons.py <path-to-icon.png>")
        sys.exit(1)

    src_path = sys.argv[1]
    if not os.path.exists(src_path):
        print(f"File not found: {src_path}")
        sys.exit(1)

    src = Image.open(src_path).convert("RGBA")
    src = ImageOps.fit(src, (512, 512), Image.LANCZOS)

    for folder, size in SIZES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)

        square = src.resize((size, size), Image.LANCZOS)
        square.save(os.path.join(out_dir, "ic_launcher.png"))

        round_icon = make_round(square)
        round_icon.save(os.path.join(out_dir, "ic_launcher_round.png"))

        print(f"Wrote {folder}/ic_launcher.png and ic_launcher_round.png ({size}x{size})")

    print("Done. Icons updated from", src_path)


if __name__ == "__main__":
    main()
