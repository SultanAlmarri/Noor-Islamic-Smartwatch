"""Replace the 'dhuhr' basis (max-altitude time) with the true meridian transit (hour angle 0),
computed with the NREL SPA transit routine, and keep the old one as 'maxalt' for reference."""
import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import model as M, basis
import pvlib.spa as spa
def transit_local(dates, lon, lat=25.15):
    ut0=np.array([(dt.datetime(d.year,d.month,d.day)-dt.datetime(1970,1,1)).total_seconds() for d in dates])
    tr,sr,ss=spa.transit_sunrise_sunset(ut0, lat, lon, M.DELTA_T, 1)
    return tr - (ut0 - M.TZ)        # seconds after local midnight
for path in ['data/basis_all.pkl']:
    B=pickle.load(open(path,'rb')); ds=B['dates']; D=basis.D
    f0=transit_local(ds,B['lon0']); fe=transit_local(ds,B['lon0']+D); fw=transit_local(ds,B['lon0']-D)
    fn=transit_local(ds,B['lon0'],B['lat0']+D); fs=transit_local(ds,B['lon0'],B['lat0']-D)
    B['maxalt']=B['dhuhr']
    B['dhuhr']=(f0,(fn-fs)/(2*D),(fe-fw)/(2*D),np.zeros_like(f0),(fe-2*f0+fw)/D**2,np.zeros_like(f0))
    pickle.dump(B,open(path,'wb'))
    print(path,'max |maxalt - meridian| = %.1f s'%np.max(np.abs(B['maxalt'][0]-f0)), ' lat sensitivity %.4f s/deg'%np.max(np.abs((fn-fs)/(2*D))))
