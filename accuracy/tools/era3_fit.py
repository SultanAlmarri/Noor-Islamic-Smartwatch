import sys, pickle, numpy as np, datetime as dt, json
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_all.pkl','rb'))
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv')
P={**W,**api}
E3={d:v for d,v in P.items() if d>=dt.date(2020,8,20)}
S=A.subset_basis(B,sorted(E3)); N=len(E3); print('Era 3 days',N, min(E3), max(E3))
out={}
for pr in A.PR:
    r=A.scan_matches(S,E3,pr); b=r['best']; mx=b.max()
    cells=np.argwhere(b==mx)
    lats=A.LATS[cells[:,0]]; lons=A.LONS[cells[:,1]]; ps=[r['params'][k] for k in cells[:,2]]
    out[pr]=r
    print('%-8s best %d/%d (%.1f%%)  cells %d  lat %.2f-%.2f  lon %.2f-%.2f  param %s-%s'%(pr,mx,N,100*mx/N,len(cells),lats.min(),lats.max(),lons.min(),lons.max(),min(ps) if ps[0] is not None else '-',max(ps) if ps[0] is not None else '-'))
pickle.dump(out,open('data/era3_matches.pkl','wb'))
