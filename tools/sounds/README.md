# Sound pipeline

Since 0.17 Noor bundles only the set the owner asked for on 16 November 2026: an Adhan voice per place (Makkah,
Madinah; Dubai from the owner's own recordings), each whole and as its opening ("Allahu Akbar, Allahu Akbar"), and five
real recordings of nature (birds, rooster, sea waves, rain, stream), each long and short. Nothing is generated: every
sound is a recording whose licence allows redistribution in this public repository and use in an app that may become
commercial (public domain, CC0, CC BY, CC BY-SA; never NC or ND). `docs/SOUNDS.md` credits each one.

0. **Discovery** — `discover_commons.py` (run by the *Discover Adhan recordings* workflow) lists every Adhan
   recording on Commons with its licence, author, description and length and publishes the list on the `sound-research`
   pre-release, for review. Nothing reaches the app from it directly.
1. **Sources** — `adhan_sources.json` lists every recording, of three kinds: `commons` (a Wikimedia Commons file and the
   licence it must have), `url` (a file from an archive that states its licence: the US National Park Service sound
   libraries, which are public domain, or radio aporee field recordings on the Internet Archive; the page, author,
   licence and the file's SHA-1 are recorded by hand) and `local` (a recording the owner supplies, kept in
   `tools/sounds/owner/`, with who made it and who holds the rights).
2. **Fetch** — `fetch_commons.py <manifest> <out>` (run by the *Fetch licensed Adhan recordings* workflow on every change
   to the list) checks each source: on Commons the live licence must match the manifest; every file's SHA-1 must
   match. It keeps the audio as FLAC and writes `sources.json` with the attribution data, published with the files on
   the `sound-sources` pre-release. Requests carry a descriptive User-Agent and honour `Retry-After`.
3. **Library** — `build_library.py <sources_dir> <repo_root>` cuts each recording (the cut points are in the script),
   adds short fades, a high-pass (70 Hz for voices, 40 Hz for nature), one linear gain to −16 LUFS (less where the true
   peak would pass −1.5 dBTP), mono Ogg Vorbis, and writes `assets/sounds` for the phone (everything) and the watch
   (Adhan openings and both versions of every effect), `library.json`, the backup alert sound
   (`alerts/src/main/res/raw/noor_backup_adhan.ogg`, a byte copy of the Makkah opening) and `docs/SOUNDS.md`.

`synthesize.py` (Noor's own generated alert sounds up to 0.16) is no longer used: the owner wants no generated sounds.

## Adding a voice from the owner's own recording

Put the file in `tools/sounds/owner/`, add a `local` entry to `adhan_sources.json` (id, path relative to this folder,
name, artist, licence, provenance: who recorded it, where, when, who holds the rights and on what terms), run
`fetch_commons.py`, add an entry to `ADHANS` in `build_library.py` with its id (`dubai`, `dubai_fajr` with
`fajr=True`) and cut points, and run `build_library.py`.

## How the cut points were chosen

Each recording's band energy (voice band 250–2500 Hz for the Adhans, 0.1–0.25 s frames) was segmented into phrases and
checked on spectrograms; the Adhan openings were also checked with a speech recogniser (faster-whisper, Arabic), which
heard exactly "الله أكبر الله أكبر" in the Madinah opening (in the Makkah recording the crowd defeats it; there the
phrase structure of the Haram Adhan, twelve phrases, was counted between the cuts). Cuts are placed in the silence before the
first phrase and after the last, never inside a phrase; an opening ends in the silence after the first phrase
("Allahu Akbar, Allahu Akbar").

- Makkah: the Maghrib 2012 recording is the only complete, freely licensed Haram Adhan found; the crowd can be heard
  between phrases. The 2013 recording is clearer but stops during the 11th phrase; the 2018 one starts mid-phrase.
- Madinah: the Prophet's Mosque recording (ejaz215) is complete and clean.
- Al-Quds and Egypt: no recording with a usable licence and a clear single voice could be downloaded and checked
  (details in `docs/SOUNDS.md`); the app offers them as "add your own recording". Candidates found on Freesound
  (one at the Holy Sepulchre in Jerusalem, six in Egypt; CC0 or CC BY 4.0) are listed in `adhan_sources.json` with
  `"status": "candidate"` and `"sha1": null`, because Freesound refuses the cloud session: the Fetch workflow
  downloads them on GitHub and publishes them on `sound-sources`, where they can be listened to, segmented and checked
  with the speech recogniser; a chosen one gets its SHA-1 pinned and an `ADHANS` entry.
- Nature: windows were chosen with band-energy profiles and spectrograms to start and end in a lull (waves), hold
  several crows (rooster) or steady sound (rain, stream), and to avoid intruding noises; the birds' short version is
  two chirps (1.25 s), the rooster's one crow, the waves' one wave.

Nothing is pitch-shifted, time-stretched, looped, spliced or denoised.
