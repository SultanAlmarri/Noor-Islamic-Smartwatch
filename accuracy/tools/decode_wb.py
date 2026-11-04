import json, datetime as dt, csv, sys
J=json.load(open('data/iacad_wayback_enc.json'))
rows={}; bad=0
for seg in J['segments']:
    start,n,s,code=seg.split(' '); n=int(n); s=int(s)
    v=[int(code[i*4:i*4+4]) for i in range(6)]; out=[v[:]]; body=code[24:]
    if len(body)!=(n-1)*6: print('LEN', start, len(body), (n-1)*6); bad+=1; continue
    for i in range(0,len(body),6):
        v=[a+ord(c)-109 for a,c in zip(v,body[i:i+6])]; out.append(v[:])
    tot=sum(map(sum,out))
    if tot!=s: print('SUM', start, tot, s); bad+=1
    d=dt.date.fromisoformat(start)
    for r in out: rows[d]=('wayback',r); d+=dt.timedelta(1)
R=J['ramadan_2022_R20220620170505']; d=dt.date.fromisoformat(R['start'])
for r in R['rows']:
    assert d not in rows; rows[d]=('wayback-ramadan2022',r); d+=dt.timedelta(1)
with open('data/iacad_official_archive_2014_2023.csv','w',newline='') as f:
    f.write('# Official IACAD SmartPortal timetables (Dubai City) recovered from Internet Archive captures; see iacad_wayback_enc.json for provenance\n')
    w=csv.writer(f); w.writerow(['date','fajr','shuruq','dhuhr','asr','maghrib','isha','source'])
    for d in sorted(rows):
        src,r=rows[d]; w.writerow([d.isoformat()]+['%02d:%02d'%divmod(x,60) for x in r]+[src])
print('dates', len(rows), 'bad segments', bad)
sys.exit(1 if bad else 0)
