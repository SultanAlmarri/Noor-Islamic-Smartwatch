# Validation report (research harness)

Generated 2026-11-04 by `tools/validate.py`.

## Dataset integrity tests

- [PASS] official rows ordered Fajr<Shuruq<Dhuhr<Asr<Maghrib<Isha  ([])
- [PASS] no day-to-day jump > 3 min in consecutive official days  ([])
- [PASS] emirates247 copy identical to IACAD API (Nov 2026, 180 values)  ([])
- [PASS] Nestle 2020 PDF equals official archive on 2020-04-24
- [PASS] Era 1: Shuruq-Fajr = 80 and Isha-Maghrib = 90 on 269 of 270 days (exception: 2016-04-12)
- [PASS] Era 1 near-perpetual: 88 of 90 year-apart pairs identical (differ: 2015-05-05, 2016-05-05, Asr by 1 min)

## Exact-minute agreement with official IACAD data

| Engine | Dataset | Fajr | Shuruq | Dhuhr | Asr | Maghrib | Isha | All |
|---|---|---|---|---|---|---|---|---|
| current watch-face engine v1.3.2 | E1 (to 1438 AH), 270 days | 13/270 | 0/270 | 40/270 | 0/270 | 50/270 | 0/270 | 103/1620 (6.4%) |
| current watch-face engine v1.3.2 | E2 (1439-1441 AH), 165 days | 146/165 | 147/165 | 133/165 | 0/165 | 144/165 | 137/165 | 707/990 (71.4%) |
| current watch-face engine v1.3.2 | E3 (1442 AH onward), 98 days | 75/98 | 76/98 | 87/98 | 85/98 | 79/98 | 82/98 | 484/588 (82.3%) |
| candidate C1 (lat 25.12, 18.0/18.0, horizon SD+34'+0.10 deg, ceil, -2/-4/+2/0/+2/0) - NOT ADOPTED | E1 (to 1438 AH), 270 days | 16/270 | 0/270 | 35/270 | 0/270 | 38/270 | 0/270 | 89/1620 (5.5%) |
| candidate C1 (lat 25.12, 18.0/18.0, horizon SD+34'+0.10 deg, ceil, -2/-4/+2/0/+2/0) - NOT ADOPTED | E2 (1439-1441 AH), 165 days | 118/165 | 138/165 | 141/165 | 0/165 | 147/165 | 135/165 | 679/990 (68.6%) |
| candidate C1 (lat 25.12, 18.0/18.0, horizon SD+34'+0.10 deg, ceil, -2/-4/+2/0/+2/0) - NOT ADOPTED | E3 (1442 AH onward), 98 days | 82/98 | 90/98 | 91/98 | 91/98 | 87/98 | 86/98 | 527/588 (89.6%) |
| salatcalendar.com official mode (IAC replica, reference only) | E2 (1439-1441 AH), 165 days | 5/5 | 3/5 | 5/5 | 0/5 | 1/5 | 5/5 | 19/30 (63.3%) |
| salatcalendar.com official mode (IAC replica, reference only) | E3 (1442 AH onward), 98 days | 70/98 | 66/98 | 90/98 | 91/98 | 65/98 | 83/98 | 465/588 (79.1%) |
