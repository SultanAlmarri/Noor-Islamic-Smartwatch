#!/usr/bin/env python3
"""Builds Noor's bundled sound library from the verified sources.

Since 0.17 the library is the small set the owner asked for on 16 November 2026: an Adhan voice for each of a few
places (the whole Adhan and its opening, "Allahu Akbar, Allahu Akbar") and five real recordings of nature (each long
and short). Nothing is synthesised or generated: every sound is a recording, credited in docs/SOUNDS.md.

Inputs
  <sources_dir>  output of fetch_commons.py (FLAC files + sources.json): Wikimedia Commons files, files from licensed
                 archives (US National Park Service, Internet Archive) and the owner's own recordings (tools/sounds/owner)
Outputs
  app/src/main/assets/sounds/   every file + library.json                                (phone)
  wear/src/main/assets/sounds/  Adhan openings + both versions of every effect + library.json
                                (watch; whole Adhans are sent from the phone on demand)
  alerts/src/main/res/raw/noor_backup_adhan.ogg   the default Adhan's opening, byte for byte (BackupSoundTest)
  docs/SOUNDS.md                attribution and the exact edits made to each recording

Edits are limited to: keeping only the wanted part (cutting unrelated sound before/after, in silence between phrases,
never inside one), a short fade-in/out, a high-pass (70 Hz for voices, 40 Hz for nature), a linear gain to -16 LUFS
(less where the true peak would pass -1.5 dBTP), mono, Ogg Vorbis. Nothing is pitch-shifted, sped up, looped,
spliced or denoised. Cut points were found from each recording's band energy and spectrogram (tools/sounds/README.md).

To add a place's Adhan from a new source (for example the owner's own recording, kind "local" in adhan_sources.json):
add the source to adhan_sources.json, run fetch_commons.py, add an entry to ADHANS below with its cut points, and run
this script.

usage: build_library.py <sources_dir> <repo_root>
"""
import json
import os
import shutil
import subprocess
import sys

# Every cut is (start_s, end_s) in the original recording. "short" is the opening takbir: the first
# "Allahu Akbar, Allahu Akbar", up to the silence before the second. "full" is the whole Adhan, or None when the
# recording does not hold it (the entry is then offered as its opening only and says so).
ADHANS = [
    dict(id="makkah", src="makkah_maghrib_2012", fajr=False,
         title_en="Makkah", title_ar="مكة",
         place_en="al-Masjid al-Haram · Maghrib, 2012", place_ar="المسجد الحرام · المغرب، 2012",
         region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=2012,
         full=(114.2, 290.7), short=(114.2, 130.4),
         note_en="Recorded among the worshippers in the Haram, so the crowd can be heard between the phrases.",
         note_ar="سُجّل بين المصلّين في الحرم، لذا يُسمع صوت الحشود بين الجمل."),
    dict(id="madinah", src="madinah_prophets_mosque", fajr=False,
         title_en="Madinah", title_ar="المدينة",
         place_en="The Prophet's Mosque", place_ar="المسجد النبوي",
         region="haramain", country_en="Saudi Arabia", country_ar="السعودية", year=None,
         full=(0.6, 186.4), short=(0.6, 19.8), note_en=None, note_ar=None),
]

# Places the owner asked for that have no recording here yet, and why (shown in docs/SOUNDS.md). The app offers
# them as "add your own recording". The Dubai voice comes from the owner's own recordings, added separately.
MISSING = [
    ("Al-Quds", "القدس",
     "No freely licensed recording of the Adhan of al-Masjid al-Aqsa was found, and no Jerusalem recording could "
     "be downloaded and checked here. One candidate exists: the Adhan heard at the Church of the Holy Sepulchre "
     "in the Old City (Freesound 191866, Zabuhailo, CC BY 4.0, 109 s). Freesound refuses the cloud session "
     "Minhad is made in, so it is listed in tools/sounds/adhan_sources.json (status \"candidate\") for the Fetch "
     "workflow to download on GitHub; it must be listened to before it is used. Examined and not usable (16 "
     "November 2026): on Wikimedia Commons, a 15-second clip heard from a distance at the Garden Tomb and a "
     "60-second travel video from the Haram (Neollene Films, 2017: too short to hold a whole Adhan); radio "
     "aporee field recordings on the Internet Archive from the Golden Gate (2014: the end of a prayer over a "
     "loudspeaker), Pisgat Ze'ev (2016: distant calls with gunfire), the Lion's Gate (2015: street sound, no "
     "Adhan), the Notre Dame rooftop at sunset (2013: several distant mosques, 67 s), Bethlehem (2023: distant, "
     "behind voices, 64 s) and Ramallah (2019, CC BY 3.0, Maghrib and Fajr from a window: far away, many mosques "
     "at once, and not Jerusalem; a speech recogniser heard no words in either). Freesound's \"Palestine muezzin\" "
     "(nicotep) is CC BY-NC; other uploads on the Internet Archive are broadcast or YouTube copies whose "
     "uploader does not hold the rights."),
    ("Egypt", "مصر",
     "No single, clear Egyptian Adhan with a usable licence could be downloaded and checked here. Six candidates "
     "were found on Freesound, each a field recording published by its recordist: Al-Azhar Park, Cairo (Ahmad "
     "Aiuby, CC0, 64 s), Maadi, Cairo (Ahmad Aiuby, CC BY 4.0, 74 s), El Quseir (Ahmad Aiuby, CC0, 127 s), a "
     "rooftop at Khan el-Khalili (3bagbrew, CC0, 336 s) and two in Alexandria (CyWeb, CC BY 4.0, 185 s; "
     "nervousneal, CC0, 54 s). Freesound refuses the cloud session Minhad is made in, so they are listed in "
     "tools/sounds/adhan_sources.json (status \"candidate\") for the Fetch workflow to download on GitHub; they "
     "must be listened to before one is used. Examined and not usable (16 November 2026): radio aporee field "
     "recordings on the Internet Archive from Maadi (2012, CC BY 3.0: the dawn call of many distant mosques at "
     "once), Hurghada (2010: starts in the middle of the Adhan, at \"Ashhadu anna Muhammadan rasul Allah\", with "
     "street and café noise), Aswan (2009: several mosques overlapping, starting mid-phrase) and Luxor (2019: "
     "street noise). Not used for their terms or origin: the Cambridge \"Voices and Faces of the Adhan: Cairo\" "
     "collection (CC BY-NC-ND), several Freesound recordings under CC BY-NC, Freesound's \"Pandemic Adhan - "
     "Maadi\" (by its title the Adhan of the 2020 pandemic, which adds \"pray in your homes\"; not listened to), "
     "and the Internet Archive's \"Adhan from Egypt, Abdulnasser Harak\" (a radio recording re-uploaded by someone "
     "who does not hold its rights)."),
]

# Five recordings of nature (the owner: real recordings, high quality and clear, from a known source; no music,
# melody or musical tones). "long" is played as an alert sound; "short" is a brief version of the same recording.
EFFECTS = [
    dict(id="birds", src="birds_yellowstone_dawn", title_en="Birds", title_ar="عصافير",
         place_en="Dawn chorus, Yellowstone National Park", place_ar="جوقة الفجر، متنزه يلوستون الوطني",
         long=(0.0, 31.6), short=(1.05, 2.3),
         note_en="The short version is two chirps.", note_ar="النسخة القصيرة زقزقتان."),
    dict(id="rooster", src="rooster_houx", title_en="Rooster", title_ar="ديك",
         place_en="A farm in Houx, France", place_ar="مزرعة في أوو، فرنسا",
         long=(10.3, 41.6), short=(37.7, 41.3),
         note_en="The rooster crows three times; the short version is one crow.",
         note_ar="يصيح الديك ثلاث مرات؛ النسخة القصيرة صيحة واحدة."),
    dict(id="waves", src="waves_yilan", title_en="Sea waves", title_ar="أمواج البحر",
         place_en="A rocky shore in Yilan, Taiwan", place_ar="شاطئ صخري في إيلان، تايوان",
         long=(8.0, 38.0), short=(9.0, 14.0),
         note_en="The short version is one wave.", note_ar="النسخة القصيرة موجة واحدة."),
    dict(id="rain", src="rain_taitung", title_en="Rain", title_ar="مطر",
         place_en="Steady rain in Taitung, Taiwan", place_ar="مطر متواصل في تايتونغ، تايوان",
         long=(20.0, 50.0), short=(30.0, 34.0), note_en=None, note_ar=None),
    dict(id="stream", src="stream_rocky_mountain", title_en="Stream", title_ar="جدول ماء",
         place_en="A mountain stream, Rocky Mountain National Park", place_ar="جدول جبلي، متنزه جبال روكي الوطني",
         long=(30.0, 60.0), short=(40.0, 44.0), note_en=None, note_ar=None),
]

DEFAULT_ADHAN = "makkah"   # its opening is also alerts/res/raw/noor_backup_adhan.ogg
LUFS = -16.0
TRUE_PEAK = -1.5


def run(args):
    subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         check=True, capture_output=True, text=True).stdout
    return round(float(out.strip()), 2)


def measure(src, filters):
    """Integrated loudness and true peak of the edited (but not yet amplified) audio."""
    p = subprocess.run(["ffmpeg", "-hide_banner", "-i", src, "-af",
                        f"{filters},loudnorm=I={LUFS}:TP={TRUE_PEAK}:LRA=20:print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True, check=True).stderr
    j = json.loads(p[p.rindex("{"):p.rindex("}") + 1])
    return float(j["input_i"]), float(j["input_tp"])


def render(src, start, end, out, fade_in, fade_out, highpass, quality):
    """Cut, fade, high-pass, then one linear gain: to LUFS, or less if the true peak would pass TRUE_PEAK.

    The cut is first written as mono 32-bit float (left and right averaged), so the measuring pass and the final pass
    read exactly the same samples and the gain lands where it was computed."""
    length = end - start
    cut = out + ".cut.wav"
    run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-to", str(end), "-i", src, "-vn",
         "-af", "pan=mono|c0=0.5*c0+0.5*c1" if channels(src) == 2 else "anull",
         "-ac", "1", "-c:a", "pcm_f32le", cut])
    base = (f"highpass=f={highpass},afade=t=in:d={fade_in},"
            f"afade=t=out:st={max(0.0, length - fade_out):.2f}:d={fade_out}")
    loudness, peak = measure(cut, base)
    gain = min(LUFS - loudness, TRUE_PEAK - peak)
    run(["ffmpeg", "-v", "error", "-y", "-i", cut, "-af", f"{base},volume={gain:.2f}dB,aresample=44100",
         "-c:a", "libvorbis", "-q:a", str(quality), "-map_metadata", "-1", out])
    os.remove(cut)
    return gain


def channels(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels",
                          "-of", "csv=p=0", path], check=True, capture_output=True, text=True).stdout
    return int(out.strip())


def credit(m, changes):
    c = dict(author=m["artist"], licence=m["licence"], licence_url=m.get("licence_url") or "", source=m["page"],
             changes=changes)
    if "SA" in m["licence"]:
        c["derivative_licence"] = m["licence"]
    return c


def main(sources_dir, root):
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
                 place_en=a["place_en"], place_ar=a["place_ar"], categories=["renowned", "traditional"] +
                 (["fajr"] if a["fajr"] else []), fajr=a["fajr"])
        for k in ("region", "country_en", "country_ar", "year"):
            if a.get(k):
                e[k] = a[k]
        changes = []
        if a["full"]:
            name = f"adhan_{a['id']}.ogg"
            render(src, *a["full"], os.path.join(phone, name), 0.25, 1.5, 70, 3)
            e.update(file="sounds/" + name, seconds=duration(os.path.join(phone, name)))
            changes.append(f"full version: {a['full'][0]}–{a['full'][1]} s of the original")
        sname = f"adhan_{a['id']}_short.ogg"
        render(src, *a["short"], os.path.join(phone, sname), 0.25, 2.0, 70, 3)
        shutil.copyfile(os.path.join(phone, sname), os.path.join(watch, sname))
        e["short"] = dict(file="sounds/" + sname, seconds=duration(os.path.join(phone, sname)))
        changes.append(f"opening (“Allahu Akbar, Allahu Akbar”): {a['short'][0]}–{a['short'][1]} s")
        changes_txt = ("Audio only; " + "; ".join(changes) + "; fade in/out, 70 Hz high-pass, loudness normalised "
                       "to -16 LUFS, mono Ogg Vorbis.")
        e["credit"] = credit(m, changes_txt)
        if a.get("note_en"):
            e["note_en"], e["note_ar"] = a["note_en"], a["note_ar"]
        entries.append(e)
        doc.append((e, m, changes_txt))
        if a["id"] == DEFAULT_ADHAN:
            shutil.copyfile(os.path.join(phone, sname), os.path.join(root, "alerts/src/main/res/raw/noor_backup_adhan.ogg"))

    effects, fxdoc = [], []
    for x in EFFECTS:
        m = meta[x["src"]]
        src = os.path.join(sources_dir, x["src"] + ".flac")
        name, sname = f"fx_{x['id']}.ogg", f"fx_{x['id']}_short.ogg"
        render(src, *x["long"], os.path.join(phone, name), 1.0, 3.0, 40, 4)
        short_len = x["short"][1] - x["short"][0]
        render(src, *x["short"], os.path.join(phone, sname), 0.05 if short_len < 3 else 0.3,
               0.4 if short_len < 3 else 1.0, 40, 4)
        for f in (name, sname):
            shutil.copyfile(os.path.join(phone, f), os.path.join(watch, f))
        changes_txt = (f"long version: {x['long'][0]}–{x['long'][1]} s of the original; short version: "
                       f"{x['short'][0]}–{x['short'][1]} s; fade in/out, 40 Hz high-pass, loudness normalised to "
                       "-16 LUFS (less where the peak limit requires), mono Ogg Vorbis.")
        e = dict(id="fx." + x["id"], kind="alt", title_en=x["title_en"], title_ar=x["title_ar"],
                 place_en=x["place_en"], place_ar=x["place_ar"], categories=["nature"],
                 file="sounds/" + name, seconds=duration(os.path.join(phone, name)),
                 short=dict(file="sounds/" + sname, seconds=duration(os.path.join(phone, sname))),
                 credit=credit(m, changes_txt))
        if x.get("note_en"):
            e["note_en"], e["note_ar"] = x["note_en"], x["note_ar"]
        effects.append(e)
        fxdoc.append((e, m, changes_txt))

    for d in (phone, watch):
        with open(os.path.join(d, "library.json"), "w", encoding="utf-8") as f:
            json.dump(dict(version=1, sounds=entries + effects), f, ensure_ascii=False, indent=1)

    def section(f, e, m, ch):
        f.write(f"### {e['title_en']} — {e['place_en']}\n\n")
        f.write(f"- Recording: [{m['title']}]({m['page']})\n- Author: {m['artist']}\n")
        f.write(f"- Licence: {m['licence']}" + (f" ({m['licence_url']})" if m.get('licence_url') else "") + "\n")
        f.write(f"- Provenance: {m['provenance']}\n- Changes made by Noor: {ch}\n")
        if "SA" in m["licence"]:
            f.write(f"- The edited audio in Noor is shared under the same licence ({m['licence']}).\n")
        if e.get("note_en"):
            f.write(f"- Note: {e['note_en']}\n")
        f.write(f"- Lengths in Noor: " + (f"whole {e['seconds']} s, " if e.get("file") and e["kind"] == "adhan" else
                                          (f"long {e['seconds']} s, " if e.get("file") else "")) +
                f"{'opening' if e['kind'] == 'adhan' else 'short'} {e['short']['seconds']} s\n")
        f.write(f"- SHA-1 of the original file: `{m['sha1']}`\n\n")

    with open(os.path.join(root, "docs/SOUNDS.md"), "w", encoding="utf-8") as f:
        f.write("# Sounds in Noor\n\nGenerated by `tools/sounds/build_library.py`; do not edit by hand.\n\n"
                "Since 0.17 Noor bundles only the set the owner chose on 16 November 2026: an Adhan voice per place, "
                "each whole and as its opening (“Allahu Akbar, Allahu Akbar”), and five recordings of nature, each "
                "long and short. Every sound is a real recording under a licence that allows redistribution and "
                "commercial use (public domain, CC0, CC BY or CC BY-SA); nothing is generated or synthesised. "
                "`tools/sounds/fetch_commons.py` re-checks every source (licence on Commons, SHA-1 everywhere) "
                "before use. The default Adhan's opening is also the backup alert sound "
                "(`alerts/src/main/res/raw/noor_backup_adhan.ogg`, byte for byte the library file).\n\n")
        f.write("## Adhan voices\n\n")
        for e, m, ch in doc:
            section(f, e, m, ch)
        f.write("No Fajr Adhan (with “As-salatu khayrun min an-nawm”) with a usable licence was found for Makkah, "
                "Madinah, Al-Quds or Egypt; at Fajr these voices play their usual Adhan.\n\n")
        f.write("## Places without a recording yet\n\nThese voices are offered as “add your own recording”.\n\n")
        for en, ar, why in MISSING:
            f.write(f"- **{en}** ({ar}): {why}\n")
        f.write("\nThe Dubai voice is added from the owner's own recordings (a `local` source in "
                "`tools/sounds/adhan_sources.json`).\n\n")
        f.write("## Sound effects\n\nReal recordings of nature, with no music, melody or musical tones.\n\n")
        for e, m, ch in fxdoc:
            section(f, e, m, ch)
        f.write("## Considered and not used\n\n"
                "- *Adhan, Great Mosque of Mecca – 2013* (CC BY 3.0): clearer than the 2012 recording, but it stops "
                "during the 11th of the 12 phrases, so it cannot give the whole Adhan.\n"
                "- *Time of Adhan, Great Mosque of Mecca – Jan 6, 2018*: starts during the first phrase.\n"
                "- *Oración Al-Azzan* (Madinah, public domain): holds the opening only; the Prophet's Mosque "
                "recording is complete and cleaner.\n"
                "- Makkah and Madinah Adhans on the Internet Archive marked “public domain” or CC0 "
                "(HaramainRecordings and similar uploads): captures of television broadcasts whose uploaders do not "
                "hold the rights.\n"
                "- *Adhan/Azaan (Dubai Mall) – Abdulrahman Al-Hindi* on the Internet Archive: a YouTube copy "
                "uploaded by someone who does not hold its rights.\n"
                "- Jumeira 3, Dubai (radio aporee, 2011): three mosques calling at once, with waves; not the Dubai "
                "Mall Adhan.\n"
                "- *Call to prayer by Sabah Fakhry.mp3*: tagged public domain, but it is a commercial recording by a "
                "singer who died in 2021; the claim is not credible.\n"
                "- Alert sounds generated from code (`tools/sounds/synthesize.py`, up to 0.16): removed at the "
                "owner's word (“no generated sounds”, “zero music”).\n"
                "- Nature: a rooster in Austin, Texas (heavy background noise); NPS clips of the ocean, rain and "
                "chickens (5–7 seconds, low bit rate); thunderstorms from Rocky Mountain National Park (mostly "
                "thunder, little rain); sparrows in La Bouille and Berlin (traffic hum).\n")
    print(f"{len(entries)} Adhans, {len(effects)} effects; phone "
          f"{sum(os.path.getsize(os.path.join(phone, x)) for x in os.listdir(phone)) // 1024} KB, watch "
          f"{sum(os.path.getsize(os.path.join(watch, x)) for x in os.listdir(watch)) // 1024} KB")


if __name__ == "__main__":
    main(*sys.argv[1:3])
