# Dubai (IACAD) prayer-time accuracy research — 4 November 2026

Research workspace only. Nothing here changes the watch-face project (v1.3.2), which is untouched.

## Data (`data/`)

| File | What it is | Tier |
|---|---|---|
| `iacad_api_2026-11.csv` / `.iso.csv` | IACAD website feed (the official prayer-times page's own data request), 1–30 Nov 2026, "Dubai City" | Official |
| `iacad_official_archive_2014_2023.csv` | 503 days recovered from Internet Archive captures of IACAD's SmartPortal timings pages (daily, month, Ramadan tables) | Official, archived |
| `iacad_wayback_enc.json` | The same data in the checksummed transfer format, with provenance notes and exclusions | — |
| `iacad_via_nestle_ramadan_2020.csv` | Nestlé PDF that republishes IACAD, Ramadan 2020 | Secondary |
| `salatcalendar_official_dubai_2019_2026.csv` | salatcalendar.com (IACAD + International Astronomical Center) "official times" mode, 2,922 days | IAC replica, reference only |

Exclusions: the Ramadan 2015 table captured on 4 May 2015 (a pre-Ramadan draft that disagrees with the later table and with two daily captures). Known anomalies: 3 Apr 2016 daily capture differs by 1 min on 4 times from the April 2016 month table (month table kept); Nestlé 2020 PDF has Fajr and Isha swapped on 30 Apr / 1 May.

## Findings in one paragraph

IACAD's Dubai conventions changed with the 1439 AH calendar (21 Sep 2017: fixed 80/90-minute Fajr/Isha replaced by twilight angles) and the 1442 AH calendar (20 Aug 2020: Asr 2 minutes earlier). For the current era (98 official days) the most reliable transparent candidate (C1: latitude 25.12° N, Fajr/Isha 18.0°, horizon = refraction 34′ + solar radius + 0.10°, round-up, minutes −2/−4/+2/0/+2/0 at longitude 55.189° E) matches 89.6% of times, 92.8% and 86.5% on held-out splits; every miss is 1 minute. The watch-face engine v1.3.2 matches 82.3%. Exact reproduction is not achieved.

## Tools (`tools/`)

- `model.py` — NREL SPA based event solver (topocentric, explicit refraction/semidiameter/dip).
- `basis.py`, `fix_transit.py` — precomputed event-time bases (Dhuhr = true meridian transit).
- `analyse.py` — residual-spread and match-count scans (no per-date terms anywhere).
- `joint_search.py`, `holdout.py`, `principled.py` — convention searches and held-out validation.
- `validate.py` — automated harness: 6 dataset-integrity tests + scoring of every candidate engine against every era; writes `results/validation_report.md` and one discrepancy CSV per engine and era.
- Hypothesis tests kept for the record: `dhuhr_eot.py`, `dhuhr_ref.py`, `hourangle.py`/`test_hourangle.py`, `two_stage.py`, `extreme_points.py`.

Run: `python3 tools/validate.py` (needs numpy, pvlib, ephem). The basis files (`data/basis_*.pkl`) are rebuilt with `tools/build_bases.py`, `tools/build_basis_archive.py`, `tools/merge_basis.py`, `tools/fix_transit.py`.

## Results (`results/`)

`validation_report.md`, `discrepancies_*.csv` (every mismatch, date by date), `current_engine_vs_official.csv`, `principled_conventions.json`.
