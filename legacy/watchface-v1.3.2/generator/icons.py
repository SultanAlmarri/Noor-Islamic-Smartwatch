"""Draw the watch-face icons as SVG and rasterise them to PNG (white on
transparent; the watch face tints them with the accent colour)."""
import os, sys
import cairosvg

OUT = sys.argv[1] if len(sys.argv) > 1 else "icons"
S = 'stroke="#FFFFFF" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" fill="none"'
F = 'fill="#FFFFFF"'


def svg(body, size=96):
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 96 96">%s</svg>'
            % (size, size, body))


def rays(cx, cy, r1, r2, angles):
    import math
    out = []
    for a in angles:
        t = math.radians(a)
        out.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" %s/>' % (
            cx + r1 * math.cos(t), cy - r1 * math.sin(t), cx + r2 * math.cos(t), cy - r2 * math.sin(t), S))
    return "".join(out)


def star(cx, cy, r):
    import math
    pts = []
    for i in range(8):
        rr = r if i % 2 == 0 else r * 0.38
        t = math.radians(90 + i * 45)
        pts.append("%.1f,%.1f" % (cx + rr * math.cos(t), cy - rr * math.sin(t)))
    return '<polygon points="%s" %s/>' % (" ".join(pts), F)


def crescent(cx, cy, r, cut_dx, cut_dy, cut_r):
    """Crescent = disc (cx,cy,r) minus disc (cx+cut_dx, cy+cut_dy, cut_r), drawn
    as an explicit two-arc outline (no masks, renders identically everywhere)."""
    import math
    bx, by = cx + cut_dx, cy + cut_dy
    d = math.hypot(cut_dx, cut_dy)
    a = (r * r - cut_r * cut_r + d * d) / (2 * d)
    h = math.sqrt(max(r * r - a * a, 0))
    ux, uy = cut_dx / d, cut_dy / d
    px, py = cx + a * ux, cy + a * uy
    p1 = (px + h * uy, py - h * ux)
    p2 = (px - h * uy, py + h * ux)
    return ('<path d="M%.2f %.2f A%.2f %.2f 0 1 0 %.2f %.2f A%.2f %.2f 0 0 1 %.2f %.2f Z" %s/>'
            % (p1[0], p1[1], r, r, p2[0], p2[1], cut_r, cut_r, p1[0], p1[1], F))


ICONS = {
    # Fajr: true dawn - the crescent is still up while the first light breaks
    # from below the horizon (no sun disc yet, unlike Shuruq).  Redesigned v1.2;
    # v1.3.1 adds stars beside the crescent for the pre-dawn night sky.
    "ic_fajr": svg(
        crescent(48, 28, 14, 7, -5, 12.5)
        + star(22, 30, 5.5) + star(74, 22, 5.5) + star(30, 10, 3.5)
        + '<line x1="12" y1="72" x2="84" y2="72" %s/>' % S
        + rays(48, 88, 24, 35, [55, 90, 125])),
    # Shuruq: half sun rising above the horizon
    "ic_sunrise": svg(
        '<path d="M26 70 A22 22 0 0 1 70 70" %s/>' % S
        + rays(48, 70, 30, 39, [20, 55, 90, 125, 160])
        + '<line x1="10" y1="70" x2="86" y2="70" %s/>' % S
        + '<path d="M40 86 L48 79 L56 86" %s/>' % S),
    # Dhuhr: full sun at its height
    "ic_dhuhr": svg(
        '<circle cx="48" cy="48" r="15" %s/>' % F
        + rays(48, 48, 24, 33, range(0, 360, 45))),
    # Asr: sun past its height and the lengthening shadow of an upright object
    "ic_asr": svg(
        '<circle cx="30" cy="28" r="11" %s/>' % F
        + rays(30, 28, 17, 24, range(0, 360, 60))
        + '<line x1="62" y1="44" x2="62" y2="74" %s/>' % S
        + '<line x1="62" y1="74" x2="88" y2="74" stroke="#FFFFFF" stroke-width="5" stroke-linecap="round" opacity="0.55"/>'
        + '<line x1="10" y1="74" x2="56" y2="74" %s/>' % S),
    # Maghrib: sun setting below the horizon
    "ic_maghrib": svg(
        '<path d="M26 66 A22 22 0 0 1 70 66" %s/>' % S
        + rays(48, 66, 30, 39, [20, 55, 90, 125, 160])
        + '<line x1="10" y1="66" x2="86" y2="66" %s/>' % S
        + '<path d="M40 77 L48 84 L56 77" %s/>' % S),
    # Isha: night - crescent and stars
    "ic_isha": svg(
        crescent(44, 52, 26, 13, -9, 23)
        + star(72, 24, 7) + star(80, 50, 4.5) + star(58, 14, 3.5)),
    # Stats
    "ic_steps": svg(
        '<path d="M30 14 C40 14 42 30 40 42 C39 50 22 50 21 42 C19 30 20 14 30 14 Z" %s/>' % F
        + '<ellipse cx="31" cy="56" rx="8" ry="6" %s/>' % F
        + '<path d="M64 34 C74 34 76 50 74 62 C73 70 56 70 55 62 C53 50 54 34 64 34 Z" %s/>' % F
        + '<ellipse cx="65" cy="76" rx="8" ry="6" %s/>' % F),
    "ic_heart": svg(
        '<path d="M48 82 C20 62 10 46 14 32 C18 18 36 14 48 30 C60 14 78 18 82 32 C86 46 76 62 48 82 Z" %s/>' % F),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, body in ICONS.items():
        open(os.path.join(OUT, name + ".svg"), "w").write(body)
        cairosvg.svg2png(bytestring=body.encode(), write_to=os.path.join(OUT, name + ".png"),
                         output_width=96, output_height=96)
    print("icons:", ", ".join(sorted(ICONS)))


if __name__ == "__main__":
    main()
