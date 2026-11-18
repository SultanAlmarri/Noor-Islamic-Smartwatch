#!/usr/bin/env python3
"""
Contact sheets of the widgets' renders (0.23): python3 tools/widget/sheet.py <renders dir> <out dir> [filter ...]

Lays the PNGs written by WidgetGalleryTest (app/build/widgets) out on sheets, one per widget kind (and per filter
word given), each picture at its own size with its name under it, for review.
"""
import os
import sys
from PIL import Image, ImageDraw


def sheet(files, src, dst, title, width=1800, scale=0.5):
    pics = []
    for f in files:
        im = Image.open(os.path.join(src, f)).convert("RGB")
        im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
        pics.append((f[:-4], im))
    pad, label = 16, 18
    rows, row, x, h = [], [], pad, 0
    for name, im in pics:
        if x + im.width + pad > width and row:
            rows.append((row, h)); row, x, h = [], pad, 0
        row.append((name, im)); x += im.width + pad; h = max(h, im.height)
    if row:
        rows.append((row, h))
    total = pad + sum(h + label + pad for _, h in rows) + 30
    out = Image.new("RGB", (width, total), (24, 26, 32))
    d = ImageDraw.Draw(out)
    d.text((pad, 8), title, fill=(230, 230, 230))
    y = 30
    for row, h in rows:
        x = pad
        for name, im in row:
            out.paste(im, (x, y))
            d.text((x, y + im.height + 3), name, fill=(170, 175, 185))
            x += im.width + pad
        y += h + label + pad
    out.save(os.path.join(dst, title + ".png"))
    return os.path.join(dst, title + ".png")


def main():
    src, dst = sys.argv[1], sys.argv[2]
    words = sys.argv[3:]
    os.makedirs(dst, exist_ok=True)
    files = sorted(f for f in os.listdir(src) if f.endswith(".png"))
    groups = {}
    for f in files:
        kind = f.split("_")[0]
        if words and not any(w in f for w in words):
            continue
        groups.setdefault(kind, []).append(f)
    for kind, fs in groups.items():
        print(sheet(fs, src, dst, kind + ("_" + "_".join(words) if words else "")))


if __name__ == "__main__":
    main()
