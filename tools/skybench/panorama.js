// (0.23) The Sun's real path for the benches: a port of the app's SunPanorama.kt and SkyGeometry (LivingSky.kt).
// Shared by the Medium bench (tools/skybench/render.js) and the High bench (tools/sky3d/bench.js).
// The Sun's position comes from the Astronomical Almanac's low-precision formulas (about 0.01°; the app uses SPA).
// An equal-angle picture facing the noon Sun, one scale (px per degree) across and up, never mirrored.
(function (root) {
  const DEG = Math.PI / 180;
  const wrap = (a) => { let x = a % 360; if (x > 180) x -= 360; if (x <= -180) x += 360; return x; };

  /** [elevation, azimuth from north towards east] (degrees) at unix ms. */
  function sun(ms, lat, lon) {
    const n = ms / 86400000 + 2440587.5 - 2451545.0;
    const g = (357.528 + 0.9856003 * n) * DEG;
    const lam = (280.460 + 0.9856474 * n + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)) * DEG;
    const eps = (23.439 - 0.0000004 * n) * DEG;
    const ra = Math.atan2(Math.cos(eps) * Math.sin(lam), Math.cos(lam));
    const dec = Math.asin(Math.sin(eps) * Math.sin(lam));
    const h = ((18.697374558 + 24.06570982441908 * n) * 15 + lon) * DEG - ra;
    const p = lat * DEG;
    const el = Math.asin(Math.sin(p) * Math.sin(dec) + Math.cos(p) * Math.cos(dec) * Math.cos(h));
    const az = Math.atan2(Math.sin(h), Math.cos(h) * Math.sin(p) - Math.tan(dec) * Math.cos(p)) / DEG + 180;
    return [el / DEG, ((az % 360) + 360) % 360];
  }

  // LivingSky.horizonLift: the Sun's visible upper edge, lifted by the air near the horizon.
  const refr = (e) => { const h = Math.max(e, -1.9); return 1.02 / Math.tan((h + 10.3 / (h + 5.11)) * DEG) / 60; };
  function apparent(el) {
    if (el >= -0.8333) return el + refr(el) * (0.5667 / refr(-0.8333)) + 0.2667;
    const t = Math.min(1, Math.max(0, (el + 4) / (4 - 0.8333)));
    return el + 0.8333 * t * t * (3 - 2 * t);
  }

  /**
   * The day's track: startMs (local midnight), lat, lon; events: {FAJR, SHURUQ, MAGHRIB, ISHA} as fractions of the day
   * (any may be missing). Returns { fr, el, across, facingNorth, travel, start, end, across bounds, highest, lowest }.
   */
  function track(startMs, lat, lon, events) {
    const fr = [], el = [], az = [];
    const coarse = []; for (let m = 0; m <= 1440; m += 6) coarse.push(m);
    let top = 0, topEl = -99;
    coarse.forEach((m) => { const s = sun(startMs + m * 60000, lat, lon); if (s[0] > topEl) { topEl = s[0]; top = m; } });
    const mins = topEl > 70 ? coarse.filter(m => m <= top - 60 || m >= top + 60).concat(Array.from({ length: 119 }, (_, i) => top - 59 + i)).sort((a, b) => a - b) : coarse;
    mins.forEach((m) => { const s = sun(startMs + m * 60000, lat, lon); fr.push(m / 1440); el.push(s[0]); az.push(s[1]); });
    const n = fr.length;
    let ti = 0; for (let i = 0; i < n; i++) if (el[i] > el[ti]) ti = i;
    const NZ = 89;
    let noonAz;
    if (el[ti] < NZ) noonAz = az[ti];
    else {
      let later = n - 1; for (let i = ti; i < n; i++) if (el[i] < NZ - 4) { later = i; break; }
      let earlier = 0; for (let i = ti; i >= 0; i--) if (el[i] < NZ - 4) { earlier = i; break; }
      noonAz = Math.cos(az[later] * DEG) + Math.cos(az[earlier] * DEG) > 0 ? 0 : 180;
    }
    const facingNorth = Math.cos(noonAz * DEG) > 0, facing = facingNorth ? 0 : 180, travel = facingNorth ? -1 : 1;
    const step = (d) => (Math.abs(d) > 90 && d * travel < 0 ? d + 360 * travel : d);
    const acr = new Array(n).fill(NaN), ok = el.map(e => e < NZ);
    let first = ti, best = 1e9; for (let i = 0; i < n; i++) if (ok[i] && Math.abs(i - ti) < best) { best = Math.abs(i - ti); first = i; }
    acr[first] = wrap(az[first] - facing);
    let last = first; for (let i = first + 1; i < n; i++) if (ok[i]) { acr[i] = acr[last] + step(wrap(az[i] - az[last])); last = i; }
    last = first; for (let i = first - 1; i >= 0; i--) if (ok[i]) { acr[i] = acr[last] - step(wrap(az[last] - az[i])); last = i; }
    for (let i = 0; i < n; i++) if (isNaN(acr[i])) {
      let a = i; while (a >= 0 && isNaN(acr[a])) a--; let b = i; while (b < n && isNaN(acr[b])) b++;
      acr[i] = a >= 0 && b < n ? acr[a] + (acr[b] - acr[a]) * (fr[i] - fr[a]) / (fr[b] - fr[a]) : a >= 0 ? acr[a] : b < n ? acr[b] : 0;
    }
    const noonF = fr[ti], hour = 1 / 24;
    let s = events.FAJR ?? (events.SHURUQ !== undefined ? events.SHURUQ - hour : undefined); if (!(s > 0 && s < noonF)) s = 0;
    let e = events.ISHA ?? (events.MAGHRIB !== undefined ? events.MAGHRIB + hour : undefined); if (!(e < 1 && e > noonF)) e = 1;
    const lerp = (v, f) => {
      if (f <= fr[0]) return v[0]; if (f >= fr[n - 1]) return v[n - 1];
      let lo = 0, hi = n - 1; while (hi - lo > 1) { const m = (lo + hi) >> 1; if (fr[m] <= f) lo = m; else hi = m; }
      return v[lo] + (v[hi] - v[lo]) * (f - fr[lo]) / (fr[hi] - fr[lo]);
    };
    const T = { fr, el, across: acr, facingNorth, travel, start: s, end: e, noonF, events, maxEl: Math.max(...el), minEl: Math.min(...el), acrossAt: f => lerp(acr, f), elAt: f => lerp(el, f) };
    let a = Math.min(T.acrossAt(s), T.acrossAt(e)), b = Math.max(T.acrossAt(s), T.acrossAt(e));
    let hi = Math.max(T.elAt(s), T.elAt(e)), lo = Math.min(T.elAt(s), T.elAt(e));
    for (let i = 0; i < n; i++) if (fr[i] >= s && fr[i] <= e) { a = Math.min(a, acr[i]); b = Math.max(b, acr[i]); hi = Math.max(hi, el[i]); lo = Math.min(lo, el[i]); }
    Object.assign(T, { acrossMin: a, acrossMax: b, highest: hi, lowest: lo });
    return T;
  }

  // (0.23) SimpleArch.kt: the default path. One fixed half-sine arch to the arch's top, its ends where the Sun really
  // rises and sets on a fixed scale (half the width = 135 degrees from the noon Sun), evenly in time; a fixed dip under the
  // horizon to Fajr and Isha at their real azimuths and on to the edges; polar day and night fallbacks.
  function simpleArch(T, w, hy, arcTop, ground) {
    const HALF = 135, MARGIN = 0.03, EDGE = 0.04, GAP = 0.03;
    const travel = T.travel, height = Math.max(hy - arcTop, 0);
    const dip = ground - hy > 1 ? 0.5 * (ground - hy) : 0.3 * height;
    const kA = 0.5 * w / HALF;
    const X = (a) => Math.min((1 - MARGIN) * w, Math.max(MARGIN * w, w / 2 + kA * a));
    const ev = T.events, noon = T.noonF, up = T.maxEl > -0.8333;
    let r = ev.SHURUQ !== undefined && ev.SHURUQ < noon ? ev.SHURUQ : undefined;
    let s = ev.MAGHRIB !== undefined && ev.MAGHRIB > noon ? ev.MAGHRIB : undefined;
    const polar = up && r === undefined && s === undefined;
    const polarDay = polar && T.minEl > -0.8333;
    let rise, set, arch = true, xr, xs;
    const start = travel > 0 ? MARGIN * w : (1 - MARGIN) * w, end = travel > 0 ? (1 - MARGIN) * w : MARGIN * w;
    if (r !== undefined && s !== undefined) { rise = r; set = s; xr = X(T.acrossAt(r)); xs = X(T.acrossAt(s)); }
    else if (r !== undefined) { rise = r; set = 2 * noon - r; xr = X(T.acrossAt(r)); xs = w - xr; }
    else if (s !== undefined) { set = s; rise = 2 * noon - s; xs = X(T.acrossAt(s)); xr = w - xs; }
    else if (polar) { rise = noon - 0.5; set = noon + 0.5; xr = start; xs = end; }
    else { rise = noon; set = noon; arch = false; xr = w / 2; xs = w / 2; }
    const from = travel > 0 ? -EDGE * w : (1 + EDGE) * w, to = travel > 0 ? (1 + EDGE) * w : -EDGE * w;
    const outside = (x, f0, dir) => { const gap = GAP * w; const h = (x - f0) * dir < gap ? f0 + dir * gap : x; return Math.min(0.99 * w, Math.max(0.01 * w, h)); };
    const curve = (pts) => {
      const xs_ = pts.map(p => p[0]), ys = pts.map(p => travel * p[1]); const n = xs_.length;
      const d = [], m = [];
      for (let i = 0; i < n - 1; i++) d.push((ys[i + 1] - ys[i]) / (xs_[i + 1] - xs_[i]));
      for (let i = 0; i < n; i++) m.push(i === 0 ? d[0] : i === n - 1 ? d[n - 2] : (d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2));
      for (let i = 0; i < n - 1; i++) { if (d[i] === 0) { m[i] = 0; m[i + 1] = 0; continue; } const a = m[i] / d[i], b = m[i + 1] / d[i], q = a * a + b * b; if (q > 9) { const k = 3 / Math.sqrt(q); m[i] = k * a * d[i]; m[i + 1] = k * b * d[i]; } }
      return (v) => { let i = 0; while (i < n - 2 && xs_[i + 1] <= v) i++; const h = xs_[i + 1] - xs_[i], t = Math.max(0, Math.min(1, (v - xs_[i]) / h)), t2 = t * t, t3 = t2 * t;
        return travel * ((2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * m[i] + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * m[i + 1]); };
    };
    const fajr = ev.FAJR !== undefined && ev.FAJR < rise - 1e-4 ? ev.FAJR : undefined;
    const isha = ev.ISHA !== undefined && ev.ISHA > set + 1e-4 ? ev.ISHA : undefined;
    const xF = fajr !== undefined ? outside(X(T.acrossAt(Math.max(0, Math.min(1, fajr)))), xr, -travel) : undefined;
    const xI = isha !== undefined ? outside(X(T.acrossAt(Math.max(0, Math.min(1, isha)))), xs, travel) : undefined;
    const morning = rise > 1e-4 ? curve(fajr !== undefined && fajr <= 1e-4 ? [[0, xF], [rise, xr]] : [[0, from], ...(fajr !== undefined ? [[fajr, xF]] : []), [rise, xr]]) : null;
    const evening = set < 1 - 1e-4 ? curve(isha !== undefined && isha >= 1 - 1e-4 ? [[set, xs], [1, xI]] : [[set, xs], ...(isha !== undefined ? [[isha, xI]] : []), [1, to]]) : null;
    const x = (f0) => { const t = Math.max(0, Math.min(1, f0));
      if (t < rise) return morning ? morning(t) : xr; if (t > set) return evening ? evening(t) : xs;
      if (set - rise < 1e-9) return xr; return xr + (xs - xr) * (t - rise) / (set - rise); };
    const y = (el, f0) => { const t = Math.max(0, Math.min(1, f0));
      if (!arch) { const d = Math.min(1, Math.abs(x(t) - w / 2) / (0.5 * w)); return hy + dip * (1 / 3 + 2 / 3 * Math.sin(Math.PI / 2 * d)); }
      if (t >= rise && t <= set) return hy - height * Math.sin(Math.PI * (t - rise) / (set - rise));
      if (polarDay) return hy;
      const [f1, edge] = t < rise ? [xr, from] : [xs, to];
      const d = Math.min(1, Math.max(0, Math.abs(x(t) - f1) / Math.max(Math.abs(edge - f1), 1)));
      return hy + dip * Math.sin(Math.PI / 2 * d); };
    const fractions = []; for (let i = 0; i <= 480; i++) fractions.push(i / 480);
    if (rise >= 0 && rise <= 1) fractions.push(rise); if (set >= 0 && set <= 1) fractions.push(set);
    fractions.sort((a, b) => a - b);
    const points = (f0 = 0, f1 = 1) => { const pts = [[x(f0), y(0, f0)]]; fractions.forEach(f => { if (f > f0 && f < f1) pts.push([x(f), y(0, f)]); }); pts.push([x(f1), y(0, f1)]); return pts; };
    return { k: kA, x, y, fractions, points, travel, facingNorth: T.facingNorth, elAt: T.elAt, simple: true };
  }

  /** SkyGeometry: w (px), horizonY, arcTopY, groundY; mode 'simple' (the default) or 'real'. Returns { k, x(f), y(el, f), fractions, travel }. */
  function geometry(T, w, hy, arcTop, ground, mode) {
    if (mode !== 'real') return simpleArch(T, w, hy, arcTop, ground);
    const MARGIN = 0.07, EDGE = 0.04;
    const span = Math.max(T.acrossMax - T.acrossMin, 1);
    const kW = w * (1 - 2 * MARGIN) / span;
    const kV = hy - arcTop > 1 ? (hy - arcTop) / Math.max(apparent(T.highest), 1) : Infinity;
    const kG = ground - hy > 1 && apparent(T.lowest) < -1 ? (ground - hy) / -apparent(T.lowest) : Infinity;
    const k = Math.min(kW, kV, kG);
    const cx = w / 2 - k * (T.acrossMin + T.acrossMax) / 2;
    const tx = (f) => cx + k * T.acrossAt(f);
    const travel = T.travel;
    const from = travel > 0 ? -EDGE * w : (1 + EDGE) * w, to = travel > 0 ? (1 + EDGE) * w : -EDGE * w;
    const xs = tx(T.start), xe = tx(T.end);
    const slope = (f, sec) => { const d = 1 / 1440; const s = (tx(f + d) - tx(f - d)) / (2 * d); const m = s * travel > 0 ? s : 0; return Math.abs(m) > 3 * Math.abs(sec) ? 3 * sec : m; };
    const ms = T.start > 0 ? slope(T.start, (xs - from) / T.start) : 0;
    const me = T.end < 1 ? slope(T.end, (to - xe) / (1 - T.end)) : 0;
    const herm = (t, a, b, ma, mb) => { const t2 = t * t, t3 = t2 * t; return (2 * t3 - 3 * t2 + 1) * a + (t3 - 2 * t2 + t) * ma + (-2 * t3 + 3 * t2) * b + (t3 - t2) * mb; };
    const x = (f0) => {
      const f = Math.max(0, Math.min(1, f0));
      if (f < T.start) return herm(f / T.start, from, xs, xs - from, ms * T.start);
      if (f > T.end) return herm((f - T.end) / (1 - T.end), xe, to, me * (1 - T.end), to - xe);
      return tx(f);
    };
    const y = (el) => hy - k * apparent(el);   // (the real path: from the elevation alone)
    const fractions = [];
    if (T.start > 0) for (let i = 0; i < 48; i++) fractions.push(T.start * i / 48);
    fractions.push(T.start);
    T.fr.forEach(f => { if (f > T.start && f < T.end) fractions.push(f); });
    fractions.push(T.end);
    if (T.end < 1) for (let i = 1; i <= 48; i++) fractions.push(T.end + (1 - T.end) * i / 48);
    /** The path from f0 to f1 as points [x, y]. */
    const points = (f0 = 0, f1 = 1) => {
      const pts = [[x(f0), y(T.elAt(f0))]];
      fractions.forEach(f => { if (f > f0 && f < f1) pts.push([x(f), y(T.elAt(f))]); });
      pts.push([x(f1), y(T.elAt(f1))]);
      return pts;
    };
    return { k, x, y, fractions, points, travel, facingNorth: T.facingNorth, elAt: T.elAt };
  }

  root.SunPanorama = { sun, apparent, track, geometry };
})(typeof window !== 'undefined' ? window : globalThis);
