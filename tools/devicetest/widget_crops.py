#!/usr/bin/env python3
"""Cuts each widget out of the screens tools/devicetest/widgets.sh photographed.

    python3 tools/devicetest/widget_crops.py <widgets.sh output dir>

For every <name>.png with its <name>.txt (the host's lines), each BOUNDS line gives a widget's place on the screen in
pixels; the widget is saved as crops/<Provider>_<W>x<H>/<name>.png, so one folder holds one widget at one size in every
language, theme and background. Also writes crops/<Provider>_<W>x<H>.png, those pictures side by side with their names.
Needs Pillow.
"""
import os
import re
import sys

from PIL import Image, ImageDraw

out = sys.argv[1]
crops = os.path.join(out, 'crops')
groups = {}
for f in sorted(os.listdir(out)):
    if not f.endswith('.txt') or f in ('steps.txt', 'logcat.txt', 'widgets_log.txt'):
        continue
    name = f[:-4]
    png = os.path.join(out, name + '.png')
    if not os.path.exists(png):
        continue
    shot = Image.open(png).convert('RGB')
    for line in open(os.path.join(out, f), encoding='utf-8', errors='replace'):
        m = re.search(r'BOUNDS id=\d+ provider=(\S+) size=(\S+) x=(-?\d+) y=(-?\d+) w=(\d+) h=(\d+)', line)
        if not m:
            continue
        provider, size = m.group(1), m.group(2)
        x, y, w, h = (int(m.group(i)) for i in range(3, 7))
        if w <= 0 or h <= 0:
            continue
        key = f'{provider}_{size}'
        os.makedirs(os.path.join(crops, key), exist_ok=True)
        pic = shot.crop((x, y, x + w, y + h))
        pic.save(os.path.join(crops, key, name + '.png'))
        groups.setdefault(key, []).append((name, pic))

# One sheet per widget and size: every language, theme and background, labelled.
for key, pics in groups.items():
    cols = 2
    w = max(p.width for _, p in pics); h = max(p.height for _, p in pics)
    rows = (len(pics) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * (w + 16) + 16, rows * (h + 40) + 16), (40, 40, 40))
    d = ImageDraw.Draw(sheet)
    for i, (name, p) in enumerate(pics):
        cx = 16 + (i % cols) * (w + 16); cy = 16 + (i // cols) * (h + 40)
        d.text((cx, cy), name, fill=(230, 230, 230))
        sheet.paste(p, (cx, cy + 20))
    sheet.save(os.path.join(crops, key + '.png'))
    print(f'{key}: {len(pics)} pictures')
