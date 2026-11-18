#!/usr/bin/env python3
"""
The widget picker's pictures for launchers before Android 12 (0.23): converts the renders WidgetGalleryTest writes to
app/build/widgets/previews into app/src/main/res/drawable-nodpi/widget_preview_*.webp. Run from the repository root
after the test:

    python3 tools/widget/previews.py
"""
import os
from PIL import Image

SRC = os.path.join("app", "build", "widgets", "previews")
DST = os.path.join("app", "src", "main", "res", "drawable-nodpi")
for f in sorted(os.listdir(SRC)):
    if not f.endswith(".png"):
        continue
    im = Image.open(os.path.join(SRC, f)).convert("RGBA")
    # About twice the size the picker shows (the renders are at 3x).
    im = im.resize((im.width * 2 // 3, im.height * 2 // 3), Image.LANCZOS)
    out = os.path.join(DST, f[:-4] + ".webp")
    im.save(out, "WEBP", quality=88, method=6)
    print(out, im.size, os.path.getsize(out))
