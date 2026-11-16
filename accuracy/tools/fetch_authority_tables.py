#!/usr/bin/env python3
"""Fetches small samples of national authorities' own published prayer times (times are facts), for measuring the
app's profiles against them (accuracy/results/authorities_measurements.md). One year per city at most, from each
authority's own public service, with a plain User-Agent; nothing is fetched by the app at runtime.

Writes core/prayer/src/test/resources/authorities/<id>.csv: a header comment with the source and the fetch date, then
date,fajr,shuruq,dhuhr,asr,maghrib,isha (local clock time, as published).

usage: python3 accuracy/tools/fetch_authority_tables.py [year]   (run from the repository root)

Sources (each checked by hand on 16 November 2026):
  JAKIM e-Solat API     https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=year&zone=<zone>
  DUMK (Kazakhstan)     https://api.muftyat.kz/prayer-times/<year>/<lat>/<lng>   (the city's own coordinates from
                        https://api.muftyat.kz/cities/?search=<name>)
  Islamic Community of Bosnia and Herzegovina, via vaktija.ba's public API (it publishes the Rijaset's Takvim)
                        https://api.vaktija.ba/vaktija/v1/<location>/<year>
  Diyanet (Turkiye)     https://namazvakitleri.diyanet.gov.tr/en-US/<district>/... (the page's yearly table)
  Kemenag (Indonesia)   via api.myquran.com, which relays bimasislam.kemenag.go.id/jadwalshalat
"""
import datetime as dt
import html
import json
import os
import re
import sys
import urllib.parse
import urllib.request

UA = {'User-Agent': 'prayer-tools/1.0'}
MALAY_MONTHS = ['jan', 'feb', 'mac', 'apr', 'mei', 'jun', 'jul', 'ogos', 'sep', 'okt', 'nov', 'dis']
OUT = 'core/prayer/src/test/resources/authorities'
TODAY = dt.date.today().isoformat()


def get(url, ua=UA):
    with urllib.request.urlopen(urllib.request.Request(url, headers=ua), timeout=60) as r:
        return r.read().decode('utf-8')


def hm(t):
    h, m = t.strip().split(':')[:2]
    return '%02d:%02d' % (int(h), int(m))


def write(name, source, rows, note=''):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + '.csv'), 'w') as f:
        f.write('# %s\n# fetched %s%s\n' % (source, TODAY, ('\n# ' + note) if note else ''))
        f.write('date,fajr,shuruq,dhuhr,asr,maghrib,isha\n')
        for d, t in sorted(rows):
            f.write(d + ',' + ','.join(hm(x) for x in t) + '\n')
    print(name, len(rows))


def jakim(year, zone):
    url = 'https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=year&zone=' + zone
    rows = []
    for r in json.loads(get(url))['prayerTime']:
        dd, mon, yy = r['date'].split('-')  # month names are Malay: Jan Feb Mac Apr Mei Jun Jul Ogos Sep Okt Nov Dis
        d = dt.date(int(yy), MALAY_MONTHS.index(mon.lower()) + 1, int(dd))
        if d.year == year:
            rows.append((d.isoformat(), [r['fajr'], r['syuruk'], r['dhuhr'], r['asr'], r['maghrib'], r['isha']]))
    write('jakim_' + zone.lower(), 'JAKIM e-Solat, zone ' + zone + ': ' + url, rows)


def dumk(year, name, search):
    found = json.loads(get('https://api.muftyat.kz/cities/?' + urllib.parse.urlencode({'search': search})))['results']
    c = next((x for x in found if x['title'].endswith('қаласы')), found[0])  # the city itself ("... қаласы"), not a village
    url = 'https://api.muftyat.kz/prayer-times/%d/%s/%s' % (year, c['lat'], c['lng'])
    rows = [(r['Date'], [r['fajr'], r['sunrise'], r['dhuhr'], r['asr'], r['maghrib'], r['isha']]) for r in json.loads(get(url))['result']]
    write('dumk_' + name, 'DUMK (muftyat.kz) %s, %s N %s E: %s' % (c['title'], c['lat'], c['lng'], url), rows,
          'the API also gives imsak (fajr + 10 min), sunset (maghrib - 3 min) and midnight, not stored')


def vaktija(year, location_id, name):
    url = 'https://api.vaktija.ba/vaktija/v1/%d/%d' % (location_id, year)
    d = json.loads(get(url))
    rows = []
    for mi, m in enumerate(d['mjesec']):
        for di, day in enumerate(m['dan']):
            rows.append((dt.date(year, mi + 1, di + 1).isoformat(), day['vakat']))
    write('iz_bih_' + name, 'Islamic Community of Bosnia and Herzegovina (Takvim) via vaktija.ba, ' + d['lokacija'] + ': ' + url, rows,
          'columns as published: zora (dawn), izlazak sunca, podne, ikindija, aksam, jacija')


def kemenag(year, city_id, name):
    rows = []
    for month in range(1, 13):
        url = 'https://api.myquran.com/v2/sholat/jadwal/%s/%d/%02d' % (city_id, year, month)
        d = json.loads(get(url))['data']
        for r in d['jadwal']:
            rows.append((r['date'], [r['subuh'], r['terbit'], r['dzuhur'], r['ashar'], r['maghrib'], r['isya']]))
    write('kemenag_' + name, 'Kemenag Bimas Islam schedule for %s, relayed by api.myquran.com: https://api.myquran.com/v2/sholat/jadwal/%s/<year>/<month>'
          % (d['lokasi'], city_id), rows, 'a third-party relay of bimasislam.kemenag.go.id/jadwalshalat; not Kemenag\'s own service')


def diyanet(year, district, name, slug):
    url = 'https://namazvakitleri.diyanet.gov.tr/en-US/%d/%s' % (district, slug)
    page = get(url, {'User-Agent': 'Mozilla/5.0 (prayer-tools/1.0)'})
    table = page[page.index('id="yourTable"'):]
    table = table[:table.index('</table>')]
    rows = []
    for tr in re.findall(r'<tr[^>]*>(.*?)</tr>', table, re.S):
        cells = [html.unescape(re.sub('<[^>]+>', '', c)).strip() for c in re.findall(r'<td[^>]*>(.*?)</td>', tr, re.S)]
        if len(cells) < 7 or not re.match(r'\d\d\.\d\d\.\d{4}', cells[0]):
            continue
        d = dt.datetime.strptime(cells[0][:10], '%d.%m.%Y').date()
        times = [c for c in cells[1:] if re.match(r'^\d\d:\d\d$', c)][:6]
        if d.year == year and len(times) == 6:
            rows.append((d.isoformat(), times))
    write('diyanet_' + name, 'Diyanet, ' + url + ' (yearly table)', rows)


if __name__ == '__main__':
    year = int(sys.argv[1]) if len(sys.argv) > 1 else 2026
    for z in ('WLY01', 'JHR02', 'PNG01', 'KTN01'):
        jakim(year, z)
    dumk(year, 'almaty', 'Алматы')
    dumk(year, 'astana', 'Астана')
    dumk(year, 'shymkent', 'Шымкент')
    dumk(year, 'aktobe', 'Ақтөбе')
    dumk(year, 'pavlodar', 'Павлодар')
    dumk(year, 'aktau', 'Ақтау')
    dumk(year, 'kyzylorda', 'Қызылорда')
    dumk(year, 'taldykorgan', 'Талдықорған')
    dumk(year, 'atyrau', 'Атырау')
    dumk(year, 'zhezkazgan', 'Жезқазған')
    dumk(year, 'karaganda', 'Қарағанды')
    dumk(year, 'karkaraly', 'Қарқаралы')
    dumk(year, 'ayagoz', 'Аягөз')
    vaktija(year, 77, 'sarajevo')
    diyanet(year, 9541, 'istanbul', 'prayer-time-for-istanbul')
    kemenag(year, '1301', 'jakarta')
    kemenag(year, '2622', 'makassar')
    kemenag(year, '0228', 'medan')
