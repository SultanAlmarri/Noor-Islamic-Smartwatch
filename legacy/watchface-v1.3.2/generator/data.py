"""Static data: cities, calculation methods, settings options."""

# id, English name, Arabic name, latitude, longitude, elevation (m)
CITIES = [
    # Dubai reference point calibrated against the IACAD-published timetable
    # (see reference_data/ and docs/PRAYER_ACCURACY.md).
    ("dxb", "Dubai", "دبي", 25.05, 55.30, 5),
    ("dxbd", "Dubai - Deira", "دبي - ديرة", 25.2697, 55.3095, 5),
    ("dxbc", "Dubai - Downtown", "دبي - وسط المدينة", 25.1972, 55.2744, 5),
    ("dxbm", "Dubai - Marina", "دبي - المرسى", 25.0805, 55.1403, 5),
    ("hat", "Hatta", "حتا", 24.7969, 56.1180, 380),
    ("auh", "Abu Dhabi", "أبوظبي", 24.4539, 54.3773, 5),
    ("shj", "Sharjah", "الشارقة", 25.3463, 55.4209, 5),
    ("ajm", "Ajman", "عجمان", 25.4052, 55.5136, 5),
    ("uaq", "Umm Al Quwain", "أم القيوين", 25.5647, 55.5552, 5),
    ("rak", "Ras Al Khaimah", "رأس الخيمة", 25.8007, 55.9762, 5),
    ("fuj", "Fujairah", "الفجيرة", 25.1288, 56.3265, 5),
    ("ain", "Al Ain", "العين", 24.2075, 55.7447, 290),
    ("ruh", "Riyadh", "الرياض", 24.7136, 46.6753, 612),
    ("jed", "Jeddah", "جدة", 21.4858, 39.1925, 12),
    ("mak", "Makkah", "مكة المكرمة", 21.4225, 39.8262, 277),
    ("med", "Madinah", "المدينة المنورة", 24.4686, 39.6142, 608),
    ("dmm", "Dammam", "الدمام", 26.4207, 50.0888, 10),
    ("doh", "Doha", "الدوحة", 25.2854, 51.5310, 10),
    ("kwi", "Kuwait City", "الكويت", 29.3759, 47.9774, 10),
    ("bah", "Manama", "المنامة", 26.2235, 50.5876, 5),
    ("mct", "Muscat", "مسقط", 23.5880, 58.3829, 15),
    ("cai", "Cairo", "القاهرة", 30.0444, 31.2357, 23),
    ("amm", "Amman", "عمّان", 31.9454, 35.9284, 800),
    ("ist", "Istanbul", "إسطنبول", 41.0082, 28.9784, 40),
    ("khi", "Karachi", "كراتشي", 24.8607, 67.0011, 10),
    ("lhe", "Lahore", "لاهور", 31.5204, 74.3587, 217),
    ("del", "New Delhi", "نيودلهي", 28.6139, 77.2090, 216),
    ("bom", "Mumbai", "مومباي", 19.0760, 72.8777, 14),
    ("dac", "Dhaka", "دكا", 23.8103, 90.4125, 4),
    ("kul", "Kuala Lumpur", "كوالالمبور", 3.1390, 101.6869, 60),
    ("jkt", "Jakarta", "جاكرتا", -6.2088, 106.8456, 8),
    ("lon", "London", "لندن", 51.5074, -0.1278, 11),
    ("par", "Paris", "باريس", 48.8566, 2.3522, 35),
    ("nyc", "New York", "نيويورك", 40.7128, -74.0060, 10),
    ("tor", "Toronto", "تورونتو", 43.6532, -79.3832, 76),
]

# Calculation methods.
#   fajr: Fajr depression angle (deg)
#   isha: Isha depression angle (deg) or None when isha_min is used
#   isha_min: Isha = Maghrib + N minutes (Umm al-Qura / Qatar style)
#   maghrib: Maghrib depression angle (deg) or None (= sunset)
#   adj: built-in minute offsets of the method (sunrise, dhuhr, asr, maghrib, isha, fajr)
METHODS = [
    dict(id="dubai", en="Dubai / IACAD (18.2°)", ar="دبي / دائرة الشؤون الإسلامية",
         fajr=18.2, isha=18.2, isha_min=None, maghrib=None,
         # Official-style precaution minutes; Asr +1 fitted to the IACAD table
         adj=dict(fajr=0, sunrise=-3, dhuhr=3, asr=1, maghrib=3, isha=0)),
    dict(id="mwl", en="Muslim World League", ar="رابطة العالم الإسلامي",
         fajr=18.0, isha=17.0, isha_min=None, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=1, asr=0, maghrib=0, isha=0)),
    dict(id="uqu", en="Umm al-Qura (Makkah)", ar="أم القرى (مكة)",
         fajr=18.5, isha=None, isha_min=90, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=0, asr=0, maghrib=0, isha=0)),
    dict(id="egy", en="Egyptian Authority", ar="الهيئة المصرية العامة للمساحة",
         fajr=19.5, isha=17.5, isha_min=None, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=1, asr=0, maghrib=0, isha=0)),
    dict(id="kar", en="Karachi", ar="جامعة العلوم الإسلامية بكراتشي",
         fajr=18.0, isha=18.0, isha_min=None, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=1, asr=0, maghrib=0, isha=0)),
    dict(id="isna", en="North America (ISNA)", ar="أمريكا الشمالية (ISNA)",
         fajr=15.0, isha=15.0, isha_min=None, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=1, asr=0, maghrib=0, isha=0)),
    dict(id="kwt", en="Kuwait", ar="الكويت",
         fajr=18.0, isha=17.5, isha_min=None, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=0, asr=0, maghrib=0, isha=0)),
    dict(id="qat", en="Qatar", ar="قطر",
         fajr=18.0, isha=None, isha_min=90, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=0, asr=0, maghrib=0, isha=0)),
    dict(id="sgp", en="Singapore / MUIS", ar="سنغافورة",
         fajr=20.0, isha=18.0, isha_min=None, maghrib=None,
         adj=dict(fajr=0, sunrise=0, dhuhr=1, asr=0, maghrib=0, isha=0)),
    dict(id="tur", en="Türkiye (Diyanet)", ar="تركيا (ديانت)",
         fajr=18.0, isha=17.0, isha_min=None, maghrib=None,
         adj=dict(fajr=0, sunrise=-7, dhuhr=5, asr=4, maghrib=7, isha=0)),
    dict(id="teh", en="Tehran (Geophysics)", ar="طهران",
         fajr=17.7, isha=14.0, isha_min=None, maghrib=4.5,
         adj=dict(fajr=0, sunrise=0, dhuhr=0, asr=0, maghrib=0, isha=0)),
]

EVENTS = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]
# Approximate local *solar* hour of each event, used only to pick the moment
# at which the sun's declination / equation of time are evaluated.
EVENT_SOLAR_HOUR = dict(fajr=5.0, sunrise=6.0, dhuhr=12.0, asr=15.0, maghrib=18.0, isha=19.5)

EVENT_NAMES = {
    "fajr": ("FAJR", "الفجر"),
    "sunrise": ("SHURUQ", "الشروق"),
    "dhuhr": ("DHUHR", "الظهر"),
    "asr": ("ASR", "العصر"),
    "maghrib": ("MAGHRIB", "المغرب"),
    "isha": ("ISHA", "العشاء"),
}

ADJ_RANGE = list(range(-10, 11))   # per-event user minute adjustment


def adj_id(v):
    return ("p%d" % v) if v >= 0 else ("m%d" % -v)
