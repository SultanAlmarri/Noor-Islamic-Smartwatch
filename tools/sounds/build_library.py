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
    dict(id="makkah_maghrib_2012", src="makkah_maghrib_2012",
         title_en="Makkah", title_ar="مكة المكرمة",
         place_en="al-Masjid al-Haram · Maghrib, 2012", place_ar="المسجد الحرام · المغرب، 2012",
         categories=["renowned", "traditional"], fajr=False,
         full=(114.2, 290.7), short=(114.2, 146.3),
         note_en="Recorded among the worshippers in the Haram, so the crowd can be heard.",
         note_ar="سُجّل بين المصلّين في الحرم، لذا يُسمع صوت الحشود."),
    dict(id="makkah_2013", src="makkah_2013",
         title_en="Makkah (opening)", title_ar="مكة المكرمة (المطلع)",
         place_en="al-Masjid al-Haram · 2013", place_ar="المسجد الحرام · 2013",
         categories=["renowned", "traditional"], fajr=False,
         full=None, short=(7.8, 33.5),
         note_en="The original recording stops before the end of the Adhan, so only its opening is offered.",
         note_ar="التسجيل الأصلي ينقطع قبل نهاية الأذان، لذا يُقدَّم مطلعه فقط."),
    dict(id="madinah_2018", src="madinah_2018",
         title_en="Madinah (opening)", title_ar="المدينة المنورة (المطلع)",
         place_en="Madinah · 2018", place_ar="المدينة المنورة · 2018",
         categories=["renowned", "traditional"], fajr=False,
         full=None, short=(0.55, 30.0),
         note_en="The recording contains the opening takbir only.",
         note_ar="يحتوي التسجيل على التكبير الأول فقط."),
    dict(id="malmo_fajr_2012", src="malmo_fajr_2012",
         title_en="Malmö", title_ar="مالمو",
         place_en="Islamic Center Malmö, Sweden · 2012", place_ar="المركز الإسلامي في مالمو، السويد · 2012",
         categories=["fajr", "regional"], fajr=True,
         full=(4.6, 247.55), short=(4.6, 49.0),
         note_en="Includes “As-salatu khayrun min an-nawm”. The original recording ends during the last phrase; a short fade was added.",
         note_ar="يتضمن «الصلاة خير من النوم». ينتهي التسجيل الأصلي أثناء العبارة الأخيرة، فأضيف تلاشٍ قصير."),
    dict(id="shalqar_2014", src="shalqar_2014",
         title_en="Shalqar", title_ar="شالقار",
         place_en="Matey Qoqanuly Mosque, Kazakhstan · 2014", place_ar="مسجد ماتاي قوقانولي، كازاخستان · 2014",
         categories=["regional"], fajr=False,
         full=(2.8, 134.5), short=(2.8, 26.0), note_en=None, note_ar=None),
    dict(id="casablanca_2019", src="casablanca_2019",
         title_en="Casablanca", title_ar="الدار البيضاء",
         place_en="Beside the Hassan II Mosque, Morocco · 2019", place_ar="بجوار مسجد الحسن الثاني، المغرب · 2019",
         categories=["regional"], fajr=False,
         full=(3.8, 95.9), short=(3.8, 34.7),
         note_en="An outdoor recording by the Atlantic; the sea and the city can be heard.",
         note_ar="تسجيل في الهواء الطلق قرب المحيط الأطلسي؛ يُسمع البحر والمدينة."),
    dict(id="istanbul_2025", src="istanbul_2025",
         title_en="Istanbul (street recording)", title_ar="إسطنبول (تسجيل من الشارع)",
         place_en="Istanbul, Türkiye · 2025", place_ar="إسطنبول، تركيا · 2025",
         categories=["regional"], fajr=False,
         full=None, short=(0.0, 50.0),
         note_en="A 50-second excerpt recorded in the street, with the city around it.",
         note_ar="مقطع من 50 ثانية سُجّل في الشارع مع أصوات المدينة."),
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
                "- *The Adhan – Muslim Call to Prayer – Aaqib Azeez.mp3*: the uploader's authorship could not be confirmed.\n\n")
        f.write("## Alert sounds\n\nTwo ticks, gentle tone, soft chime, subtle bell, melodic chimes, morning birds and calm "
                "ambience are original sounds generated from code by `tools/sounds/synthesize.py` (no samples). "
                "They are released under CC0.\n")
    print(f"{len(entries)} sounds; phone {sum(os.path.getsize(os.path.join(phone, x)) for x in os.listdir(phone)) // 1024} KB, "
          f"watch {sum(os.path.getsize(os.path.join(watch, x)) for x in os.listdir(watch)) // 1024} KB")


if __name__ == "__main__":
    main(*sys.argv[1:4])
