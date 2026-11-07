import json, sys, math
def bez(x1,y1,x2,y2):
    def f(t):
        # solve x(s)=t
        lo,hi=0.0,1.0
        for _ in range(40):
            s=(lo+hi)/2; x=3*(1-s)**2*s*x1+3*(1-s)*s*s*x2+s**3
            if x<t: lo=s
            else: hi=s
        s=(lo+hi)/2; return 3*(1-s)**2*s*y1+3*(1-s)*s*s*y2+s**3
    return f
ease=bez(0.45,0,0.15,1)
def sm(a,b,x):
    t=min(1,max(0,(x-a)/(b-a))); return t*t*(3-2*t)
def hm(h,m): return (h*60+m)/1440
dxb={"lat":25.2048,"lon":55.2708,"tz":4}; ist={"lat":41.0082,"lon":28.9784,"tz":3}
base=dict(date="2026-11-07", density=2.0, skyScale=0.6, wDp=412, hDp=400, horizonDp=318, arcTopDp=237, topDp=40, hijriDay=26)
kind=sys.argv[1]; out=[]
fps=30
if kind.startswith("open"):
    # The app's own times for 7 November 2026: Dubai Isha 6:54 PM; Istanbul Shuruq 7:35 AM, Dhuhr 12:53 PM.
    if kind=="open_dxb": place,dunes,seed,now,start,title,cd,pn = dxb,True,3,hm(18,5),hm(17,15),"Isha  ·  6:54 PM","49:00","Dubai"
    else: place,dunes,seed,now,start,title,cd,pn = ist,False,11,hm(8,30),hm(7,35),"Dhuhr  ·  12:53 PM","4:23:00","Istanbul"
    D=(1900+1100*min(1,max(0,(now-start)/0.2)))/1000
    n=int((D+1.2)*fps)
    for i in range(n):
        t=i/fps; o=min(1,t/D)
        fr=start+(now-start)*ease(o)
        q=1-o; lapse=45*(1-q**3)-45
        out.append(dict(base, name=f"f{i:04d}", place=place, dunes=dunes, seed=seed, landSeed=seed*7+1, fraction=fr, time=40+t+lapse,
            fade=sm(0,0.38,o), pathAlpha=sm(0,0.45,o), marksAlpha=sm(0.25,0.75,o), landLift=(1-o)**2*14, title=title, countdown=cd, placeName=pn, next="ISHA" if dunes else "DHUHR"))
else:
    place,dunes,seed,pn = (dxb,True,3,"Dubai") if kind=="drag_dxb" else (ist,False,11,"Istanbul")
    T=12.0; n=int(T*fps)
    for i in range(n):
        u=i/(n-1)
        out.append(dict(base, name=f"f{i:04d}", place=place, dunes=dunes, seed=seed, landSeed=seed*7+1, xFrac=0.02+0.96*u, time=40+i/fps, scrub=True, placeName=pn))
json.dump(out, open(f"{kind}.json","w")); print(len(out))
