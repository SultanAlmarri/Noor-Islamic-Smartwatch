# Sound pipeline

Minhad bundles only the set the owner asked for: an Adhan voice per place (Makkah, Madinah, Egypt; Dubai from the
owner's own recordings), each whole and as its opening (exactly the first two takbirs, "Allahu Akbar, Allahu Akbar",
since 0.26), and, since 0.26 (27 November 2026), five short alert sounds: real recordings of beeps and birds, 2–4 s
each, in one version. Nothing is generated: every sound is a recording whose licence allows redistribution in this
public repository and use in an app that may become commercial (public domain, CC0, CC BY, CC BY-SA; never NC or ND).
`docs/SOUNDS.md` credits each one; `build_library.py` writes it.

0. **Discovery** — `discover_commons.py` (run by the *Discover Adhan recordings* workflow) lists every Adhan
   recording on Commons with its licence, author, description and length and publishes the list on the `sound-research`
   pre-release, for review. Nothing reaches the app from it directly.
1. **Sources** — `adhan_sources.json` lists every recording, of three kinds: `commons` (a Wikimedia Commons file and the
   licence it must have), `url` (a file from an archive that states its licence: the US National Park Service sound
   libraries, which are public domain, or a Freesound upload by its recordist under CC0; the page, author, licence and
   the file's SHA-1 are recorded by hand) and `local` (a recording the owner supplies, kept in `tools/sounds/owner/`,
   with who made it and who holds the rights).
2. **Fetch** — `fetch_commons.py <manifest> <out>` (run by the *Fetch licensed Adhan recordings* workflow on every change
   to the list) checks each source: on Commons the live licence must match the manifest; every file's SHA-1 must
   match. It keeps the audio as FLAC and writes `sources.json` with the attribution data, published with the files on
   the `sound-sources` pre-release. Requests carry a descriptive User-Agent and honour `Retry-After`. (Wikimedia's file
   host answers a cloud session "429 Too Many Requests" at times, for an hour or more: then fetch on GitHub with the
   workflow, never around the limit.)
3. **Library** — `build_library.py <sources_dir> <repo_root> [--keep-whole]` cuts each recording (the cut points are
   in the script), adds short fades, a high-pass (70 Hz for voices; for an alert sound, just below the sound itself),
   one gain (the Adhans to −16 LUFS, less where the true peak would pass −1.5 dBTP; the alert sounds to −17 LUFS, with
   a gentle limiter only if a peak would pass −1.5 dBTP), mono Ogg Vorbis, and writes `assets/sounds` for the phone
   (everything) and the watch (Adhan openings and the alert sounds), `library.json`, the backup alert sound
   (`alerts/src/main/res/raw/noor_backup_adhan.ogg`, a byte copy of the Makkah opening) and `docs/SOUNDS.md`.
   `--keep-whole` leaves the whole Adhans already in the repository as they are (0.26 re-cut only the openings).

`synthesize.py` (Noor's own generated alert sounds up to 0.16) is no longer used: the owner wants no generated sounds.

## Replacing a sound

An alert sound's id is stored in users' settings (a prayer's alert, an alarm, the favourites, on the phone and the
watch). When a sound is replaced, map its old id to the new one in `SoundIds.RENAMED` (`core/prayer`, read wherever
settings are decoded and in `SoundLibrary.byId`) and list the old id in the new entry's `replaces` in `EFFECTS`;
`SoundIdsTest`, `SoundLibraryTest`, `BackupSoundTest` and `DateNavigationTest` check that every old id still finds a
sound.

## Adding a voice from the owner's own recording

Put the file in `tools/sounds/owner/`, add a `local` entry to `adhan_sources.json` (id, path relative to this folder,
name, artist, licence, provenance: who recorded it, where, when, who holds the rights and on what terms), run
`fetch_commons.py`, add an entry to `ADHANS` in `build_library.py` with `owner=True`, its cut points (`full`, `short`,
`short_fade`) and a line on how the opening was found (`how`), and run `build_library.py`.

## How the cut points were chosen

Each recording's band energy (voice band 250–2500 Hz for the Adhans, 50–100 ms frames; the bird's or device's own band
for the alert sounds) was segmented and checked on spectrograms (`ffmpeg showspectrumpic`), which were looked at.

- **Adhan openings (0.26)**: the owner wants exactly the first "Allahu Akbar, Allahu Akbar". Each voice was checked with
  a speech recogniser (faster-whisper, model "medium", Arabic, word times: `pip install faster-whisper`; its model
  downloads from Hugging Face) on the first 40 s: Madinah, Makkah, Dubai and Dubai's Fajr sing the first two takbirs
  in one breath and the next two in the next breath, so the opening is the first breath and ends in the pause after it;
  Egypt sings the first two quickly (4.7 s) and draws the next two out over 16 s, so its opening is the first short
  phrase (the recogniser heard "الله أكبر الله أكبر" in it alone, word confidence 0.6–1.0); until 0.25 Egypt's opening
  held all four takbirs. Each opening starts with a fade of at most 0.2 s just before the voice and ends with a fade of
  0.25–0.3 s in the pause; the exact times and the evidence are in `ADHANS` and in `docs/SOUNDS.md`.
- **Whole Adhans**: cut in the silence before the first phrase and after the last, never inside a phrase.
- **Alert sounds**: the cleanest stretch (least background, no other bird or noise) holding two or three calls or
  beeps, cut in the silence before the first and after the last, so that the sound starts at once and, repeated by an
  alarm, keeps its rhythm.

Nothing is pitch-shifted, time-stretched, looped, spliced or denoised.
