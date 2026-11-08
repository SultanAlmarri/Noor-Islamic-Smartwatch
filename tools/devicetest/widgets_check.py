"""Checks the widgets' countdown in the pictures taken by tools/devicetest/widgets.sh (0.9.12).

For each widget whose countdown the host logged (NoorWidgetHost: where its minutes picture and its seconds window are
on screen): in pictures taken in different seconds the seconds window must differ (Android turns the wheel by itself
every second); within one minute the minutes picture must not change, and across the start of a minute it must.
Writes countdowns_<series>.png (the countdowns of each series, one row per picture) for a person to look at.

Usage: python3 tools/devicetest/widgets_check.py <output dir>
"""
import glob
import os
import re
import sys

from PIL import Image, ImageChops, ImageStat

out = sys.argv[1]
places = {}
for line in open(os.path.join(out, "widget_log.txt"), encoding="utf-8", errors="replace"):
    m = re.search(r"WIDGET (\d+) (\w+) main=(\S+) window=(\S+) plain=(\S+)", line)
    if m:
        def box(s):
            return tuple(int(v) for v in s.split(",")) if "," in s else None
        places[m.group(1) + " " + m.group(2)] = (box(m.group(3)), box(m.group(4)))
print("countdowns found:", {k: v for k, v in places.items()})
if not places:
    print("FAIL: the host logged no countdowns")
    sys.exit(0)


def series(name):
    shots = []
    for f in glob.glob(os.path.join(out, f"{name}_*_t*.png")):
        m = re.search(r"_(\d+)_t(\d+)\.png$", f)
        shots.append((int(m.group(1)), int(m.group(2)), f))
    return [(t, f) for _, t, f in sorted(shots)]


def crop(img, b):
    x, y, w, h = b
    return img.crop((x, y, x + w, y + h)).convert("RGB")


def diff(a, b):
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3


ok = True
for name in ("en_western_on_arabic_phone", "minute", "ar_arabic_digits", "ar_western_digits", "en_western_again"):
    shots = series(name)
    if len(shots) < 2:
        print(f"{name}: {len(shots)} pictures")
        continue
    imgs = [(t, Image.open(f)) for t, f in shots]
    rows = []
    for tag, (main, window) in sorted(places.items()):
        if main is None or window is None:
            print(f"{name} {tag}: no seconds window (main={main}, window={window})")
            continue
        ticks = same = 0; minute_ok = None; minute_steady = True
        for (t0, a), (t1, b) in zip(imgs, imgs[1:]):
            wd = diff(crop(a, window), crop(b, window))
            md = diff(crop(a, main), crop(b, main))
            if t1 != t0:
                if wd > 1.0: ticks += 1
                else: same += 1
            if t1 // 60 != t0 // 60:
                minute_ok = md > 1.0 if minute_ok is None else (minute_ok and md > 1.0)
            elif md > 1.0:
                minute_steady = False
        verdict = "ok" if ticks > 0 and same <= 1 else "FAIL"
        if verdict == "FAIL": ok = False
        line = f"{name} {tag}: seconds window changed in {ticks} of {ticks + same} pairs taken in different seconds: SECONDS TICK {verdict}"
        if minute_ok is not None:
            line += f"; across the minute the minutes changed: MINUTE CHANGE {'ok' if minute_ok else 'FAIL'}"
            if not minute_ok: ok = False
        line += f"; within a minute the minutes held: {'ok' if minute_steady else 'FAIL'}"
        if not minute_steady: ok = False
        print(line)
        x0 = main[0]; y0 = min(main[1], window[1]); x1 = window[0] + window[2]; y1 = max(main[1] + main[3], window[1] + window[3])
        rows.append([img.crop((x0 - 10, y0 - 6, x1 + 10, y1 + 6)) for _, img in imgs])
    if rows:
        sheet = Image.new("RGB", (max(c.width for r in rows for c in r) * len(rows), max(c.height for r in rows for c in r) * len(imgs)), (20, 20, 20))
        cw = max(c.width for r in rows for c in r); ch = max(c.height for r in rows for c in r)
        for j, r in enumerate(rows):
            for i, c in enumerate(r):
                sheet.paste(c, (j * cw, i * ch))
        sheet.save(os.path.join(out, f"countdowns_{name}.png"))
print("ALL CHECKS", "ok" if ok else "FAIL")
