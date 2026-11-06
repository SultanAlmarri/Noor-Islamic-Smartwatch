#!/usr/bin/env python3
"""Builds Noor's bundled sound library from the verified sources.

Inputs
  <sources_dir>  output of fetch_commons.py (FLAC files + sources.json), published on the "sound-sources" release
  <alt_dir>      output of synthesize.py (WAV files of Noor's own alert sounds)
Outputs
  app/src/main/assets/sounds/   every file + library.json            (phone)
  wear/src/main/assets/sounds/  short Adhans + alert sounds + library.json  (watch; full Adhans are sent from the phone on demand)
  docs/SOUNDS.md                attribution and the exact edits made to each recording

Edits are limited to: keeping only the Adhan (cutting unrelated sound before/after), a short fade-in/out, a gentle
70 Hz high-pass, loudness normalisation to -16 LUFS, mono, Ogg Vorbis. Nothing is pitch-shifted, sped up or spliced.
The cut points were found by analysing each recording's voice-band energy and spectrogram (tools/sounds/README.md).

usage: build_library.py <sources_dir> <alt_dir> <repo_root>
"""
import json
import os
import subprocess
import sys

# Arabic place names use Western digits: the app converts digits to the user's numeral choice.
# Every cut is (start_s, end_s) in the original recording. "full" is omitted when the original does not contain the
# whole Adhan; such entries are offered as short versions only and say so.
ADHANS = [
    dict(region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=2012, id="makkah_maghrib_2012", src="makkah_maghrib_2012",
         title_en="Makkah", title_ar="مكة المكرمة",
         place_en="al-Masjid al-Haram · Maghrib, 2012", place_ar="المسجد الحرام · المغرب، 2012",
         categories=["renowned", "traditional"], fajr=False,
         full=(114.2, 290.7), short=(114.2, 146.3),
         note_en="Recorded among the worshippers in the Haram, so the crowd can be heard.",
         note_ar="سُجّل بين المصلّين في الحرم، لذا يُسمع صوت الحشود."),
    dict(region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=2013, id="makkah_2013", src="makkah_2013",
         title_en="Makkah (opening)", title_ar="مكة المكرمة (المطلع)",
         place_en="al-Masjid al-Haram · 2013", place_ar="المسجد الحرام · 2013",
         categories=["renowned", "traditional"], fajr=False,
         full=None, short=(7.8, 33.5),
         note_en="The original recording stops before the end of the Adhan, so only its opening is offered.",
         note_ar="التسجيل الأصلي ينقطع قبل نهاية الأذان، لذا يُقدَّم مطلعه فقط."),
    dict(region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=2018, id="madinah_2018", src="madinah_2018",
         title_en="Madinah (opening)", title_ar="المدينة المنورة (المطلع)",
         place_en="Madinah · 2018", place_ar="المدينة المنورة · 2018",
         categories=["renowned", "traditional"], fajr=False,
         full=None, short=(0.55, 30.0),
         note_en="The recording contains the opening takbir only.",
         note_ar="يحتوي التسجيل على التكبير الأول فقط."),
    dict(region="europe", country_en="Sweden", country_ar="السويد", year=2012, id="malmo_fajr_2012", src="malmo_fajr_2012",
         title_en="Malmö", title_ar="مالمو",
         place_en="Islamic Center Malmö, Sweden · 2012", place_ar="المركز الإسلامي في مالمو، السويد · 2012",
         categories=["fajr", "regional"], fajr=True,
         full=(4.6, 247.55), short=(4.6, 49.0),
         note_en="Includes “As-salatu khayrun min an-nawm”. The original recording ends during the last phrase; a short fade was added.",
         note_ar="يتضمن «الصلاة خير من النوم». ينتهي التسجيل الأصلي أثناء العبارة الأخيرة، فأضيف تلاشٍ قصير."),
    dict(region="asia", country_en="Kazakhstan", country_ar="كازاخستان", year=2014, id="shalqar_2014", src="shalqar_2014",
         title_en="Shalqar", title_ar="شالقار",
         place_en="Matey Qoqanuly Mosque, Kazakhstan · 2014", place_ar="مسجد ماتاي قوقانولي، كازاخستان · 2014",
         categories=["regional"], fajr=False,
         full=(2.8, 134.5), short=(2.8, 26.0), note_en=None, note_ar=None),
    dict(region="africa", country_en="Morocco", country_ar="المغرب", year=2019, id="casablanca_2019", src="casablanca_2019",
         title_en="Casablanca", title_ar="الدار البيضاء",
         place_en="Beside the Hassan II Mosque, Morocco · 2019", place_ar="بجوار مسجد الحسن الثاني، المغرب · 2019",
         categories=["regional"], fajr=False,
         full=(3.8, 95.9), short=(3.8, 34.7),
         note_en="An outdoor recording by the Atlantic; the sea and the city can be heard.",
         note_ar="تسجيل في الهواء الطلق قرب المحيط الأطلسي؛ يُسمع البحر والمدينة."),
    dict(region="middle_east", country_en="Türkiye", country_ar="تركيا", year=2025, id="istanbul_2025", src="istanbul_2025",
         title_en="Istanbul (street recording)", title_ar="إسطنبول (تسجيل من الشارع)",
         place_en="Istanbul, Türkiye · 2025", place_ar="إسطنبول، تركيا · 2025",
         categories=["regional"], fajr=False,
         full=None, short=(0.0, 50.0),
         note_en="A 50-second excerpt recorded in the street, with the city around it.",
         note_ar="مقطع من 50 ثانية سُجّل في الشارع مع أصوات المدينة."),
    # 0.9.5: chosen from the full Commons list (discover_commons.py), cut points from voice-band segmentation and
    # spectrograms like the first seven. "Short only" where the recording holds only the opening, or where the
    # ending could not be placed with certainty.
    dict(region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=None,
         id="madinah_prophets_mosque", src="madinah_prophets_mosque",
         title_en="Madinah", title_ar="المدينة المنورة",
         place_en="The Prophet's Mosque", place_ar="المسجد النبوي",
         categories=["renowned", "traditional"], fajr=False,
         full=(0.6, 186.4), short=(0.6, 34.5), note_en=None, note_ar=None),
    dict(region="middle_east", country_en="Iran", country_ar="إيران", year=2015,
         id="mahur_iran", src="mahur_iran",
         title_en="Iran · Dastgah Mahur", title_ar="إيران · دستگاه ماهور",
         place_en="Saeed Hatamzadeh-Varmazyar · 2015", place_ar="سعيد حاتم زاده ورمزيار · 2015",
         categories=["regional"], fajr=False,
         full=(3.3, 166.6), short=(3.3, 45.6),
         note_en="Sung in the Persian dastgah Mahur. Adhans in Iran usually follow the Shia wording, which adds phrases; listen before choosing it.",
         note_ar="بمقام «ماهور» الفارسي. الأذان في إيران يتبع عادة الصيغة الشيعية التي تضيف عبارات؛ استمع إليه قبل اختياره."),
    dict(region="middle_east", country_en="Iran", country_ar="إيران", year=None,
         id="sobhdel_iran", src="sobhdel_iran",
         title_en="Hussein Sobhdel (opening)", title_ar="حسين صبحدل (المطلع)",
         place_en="Iran", place_ar="إيران",
         categories=["regional"], fajr=False,
         full=None, short=(2.5, 43.0),
         note_en="Only the opening takbir is offered: the original continues with a long, ornate Adhan and further recordings.",
         note_ar="يُقدَّم التكبير الأول فقط: يستمر التسجيل الأصلي بأذان طويل مزخرف ثم بتسجيلات أخرى."),
    dict(region="middle_east", country_en="Iran", country_ar="إيران", year=2016,
         id="isfahan_2016", src="isfahan_2016",
         title_en="Isfahan (excerpt)", title_ar="أصفهان (مقطع)",
         place_en="Shah Mosque, Isfahan · 2016", place_ar="مسجد الشاه، أصفهان · 2016",
         categories=["regional"], fajr=False,
         full=None, short=(0.0, 42.4),
         note_en="Recited by a guide under the great dome, whose echo carries the voice.",
         note_ar="يرفعه مرشد تحت القبة الكبرى، وصداها يحمل الصوت."),
    dict(region="middle_east", country_en="Palestine", country_ar="فلسطين", year=2011,
         id="jaffa_2011", src="jaffa_2011",
         title_en="Jaffa (excerpt)", title_ar="يافا (مقطع)",
         place_en="Mahmoudiya Mosque, Jaffa · 2011", place_ar="مسجد المحمودية، يافا · 2011",
         categories=["regional"], fajr=False,
         full=None, short=(0.2, 38.5), note_en=None, note_ar=None),
    dict(region="africa", country_en="Morocco", country_ar="المغرب", year=None,
         id="marrakech_koutoubia", src="marrakech_koutoubia",
         title_en="Marrakech (excerpt)", title_ar="مراكش (مقطع)",
         place_en="Koutoubia Mosque", place_ar="جامع الكتبية",
         categories=["regional"], fajr=False,
         full=None, short=(6.0, 26.7), note_en=None, note_ar=None),
    dict(region="africa", country_en="Ghana", country_ar="غانا", year=2026,
         id="ghana_ramadan_2026", src="ghana_ramadan_2026",
         title_en="Ghana", title_ar="غانا",
         place_en="Ramadan 2026", place_ar="رمضان 2026",
         categories=["regional"], fajr=False,
         full=(0.5, 83.6), short=(0.5, 19.2), note_en="A brisk call, under two minutes.", note_ar="أذان سريع، أقل من دقيقتين."),
    dict(region="africa", country_en="Ghana", country_ar="غانا", year=2026,
         id="ghana_ramadan_2026_b", src="ghana_ramadan_2026_b",
         title_en="Ghana (opening)", title_ar="غانا (المطلع)",
         place_en="Ramadan 2026", place_ar="رمضان 2026",
         categories=["regional"], fajr=False,
         full=None, short=(0.7, 23.5), note_en=None, note_ar=None),
    dict(region="africa", country_en="Nigeria", country_ar="نيجيريا", year=2026,
         id="nigeria_2026", src="nigeria_2026",
         title_en="Nigeria", title_ar="نيجيريا",
         place_en="2026", place_ar="2026",
         categories=["regional"], fajr=False,
         full=(0.0, 64.8), short=(0.0, 19.6), note_en="A brisk call, about a minute.", note_ar="أذان سريع، نحو دقيقة."),
    dict(region=None, country_en=None, country_ar=None, year=2022,
         id="adhan_beautiful_2022", src="adhan_beautiful_2022",
         title_en="Recording 2022 · A", title_ar="تسجيل 2022 · أ",
         place_en="Place not stated by the recordist", place_ar="لم يذكر المسجِّل المكان",
         categories=["traditional"], fajr=False,
         full=(0.4, 152.5), short=(0.4, 30.6), note_en=None, note_ar=None),
    dict(region=None, country_en=None, country_ar=None, year=2022,
         id="adhan_andrewler_2022", src="adhan_andrewler_2022",
         title_en="Recording 2022 · B", title_ar="تسجيل 2022 · ب",
         place_en="Place not stated by the recordist", place_ar="لم يذكر المسجِّل المكان",
         categories=["traditional"], fajr=False,
         full=(0.4, 181.0), short=(0.4, 31.3), note_en=None, note_ar=None),
]

# Noor's own sounds (synthesize.py, CC0). Grouped by length for the picker.
ALTS = [
    ("two_ticks", "Two ticks", "نقرتان", "brief"),
    ("gentle_tone", "Gentle tone", "نغمة لطيفة", "brief"),
    ("soft_chime", "Soft chime", "جرس ناعم", "short"),
    ("subtle_bell", "Subtle bell", "جرس هادئ", "short"),
    ("melodic_chimes", "Melodic chimes", "أجراس لحنية", "short"),
    ("morning_birds", "Morning birds", "عصافير الصباح", "long"),
    ("calm_ambience", "Calm ambience", "أجواء هادئة", "long"),
    ("wooden_knock", "Wooden knock", "طرقات خشبية", "brief"),
    ("soft_pulse", "Soft pulse", "نبض هادئ", "brief"),
    ("rising_tones", "Rising tones", "نغمات صاعدة", "short"),
    ("water_drops", "Water drops", "قطرات ماء", "short"),
    ("dawn_bells", "Dawn bells", "أجراس الفجر", "short"),
    ("singing_bowl", "Singing bowl", "وعاء رنّان", "short"),
    ("wind_chimes", "Wind chimes", "أجراس الريح", "long"),
]

LUFS = -16.0
FADE_IN = 0.25


def run(args):
    subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         check=True, capture_output=True, text=True).stdout
    return round(float(out.strip()), 2)


def measure(src, start, end, filters):
    """First loudnorm pass: returns the measured values for an accurate linear second pass."""
    p = subprocess.run(["ffmpeg", "-hide_banner", "-ss", str(start), "-to", str(end), "-i", src, "-af",
                        f"{filters},loudnorm=I={LUFS}:TP=-1.5:LRA=18:print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True, check=True).stderr
    j = json.loads(p[p.rindex("{"):p.rindex("}") + 1])
    return j


def render(src, start, end, out, fade_out):
    length = end - start
    base = f"highpass=f=70,afade=t=in:d={FADE_IN},afade=t=out:st={max(0.0, length - fade_out):.2f}:d={fade_out}"
    m = measure(src, start, end, f"aformat=channel_layouts=mono,{base}")
    norm = (f"loudnorm=I={LUFS}:TP=-1.5:LRA=18:measured_I={m['input_i']}:measured_TP={m['input_tp']}:"
            f"measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true")
    run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-to", str(end), "-i", src, "-af",
         f"aformat=channel_layouts=mono,{base},{norm},aresample=44100", "-ac", "1", "-c:a", "libvorbis", "-q:a", "3",
         "-map_metadata", "-1", out])


def main(sources_dir, alt_dir, root):
    meta = {m["id"]: m for m in json.load(open(os.path.join(sources_dir, "sources.json"), encoding="utf-8"))["fetched"]}
    phone = os.path.join(root, "app/src/main/assets/sounds")
    watch = os.path.join(root, "wear/src/main/assets/sounds")
    for d in (phone, watch):
        os.makedirs(d, exist_ok=True)
        for f in os.listdir(d):
            os.remove(os.path.join(d, f))

    entries, doc = [], []
    for a in ADHANS:
        m = meta[a["src"]]
        src = os.path.join(sources_dir, a["src"] + ".flac")
        e = dict(id="adhan." + a["id"], kind="adhan", title_en=a["title_en"], title_ar=a["title_ar"],
                 place_en=a["place_en"], place_ar=a["place_ar"], categories=a["categories"], fajr=a["fajr"])
        for k in ("region", "country_en", "country_ar", "year"):
            if a.get(k): e[k] = a[k]
        changes = []
        if a["full"]:
            name = f"adhan_{a['id']}.ogg"
            render(src, *a["full"], os.path.join(phone, name), fade_out=1.5)
            e.update(file="sounds/" + name, seconds=duration(os.path.join(phone, name)))
            changes.append(f"full version: {a['full'][0]}–{a['full'][1]} s of the original")
        sname = f"adhan_{a['id']}_short.ogg"
        render(src, *a["short"], os.path.join(phone, sname), fade_out=2.0)
        os.link(os.path.join(phone, sname), os.path.join(watch, sname))
        e["short"] = dict(file="sounds/" + sname, seconds=duration(os.path.join(phone, sname)))
        changes.append(f"short version: {a['short'][0]}–{a['short'][1]} s")
        changes_txt = ("Audio only (video removed); " + "; ".join(changes) +
                       "; fade in/out, 70 Hz high-pass, loudness normalised to -16 LUFS, mono Ogg Vorbis.")
        e["credit"] = dict(author=m["artist"], licence=m["licence"], licence_url=m.get("licence_url") or "",
                           source=m["page"], changes=changes_txt)
        if a.get("note_en"):
            e["note_en"], e["note_ar"] = a["note_en"], a["note_ar"]
        if "SA" in m["licence"]:
            e["credit"]["derivative_licence"] = m["licence"]
        entries.append(e)
        doc.append((e, m, changes_txt))

    for sid, en, ar, cat in ALTS:
        wav = os.path.join(alt_dir, f"alt_{sid}.wav")
        name = f"alt_{sid}.ogg"
        run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", os.path.join(phone, name)])
        os.link(os.path.join(phone, name), os.path.join(watch, name))
        entries.append(dict(id="alt." + sid, kind="alt", title_en=en, title_ar=ar, categories=[cat],
                            file="sounds/" + name, seconds=duration(os.path.join(phone, name)),
                            credit=dict(author="Noor (original sound, tools/sounds/synthesize.py)", licence="CC0",
                                        licence_url="https://creativecommons.org/publicdomain/zero/1.0/", source="", changes="")))

    lib = dict(version=1, sounds=entries)
    for d in (phone, watch):
        with open(os.path.join(d, "library.json"), "w", encoding="utf-8") as f:
            json.dump(lib, f, ensure_ascii=False, indent=1)

    with open(os.path.join(root, "docs/SOUNDS.md"), "w", encoding="utf-8") as f:
        f.write("# Sounds in Noor\n\nGenerated by `tools/sounds/build_library.py`; do not edit by hand.\n\n")
        f.write("## Adhan recordings\n\nAll from Wikimedia Commons, each under a licence that allows redistribution. "
                "`tools/sounds/fetch_commons.py` re-checks every licence and file checksum on Commons before use.\n\n")
        for e, m, ch in doc:
            f.write(f"### {e['title_en']} — {e['place_en']}\n\n")
            f.write(f"- Recording: [{m['title']}]({m['page']})\n- Author: {m['artist']}\n")
            f.write(f"- Licence: {m['licence']}" + (f" ({m['licence_url']})" if m.get('licence_url') else "") + "\n")
            f.write(f"- Provenance: {m['provenance']}\n- Changes made by Noor: {ch}\n")
            if "SA" in m["licence"]:
                f.write(f"- The edited audio in Noor is shared under the same licence ({m['licence']}).\n")
            if e.get("note_en"):
                f.write(f"- Note: {e['note_en']}\n")
            f.write(f"- SHA-1 of the original file: `{m['sha1']}`\n\n")
        f.write("## Considered and not used\n\n"
                "- *Islamic call to worship.oga* (CC BY-SA 4.0): analysis shows continuous speech, matching its description "
                "(an explanation of the Adhan), not a recitation.\n"
                "- *Call to prayer by Sabah Fakhry.mp3*: tagged public domain, but it is a commercial recording by a singer "
                "who died in 2021; the claim is not credible.\n"
                "- *The Adhan – Muslim Call to Prayer – Aaqib Azeez.mp3*: the uploader's authorship could not be confirmed.\n"
                "- *Time of Adhan, Great Mosque of Mecca – Jan 6, 2018*: the recording starts during the first phrase, so the "
                "Adhan would begin mid-word.\n"
                "- *Adhan from the Ali Matan Mosque, Hargeisa* (ASC Leiden): the recording starts in the middle of the Adhan.\n"
                "- *The sound of Syria (2007)*: the Damascus section is an edited excerpt, mostly street sound, starting mid-call.\n"
                "- *Hminsec150 Konya*: its licence is still awaiting review on Commons.\n"
                "- *Oldest recording of the Quran 1885*: Commons itself disputes its date and whether it is the Adhan.\n"
                "- Iqamah recordings, spoken readings of the Adhan, and clips dominated by crowd or protest noise.\n\n")
        f.write("## Alert sounds\n\n" + ", ".join(en for _, en, _, _ in ALTS) + " are original sounds generated from code by "
                "`tools/sounds/synthesize.py` (no samples). They are released under CC0.\n")
    print(f"{len(entries)} sounds; phone {sum(os.path.getsize(os.path.join(phone, x)) for x in os.listdir(phone)) // 1024} KB, "
          f"watch {sum(os.path.getsize(os.path.join(watch, x)) for x in os.listdir(watch)) // 1024} KB")


if __name__ == "__main__":
    main(*sys.argv[1:4])
