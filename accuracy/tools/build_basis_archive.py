import sys, datetime as dt, pickle, time
sys.path.insert(0,'tools'); import basis, analyse as A
W=A.load('data/iacad_official_archive_2014_2023.csv')
dates=sorted(d for d in W if d.year<2019)
t=time.time(); basis.build(dates,'data/basis_archive_pre2019.pkl'); print(len(dates),'dates %.0fs'%(time.time()-t))
