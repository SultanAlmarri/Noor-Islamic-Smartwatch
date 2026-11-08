#!/usr/bin/env python3
"""The Noor watch face (0.9.8): three designs in five colours, written in Watch Face Format.

The face shows Noor's own prayer data through Noor's complications (the next prayer with the interval drawn as a
ring, the Hijri date), so it always agrees with the app: same place, same method, same adjustments. Nothing about
prayer times is computed in the face itself.

Designs
- horizon: the time large in the middle, the Hijri and the Gregorian date above, the next prayer and its countdown
  below, a ring that fills as the interval to the next prayer passes, and the dunes of the home screen at the bottom.
- dial: an analogue dial (hands, minute ticks), the prayer ring inside the ticks, the Hijri date above, the next
  prayer below, two complications the user chooses (Noor's Qibla bearing and the battery by default).
- minimal: the time very large on black, the next prayer and its countdown in one line, a thin ring.

The watch's own editor (long press on the face) offers what the phone does not set: the seconds, and for the dial
the two complications. The phone's Noor app chooses the design and the colour; the watch app replaces the face in
place (same package, same slot), so the change shows at once and the face stays active.

Usage: python3 tools/watchface/noor_face.py generate  (after any change here; needs Pillow, numpy and fontTools)
writes every face's resources to tools/watchface/faces/<id>/res, which are committed, and the phone editor's
pictures; tools/watchface/build_pushed_face.py packages and validates them at every build.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W = H = 450
C = W / 2
APP = "com.noor.smartwatch"
NEXT = f"{APP}/com.noor.wear.complication.NextPrayerComplicationService"
HIJRI = f"{APP}/com.noor.wear.complication.HijriComplicationService"
QIBLA = f"{APP}/com.noor.wear.complication.QiblaComplicationService"

DESIGNS = ["horizon", "dial", "minimal"]
COLOURS = {
    "gold": "#E3C27E",
    "emerald": "#5FCB9C",
    "turquoise": "#58C6D2",
    "rose": "#E99CAB",
    "silver": "#C9D1DB",
}
CREAM = "#FFF4EEE2"
MIST = "#FF9AA6B8"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def n(v):
    """Part coordinates are whole pixels in Watch Face Format."""
    return int(round(v))


def argb(hex_rgb, alpha=255):
    return "#%02X%s" % (alpha, hex_rgb.lstrip("#").upper())


def font(size, color, family="SYNC_TO_DEVICE", weight=None):
    w = f' weight="{weight}"' if weight else ""
    return f'<Font family="{family}" size="{size}" color="{color}"{w}>'


def text(x, y, w, h, template, params, size, color, family="SYNC_TO_DEVICE", weight=None, ambient_alpha=None, align="CENTER"):
    p = "".join(f'<Parameter expression="{e}"/>' for e in params)
    amb = f'<Variant mode="AMBIENT" target="alpha" value="{ambient_alpha}"/>' if ambient_alpha is not None else ""
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}">{amb}'
            f'<Text align="{align}" ellipsis="TRUE">{font(size, color, family, weight)}<Template>{template}{p}</Template></Font></Text></PartText>')


def ring(radius, thickness, color, ambient_alpha=0):
    d = radius * 2
    return (f'<PartDraw x="0" y="0" width="{W}" height="{H}"><Variant mode="AMBIENT" target="alpha" value="{ambient_alpha}"/>'
            f'<Arc centerX="{C}" centerY="{C}" width="{d}" height="{d}" startAngle="0" endAngle="360">'
            f'<Stroke color="{color}" thickness="{thickness}"/></Arc></PartDraw>')


def progress(radius, thickness, accent):
    """The interval to the next prayer, from Noor's ranged complication: fills clockwise from the top."""
    d = radius * 2
    frac = ("(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
            "max(1, [COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]))")
    return (f'<PartDraw x="0" y="0" width="{W}" height="{H}"><Variant mode="AMBIENT" target="alpha" value="110"/>'
            f'<Arc centerX="{C}" centerY="{C}" width="{d}" height="{d}" startAngle="0" endAngle="1">'
            f'<Transform target="endAngle" value="max(1, 360 * {frac})"/>'
            f'<Stroke color="{argb(accent)}" thickness="{thickness}" cap="ROUND"/></Arc></PartDraw>'
            # The bright head of the ring.
            f'<Group name="head" x="0" y="0" width="{W}" height="{H}" pivotX="0.5" pivotY="0.5">'
            f'<Transform target="angle" value="360 * {frac}"/><Variant mode="AMBIENT" target="alpha" value="0"/>'
            f'<PartDraw x="{n(C - thickness * 1.2)}" y="{n(C - radius - thickness * 1.2)}" width="{n(thickness * 2.4)}" height="{n(thickness * 2.4)}">'
            f'<Ellipse x="0" y="0" width="{n(thickness * 2.4)}" height="{n(thickness * 2.4)}"><Fill color="{CREAM}"/></Ellipse></PartDraw></Group>')


def next_prayer_slot(design, accent, title_y, line_y, ring_r, ring_t):
    """Slot 0: Noor's next prayer (name, countdown, the ring). Not customisable: it is the face's purpose."""
    a = argb(accent)
    if design == "minimal":
        body = text(40, line_y, W - 80, 44, "%s  ·  %s", ["[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"], 30, a, weight="MEDIUM", ambient_alpha=200)
        short = text(40, line_y, W - 80, 44, "%s  ·  %s", ["[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"], 30, a, weight="MEDIUM", ambient_alpha=200)
    else:
        body = (text(60, title_y, W - 120, 40, "%s", ["[COMPLICATION.TITLE]"], 32, a, weight="MEDIUM", ambient_alpha=200)
                + text(60, line_y, W - 120, 36, "%s", ["[COMPLICATION.TEXT]"], 27, CREAM, ambient_alpha=200))
        short = (text(60, title_y, W - 120, 40, "%s", ["[COMPLICATION.TITLE]"], 32, a, weight="MEDIUM", ambient_alpha=200)
                 + text(60, line_y, W - 120, 36, "%s", ["[COMPLICATION.TEXT]"], 27, CREAM, ambient_alpha=200))
    return (f'<ComplicationSlot slotId="0" x="0" y="0" width="{W}" height="{H}" displayName="slot_next" '
            f'supportedTypes="RANGED_VALUE SHORT_TEXT" isCustomizable="FALSE">'
            f'<DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY" primaryProvider="{NEXT}" primaryProviderType="RANGED_VALUE"/>'
            f'<BoundingBox x="90" y="{title_y - 6}" width="{W - 180}" height="{line_y - title_y + 50}"/>'
            f'<Complication type="RANGED_VALUE">{progress(ring_r, ring_t, accent)}{body}</Complication>'
            f'<Complication type="SHORT_TEXT">{short}</Complication>'
            f'</ComplicationSlot>')


def hijri_slot(y, size, color):
    return (f'<ComplicationSlot slotId="1" x="0" y="{y}" width="{W}" height="{size + 12}" displayName="slot_hijri" '
            f'supportedTypes="LONG_TEXT SHORT_TEXT" isCustomizable="FALSE">'
            f'<DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY" primaryProvider="{HIJRI}" primaryProviderType="LONG_TEXT"/>'
            f'<BoundingBox x="100" y="0" width="{W - 200}" height="{size + 12}"/>'
            f'<Complication type="LONG_TEXT">{text(70, 0, W - 140, size + 12, "%s", ["[COMPLICATION.TEXT]"], size, color, ambient_alpha=170)}</Complication>'
            f'<Complication type="SHORT_TEXT">{text(70, 0, W - 140, size + 12, "%s", ["[COMPLICATION.TEXT]"], size, color, ambient_alpha=170)}</Complication>'
            f'</ComplicationSlot>')


def side_slot(slot_id, x, y, provider, provider_type, system_default, name, accent):
    """A small complication the user chooses in the watch's editor."""
    w, h = 96, 70
    if provider:
        policy = f'<DefaultProviderPolicy defaultSystemProvider="{system_default}" defaultSystemProviderType="SHORT_TEXT" primaryProvider="{provider}" primaryProviderType="{provider_type}"/>'
    else:
        policy = f'<DefaultProviderPolicy defaultSystemProvider="{system_default}" defaultSystemProviderType="SHORT_TEXT"/>'
    short = (f'<Complication type="SHORT_TEXT">'
             f'<PartImage x="{n(w / 2 - 12)}" y="2" width="24" height="24" tintColor="{argb(accent)}"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/>'
             f'<Variant mode="AMBIENT" target="alpha" value="0"/></PartImage>'
             + text(0, 26, w, 34, "%s", ["[COMPLICATION.TEXT]"], 24, CREAM, ambient_alpha=170)
             + '</Complication>')
    return (f'<ComplicationSlot slotId="{slot_id}" x="{x}" y="{y}" width="{w}" height="{h}" displayName="{name}" '
            f'supportedTypes="SHORT_TEXT EMPTY" isCustomizable="TRUE">{policy}'
            f'<BoundingBox x="0" y="0" width="{w}" height="{h}"/>{short}</ComplicationSlot>')


def background(design, accent):
    if design == "minimal":
        return ""
    return (f'<PartDraw x="0" y="0" width="{W}" height="{H}"><Variant mode="AMBIENT" target="alpha" value="0"/>'
            f'<Ellipse x="0" y="0" width="{W}" height="{H}"><Fill color="#FF050A14">'
            f'<RadialGradient centerX="{C}" centerY="96" radius="400" colors="#FF16294A #FF0B1526 #FF03060C" positions="0 0.5 1"/>'
            f'</Fill></Ellipse></PartDraw>')


def seconds_dot(radius, accent):
    return (f'<BooleanConfiguration id="seconds"><BooleanOption id="TRUE">'
            f'<Group name="sec" x="0" y="0" width="{W}" height="{H}" pivotX="0.5" pivotY="0.5">'
            f'<Transform target="angle" value="[SECOND] * 6"/><Variant mode="AMBIENT" target="alpha" value="0"/>'
            f'<PartDraw x="{n(C - 5)}" y="{n(C - radius - 5)}" width="10" height="10">'
            f'<Ellipse x="0" y="0" width="10" height="10"><Fill color="{argb(accent) if accent.startswith("#") and len(accent) == 7 else accent}"/></Ellipse></PartDraw></Group>'
            f'</BooleanOption></BooleanConfiguration>')


def digital_time(y, h, size):
    return (f'<DigitalClock x="0" y="{y}" width="{W}" height="{h}">'
            f'<TimeText format="hh:mm" hourFormat="SYNC_TO_DEVICE" align="CENTER" x="0" y="0" width="{W}" height="{h}">'
            f'{font(size, CREAM, "noor_digits")}</Font></TimeText></DigitalClock>')


def gregorian(y, size):
    return text(80, y, W - 160, size + 10, "%s %s %s", ["[DAY_OF_WEEK_S]", "[DAY]", "[MONTH_S]"], size, MIST, ambient_alpha=170)


def ticks(accent):
    out = [f'<PartDraw x="0" y="0" width="{W}" height="{H}"><Variant mode="AMBIENT" target="alpha" value="120"/>']
    for i in range(60):
        a = math.radians(i * 6)
        hour = i % 5 == 0
        r1 = 218 - (16 if hour else 7)
        r2 = 218
        x1, y1 = C + r1 * math.sin(a), C - r1 * math.cos(a)
        x2, y2 = C + r2 * math.sin(a), C - r2 * math.cos(a)
        col = argb(accent) if i == 0 else (CREAM if hour else "#66F4EEE2")
        out.append(f'<Line startX="{x1:.1f}" startY="{y1:.1f}" endX="{x2:.1f}" endY="{y2:.1f}">'
                   f'<Stroke color="{col}" thickness="{4.5 if hour else 2}" cap="ROUND"/></Line>')
    out.append('</PartDraw>')
    return "".join(out)


def hands(accent):
    return (f'<AnalogClock x="0" y="0" width="{W}" height="{H}">'
            f'<HourHand resource="hand_hour" x="{n(C - 9)}" y="{n(C - 118)}" width="18" height="136" pivotX="0.5" pivotY="0.8676"/>'
            f'<MinuteHand resource="hand_minute" x="{n(C - 7)}" y="{n(C - 180)}" width="14" height="198" pivotX="0.5" pivotY="0.9091"/>'
            f'</AnalogClock>'
            f'<BooleanConfiguration id="seconds"><BooleanOption id="TRUE">'
            f'<AnalogClock x="0" y="0" width="{W}" height="{H}"><Variant mode="AMBIENT" target="alpha" value="0"/>'
            f'<SecondHand resource="hand_second" x="{n(C - 4)}" y="{n(C - 196)}" width="8" height="236" pivotX="0.5" pivotY="0.8305">'
            f'<Sweep frequency="15"/></SecondHand></AnalogClock>'
            f'</BooleanOption></BooleanConfiguration>'
            f'<PartDraw x="{n(C - 9)}" y="{n(C - 9)}" width="18" height="18"><Ellipse x="0" y="0" width="18" height="18"><Fill color="{argb(accent)}"/></Ellipse></PartDraw>')


def scene(design, accent):
    parts = [background(design, accent)]
    if design == "horizon":
        parts += [
            f'<PartImage x="0" y="{H - 132}" width="{W}" height="132"><Image resource="dunes"/><Variant mode="AMBIENT" target="alpha" value="0"/></PartImage>',
            ring(214, 6, "#2EF4EEE2"),
            hijri_slot(72, 25, argb(accent)),
            gregorian(108, 22),
            digital_time(140, 140, 118),
            next_prayer_slot(design, accent, 282, 322, 214, 6),
            seconds_dot(214, "#FFFFFF"),
        ]
    elif design == "dial":
        parts += [
            ticks(accent),
            ring(192, 4, "#24F4EEE2"),
            hijri_slot(96, 23, argb(accent)),
            side_slot(2, 62, 190, QIBLA, "SHORT_TEXT", "EMPTY", "slot_left", accent),
            side_slot(3, W - 62 - 96, 190, None, None, "WATCH_BATTERY", "slot_right", accent),
            next_prayer_slot(design, accent, 286, 322, 192, 5),
            hands(accent),
        ]
    else:
        parts += [
            ring(218, 3, "#22F4EEE2"),
            hijri_slot(96, 24, MIST),
            digital_time(130, 170, 150),
            next_prayer_slot(design, accent, 312, 312, 218, 3),
            seconds_dot(218, argb(accent)),
        ]
    return "".join(parts)


def watchface_xml(design, colour):
    accent = COLOURS[colour]
    clock = "ANALOG" if design == "dial" else "DIGITAL"
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<WatchFace width="{W}" height="{H}" clipShape="CIRCLE">'
            f'<Metadata key="CLOCK_TYPE" value="{clock}"/><Metadata key="PREVIEW_TIME" value="10:08:32"/>'
            f'<UserConfigurations><BooleanConfiguration id="seconds" displayName="cfg_seconds" screenReaderText="cfg_seconds" '
            f'defaultValue="{"TRUE" if design == "dial" else "FALSE"}"/></UserConfigurations>'
            f'<Scene backgroundColor="#FF000000">{scene(design, accent)}</Scene></WatchFace>\n')


# ---- Images ----

def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def dunes_png(path, accent):
    """The dunes of the home screen at night: three ridges of sharp-crested dunes, the nearest crest rimmed with the
    accent (the last light), fading into the sky at the top."""
    import numpy as np
    w, h = W * 2, 132 * 2
    rgba = np.zeros((h, w, 4), dtype=np.float32)
    ac = np.array(hex_rgb(accent), dtype=np.float32)
    x = np.arange(w) / w
    yy = np.arange(h)[:, None]
    layers = [(0.30, (34, 50, 82), 3.2, 0.3, 0.16), (0.52, (19, 31, 55), 2.3, 1.9, 0.20), (0.74, (7, 12, 22), 1.6, 4.1, 0.22)]
    for k, (top, col, f, ph, amp) in enumerate(layers):
        # A dune: a long gentle windward slope and a short steep lee, so the crests are sharp.
        u = (x * f + ph / 6.283) % 1.0
        prof = np.where(u < 0.72, u / 0.72, 1 - (u - 0.72) / 0.28) ** 1.6
        crest = h * (top + amp * (0.5 - prof) + 0.03 * np.sin(x * 9 + ph))
        inside = yy >= crest[None, :]
        shade = np.clip((yy - crest[None, :]) / (h * 0.5), 0, 1)
        c = np.array(col, dtype=np.float32) * (1 - 0.35 * shade[..., None])
        rgba[inside] = np.concatenate([c, np.full(c.shape[:-1] + (1,), 255.0)], axis=-1)[inside]
        if k == 2:
            rim = np.exp(-np.abs(yy - crest[None, :]) / 1.8) * np.clip(prof[None, :] * 1.4 - 0.2, 0, 1)
            rgba[..., :3] = rgba[..., :3] * (1 - rim[..., None] * 0.7) + ac * rim[..., None] * 0.7
            rgba[..., 3] = np.maximum(rgba[..., 3], rim * 200)
    rgba[..., 3] *= np.clip((yy / h) * 3.2, 0, 1)
    Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), "RGBA").resize((W, 132), Image.LANCZOS).save(path, optimize=True)


def hand_png(path, w, h, color, tail):
    s = 4
    img = Image.new("RGBA", (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w * s - 1, h * s - 1], radius=w * s // 2, fill=color + (255,))
    if tail:
        d.rounded_rectangle([w * s * 0.3, h * s * 0.06, w * s * 0.7, h * s * (1 - tail) - w * s * 0.6], radius=w * s * 0.2, fill=(10, 16, 28, 255))
    img.resize((w, h), Image.LANCZOS).save(path, optimize=True)


def digits_font(path):
    from fontTools import subset
    src = os.path.join(ROOT, "app/src/main/res/font/plex_arabic_light.ttf")
    opts = subset.Options()
    opts.layout_features = ["tnum", "lnum"]
    opts.name_IDs = ["*"]
    f = subset.load_font(src, opts)
    s = subset.Subsetter(opts)
    s.populate(text="0123456789: ٠١٢٣٤٥٦٧٨٩")
    s.subset(f)
    f.save(path)


def preview_png(path, design, colour):
    """The picture the watch's face picker shows: drawn here with the same layout."""
    accent = hex_rgb(COLOURS[colour])
    s = 2
    img = Image.new("RGB", (W * s, H * s), (0, 0, 0))
    d = ImageDraw.Draw(img)
    if design != "minimal":
        import numpy as np
        yy, xx = np.mgrid[0:H * s, 0:W * s] / s
        t = np.clip(np.hypot(xx - C, yy - 96) / 400, 0, 1)[..., None]
        stops = [np.array(v, dtype=np.float32) for v in ((22, 41, 74), (11, 21, 38), (3, 6, 12))]
        bg = np.where(t < 0.5, stops[0] + (stops[1] - stops[0]) * (t / 0.5), stops[1] + (stops[2] - stops[1]) * ((t - 0.5) / 0.5))
        img = Image.fromarray(bg.astype(np.uint8), "RGB")
        d = ImageDraw.Draw(img)
    mask = Image.new("L", (W * s, H * s), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, W * s, H * s], fill=255)
    digits = os.path.join(os.path.dirname(path), "..", "font", "noor_digits.ttf")
    text_font = os.path.join(ROOT, "app/src/main/res/font/plex_arabic_regular.ttf")
    def tx(x, y, s_, string, col, f=text_font):
        fnt = ImageFont.truetype(f, int(s_ * s))
        d.text((x * s, y * s), string, fill=col, font=fnt, anchor="mm")
    cream = (244, 238, 226); mist = (154, 166, 184)
    def arc(r, t, frac, col):
        d.arc([(C - r) * s, (C - r) * s, (C + r) * s, (C + r) * s], -90, -90 + 360 * frac, fill=col, width=int(t * s))
    if design == "horizon":
        dn = Image.open(os.path.join(os.path.dirname(path), "dunes.png")).resize((W * s, 132 * s))
        img.paste(dn, (0, (H - 132) * s), dn)
        arc(214, 6, 1.0, (52, 58, 70)); arc(214, 6, 0.62, accent)
        tx(C, 84, 25, "17 Jumada I 1448", accent); tx(C, 119, 22, "SAT 8 NOV", mist)
        tx(C, 210, 118, "10:08", cream, digits); tx(C, 302, 32, "Asr", accent); tx(C, 340, 27, "1h 23m", cream)
    elif design == "dial":
        for i in range(60):
            a = math.radians(i * 6); hour = i % 5 == 0; r1 = 218 - (16 if hour else 7)
            d.line([((C + r1 * math.sin(a)) * s, (C - r1 * math.cos(a)) * s), ((C + 218 * math.sin(a)) * s, (C - 218 * math.cos(a)) * s)],
                   fill=accent if i == 0 else (cream if hour else (110, 110, 110)), width=int((4.5 if hour else 2) * s))
        arc(192, 4, 1.0, (40, 46, 58)); arc(192, 5, 0.62, accent)
        tx(C, 108, 23, "17 Jumada I 1448", accent)
        tx(C - 115, 225, 24, "258°", cream); tx(C + 115, 225, 24, "84%", cream)
        tx(C, 306, 32, "Asr", accent); tx(C, 340, 27, "1h 23m", cream)
        for ang, ln, wd, col in ((10 * 30 + 4, 118, 18, cream), (8 * 6, 180, 14, cream)):
            a = math.radians(ang)
            d.line([(C * s, C * s), ((C + ln * math.sin(a)) * s, (C - ln * math.cos(a)) * s)], fill=col, width=int(wd * s))
        d.ellipse([(C - 9) * s, (C - 9) * s, (C + 9) * s, (C + 9) * s], fill=accent)
    else:
        arc(218, 3, 1.0, (40, 40, 40)); arc(218, 3, 0.62, accent)
        tx(C, 108, 24, "17 Jumada I 1448", mist)
        tx(C, 215, 150, "10:08", cream, digits); tx(C, 334, 30, "Asr  ·  1h 23m", accent)
    out = Image.new("RGB", (W * s, H * s), (0, 0, 0)); out.paste(img, (0, 0), mask)
    out.resize((W, H), Image.LANCZOS).save(path, optimize=True)


STRINGS = {
    "": dict(watch_face_name="Noor", cfg_seconds="Seconds", slot_next="Next prayer", slot_hijri="Hijri date",
             slot_left="Left", slot_right="Right"),
    "-ar": dict(watch_face_name="نور", cfg_seconds="الثواني", slot_next="الصلاة القادمة", slot_hijri="التاريخ الهجري",
                slot_left="اليسار", slot_right="اليمين"),
}


def write_variant(res, design, colour):
    accent = COLOURS[colour]
    for sub in ("raw", "drawable-nodpi", "font", "xml", "values", "values-ar"):
        os.makedirs(os.path.join(res, sub), exist_ok=True)
    open(os.path.join(res, "raw", "watchface.xml"), "w").write(watchface_xml(design, colour))
    digits_font(os.path.join(res, "font", "noor_digits.ttf"))
    dn = os.path.join(res, "drawable-nodpi")
    if design == "horizon":
        dunes_png(os.path.join(dn, "dunes.png"), accent)
    if design == "dial":
        hand_png(os.path.join(dn, "hand_hour.png"), 18, 136, hex_rgb(CREAM[3:]), 0.13)
        hand_png(os.path.join(dn, "hand_minute.png"), 14, 198, hex_rgb(CREAM[3:]), 0.09)
        hand_png(os.path.join(dn, "hand_second.png"), 8, 236, hex_rgb(accent), 0)
    preview_png(os.path.join(dn, "preview.png"), design, colour)
    open(os.path.join(res, "xml", "watch_face_info.xml"), "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<WatchFaceInfo>\n  <Preview value="@drawable/preview" />\n'
        '  <Editable value="true" />\n  <MultipleInstancesAllowed value="false" />\n</WatchFaceInfo>\n')
    for suffix, strings in STRINGS.items():
        body = "".join(f'    <string name="{k}">{v}</string>\n' for k, v in strings.items())
        open(os.path.join(res, "values" + suffix, "strings.xml"), "w").write(
            f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{body}</resources>\n')


def phone_previews(out_dir, size=360):
    """The pictures the phone's editor shows (app/src/main/res/drawable-nodpi/face_<id>.webp), from the same drawing
    as the watch's own preview, and the original face's own preview."""
    import shutil
    import tempfile
    os.makedirs(out_dir, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for d in DESIGNS:
            for c in COLOURS:
                res = os.path.join(tmp, f"{d}_{c}")
                for sub in ("drawable-nodpi", "font"):
                    os.makedirs(os.path.join(res, sub), exist_ok=True)
                digits_font(os.path.join(res, "font", "noor_digits.ttf"))
                if d == "horizon":
                    dunes_png(os.path.join(res, "drawable-nodpi", "dunes.png"), COLOURS[c])
                preview_png(os.path.join(res, "drawable-nodpi", "preview.png"), d, c)
                Image.open(os.path.join(res, "drawable-nodpi", "preview.png")).resize((size, size), Image.LANCZOS) \
                    .save(os.path.join(out_dir, f"face_{d}_{c}.webp"), "WEBP", quality=88, method=6)
    legacy = os.path.join(ROOT, "legacy/watchface-v1.3.2/NoorWatchFace/watchface/src/main/res/drawable-nodpi/preview.png")
    Image.open(legacy).convert("RGB").resize((size, size), Image.LANCZOS).save(os.path.join(out_dir, "face_original.webp"), "WEBP", quality=88, method=6)


def generate(root=os.path.join(ROOT, "tools/watchface/faces")):
    """Writes every face's resources to tools/watchface/faces/<id>/res (committed; the build only packages them, so it
    needs no Python libraries), and the phone editor's pictures."""
    import shutil
    for d in DESIGNS:
        for c in COLOURS:
            res = os.path.join(root, f"{d}_{c}", "res")
            shutil.rmtree(os.path.dirname(res), ignore_errors=True)
            write_variant(res, d, c)
    phone_previews(os.path.join(ROOT, "app/src/main/res/drawable-nodpi"))


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 2 and sys.argv[1] == "previews":
        phone_previews(sys.argv[2])
    elif len(sys.argv) > 1 and sys.argv[1] == "generate":
        generate()
