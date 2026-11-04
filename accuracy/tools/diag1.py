import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_2019_2026.pkl','rb'))
P=A.load('data/iacad_via_nestle_ramadan_2020.csv')
for drop in [[],[dt.date(2020,4,30),dt.date(2020,5,1)]]:
    Q={d:v for d,v in P.items() if d not in drop}
    S=A.subset_basis(B,sorted(Q))
    for pr in ['fajr','isha']:
        r=A.scan(S,Q,pr); sp=r['spread']; k=np.unravel_index(np.argmin(sp),sp.shape)
        print('drop',len(drop),pr,'min spread %.3f'%sp[k], 'feasible', int((sp<1).sum()), 'at', A.LATS[k[0]],A.LONS[k[1]],r['params'][k[2]])
# residual listing for fajr at a typical point
S=A.subset_basis(B,sorted(P))
u=A.residuals(S,P,'fajr',25.20,55.27,18.2)
for d,x in zip(sorted(P),u): print(d, P[d]['fajr'], '%.2f'%x)
