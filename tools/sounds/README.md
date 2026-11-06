# Sound pipeline

0. **Discovery** — `discover_commons.py` (run by the *Discover Adhan recordings* workflow) lists every Adhan
   recording on Commons with its licence, author, description and length and publishes the list on the `sound-research`
   pre-release, for review. Nothing reaches the app from it directly: a reviewed recording is added to
   `adhan_sources.json`, and only then fetched and checked by `fetch_commons.py`.
1. **Alert sounds** — `synthesize.py <out_dir>` generates Noor's fourteen alert sounds from code (no samples), so they are
   original works, released under CC0.
2. **Adhan recordings** — `adhan_sources.json` lists Wikimedia Commons files and the licence each must have.
   `fetch_commons.py` (run by the *Fetch licensed Adhan recordings* workflow on every change to the list) asks the
   Commons API for each file's licence and SHA-1, refuses any file whose licence differs, downloads it, checks the
   SHA-1, keeps only the audio as FLAC and publishes everything with `sources.json` on the `sound-sources` pre-release.
   Requests carry a descriptive User-Agent and honour `Retry-After`.
3. **Library** — `build_library.py <sources_dir> <alt_dir> <repo_root>` cuts each recording to the Adhan, adds short
   fades, a 70 Hz high-pass, loudness normalisation to −16 LUFS and mono Ogg Vorbis, makes a short version (the
   opening takbir), writes `assets/sounds` for the phone (everything) and the watch (short versions and alert sounds),
   `library.json` and `docs/SOUNDS.md`.

## How the cut points were chosen

Each recording's voice-band energy (250–2500 Hz, 0.1 s frames) was segmented into phrases and checked on
spectrograms. Cuts are placed in the silence before the first phrase and after the last, never inside a phrase.
Findings that changed what is offered:

- *Adhan, Great Mosque of Mecca – 2013* stops during the 11th of 12 phrases, so only its opening is offered.
- *Eid al-Fitr Fajr azan at Malmö* ends during the final phrase; a 1.5 s fade was added and the app says so.
- *Adhan in Istanbul* is a 50-second street excerpt; *Oración Al-Azzan* (Madinah) holds the opening takbir only.
- *Islamic call to worship.oga* is continuous speech (an explanation of the Adhan), so it is not used.

Nothing is pitch-shifted, time-stretched or spliced.

## 0.9.5 additions

Reviewed from the full Commons list (191 audio and video files found, most of them unrelated: speeches, sermons,
music). Taken: the Prophet's Mosque (Madinah), Iran (dastgah Mahur; Hussein Sobhdel, opening only), Isfahan, Jaffa,
Marrakech, Ghana (two), Nigeria and two self-published recordings. Cut points were found the same way: phrase
segmentation of the voice band and spectrograms. Left out after analysis: Makkah 2018 (starts during its first
phrase), Hargeisa (starts mid-Adhan), the Damascus section of *The sound of Syria* (edited, mostly street sound). The
Sobhdel recording is offered as its opening only because the end of its Adhan could not be placed with certainty.
When rebuilding, files of recordings whose cuts did not change are kept as they were (an encoder update changes
bytes, not sound), so `BackupSoundTest`'s byte check keeps holding.

