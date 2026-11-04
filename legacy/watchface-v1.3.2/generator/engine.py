"""Prayer-time engine expressed in the expression DSL.

The functions here build expression trees that are emitted verbatim into the
watch face (WFF) *and* evaluated in Python for tests and comparisons, so the
watch and the tests run exactly the same formulas.

Astronomy: U.S. Naval Observatory "approximate solar coordinates"
(the algorithm used by PrayTimes.org and most prayer apps), evaluated at the
approximate moment of each event.  Equation of time uses the reduction-to-
equator series (tan^2(eps/2) terms), which avoids atan2 (not available in WFF).
"""
import math
from expr import (Src, Num, Str, Fn, tern, chain, sin, sqrt, acos, clamp, fabs,
                  floor, jround, fmin)
import data

D2R = math.pi / 180.0
R2D = 180.0 / math.pi

CITY = "CONFIGURATION.city"
METHOD = "CONFIGURATION.method"
ASRHL = "CONFIGURATION.asrhl"      # combined Asr method + high-latitude rule (v1.2)
ASRHL_OPTIONS = ["std_angle", "std_mid", "std_7th", "han_angle", "han_mid", "han_7th"]


def ADJ(ev):
    return "CONFIGURATION.adj_" + ev


class Params:
    """City / method parameters as expression nodes driven by user settings."""

    def __init__(self):
        C = {c[0]: c for c in data.CITIES}
        M = {m["id"]: m for m in data.METHODS}
        dc = data.CITIES[0]
        dm = data.METHODS[0]
        cid = lambda f: {k: f(v) for k, v in C.items() if k != dc[0]}
        mid = lambda f: {k: f(v) for k, v in M.items() if k != dm["id"]}

        self.sphi = chain(CITY, cid(lambda c: round(math.sin(c[3] * D2R), 7)), round(math.sin(dc[3] * D2R), 7))
        self.cphi = chain(CITY, cid(lambda c: round(math.cos(c[3] * D2R), 7)), round(math.cos(dc[3] * D2R), 7))
        self.lat_rad = chain(CITY, cid(lambda c: round(c[3] * D2R, 7)), round(dc[3] * D2R, 7))
        self.lon = chain(CITY, cid(lambda c: c[4]), dc[4])
        h0 = lambda c: round(math.sin(-(0.833 + 0.0347 * math.sqrt(max(c[5], 0))) * D2R), 7)
        self.sinh0 = chain(CITY, cid(h0), h0(dc))

        self.fajr_sin = chain(METHOD, mid(lambda m: round(-math.sin(m["fajr"] * D2R), 7)), round(-math.sin(dm["fajr"] * D2R), 7))
        self.fajr_ang = chain(METHOD, mid(lambda m: m["fajr"]), dm["fajr"])
        ia = lambda m: m["isha"] if m["isha"] is not None else 18.0
        self.isha_sin = chain(METHOD, mid(lambda m: round(-math.sin(ia(m) * D2R), 7)), round(-math.sin(ia(dm) * D2R), 7))
        self.isha_ang = chain(METHOD, mid(ia), ia(dm))
        im = lambda m: m["isha_min"] if m["isha_min"] is not None else 0
        self.isha_min = chain(METHOD, mid(im), im(dm))          # 0 = angle based
        ma = lambda m: round(-math.sin(m["maghrib"] * D2R), 7) if m["maghrib"] else 0.0
        self.magh_sin = chain(METHOD, mid(ma), ma(dm))           # 0 = sunset
        han = Src(ASRHL).eq(Str("han_angle")).or_(Src(ASRHL).eq(Str("han_mid"))).or_(Src(ASRHL).eq(Str("han_7th")))
        self.asr_factor = tern(han, 2.0, 1.0)
        self.hl_mid = Src(ASRHL).eq(Str("std_mid")).or_(Src(ASRHL).eq(Str("han_mid")))
        self.hl_7th = Src(ASRHL).eq(Str("std_7th")).or_(Src(ASRHL).eq(Str("han_7th")))

        self.Y = Src("YEAR")
        self.N = Src("DAY_OF_YEAR")
        self.offset_h = Src("TIMEZONE_OFFSET_MINUTES_DST") / 60.0

        # total minute adjustment per event = method built-in + user offset
        self.adj = {}
        for ev in data.EVENTS:
            method_part = chain(METHOD, mid(lambda m: m["adj"][ev]), dm["adj"][ev])
            user_part = chain(ADJ(ev), {data.adj_id(v): v for v in data.ADJ_RANGE if v != 0}, 0)
            self.adj[ev] = (method_part, user_part)

    def portion_mult(self, angle_node):
        # High-latitude rule: fraction of the night used as a limit
        return tern(self.hl_mid, 0.5, tern(self.hl_7th, 1.0 / 7.0, angle_node / 60.0))


def day_number(P, solar_hour, day_offset):
    """Days since J2000.0 (JD 2451545.0) at the event's approximate moment.

    The event's UT is approximated as (solar hour - watch UTC offset); the
    true value is (solar hour - longitude/15).  The difference (usually well
    under 1 h) moves the sun's declination by < 0.02 deg, i.e. < 1 s of time,
    and keeps the (long) city tables out of every trigonometric term."""
    Y, N = P.Y, P.N
    base = (Y - 2000) * 365 + floor((Y - 1997) / 4.0)
    return base + (N + (day_offset - 1)) + ((solar_hour - P.offset_h) / 24.0 - 0.5)


def sun(d):
    g = (d * 0.98560028 + 357.529) * D2R
    L = (d * 0.98564736 + 280.459) + sin(g) * 1.915 + sin(g * 2.0) * 0.020
    Lr = L * D2R
    sin_eps = sin((23.439 - d * 0.00000036) * D2R)
    sin_d = sin_eps * sin(Lr)
    cos_d = Fn("cos", Fn("asin", sin_d))
    eqt_min = (sin(g) * (-1.915) - sin(g * 2.0) * 0.020 + sin(Lr * 2.0) * 2.46545
               - sin(Lr * 4.0) * 0.053044 + sin(Lr * 6.0) * 0.001522) * 4.0
    return sin_d, cos_d, eqt_min


def hour_angle_h(P, sin_alt, sin_d, cos_d):
    """Hours between transit and the moment the sun reaches altitude asin(sin_alt)."""
    c = (sin_alt - P.sphi * sin_d) / (P.cphi * cos_d)
    return acos(clamp(c, -1.0, 1.0)) * (R2D / 15.0)


def event_hours(P, ev, day_offset=0):
    """Local clock time of an event in hours (before rounding / adjustments)."""
    d = day_number(P, data.EVENT_SOLAR_HOUR[ev], day_offset)
    sin_d, cos_d, eqt = sun(d)
    transit = (12.0 + P.offset_h) - P.lon / 15.0 - eqt / 60.0
    H0 = lambda: hour_angle_h(P, P.sinh0, sin_d, cos_d)
    if ev == "dhuhr":
        return transit
    if ev == "sunrise":
        return transit - H0()
    if ev == "maghrib":
        # sunset, or a depression angle for methods that define one (Tehran)
        alt = tern(P.magh_sin.eq(0.0), P.sinh0, P.magh_sin)
        return transit + hour_angle_h(P, alt, sin_d, cos_d)
    if ev == "asr":
        # shadow factor: cot(a) = f + tan|lat - decl|
        t = fabs(Fn("tan", P.lat_rad - Fn("asin", sin_d)))
        f = P.asr_factor + t
        sin_a = 1.0 / sqrt(f * f + 1.0)
        return transit + hour_angle_h(P, sin_a, sin_d, cos_d)
    if ev == "fajr":
        # transit - min(H_fajr, H_sunrise + k * night), night = 24 - 2 * H_sunrise
        k = P.portion_mult(P.fajr_ang)
        h0 = H0()
        hf = hour_angle_h(P, P.fajr_sin, sin_d, cos_d)
        return transit - fmin(hf, h0 * (1.0 - k * 2.0) + k * 24.0)
    if ev == "isha":
        k = P.portion_mult(P.isha_ang)
        h0 = H0()
        hi = hour_angle_h(P, P.isha_sin, sin_d, cos_d)
        by_angle = fmin(hi, h0 * (1.0 - k * 2.0) + k * 24.0)
        by_interval = h0 + P.isha_min / 60.0
        return transit + tern(P.isha_min.gt(0), by_interval, by_angle)
    raise ValueError(ev)


def event_seconds(P, ev, day_offset=0):
    """Seconds after local midnight of the *same* day as day_offset, rounded to
    the nearest whole minute, with method + user adjustments applied."""
    method_adj, user_adj = P.adj[ev]
    minutes = event_hours(P, ev, day_offset) * 60.0 + method_adj + user_adj
    return jround(minutes) * 60
