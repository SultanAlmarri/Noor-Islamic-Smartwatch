#!/usr/bin/env python3
"""
Minhad's mark (0.25): the crescent and its eight-pointed star over the Earth's edge at first light.

One description, written out as everything that shows the mark, so they never drift apart:
  - the launcher icons of the phone and the watch (adaptive: background = the sky at dawn and the Earth's lit edge,
    foreground = the crescent and the star; monochrome for themed icons),
  - the launch screen's mark (ic_splash_mark) and the status bar's sign (ic_notification),
  - an SVG and an HTML sheet to look at (tools/brand/out, git-ignored).

Only what Android's vector drawables can draw is used: paths, linear gradients and circular radial gradients in
the viewport's own units (no blur, no elliptical gradient, no gradient on a group's transform).

Two places draw the mark in code from the same numbers and must be changed with it: the welcome's own mark
(app/.../ui/onboarding/WelcomeArt.kt, `Mark`) and the opening's dawn colours (app/.../ui/opening/Opening.kt).

Run from the repository's root:  python3 tools/brand/logo.py
"""
import math, os, sys

V = 108.0  # the adaptive icon's viewport; a launcher shows the middle 72 at most, and keeps the middle 66 safe

# ---- the geometry -------------------------------------------------------------------------------------------------
CX, CY, R = 52.5, 46.0, 22.0       # the crescent's outer circle
INNER_R, INNER_OFF, INNER_ANG = 18.2, 8.0, -40.0   # the circle that bites it: radius, distance and bearing of its centre
STAR = (68.4, 37.2, 5.8, 3.2)      # the star: centre, outer and inner radius (eight points: the khatam)
LIMB_Y, EARTH_R = 80.0, 150.0      # the Earth's edge: its highest point, and the radius of the arc

SKY = [(0.00, "#050920"), (0.42, "#101C52"), (0.68, "#2A3384"), (0.82, "#6A5598"), (0.91, "#DB8E70"), (0.97, "#F8C078")]
SKY_END_Y = 83.0                   # the sky's gradient runs from the top to here
GLOW = [(0.0, "#FFE6B8", 0.85), (0.42, "#FAAA6C", 0.34), (1.0, "#F29B6A", 0.0)]
GLOW_C, GLOW_R = (54.0, 86.0), 40.0
MOON = [(0.0, "#FFFBEF"), (1.0, "#EDCB8A")]
STAR_FILL = "#FFF8E2"
EARTH = [(0.0, "#1B1838"), (1.0, "#07081A")]
RIM = [(0.0, "#FFD9A0", 0.0), (0.28, "#FFE2B0", 0.75), (0.5, "#FFF6DC", 1.0), (0.72, "#FFE2B0", 0.75), (1.0, "#FFD9A0", 0.0)]
RIM_W = 1.4


def crescent(cx=CX, cy=CY, R=R, r=INNER_R, off=INNER_OFF, ang=INNER_ANG):
    a = math.radians(ang)
    x = (off * off - r * r + R * R) / (2 * off)
    h = math.sqrt(max(R * R - x * x, 0.0))
    px, py = cx + x * math.cos(a), cy + x * math.sin(a)
    p1 = (px - h * math.sin(a), py + h * math.cos(a))
    p2 = (px + h * math.sin(a), py - h * math.cos(a))
    return (f"M{p1[0]:.2f} {p1[1]:.2f} A{R:g} {R:g} 0 1 1 {p2[0]:.2f} {p2[1]:.2f} "
            f"A{r:g} {r:g} 0 1 0 {p1[0]:.2f} {p1[1]:.2f} Z")


def star(cx=STAR[0], cy=STAR[1], ro=STAR[2], ri=STAR[3]):
    pts = []
    for i in range(16):
        rr = ro if i % 2 == 0 else ri
        t = math.radians(i * 22.5 - 90)
        pts.append((cx + rr * math.cos(t), cy + rr * math.sin(t)))
    return "M" + " L".join(f"{x:.2f} {y:.2f}" for x, y in pts) + " Z"


def earth():
    """The Earth below its edge, as a closed path (an arc of a very large circle across the icon)."""
    cy = LIMB_Y + EARTH_R
    # where the circle crosses x = -6 and x = 114 (past the icon's sides)
    def y_at(x): return cy - math.sqrt(EARTH_R ** 2 - (x - 54.0) ** 2)
    x0, x1 = -6.0, 114.0
    return f"M{x0:g} {y_at(x0):.2f} A{EARTH_R:g} {EARTH_R:g} 0 0 1 {x1:g} {y_at(x1):.2f} L{x1:g} 114 L{x0:g} 114 Z"


def rim():
    cy = LIMB_Y + EARTH_R
    def y_at(x): return cy - math.sqrt(EARTH_R ** 2 - (x - 54.0) ** 2)
    x0, x1 = 4.0, 104.0
    return f"M{x0:g} {y_at(x0):.2f} A{EARTH_R:g} {EARTH_R:g} 0 0 1 {x1:g} {y_at(x1):.2f}"


# ---- Android vector drawables -------------------------------------------------------------------------------------
def argb(hex_rgb, alpha=1.0):
    return "#%02X%s" % (round(alpha * 255), hex_rgb.lstrip("#").upper())


def items(stops):
    out = []
    for s in stops:
        o, c = s[0], s[1]
        a = s[2] if len(s) > 2 else 1.0
        out.append(f'                <item android:offset="{o:g}" android:color="{argb(c, a)}"/>')
    return "\n".join(out)


def linear(x0, y0, x1, y1, stops, attr="fillColor"):
    return (f'        <aapt:attr name="android:{attr}">\n'
            f'            <gradient android:type="linear" android:startX="{x0:g}" android:startY="{y0:g}" android:endX="{x1:g}" android:endY="{y1:g}">\n'
            f'{items(stops)}\n            </gradient>\n        </aapt:attr>')


def radial(cx, cy, r, stops):
    return (f'        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="radial" android:centerX="{cx:g}" android:centerY="{cy:g}" android:gradientRadius="{r:g}">\n'
            f'{items(stops)}\n            </gradient>\n        </aapt:attr>')


HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Minhad\'s mark. Written by tools/brand/logo.py: change it there. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="{w}dp" android:height="{w}dp" android:viewportWidth="{v:g}" android:viewportHeight="{v:g}">\n')
FULL = f"M0,0h{V:g}v{V:g}h-{V:g}z"


def background_xml():
    return (HEAD.format(w=108, v=V)
            + f'    <path android:pathData="{FULL}">\n{linear(0, 0, 0, SKY_END_Y, SKY)}\n    </path>\n'
            + f'    <path android:pathData="{FULL}">\n{radial(GLOW_C[0], GLOW_C[1], GLOW_R, GLOW)}\n    </path>\n'
            + f'    <path android:pathData="{earth()}">\n{linear(0, LIMB_Y, 0, V, EARTH)}\n    </path>\n'
            + f'    <path android:pathData="{rim()}" android:strokeWidth="{RIM_W:g}" android:strokeLineCap="round" android:fillColor="#00000000">\n'
            + f'{linear(4, 0, 104, 0, RIM, attr="strokeColor")}\n    </path>\n</vector>\n')


def moon_gradient():
    # across the crescent, from its upper left to its lower right
    return linear(CX - R * 0.7, CY - R * 0.9, CX + R * 0.6, CY + R * 0.9, MOON)


def foreground_xml():
    return (HEAD.format(w=108, v=V)
            + f'    <path android:pathData="{crescent()}">\n{moon_gradient()}\n    </path>\n'
            + f'    <path android:fillColor="{argb(STAR_FILL)}" android:pathData="{star()}"/>\n</vector>\n')


def plain_xml(color="#FFFFFF", w=108, viewport=V, shift=(0.0, 0.0), comment=None, alpha=1.0):
    head = HEAD.format(w=w, v=viewport)
    if comment: head = head.replace("change it there. -->", f"change it there. {comment} -->")
    body = (f'        <path android:fillColor="{argb(color, alpha)}" android:pathData="{crescent()}"/>\n'
            f'        <path android:fillColor="{argb(color, alpha)}" android:pathData="{star()}"/>\n')
    return head + f'    <group android:translateX="{shift[0]:g}" android:translateY="{shift[1]:g}">\n{body}    </group>\n</vector>\n'


def splash_xml():
    # The launch screen's mark: the crescent and the star alone, in their light, where the launcher icon has them.
    return foreground_xml()


def notification_xml(alpha=1.0, comment="For the status bar (Android tints it)."):
    # The status bar's sign: the mark alone, filling a 24 dp square (Android tints it).
    box = bounds()
    side = max(box[2] - box[0], box[3] - box[1]) + 4.0
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    return plain_xml(w=24, viewport=side, shift=(side / 2 - cx, side / 2 - cy), comment=comment, alpha=alpha)


def bounds():
    """The mark's box (the crescent's circle and the star)."""
    x0 = min(CX - R, STAR[0] - STAR[2]); x1 = max(CX + R, STAR[0] + STAR[2])
    y0 = min(CY - R, STAR[1] - STAR[2]); y1 = max(CY + R, STAR[1] + STAR[2])
    return (x0, y0, x1, y1)


# ---- SVG (to look at) -----------------------------------------------------------------------------------------------
def svg(prefix="m"):
    def stops(ss):
        return "".join(f'<stop offset="{s[0]:g}" stop-color="{s[1]}" stop-opacity="{(s[2] if len(s) > 2 else 1):g}"/>' for s in ss)
    p = prefix
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>'
            f'<linearGradient id="{p}s" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="{SKY_END_Y:g}">{stops(SKY)}</linearGradient>'
            f'<radialGradient id="{p}g" gradientUnits="userSpaceOnUse" cx="{GLOW_C[0]:g}" cy="{GLOW_C[1]:g}" r="{GLOW_R:g}">{stops(GLOW)}</radialGradient>'
            f'<linearGradient id="{p}e" gradientUnits="userSpaceOnUse" x1="0" y1="{LIMB_Y:g}" x2="0" y2="108">{stops(EARTH)}</linearGradient>'
            f'<linearGradient id="{p}r" gradientUnits="userSpaceOnUse" x1="4" y1="0" x2="104" y2="0">{stops(RIM)}</linearGradient>'
            f'<linearGradient id="{p}m" gradientUnits="userSpaceOnUse" x1="{CX - R * 0.7:g}" y1="{CY - R * 0.9:g}" x2="{CX + R * 0.6:g}" y2="{CY + R * 0.9:g}">{stops(MOON)}</linearGradient></defs>'
            f'<rect width="108" height="108" fill="url(#{p}s)"/><rect width="108" height="108" fill="url(#{p}g)"/>'
            f'<path d="{earth()}" fill="url(#{p}e)"/><path d="{rim()}" fill="none" stroke="url(#{p}r)" stroke-width="{RIM_W:g}" stroke-linecap="round"/>'
            f'<path d="{crescent()}" fill="url(#{p}m)"/><path d="{star()}" fill="{STAR_FILL}"/></svg>')


def sheet():
    cells = ""
    for i, (mask, sz) in enumerate((("27%", 256), ("50%", 256), ("27%", 120), ("50%", 120), ("27%", 60), ("50%", 48), ("27%", 36))):
        cells += (f'<div style="width:{sz}px;height:{sz}px;border-radius:{mask};overflow:hidden;box-shadow:0 6px 18px rgba(0,0,0,.35)">'
                  f'<div style="width:{sz * 1.5}px;height:{sz * 1.5}px;margin:-{sz * 0.25}px">{svg("m%d" % i)}</div></div>')
    return ('<html><body style="margin:0"><div style="display:flex;flex-wrap:wrap;align-items:center;gap:22px;padding:22px;'
            f'background:linear-gradient(120deg,#d9dee9,#5a6480 60%,#1c2030)">{cells}</div></body></html>')


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f: f.write(text)
    print("wrote", path)


if __name__ == "__main__":
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    for module in ("app", "wear"):
        d = os.path.join(root, module, "src", "main", "res", "drawable")
        write(os.path.join(d, "ic_launcher_background.xml"), background_xml())
        write(os.path.join(d, "ic_launcher_foreground.xml"), foreground_xml())
        write(os.path.join(d, "ic_launcher_monochrome.xml"), plain_xml())
        write(os.path.join(d, "ic_notification.xml"), notification_xml())
    write(os.path.join(root, "app", "src", "main", "res", "drawable", "ic_splash_mark.xml"), splash_xml())
    # (0.24) The same sign, transparent: the status-bar timer's icon slot when the owner's setting leaves the icon out.
    write(os.path.join(root, "app", "src", "main", "res", "drawable", "ic_notification_clear.xml"),
          notification_xml(alpha=0.0, comment="Transparent: the Live Update's small icon when AppSettings.statusChipIcon is off."))
    out = os.path.join(root, "tools", "brand", "out")
    write(os.path.join(out, "minhad.svg"), svg())
    write(os.path.join(out, "sheet.html"), sheet())
