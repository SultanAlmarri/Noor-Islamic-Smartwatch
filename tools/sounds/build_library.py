#!/usr/bin/env python3
"""Builds Minhad's bundled sound library from the verified sources.

The library is the set the owner asked for: an Adhan voice for each of a few places (the whole Adhan and its opening,
exactly the first "Allahu Akbar, Allahu Akbar", 27 November 2026) and, since 0.26, five short alert sounds (the owner,
27 November 2026: "short alerts, like beep beep ... two birds or some birds, chirp chirp ... I don't want any AI
generated sounds. I want actual sound effects"). Nothing is synthesised or generated: every sound is a recording,
credited in docs/SOUNDS.md, which this script writes.

Inputs
  <sources_dir>  output of fetch_commons.py (FLAC files + sources.json): Wikimedia Commons files, files from licensed
                 archives (US National Park Service, Freesound) and the owner's own recordings (tools/sounds/owner)
Outputs
  app/src/main/assets/sounds/   every file + library.json                                (phone)
  wear/src/main/assets/sounds/  Adhan openings + every alert sound + library.json
                                (watch; whole Adhans are sent from the phone on demand)
  alerts/src/main/res/raw/noor_backup_adhan.ogg   the default Adhan's opening, byte for byte (BackupSoundTest)
  docs/SOUNDS.md                attribution and the exact edits made to each recording

Edits are limited to: keeping only the wanted part (cut in silence or in a pause, never inside a sound), a short
fade-in/out, a high-pass (70 Hz for voices; for the alert sounds, below the sound itself, to take away rumble), one
gain to the target loudness, mono, Ogg Vorbis; for the alert sounds only, where one gain would push a short peak past
the limit, a gentle peak limiter. Nothing is pitch-shifted, sped up, looped, spliced or denoised. Cut points were found
from each recording's band energy, spectrograms and, for the Adhan openings, a speech recogniser (tools/sounds/README.md).

usage: build_library.py <sources_dir> <repo_root> [--keep-whole]
  --keep-whole   do not render the whole Adhans again: keep the files already in the repository (their lengths are
                 read from them). Used in 0.26, which re-cut only the openings and replaced the alert sounds.
"""
import json
import os
import shutil
import subprocess
import sys

# Every cut is (start_s, end_s) in the original recording.
#   "full"  the whole Adhan (None when the recording does not hold it: the entry is then its opening only).
#   "short" the opening: exactly the first two takbirs, "Allahu Akbar, Allahu Akbar", ending in the pause after the
#           second, never cut through a sound. 0.26 (27 November 2026): found per voice from the voice band's energy
#           (250-2500 Hz, 50 ms frames), spectrograms and a speech recogniser (faster-whisper "medium", Arabic, word
#           times); see "How the openings were cut" in docs/SOUNDS.md.
#   "short_fade" (fade-in, fade-out) of the opening, in seconds.
ADHANS = [
    dict(id="makkah", src="makkah_maghrib_2012", fajr=False,
         title_en="Makkah", title_ar="مكة",
         place_en="al-Masjid al-Haram · Maghrib, 2012", place_ar="المسجد الحرام · المغرب، 2012",
         region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=2012,
         full=(114.2, 290.7), short=(114.45, 128.0), short_fade=(0.2, 0.3),
         how=("The muezzin sings both takbirs in one breath, 114.65-127.1 s; the recogniser heard \"Allahu Akbar\" "
              "twice in it (about 119-122 s and 124-127 s) and twice again in the next breath (130.8-144.2 s). The "
              "opening ends in the 3.7 s pause between the two breaths, where only the crowd and the Haram's echo are "
              "heard."),
         note_en="Recorded among the worshippers in the Haram, so the crowd can be heard between the phrases.",
         note_ar="سُجّل بين المصلّين في الحرم، لذا يُسمع صوت الحشود بين الجمل."),
    dict(id="madinah", src="madinah_prophets_mosque", fajr=False,
         title_en="Madinah", title_ar="المدينة",
         place_en="The Prophet's Mosque", place_ar="المسجد النبوي",
         region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=None,
         full=(0.6, 186.4), short=(0.9, 18.5), short_fade=(0.1, 0.3),
         how=("One breath, 1.05-15.9 s, then the mosque's echo dies away until about 19.5 s; the recogniser heard "
              "\"الله أكبر الله أكبر\" in it, and again in the next breath (20.4-33.9 s). The opening ends in the "
              "echo's tail, 30 dB below the voice."),
         note_en=None, note_ar=None),
    dict(id="egypt", src="egypt_khan_khalili", fajr=False,
         title_en="Egypt", title_ar="مصر",
         place_en="Khan el-Khalili, Cairo", place_ar="خان الخليلي، القاهرة",
         region="africa", country_en="Egypt", country_ar="مصر", year=None,
         full=(90.0, 295.3), full_detail="the Adhan alone, without the announcements before it and the salawat after it",
         short=(90.1, 96.1), short_fade=(0.1, 0.25),
         how=("The first two takbirs are sung quickly, 90.3-95.7 s (the recogniser, on that phrase alone: \"الله أكبر "
              "الله أكبر\", word confidence 0.6-1.0); after a 0.8 s pause the second two are drawn out over 16 s "
              "(96.5-112.5 s), and the first shahada begins at 116.6 s. Until 0.25 the opening held all four takbirs "
              "(90.0-112.6 s)."),
         note_en="Recorded on a rooftop in old Cairo, so the city can be heard around the voice.",
         note_ar="سُجّل على سطح في القاهرة القديمة، لذا تُسمع المدينة حول الصوت."),
    # The owner's recordings (16 November 2026). Their whole versions were made by hand that day with these edits
    # ("silence trimmed at both ends"); the cut points below reproduce them to within a few hundredths of a second.
    dict(id="dubai", src="dubai", fajr=False, owner=True,
         title_en="Dubai", title_ar="دبي",
         place_en="Abdulrahman Al-Hindi", place_ar="عبد الرحمن الهندي",
         region="gulf", country_en="United Arab Emirates", country_ar="الإمارات", year=2026, categories=["renowned"],
         full=(0.79, 129.24), short=(0.76, 12.8), short_fade=(0.05, 0.3),
         how=("One breath, 0.8-11.3 s, its echo fading until 13.1 s; the recogniser heard \"الله أكبر الله أكبر\" in "
              "it and in the next breath (13.2-23.6 s), then the two shahadas. The opening ends in the echo, 25 dB "
              "below the voice."),
         note_en=None, note_ar=None),
    dict(id="dubai_fajr", src="dubai_fajr", fajr=True, owner=True,
         title_en="Dubai", title_ar="دبي",
         place_en="Abdulrahman Al-Hindi · Fajr", place_ar="عبد الرحمن الهندي · الفجر",
         region="gulf", country_en="United Arab Emirates", country_ar="الإمارات", year=2026,
         categories=["renowned", "fajr"],
         full=(0.1, 183.79), short=(0.08, 11.85), short_fade=(0.05, 0.25),
         how=("One breath, 0.15-10.3 s, its echo fading until 11.9 s; the recogniser heard \"الله أكبر الله أكبر\" "
              "in it and in the next breath (12.0-20.6 s), then the two shahadas."),
         note_en=None, note_ar=None),
]

# Places the owner asked for that have no recording here yet, and why (shown in docs/SOUNDS.md). The app offers
# them as "add your own recording".
MISSING = [
    ("Al-Quds", "القدس",
     "No freely licensed recording of the Adhan of al-Masjid al-Aqsa was found, and no Jerusalem recording could "
     "be downloaded and checked here. The one candidate, the Adhan heard at the Church of the Holy Sepulchre "
     "in the Old City (Freesound 191866, Zabuhailo, CC BY 4.0, 109 s), was fetched on GitHub and examined on 16 "
     "November 2026: a speech recogniser heard no phrase of the Adhan in it, so it is not used. Examined and not usable (16 "
     "November 2026): on Wikimedia Commons, a 15-second clip heard from a distance at the Garden Tomb and a "
     "60-second travel video from the Haram (Neollene Films, 2017: too short to hold a whole Adhan); radio "
     "aporee field recordings on the Internet Archive from the Golden Gate (2014: the end of a prayer over a "
     "loudspeaker), Pisgat Ze'ev (2016: distant calls with gunfire), the Lion's Gate (2015: street sound, no "
     "Adhan), the Notre Dame rooftop at sunset (2013: several distant mosques, 67 s), Bethlehem (2023: distant, "
     "behind voices, 64 s) and Ramallah (2019, CC BY 3.0, Maghrib and Fajr from a window: far away, many mosques "
     "at once, and not Jerusalem; a speech recogniser heard no words in either). Freesound's \"Palestine muezzin\" "
     "(nicotep) is CC BY-NC; other uploads on the Internet Archive are broadcast or YouTube copies whose "
     "uploader does not hold the rights."),
]

# 0.26: short alert sounds, real recordings. The owner asked for five, beeps and birds: two beeps of real devices and
# three birds. (A first set of four birds waited for the beeps, which Commons' file host refused to this machine; the
# beeps were fetched by GitHub's fetch-sounds workflow. The Red-breasted Nuthatch made way for them.)
#   "cut"      (start_s, end_s) in the original
#   "fade"     (fade-in, fade-out) in seconds
#   "highpass" Hz, below the sound itself
#   "replaces" the ids of the 0.18 effects whose users get this sound (SoundIds.RENAMED in core/prayer must agree)
EFFECTS = [
    dict(id="beeps", src="beep_microwave", category="beep", replaces=["fx.rain"],
         title_en="Three beeps", title_ar="ثلاث صفّارات",
         place_en="A microwave oven, its timer done", place_ar="فرن ميكروويف انتهى مؤقّته",
         cut=(28.15, 31.15), fade=(0.03, 0.2), highpass=1200,
         note_en="A real microwave oven's three beeps, “beep, beep, beep”.",
         note_ar="ثلاث صفّارات حقيقية من فرن ميكروويف.",
         how=("The three beeps that end the 43 s recording of a microwave at work (28.21-28.83, 29.21-29.84 and "
              "30.22-30.84 s, near 2.4 kHz with its harmonics), the room 56 dB below them. Starts 0.06 s before the "
              "first beep and ends 0.3 s after the third, in the room's quiet; the oven's hum below 1.2 kHz is "
              "filtered out. Fetched by GitHub's fetch-sounds workflow (Commons refused this machine).")),
    dict(id="alarm_clock", src="beep_alarm_clock_01", category="beep", replaces=["fx.stream"],
         title_en="Alarm clock", title_ar="منبّه",
         place_en="A bedside alarm clock, France", place_ar="منبّه بجانب السرير، فرنسا",
         cut=(2.34, 3.98), fade=(0.03, 0.12), highpass=1200,
         note_en="A real alarm clock beeping in fours, twice: “beep-beep-beep-beep”.",
         note_ar="منبّه حقيقي يصفّر أربعًا، مرتين.",
         how=("The first two groups of four beeps (2.39-2.86 and 3.39-3.86 s, near 2 kHz with harmonics up to 14 kHz), "
              "the room 56 dB below them. Starts 0.05 s before the first beep and ends 0.12 s after the eighth, before "
              "the third group. Fetched by GitHub's fetch-sounds workflow (Commons refused this machine).")),
    dict(id="chickadee", src="chickadee_romo", category="bird", replaces=["fx.birds"],
         title_en="Chickadee", title_ar="القُرقُف",
         place_en="Two calls, Rocky Mountain National Park", place_ar="نداءان، متنزه جبال روكي الوطني",
         cut=(25.36, 29.3), fade=(0.04, 0.15), highpass=2000,
         note_en="A Mountain Chickadee calls “chick-a-dee-dee” twice.",
         note_ar="طائر القرقف الجبلي ينادي مرتين.",
         how=("The cleanest pair of calls in the 51 s recording (the background 50 dB below the calls; the birds' sound "
              "lies between 3.5 and 11 kHz, the rumble below 2 kHz is filtered out). Starts 0.05 s before the first "
              "call and ends 0.2 s after the second, in silence.")),
    dict(id="pip_pip", src="nuthatch_pygmy_romo", category="bird", replaces=["fx.rooster"],
         title_en="Pip-pip", title_ar="زقزقة",
         place_en="Pygmy nuthatches, Rocky Mountain National Park", place_ar="طيور الخوّاص القزمة، متنزه جبال روكي الوطني",
         cut=(1.1, 3.0), fade=(0.05, 0.1), highpass=2500,
         note_en="Small birds chirping “pip-pip, pip-pip”.", note_ar="طيور صغيرة تزقزق مرتين ثم مرتين.",
         how=("Two quick pairs of high chirps and two single ones (4-5.5 kHz), the background about 45 dB below them. "
              "Cut in the silences before the first chirp and before the next one (3.1 s).")),
    dict(id="solitaire", src="solitaire_yell", category="bird", replaces=["fx.waves"],
         title_en="Whistle", title_ar="صفير طائر",
         place_en="Townsend's solitaire, Yellowstone National Park", place_ar="طائر السوليتير، متنزه يلوستون الوطني",
         cut=(3.92, 5.5), fade=(0.04, 0.15), highpass=1500,
         note_en="A mountain thrush whistles one clear note, twice: “tew … tew”.",
         note_ar="طائر جبلي يصفّر نغمة واحدة صافية مرتين.",
         how=("Two whistled notes 0.9 s apart (4.00-4.12 s and 4.88-5.00 s, near 2.6 kHz), the background 45-50 dB "
              "below them; the closest pair in the 16 s recording (the others are 1.4 s apart). Cut in the silence "
              "before the first note and 0.5 s after the second.")),
]

# The 0.18 sound effects, replaced in 0.26; listed in docs/SOUNDS.md so their history stays readable.
RETIRED_EFFECTS = [
    ("fx.birds", "Birds", "Dawn chorus, Yellowstone National Park (National Park Service, public domain)"),
    ("fx.rooster", "Rooster", "A farm in Houx, France (Vincent Duseigne, radio aporee, CC BY 3.0)"),
    ("fx.waves", "Sea waves", "A rocky shore in Yilan, Taiwan (Wu Tsan-Cheng, radio aporee, CC BY 3.0)"),
    ("fx.rain", "Rain", "Steady rain in Taitung, Taiwan (Wu Tsan-Cheng, radio aporee, CC BY 3.0)"),
    ("fx.stream", "Stream", "A mountain stream, Rocky Mountain National Park (Jacob Job / NPS, public domain)"),
]

DEFAULT_ADHAN = "makkah"   # its opening is also alerts/res/raw/noor_backup_adhan.ogg
LUFS = -16.0               # the Adhans
TRUE_PEAK = -1.5
FX_LUFS = -17.0            # the alert sounds (0.26)
FX_TRUE_PEAK = -1.5


def run(args):
    subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         check=True, capture_output=True, text=True).stdout
    return round(float(out.strip()), 2)


def measure(src, filters, lufs=LUFS, tp=TRUE_PEAK):
    """Integrated loudness and true peak of the edited (but not yet amplified) audio."""
    p = subprocess.run(["ffmpeg", "-hide_banner", "-i", src, "-af",
                        f"{filters},loudnorm=I={lufs}:TP={tp}:LRA=20:print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True, check=True).stderr
    j = json.loads(p[p.rindex("{"):p.rindex("}") + 1])
    return float(j["input_i"]), float(j["input_tp"])


def channels(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels",
                          "-of", "csv=p=0", path], check=True, capture_output=True, text=True).stdout
    return int(out.strip())


def render(src, start, end, out, fade_in, fade_out, highpass, quality, lufs=LUFS, tp=TRUE_PEAK, limit=False):
    """Cut, fade, high-pass, then one linear gain: to `lufs`, or less if the true peak would pass `tp`. With `limit`
    (the alert sounds), the gain goes to `lufs` and a gentle limiter (oversampled, 5 ms attack) holds the peaks below
    `tp` instead. Returns (gain_db, limited).

    The cut is first written as mono 32-bit float (left and right averaged), so the measuring pass and the final pass
    read exactly the same samples and the gain lands where it was computed."""
    length = end - start
    cut = out + ".cut.wav"
    run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-to", str(end), "-i", src, "-vn",
         "-af", "pan=mono|c0=0.5*c0+0.5*c1" if channels(src) == 2 else "anull",
         "-ac", "1", "-c:a", "pcm_f32le", cut])
    base = (f"highpass=f={highpass},afade=t=in:d={fade_in},"
            f"afade=t=out:st={max(0.0, length - fade_out):.3f}:d={fade_out}")
    loudness, peak = measure(cut, base, lufs, tp)
    limited = limit and peak + (lufs - loudness) > tp
    gain = (lufs - loudness) if limit else min(lufs - loudness, tp - peak)
    chain = f"{base},volume={gain:.2f}dB"
    if limited:
        ceiling = 10 ** ((tp - 0.5) / 20)
        chain += (f",aresample=176400,alimiter=limit={ceiling:.4f}:attack=5:release=60:level=false:asc=1,"
                  f"aresample=44100")
    run(["ffmpeg", "-v", "error", "-y", "-i", cut, "-af", f"{chain},aresample=44100",
         "-c:a", "libvorbis", "-q:a", str(quality), "-map_metadata", "-1", out])
    os.remove(cut)
    return gain, limited


def credit(m, changes, owner=False):
    source = ("owner:tools/sounds/" + m["path"]) if owner else m["page"]
    c = dict(author=m["artist"] + ("; recording supplied by the app's owner" if owner else ""), licence=m["licence"],
             licence_url=m.get("licence_url") or "", source=source)
    if owner:
        c["provenance"] = m["provenance"]
    c["changes"] = changes
    if "SA" in m["licence"]:
        c["derivative_licence"] = m["licence"]
    return c


def fmt(t):
    return f"{t:.2f}".rstrip("0").rstrip(".") if t != int(t) else f"{t:.1f}"


def main(sources_dir, root, keep_whole=False):
    meta = {m["id"]: m for m in json.load(open(os.path.join(sources_dir, "sources.json"), encoding="utf-8"))["fetched"]}
    manifest = {m["id"]: m for m in json.load(open(os.path.join(root, "tools/sounds/adhan_sources.json"),
                                                   encoding="utf-8"))["files"]}
    for k, m in meta.items():   # (owner's files: their path in the repository, for the credit)
        if m.get("kind") == "local":
            m["path"] = manifest[k]["path"]
    phone = os.path.join(root, "app/src/main/assets/sounds")
    watch = os.path.join(root, "wear/src/main/assets/sounds")
    keep = {f"adhan_{a['id']}.ogg" for a in ADHANS if a["full"]} if keep_whole else set()
    for d in (phone, watch):
        os.makedirs(d, exist_ok=True)
        for f in os.listdir(d):
            if not (d == phone and f in keep):
                os.remove(os.path.join(d, f))

    entries, doc = [], []
    for a in ADHANS:
        m = meta[a["src"]]
        owner = a.get("owner", False)
        src = os.path.join(sources_dir, a["src"] + ".flac")
        e = dict(id="adhan." + a["id"], kind="adhan", title_en=a["title_en"], title_ar=a["title_ar"],
                 place_en=a["place_en"], place_ar=a["place_ar"],
                 categories=a.get("categories") or (["renowned", "traditional"] + (["fajr"] if a["fajr"] else [])),
                 fajr=a["fajr"])
        for k in ("region", "country_en", "country_ar", "year"):
            if a.get(k):
                e[k] = a[k]
        changes = []
        if a["full"]:
            name = f"adhan_{a['id']}.ogg"
            if name not in keep:
                render(src, *a["full"], os.path.join(phone, name), 0.25, 1.5, 70, 3)
            e.update(file="sounds/" + name, seconds=duration(os.path.join(phone, name)))
            changes.append(f"full version: {fmt(a['full'][0])}–{fmt(a['full'][1])} s of the original"
                           + (f" ({a['full_detail']})" if a.get("full_detail") else ""))
        sname = f"adhan_{a['id']}_short.ogg"
        fi, fo = a["short_fade"]
        render(src, *a["short"], os.path.join(phone, sname), fi, fo, 70, 3)
        shutil.copyfile(os.path.join(phone, sname), os.path.join(watch, sname))
        e["short"] = dict(file="sounds/" + sname, seconds=duration(os.path.join(phone, sname)))
        changes.append(f"opening (“Allahu Akbar, Allahu Akbar”, the first two takbirs, cut in the pause after the "
                       f"second): {fmt(a['short'][0])}–{fmt(a['short'][1])} s"
                       + ("" if owner else ", changed: cut to the opening"))
        changes_txt = (("Audio only; " if not owner else "") + "; ".join(changes) + "; fade in/out, 70 Hz high-pass, "
                       "loudness normalised to -16 LUFS (less where the peak limit, -1.5 dBTP, requires), mono Ogg "
                       "Vorbis." + (" Includes “As-salatu khayrun min an-nawm”." if a["fajr"] else ""))
        e["credit"] = credit(m, changes_txt, owner)
        if a.get("note_en"):
            e["note_en"], e["note_ar"] = a["note_en"], a["note_ar"]
        entries.append(e)
        doc.append((e, m, changes_txt, a))
        if a["id"] == DEFAULT_ADHAN:
            shutil.copyfile(os.path.join(phone, sname), os.path.join(root, "alerts/src/main/res/raw/noor_backup_adhan.ogg"))

    effects, fxdoc = [], []
    for x in EFFECTS:
        m = meta[x["src"]]
        src = os.path.join(sources_dir, x["src"] + ".flac")
        name = f"fx_{x['id']}.ogg"
        gain, limited = render(src, *x["cut"], os.path.join(phone, name), *x["fade"], x["highpass"], 5,
                               FX_LUFS, FX_TRUE_PEAK, limit=True)
        shutil.copyfile(os.path.join(phone, name), os.path.join(watch, name))
        changes_txt = (f"{fmt(x['cut'][0])}–{fmt(x['cut'][1])} s of the original; changed: trimmed, levelled "
                       f"({fmt(x['fade'][0])} s fade-in, {fmt(x['fade'][1])} s fade-out, {x['highpass']} Hz "
                       f"high-pass, loudness to {FX_LUFS:.0f} LUFS"
                       + (", a gentle peak limiter" if limited else "") + "), mono Ogg Vorbis.")
        e = dict(id="fx." + x["id"], kind="alt", title_en=x["title_en"], title_ar=x["title_ar"],
                 place_en=x["place_en"], place_ar=x["place_ar"], categories=[x["category"]],
                 file="sounds/" + name, seconds=duration(os.path.join(phone, name)),
                 credit=credit(m, changes_txt))
        if x.get("note_en"):
            e["note_en"], e["note_ar"] = x["note_en"], x["note_ar"]
        effects.append(e)
        fxdoc.append((e, m, changes_txt, x))

    for d in (phone, watch):
        with open(os.path.join(d, "library.json"), "w", encoding="utf-8") as f:
            json.dump(dict(version=1, sounds=entries + effects), f, ensure_ascii=False, indent=1)

    write_doc(os.path.join(root, "docs/SOUNDS.md"), doc, fxdoc)
    print(f"{len(entries)} Adhans, {len(effects)} alert sounds; phone "
          f"{sum(os.path.getsize(os.path.join(phone, x)) for x in os.listdir(phone)) // 1024} KB, watch "
          f"{sum(os.path.getsize(os.path.join(watch, x)) for x in os.listdir(watch)) // 1024} KB")


def write_doc(path, doc, fxdoc):
    def section(f, e, m, ch, owner=False):
        f.write(f"### {e['title_en']} — {e['place_en']}\n\n")
        if owner:
            f.write(f"- Recording: `{e['credit']['source'].removeprefix('owner:')}` (the owner's own file)\n")
        else:
            f.write(f"- Recording: [{m['title']}]({m['page']})\n")
        f.write(f"- Author: {m['artist']}\n")
        f.write(f"- Licence: {m['licence']}" + (f" ({m['licence_url']})" if m.get('licence_url') else "") + "\n")
        f.write(f"- Provenance: {m['provenance']}\n- Changes made by Minhad: {ch}\n")
        if "SA" in m["licence"]:
            f.write(f"- The edited audio in Minhad is shared under the same licence ({m['licence']}).\n")
        if e.get("note_en"):
            f.write(f"- Note: {e['note_en']}\n")
        if e["kind"] == "adhan":
            f.write("- Lengths in Minhad: " + (f"whole {e['seconds']} s, " if e.get("file") else "") +
                    f"opening {e['short']['seconds']} s\n")
        else:
            f.write(f"- Length in Minhad: {e['seconds']} s\n")
        f.write(f"- SHA-1 of the original file: `{m['sha1']}`\n\n")

    with open(path, "w", encoding="utf-8") as f:
        f.write("# Sounds in Minhad\n\nGenerated by `tools/sounds/build_library.py`; do not edit by hand (change the "
                "script and run it again).\n\n"
                "Minhad bundles the set the owner chose: an Adhan voice per place, each whole and as its opening (exactly "
                "the first two takbirs, “Allahu Akbar, Allahu Akbar”, since 0.26), and five short alert sounds (0.26: "
                "beeps and birds, the owner's choice of 27 November 2026, replacing 0.18's five recordings of nature). "
                "Every sound is a real recording under a licence that allows redistribution and commercial use (public "
                "domain, CC0, CC BY or CC BY-SA), or the owner's own; nothing is generated or synthesised. "
                "`tools/sounds/fetch_commons.py` re-checks every source (licence on Commons, SHA-1 everywhere) before "
                "use. The default Adhan's opening is also the backup alert sound "
                "(`alerts/src/main/res/raw/noor_backup_adhan.ogg`, byte for byte the library file).\n\n")
        f.write("## Adhan voices\n\n")
        for e, m, ch, a in doc:
            section(f, e, m, ch, a.get("owner", False))
        f.write("No Fajr Adhan (with “As-salatu khayrun min an-nawm”) with a usable licence was found for Makkah, "
                "Madinah or Egypt; at Fajr these voices play their usual Adhan. Dubai's Fajr Adhan is the owner's "
                "recording.\n\n")
        f.write("The owner's recordings (Dubai): supplied on 16 November 2026 by the app's owner, who states that he owns "
                "these recordings and holds all the rights to use them; a licence document, if there is one, should be "
                "kept with the project's records.\n\n")
        f.write("### How the openings were cut (0.26, 27 November 2026)\n\n"
                "The owner: “the short Adhan is just the same version as the Adhan you chose, but it just says the "
                "first Allahu Akbar, Allahu Akbar. That's it.” Each opening was found in its original recording from "
                "the voice band's energy (250–2500 Hz, 50 ms frames, `tools/sounds/README.md`), spectrograms that were "
                "looked at, and a speech recogniser (faster-whisper, model “medium”, Arabic, word times; its times are "
                "approximate, the phrase boundaries come from the energy). Each opening starts with a fade of at most "
                "0.2 s just before the voice and ends with a 0.25–0.3 s fade in the pause after the second takbir.\n\n")
        for e, m, ch, a in doc:
            f.write(f"- **{e['title_en']}{' (Fajr)' if a['fajr'] else ''}**: {fmt(a['short'][0])}–{fmt(a['short'][1])} s "
                    f"of the original. {a['how']}\n")
        f.write("\n## Places without a recording yet\n\nThese voices are offered as “add your own recording”.\n\n")
        for en, ar, why in MISSING:
            f.write(f"- **{en}** ({ar}): {why}\n")
        f.write("\n## Alert sounds (0.26)\n\nShort real recordings to announce the prayer time: beeps of real devices "
                "and birds. No music, melody or musical instrument, nothing synthesised. Each starts at once, ends "
                "cleanly and is levelled to about −17 LUFS (true peak at most −1.5 dBTP), close to the Adhans' −16 LUFS. "
                "There is one version of each (they are 2–4 s long: no separate short version); an alarm plays it "
                "over and over.\n\n"
                "How the beeps were chosen (0.26): Commons' file host refused this cloud session (“429 Too Many "
                "Requests”) for hours, so GitHub's fetch-sounds workflow downloaded four candidates whose licences "
                "Commons' API confirmed; each was looked at as a spectrogram and an envelope. Kept: *Microwave oven.ogg* "
                "(public domain) and *Alarm clock - 01.ogg* (CC BY-SA 4.0), both real devices in a room. Not used: "
                "*Unlocking a car.ogg* (faint chirps through a window, not beeps) and *Alarmclock-bleeps.ogg* (one long "
                "loop). Rejected from their descriptions: *Alarm or siren.ogg* and *Beep alarm clock.ogg* (generated), "
                "*Joseph Sardin - Passer domesticus tschilp call* (a toy sparrow), *Alarm Clock (Directory.Audio)* "
                "(unknown origin), *Heart Monitor Beep* (probably generated), *Smoke alarm.ogg* (harsh).\n\n")
        for e, m, ch, x in fxdoc:
            section(f, e, m, ch)
            if x.get("how"):
                f.write(f"  How it was chosen: {x['how']}\n\n")
        f.write("### Replaced in 0.26\n\nThe five recordings of nature bundled from 0.18 to 0.25 (each 30 s, with a "
                "short version) were removed at the owner's word; a choice stored with their ids plays the sound that "
                "replaced it (`SoundIds.RENAMED` in `core/prayer`):\n\n")
        renamed = {old: "fx." + x["id"] for _, _, _, x in fxdoc for old in x.get("replaces", [])}
        for old, title, what in RETIRED_EFFECTS:
            f.write(f"- `{old}` {title}: {what} → `{renamed.get(old, '?')}`\n")
        f.write("\n## Considered and not used\n\n"
                "- *Adhan, Great Mosque of Mecca – 2013* (CC BY 3.0): clearer than the 2012 recording, but it stops "
                "during the 11th of the 12 phrases, so it cannot give the whole Adhan.\n"
                "- *Time of Adhan, Great Mosque of Mecca – Jan 6, 2018*: starts during the first phrase.\n"
                "- *Oración Al-Azzan* (Madinah, public domain): holds the opening only; the Prophet's Mosque "
                "recording is complete and cleaner.\n"
                "- Makkah and Madinah Adhans on the Internet Archive marked “public domain” or CC0 "
                "(HaramainRecordings and similar uploads): captures of television broadcasts whose uploaders do not "
                "hold the rights.\n"
                "- *Adhan/Azaan (Dubai Mall) – Abdulrahman Al-Hindi* on the Internet Archive: a YouTube copy "
                "uploaded by someone who does not hold its rights (the Dubai voice comes from the owner's own files "
                "instead).\n"
                "- Jumeira 3, Dubai (radio aporee, 2011): three mosques calling at once, with waves; not the Dubai "
                "Mall Adhan.\n"
                "- *Call to prayer by Sabah Fakhry.mp3*: tagged public domain, but it is a commercial recording by a "
                "singer who died in 2021; the claim is not credible.\n"
                "- The other Egyptian candidates on Freesound, fetched on GitHub and examined on 16 November 2026 with "
                "band-energy profiles and a speech recogniser: Alexandria (CyWeb, CC BY 4.0, 185 s: begins at the first "
                "shahada, without the opening takbir), Alexandria (nervousneal, CC0, 54 s: stops at \"Hayya 'ala-s-"
                "salah\"), Al-Azhar Park (CC0, 64 s), Maadi (CC BY 4.0, 74 s) and El Quseir (CC0, 127 s): too far away "
                "to be heard clearly (the voice barely 5 to 14 dB above the background; the recogniser heard no "
                "words).\n"
                "- Alert sounds generated from code (`tools/sounds/synthesize.py`, up to 0.16): removed at the "
                "owner's word (“no generated sounds”, “zero music”). 0.26 again: “I don't want any AI generated "
                "sounds.”\n")
        for line in CONSIDERED_FX:
            f.write(f"- {line}\n")


# Alert-sound candidates examined in 0.26 and not used, and why (written to docs/SOUNDS.md).
CONSIDERED_FX = []


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    main(args[0], args[1], keep_whole="--keep-whole" in sys.argv)
