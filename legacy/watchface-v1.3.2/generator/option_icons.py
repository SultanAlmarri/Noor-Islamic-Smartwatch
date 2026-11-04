"""Labelled option pictures for the watch-face editor.

Samsung's Galaxy Wearable editor shows each ListOption as a circle containing
the option's `icon` drawable (the text name alone leaves the circle blank), so
every option gets a small PNG with its label - font options show a sample of
the font itself.
"""
import os
from PIL import Image, ImageDraw, ImageFont

SIZE = 144
HERE = os.path.dirname(os.path.abspath(__file__))
FONTDIR = os.path.join(HERE, "..", "build_assets", "font")
LABEL_FONT = os.path.join(FONTDIR, "t01.ttf")          # Readex Pro (Latin + Arabic)


def _font(path, size):
    return ImageFont.truetype(path, size, layout_engine=ImageFont.Layout.RAQM)


def _fit(draw, text, path, max_w, size):
    while size > 9:
        f = _font(path, size)
        w = draw.textlength(text, font=f)
        if w <= max_w:
            return f
        size -= 1
    return _font(path, size)


def make_icon(out_path, lines, font_path=None, sizes=None, colors=None):
    """lines: 1-3 strings drawn centred; first line may use font_path."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sizes = sizes or ([40, 22, 18][:len(lines)])
    colors = colors or ["#FFFFFF", "#B9C2BE", "#B9C2BE"]
    fonts = []
    for i, (t, sz) in enumerate(zip(lines, sizes)):
        p = font_path if (i == 0 and font_path) else LABEL_FONT
        if any(0x660 <= ord(ch) <= 0x669 for ch in t):      # Arabic-Indic sample text
            p = p[:-4] + "i.ttf"
        fonts.append(_fit(d, t, p, SIZE - 18, sz))
    heights = []
    for t, f in zip(lines, fonts):
        bb = d.textbbox((0, 0), t, font=f, anchor="ls")
        heights.append(bb[3] - bb[1])
    gap = 8
    total = sum(heights) + gap * (len(lines) - 1)
    y = (SIZE - total) / 2
    for t, f, h, c in zip(lines, fonts, heights, colors):
        bb = d.textbbox((0, 0), t, font=f, anchor="ls")
        d.text((SIZE / 2, y - bb[1]), t, font=f, fill=c, anchor="ms")
        y += h + gap
    img.save(out_path)
