import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_all.pkl','rb'))
W=A.load('data/iacad_official_archive_2014_2023.csv')
Q={d:v for d,v in W.items() if d>=dt.date(2020,8,20)}
S=A.subset_basis(B,sorted(Q)); print('SmartPortal-era-3 days',len(Q))
for pr in A.PR:
    r=A.scan(S,Q,pr); sp=r['spread']; k=np.unravel_index(np.argmin(sp),sp.shape)
    ok=sp<1
    msg='min spread %.3f at lat %.2f param %s; feasible cells %d'%(sp[k],A.LATS[k[0]],r['params'][k[2]],int(ok.sum()))
    if ok.any():
        la=A.LATS[np.nonzero(ok)[0]]; ps=[r['params'][i] for i in np.nonzero(ok)[2]]
        msg+='  lat %.2f-%.2f'%(la.min(),la.max())+('  param %s-%s'%(min(ps),max(ps)) if ps[0] is not None else '')
    print(' ',pr,msg)
