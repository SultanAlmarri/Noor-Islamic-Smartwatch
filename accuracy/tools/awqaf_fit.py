"""UAE outside Dubai: which single rule set reproduces the official UAE tables for Abu Dhabi? (16 Nov 2026)

Data:
  szgmc_abudhabi_1445.csv  the UAE Hijri Calendar 1445 of the Sheikh Zayed Grand Mosque Centre, column "Abu Dhabi
                           City" (19 Jul 2023 - 6 Jul 2024, 353 days; times by the International Astronomy Center)
  awqaf_website_abudhabi_2026-11.csv  Awqaf's own website, region "Abu Dhabi" (official_id 36), 16-17 Nov 2026
Fits (the iacad_refit families, here around Abu Dhabi) are made on alternate months of the calendar and scored on
the other months, then the rule fitted on the whole calendar is scored, frozen, on Awqaf's own two days.
usage: python3 accuracy/tools/awqaf_fit.py
"""
import os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import evidence as E  # noqa: E402
import iacad_refit as R  # noqa: E402

ROOT = os.path.join(HERE, '..')
R.LATS = np.round(np.arange(24.30, 24.6001, 0.02), 2)
R.LONS = np.round(np.arange(54.20, 54.6001, 0.005), 3)
R.LON0 = 54.40
PROFILE_NOW = {'lat': 24.4539, 'lon': 54.3773}


def profile_now():
    """the app's UAE_AWQAF profile in the refit notation (18/18, dip 0.19, nearest, -1 -3 +3 +3 +2 +1)."""
    a18 = int(np.argmin(abs(R.ANG - 18.0))); d19 = int(np.argmin(abs(R.DIPS - 0.19)))
    c = dict(PROFILE_NOW)
    for p, k in zip(E.PR, (-1, -3, 3, 3, 2, 1)):
        c[p] = (a18 if p in ('fajr', 'isha') else d19 if p in ('shuruq', 'maghrib') else 0, 'round', k)
    return c


def main():
    S = E.load(os.path.join(ROOT, 'data', 'szgmc_abudhabi_1445.csv'))
    A = E.load(os.path.join(ROOT, 'data', 'awqaf_website_abudhabi_2026-11.csv'))
    ds = sorted(S)
    odd = {d: S[d] for d in ds if d.month % 2}
    even = {d: S[d] for d in ds if not d.month % 2}
    now = profile_now()
    print('Awqaf profile now: calendar %s, Awqaf website %s' % (R.score(now, ds, S), R.score(now, sorted(A), A)))
    for fam in ('c1family', 'free'):
        for name, tr, te in (('odd->even', odd, even), ('even->odd', even, odd)):
            c = R.fit(sorted(tr), tr, fam)
            b, bp = R.score(c, sorted(te), te)
            print('%-8s %s fit %d/%d, held-out %d/%d (%.1f%%) %s' % (fam, name, c['fit'], 6 * len(tr), b, 6 * len(te), 100.0 * b / (6 * len(te)), bp))
        c = R.fit(ds, S, fam)
        print('%-8s whole calendar: %s\n         fit %d/%d (%.1f%%); Awqaf website (never seen) %s' % (
            fam, R.describe(c), c['fit'], 6 * len(ds), 100.0 * c['fit'] / (6 * len(ds)), R.score(c, sorted(A), A)))


if __name__ == '__main__':
    main()
