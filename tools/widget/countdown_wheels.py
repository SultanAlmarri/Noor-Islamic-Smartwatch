"""Builds the widgets' seconds wheels (0.9.12): the seconds of the countdown, ticked by Android itself, in Noor's font
and digits.

Android lets a home-screen widget tick by itself, every second, without the app running, in only two ways: a text
clock (Chronometer, TextClock), which writes the phone's digits in the phone's font (a widget cannot load an app's
font), and an analog clock (AnalogClock, Android 12 and later), whose second hand can be any picture the app supplies,
turned by Android one step each second. So the seconds are a wheel: all sixty labels ("00" to "59", in Noor's font,
the app's colour and shadow) set around a large circle as the clock's second hand; a small window in the widget shows
only the top of the circle, where the label of the current second stands upright. The hours and minutes are drawn by
Noor itself once a minute (widget/CountdownArt.kt).

- res/drawable-nodpi/cdg_{l,a}_N.png   digit N in Western (l) or Arabic-Indic (a) digits, white at the seconds' 50 %;
- res/drawable-nodpi/cds_{l,a}_N.png   its shadow (as the app's countdown: navy at 25 %, softly blurred), smaller;
- res/drawable/cd_wheel_{down,up}_{latin,arabic}.xml   the wheels: before a prayer the seconds count down (the label
  shown during the clock's second s is 59 - s), after it they count up (s);
- res/drawable/cd_dial.xml, cd_blank.xml   the clock's invisible dial (it sets the wheel's size) and hands;
- app/src/main/java/com/noor/app/widget/CountdownWheelSpec.kt   the geometry, shared with the app.

Geometry, in em of the seconds' font (the wheel is drawn for an em of E_DP dp and scaled down by Android): each label's
first digit stands at P em from the left of its window and its baseline A em below the window's top; the window is
WIN_W em wide and A + B em tall; the wheel's radius is large enough that the labels of the seconds before and after
lie wholly outside the window.

Usage: python3 tools/widget/countdown_wheels.py   (needs Pillow with raqm, fontTools)
"""
import math
import os

from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(HERE, "../../app/src/main")
FONT = os.path.join(APP, "res/font/plex_arabic_light.ttf")
NODPI = os.path.join(APP, "res/drawable-nodpi")
DRAWABLE = os.path.join(APP, "res/drawable")
SPEC = os.path.join(APP, "java/com/noor/app/widget/CountdownWheelSpec.kt")

E_DP = 72.0            # the wheel's em, in dp (the largest countdown a widget shows)
K = 3.0                # pixels per dp of the digit pictures (sharp up to an em of 72 dp on a 3x screen)
SHADOW_K = 0.75        # pixels per dp of the (blurred) shadow pictures
LS = -0.04             # letter spacing, em (the app: -2.5 sp at about 62 sp)
# Android applies no letter spacing to Arabic script, and Arabic-Indic digits are Arabic script: the app's Arabic-Indic
# countdown stands at the digits' own spacing.
SPACING = {"l": LS, "a": 0.0}
A = 0.83               # window top above the baseline, em (digits reach 0.71 em; room for the shadow)
B = 0.14               # window bottom below the baseline, em
P = 0.12               # window's left edge before the first digit's origin, em
SHADOW_RADIUS = 0.0614  # the app's shadow: blur 10 px at its 62 sp countdown (163 px), as an em fraction
SHADOW_DY = 0.0092      # and 1.5 px down
SHADOW_ARGB = 0x40081530
SECONDS_ALPHA = 0.5
DIGITS = {"l": [chr(0x30 + i) for i in range(10)], "a": [chr(0x660 + i) for i in range(10)]}


def advances():
    f = TTFont(FONT)
    cmap = f.getBestCmap(); hm = f["hmtx"]; upm = f["head"].unitsPerEm
    return {ch: hm[cmap[ord(ch)]][0] / upm for s in DIGITS.values() for ch in s}


def render_digit(font, ch, em_px):
    """The digit, white at the seconds' alpha, cropped to its ink; returns the image and its origin inside it."""
    pad = int(em_px * 0.05) + 2
    size = int(em_px * 1.6)
    img = Image.new("L", (size, size), 0)
    ox, oy = int(em_px * 0.3), int(em_px * 1.1)
    ImageDraw.Draw(img).text((ox, oy), ch, font=font, fill=255, anchor="ls")
    x0, y0, x1, y1 = img.getbbox()
    x0 -= pad; y0 -= pad; x1 += pad; y1 += pad
    alpha = img.crop((x0, y0, x1, y1)).point(lambda v: int(v * SECONDS_ALPHA + 0.5))
    out = Image.merge("RGBA", (Image.new("L", alpha.size, 255),) * 3 + (alpha,))
    return out, ox - x0, oy - y0


def render_shadow(font, ch, em_px, scale):
    """The digit's shadow (blurred as Android blurs a text shadow), at [scale] of the digit pictures' resolution."""
    sigma = 0.57735 * SHADOW_RADIUS * em_px + 0.5
    margin = int(3.2 * sigma + em_px * 0.05) + 2
    size = int(em_px * 1.6) + 2 * margin
    img = Image.new("L", (size, size), 0)
    ox, oy = int(em_px * 0.3) + margin, int(em_px * 1.1) + margin
    ImageDraw.Draw(img).text((ox, oy + SHADOW_DY * em_px), ch, font=font, fill=255, anchor="ls")
    img = img.filter(ImageFilter.GaussianBlur(sigma))
    x0, y0, x1, y1 = img.point(lambda v: 255 if v > 1 else 0).getbbox()
    a = (SHADOW_ARGB >> 24) / 255.0
    alpha = img.crop((x0, y0, x1, y1)).point(lambda v: int(v * a + 0.5))
    w, h = alpha.size
    small = (max(1, round(w * scale)), max(1, round(h * scale)))
    r, g, b = (SHADOW_ARGB >> 16) & 255, (SHADOW_ARGB >> 8) & 255, SHADOW_ARGB & 255
    rgba = Image.merge("RGBA", (Image.new("L", alpha.size, r), Image.new("L", alpha.size, g), Image.new("L", alpha.size, b), alpha))
    # Premultiplied resize, so the edges do not darken.
    rgba = rgba.resize(small, Image.LANCZOS, reducing_gap=None)
    return rgba, (ox - x0) * scale, (oy - y0) * scale, (w, h)


def fmt(v):
    return ("%.3f" % v).rstrip("0").rstrip(".") + "dp"


def main():
    adv = advances()
    em_px = E_DP * K
    font = ImageFont.truetype(FONT, size=round(em_px), layout_engine=ImageFont.Layout.RAQM)
    os.makedirs(NODPI, exist_ok=True)
    pics = {}
    for s, digits in DIGITS.items():
        for n, ch in enumerate(digits):
            g, gx, gy = render_digit(font, ch, em_px)
            g.save(os.path.join(NODPI, f"cdg_{s}_{n}.png"), optimize=True)
            sh, sx, sy, _ = render_shadow(font, ch, em_px, SHADOW_K / K)
            sh.save(os.path.join(NODPI, f"cds_{s}_{n}.png"), optimize=True)
            # Placement of each picture relative to the digit's origin on the baseline, in dp.
            pics[(s, n)] = dict(
                g=(-gx / K, -gy / K, g.size[0] / K, g.size[1] / K),
                s=(-sx / SHADOW_K, -sy / SHADOW_K, sh.size[0] / SHADOW_K, sh.size[1] / SHADOW_K),
            )
    width = {}
    for s, digits in DIGITS.items():
        width[s] = P + max(adv[digits[t]] + SPACING[s] + adv[digits[o]] for t in range(6) for o in range(10)) + P
    win_w = max(width.values())
    h_em = A + B
    radius = (win_w + 0.3) / math.sin(math.radians(6))
    d_em = math.ceil(2 * radius + h_em)
    d = d_em * E_DP
    cell_left = (d - width_dp(win_w)) / 2

    def wheel(s, down):
        digits = DIGITS[s]
        out = ['<?xml version="1.0" encoding="utf-8"?>',
               f'<!-- Generated by tools/widget/countdown_wheels.py: the seconds wheel ({"counting down" if down else "counting up"}, '
               f'{"Western" if s == "l" else "Arabic-Indic"} digits). Do not edit. -->',
               '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">',
               f'    <item android:width="{fmt(d)}" android:height="{fmt(d)}" android:drawable="@android:color/transparent" />']
        for sec in range(60):
            v = 59 - sec if down else sec
            t, o = divmod(v, 10)
            deg = (360 - 6 * sec) % 360
            x1 = cell_left + P * E_DP
            x2 = x1 + (adv[digits[t]] + SPACING[s]) * E_DP
            base = A * E_DP
            out.append(f'    <item><rotate android:fromDegrees="{deg}" android:toDegrees="{deg}" android:pivotX="50%" android:pivotY="50%"><layer-list>')
            for kind in ("s", "g"):
                for n, x in ((t, x1), (o, x2)):
                    dx, dy, w, h = pics[(s, n)][kind]
                    name = f"cd{kind}_{s}_{n}"
                    out.append(f'        <item android:gravity="left|top" android:left="{fmt(x + dx)}" android:top="{fmt(base + dy)}" '
                               f'android:width="{fmt(w)}" android:height="{fmt(h)}" android:drawable="@drawable/{name}" />')
            out.append('    </layer-list></rotate></item>')
        out.append('</layer-list>')
        return "\n".join(out) + "\n"

    for s, name in (("l", "latin"), ("a", "arabic")):
        for down in (True, False):
            with open(os.path.join(DRAWABLE, f"cd_wheel_{'down' if down else 'up'}_{name}.xml"), "w") as fh:
                fh.write(wheel(s, down))
    with open(os.path.join(DRAWABLE, "cd_dial.xml"), "w") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/widget/countdown_wheels.py: the seconds clock\'s '
                 'invisible dial; its size is the wheel\'s. -->\n<shape xmlns:android="http://schemas.android.com/apk/res/android" '
                 f'android:shape="rectangle">\n    <solid android:color="#00000000" />\n    <size android:width="{fmt(d)}" android:height="{fmt(d)}" />\n</shape>\n')
    with open(os.path.join(DRAWABLE, "cd_blank.xml"), "w") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/widget/countdown_wheels.py: the seconds clock\'s '
                 'hidden hour and minute hands. -->\n<shape xmlns:android="http://schemas.android.com/apk/res/android" '
                 'android:shape="rectangle">\n    <solid android:color="#00000000" />\n    <size android:width="1dp" android:height="1dp" />\n</shape>\n')
    with open(SPEC, "w") as fh:
        fh.write(f"""package com.noor.app.widget

/**
 * Generated by tools/widget/countdown_wheels.py (0.9.12); do not edit. The geometry of the seconds wheels, in em of the
 * countdown's font: see the tool for what each value means.
 */
internal object CountdownWheelSpec {{
    /** The em the wheels are drawn for, in dp; a widget's clock scales them down to its own. */
    const val E_DP = {E_DP}f
    /** The wheel's (and the clock's dial's) width and height. */
    const val D_EM = {d_em}f
    const val A_EM = {A}f
    const val B_EM = {B}f
    const val P_EM = {P}f
    /** The letter spacing of Western digits (Android sets none in Arabic script, Arabic-Indic digits included). */
    const val LS_EM = {LS}f
    /** The window's width, the same for both kinds of digits. */
    const val WIN_W_EM = {win_w:.4f}f
    const val SHADOW_RADIUS_EM = {SHADOW_RADIUS}f
    const val SHADOW_DY_EM = {SHADOW_DY}f
    const val SHADOW_ARGB = 0x{SHADOW_ARGB:08X}.toInt()
    const val SECONDS_ALPHA = {SECONDS_ALPHA}f
}}
""")
    print("window", {k: round(v, 4) for k, v in width.items()}, "->", round(win_w, 4), "em; wheel", d_em, "em =", d, "dp")


def width_dp(win_w):
    return win_w * E_DP


if __name__ == "__main__":
    main()
