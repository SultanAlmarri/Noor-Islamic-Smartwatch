"""Extracts Abu Dhabi City's daily prayer times from the Sheikh Zayed Grand Mosque Centre's UAE Hijri Calendar 1445
(https://www.szgmc.gov.ae/images/calendar/HijriBook1445_en.pdf; "The International Astronomy Center - Abu Dhabi
calculated prayer times based on the unified standards of the Sharia and Astronomical Committee at Sheikh Zayed Grand
Mosque Centre"). One page per day, 1 Muharram 1445 (19 July 2023) onward; times are printed in 12-hour form.

usage: pdftotext -layout HijriBook1445_en.pdf out.txt; python3 accuracy/tools/parse_szgmc_calendar.py out.txt
       (writes accuracy/data/szgmc_abudhabi_1445.csv)
"""
import os, re, sys, datetime as dt

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
LABELS = ['FAJR', 'SUNRISE', 'DHUHR', 'ASR', 'MAGHRIB', 'ISHA']
WEEK = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']


def to24(t, label):
    h, m = map(int, t.split(':'))
    if label in ('ASR', 'MAGHRIB', 'ISHA') or (label == 'DHUHR' and h < 11):
        h += 12
    return '%02d:%02d' % (h, m)


def main(path):
    lines = open(path, encoding='utf-8').read().split('\n')
    days, weekdays = [], []
    i = 0
    while i < len(lines):
        if re.search(r'ZAYED CITY\s+AL AIN CITY\s+ABU DHABI CITY\s+PRAYER TIMES', lines[i]):
            vals = {}
            j = i + 1
            while j < len(lines) and len(vals) < 6 and j < i + 20:
                m = re.match(r'\s*(\d{1,2}:\d\d)\s+(\d{1,2}:\d\d)\s+(\d{1,2}:\d\d)\s+(FAJR|SUNRISE|DHUHR|ASR|MAGHRIB|ISHA)\s*$', lines[j])
                if m:
                    vals[m.group(4)] = m.group(3)          # third column = Abu Dhabi City
                j += 1
            if len(vals) == 6:
                days.append([to24(vals[k], k) for k in LABELS])
                # weekday printed at the top of the day's page, a few lines above
                wd = None
                for k in range(i, max(0, i - 25), -1):
                    w = re.match(r'\s*(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\s*$', lines[k])
                    if w:
                        wd = w.group(1); break
                weekdays.append(wd)
            i = j
        else:
            i += 1
    # Pages run day by day from 1 Muharram 1445 = 19 July 2023; a page whose times the text extraction could not read is
    # missing from the list, which the weekday printed on each page reveals: the dates are re-aligned there.
    start = dt.date(2023, 7, 19)
    dates, gap = [], 0
    for n, w in enumerate(weekdays):
        d = start + dt.timedelta(n + gap)
        while w and WEEK[d.weekday()] != w:
            gap += 1; d = start + dt.timedelta(n + gap)
            print('page missing before', d)
        dates.append(d)
    if gap > 3 or dates[-1] > dt.date(2024, 7, 6):
        raise SystemExit('day pages are not in calendar order')
    out = os.path.join(ROOT, 'data', 'szgmc_abudhabi_1445.csv')
    with open(out, 'w') as f:
        f.write('# Sheikh Zayed Grand Mosque Centre, UAE Hijri Calendar 1445 (HijriBook1445_en.pdf), column "ABU DHABI CITY".\n')
        f.write('# Times calculated by the International Astronomy Center - Abu Dhabi (the calendar\'s own statement). UTC+4.\n')
        f.write('date,fajr,shuruq,dhuhr,asr,maghrib,isha\n')
        for d, t in zip(dates, days):
            f.write('%s,%s\n' % (d, ','.join(t)))
    print('saved', out)


if __name__ == '__main__':
    main(sys.argv[1])
