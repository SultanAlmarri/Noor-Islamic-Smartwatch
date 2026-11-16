"""Fetches one official published timetable per authority (one request each) and stores it as a CSV in accuracy/data,
with its provenance in the header. Used only to MEASURE the app's calculation against the authority's own table.

  JAKIM (Malaysia)   e-Solat, zone WLY01 (Kuala Lumpur, Putrajaya), calendar year 2026
                     https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone=WLY01
                     (the public JSON service behind JAKIM's e-Solat site)
  Diyanet (Turkiye)  namazvakitleri.diyanet.gov.tr, Istanbul (city id 9541), the yearly table on the city's page
  Umm al-Qura        KACST's Umm al-Qura service, Makkah (21.426666 N, 39.831666 E, UTC+3), Hijri year 1448
                     (the request the official site www.ummulqura.org.sa makes for its yearly table)

usage: python3 accuracy/tools/fetch_official_tables.py [jakim|diyanet|uqu ...]
"""
import os, re, sys, json, html, datetime as dt, urllib.request

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
UA = {'User-Agent': 'accuracy-research/1.0 (one request per table)'}
TODAY = dt.date.today().isoformat()


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90) as r:
        return r.read().decode('utf-8')


def save(name, header, rows):
    path = os.path.join(ROOT, 'data', name)
    with open(path, 'w') as f:
        for h in header:
            f.write('# ' + h + '\n')
        f.write('date,fajr,shuruq,dhuhr,asr,maghrib,isha\n')
        for r in rows:
            f.write(','.join(r) + '\n')
    print('saved', path, len(rows), 'days')


def jakim():
    url = 'https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone=WLY01'
    d = json.loads(get(url))
    MY = {'Jan': 1, 'Feb': 2, 'Mac': 3, 'Mar': 3, 'Apr': 4, 'Mei': 5, 'May': 5, 'Jun': 6, 'Jul': 7, 'Ogo': 8, 'Ogos': 8, 'Aug': 8,
          'Sep': 9, 'Okt': 10, 'Oct': 10, 'Nov': 11, 'Dis': 12, 'Dec': 12}
    rows = []
    for p in d['prayerTime']:
        day, mon, yr = p['date'].split('-')
        date = dt.date(int(yr), MY[mon], int(day))
        rows.append([date.isoformat()] + [p[k][:5] for k in ('fajr', 'syuruk', 'dhuhr', 'asr', 'maghrib', 'isha')])
    save('jakim_wly01_2026.csv', ['JAKIM e-Solat, zone %s, retrieved %s from %s' % (d.get('zone'), TODAY, url),
                                  'Official zone timetable (Kuala Lumpur, Putrajaya), Asia/Kuala_Lumpur (UTC+8)'], rows)


def diyanet():
    url = 'https://namazvakitleri.diyanet.gov.tr/tr-TR/9541/istanbul-icin-namaz-vakti'
    s = get(url)
    TR = {'Ocak': 1, 'Şubat': 2, 'Mart': 3, 'Nisan': 4, 'Mayıs': 5, 'Haziran': 6, 'Temmuz': 7, 'Ağustos': 8,
          'Eylül': 9, 'Ekim': 10, 'Kasım': 11, 'Aralık': 12}
    days = {}
    for r in re.findall(r'<tr[^>]*>(.*?)</tr>', s, re.S):
        c = [html.unescape(re.sub('<[^>]+>', '', x)).strip() for x in re.findall(r'<td[^>]*>(.*?)</td>', r, re.S)]
        if len(c) == 8:
            p = c[0].split()
            key = dt.date(int(p[2]), TR[p[1]], int(p[0])).isoformat()
            if key in days and days[key] != c[2:]:
                raise SystemExit('two different rows for ' + key)
            days[key] = c[2:]
    save('diyanet_istanbul_2026.csv', ['Diyanet Isleri Baskanligi, Istanbul (city 9541), yearly table on %s, retrieved %s' % (url, TODAY),
                                       'Columns: Imsak (= Fajr), Gunes (sunrise), Ogle, Ikindi, Aksam, Yatsi. Europe/Istanbul (UTC+3)'],
         [[k] + days[k] for k in sorted(days)])


def uqu():
    url = ('https://umqserv.kacst.gov.sa/api/v1/Prayer/GetPrayerHijriYear?lang=en&format=24&yh=1448'
           '&lat=21.426666&lon=39.831666&zone=3')
    d = json.loads(get(url))
    rows = []
    for p in d:
        g = p['gregorianDate']; t = p['prayerTimes']
        h = p['hijriDate']
        rows.append(['%04d-%02d-%02d' % (g['year'], g['month'], g['day'])] + [t[k] for k in ('fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha')] + ['%d-%02d-%02d' % (h['year'], h['month'], h['day'])])
    path_rows = [r[:7] for r in rows]
    hijri = {r[0]: r[7] for r in rows}
    save('ummalqura_makkah_1448.csv', ['Umm al-Qura (KACST) official prayer times, Makkah 21.426666 N 39.831666 E, UTC+3, Hijri year 1448',
                                       'retrieved %s from %s (the request www.ummulqura.org.sa makes)' % (TODAY, url),
                                       'Ramadan 1448 = %s .. %s' % (min(k for k, v in hijri.items() if v[5:7] == '09'), max(k for k, v in hijri.items() if v[5:7] == '09'))],
         path_rows)


if __name__ == '__main__':
    for name in sys.argv[1:] or ['jakim', 'diyanet', 'uqu']:
        globals()[name]()
