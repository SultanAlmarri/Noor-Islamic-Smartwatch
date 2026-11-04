"""Fetches the current month from IACAD's public prayer-times feed (the same single request the official
page https://www.iacad.gov.ae/en/prayer-times makes) and stores it as official/iacad_YYYY-MM.csv.
One request per run; run monthly."""
import json, os, sys, urllib.request, datetime as dt

URL = 'https://www.iacad.gov.ae/api/PublicApi/GeIntegrationResult'
req = urllib.request.Request(URL, data=json.dumps({'integrationId': 1}).encode(), method='POST',
                             headers={'Content-Type': 'application/json', 'User-Agent': 'NoorAccuracyBot/1.0 (monthly, one request)'})
with urllib.request.urlopen(req, timeout=60) as r:
    payload = json.load(r)
rows = []
for d in payload['data']:
    p = (d.get('Country') or d.get('country'))
    p = (p.get('Pray') or p.get('pray'))[0]
    g = lambda k: p.get(k) or p.get(k[0].lower() + k[1:])
    m, day, y = g('Date').split('/')
    rows.append(['%s-%s-%s' % (y, m, day), g('HDate')] + [g(k)[:5] for k in ('Fajr', 'Shorooq', 'Duhr', 'Asr', 'Maghrib', 'Isha')] + [g('CityE')])
month = rows[0][0][:7]
out = os.path.join(os.path.dirname(__file__), 'official', 'iacad_%s.csv' % month)
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'w') as f:
    f.write('# IACAD public feed, retrieved %s UTC, dateModified %s\n' % (dt.datetime.utcnow().isoformat(timespec='seconds'), payload.get('dateModified')))
    f.write('date,hijri,fajr,shuruq,dhuhr,asr,maghrib,isha,city\n')
    for r in rows: f.write(','.join(r) + '\n')
print('saved', out, len(rows), 'days')
