// Scene maths for the bench (mirrors what the Kotlin side does): sun path, prayer events, time warp, geometry,
// ridge profiles, uniforms.
(function (root) {
  const DEG = Math.PI / 180;
  function solarEl(ms, lat, lon) {
    const jd = ms / 86400000 + 2440587.5;
    const n = jd - 2451545.0;
    const L = (280.460 + 0.9856474 * n) % 360;
    const g = ((357.528 + 0.9856003 * n) % 360) * DEG;
    const lam = (L + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)) * DEG;
    const eps = (23.439 - 0.0000004 * n) * DEG;
    const ra = Math.atan2(Math.cos(eps) * Math.sin(lam), Math.cos(lam));
    const dec = Math.asin(Math.sin(eps) * Math.sin(lam));
    const gmst = (18.697374558 + 24.06570982441908 * n) % 24;
    const lst = (gmst * 15 + lon) * DEG;
    const ha = lst - ra;
    const el = Math.asin(Math.sin(lat * DEG) * Math.sin(dec) + Math.cos(lat * DEG) * Math.cos(dec) * Math.cos(ha));
    return { el: el / DEG, dec };
  }

  function day(place, dateStr) {
    const [Y, M, D] = dateStr.split('-').map(Number);
    const start = Date.UTC(Y, M - 1, D) - place.tz * 3600000;
    const end = start + 86400000;
    const samples = [];
    for (let m = 0; m <= 1440; m += 6) samples.push([m / 1440, solarEl(start + m * 60000, place.lat, place.lon).el]);
    const elAt = (f) => solarEl(start + f * 86400000, place.lat, place.lon).el;
    // Events by search.
    const noonF = samples.reduce((a, b) => (b[1] > a[1] ? b : a))[0];
    function cross(target, from, to) {
      let a = from, b = to; const fa = elAt(a) - target;
      for (let i = 0; i < 50; i++) { const m = (a + b) / 2; if ((elAt(m) - target) * fa > 0) a = m; else b = m; }
      return (a + b) / 2;
    }
    const dec = solarEl(start + noonF * 86400000, place.lat, place.lon).dec;
    const asrAlt = Math.atan(1 / (1 + Math.tan(Math.abs(place.lat * DEG - dec)))) / DEG;
    const ev = {
      FAJR: cross(-18.2, 0, noonF), SHURUQ: cross(-0.833, 0, noonF), DHUHR: noonF + 3 / 1440,
      ASR: cross(asrAlt, noonF, 1), MAGHRIB: cross(-0.833, noonF, 1), ISHA: cross(-18.2, noonF, 1),
    };
    const maxEl = Math.max(...samples.map(s => s[1])), minEl = Math.min(...samples.map(s => s[1]));
    return { samples, ev, elAt, noonF, maxEl, minEl, start };
  }

  function monotone(xs, ys) {
    const n = xs.length; const d = [], m = [];
    for (let i = 0; i < n - 1; i++) d.push((ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]));
    for (let i = 0; i < n; i++) m.push(i === 0 ? d[0] : i === n - 1 ? d[n - 2] : (d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2));
    for (let i = 0; i < n - 1; i++) {
      if (d[i] === 0) { m[i] = 0; m[i + 1] = 0; continue; }
      const a = m[i] / d[i], b = m[i + 1] / d[i], s = a * a + b * b;
      if (s > 9) { const t = 3 / Math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i]; }
    }
    return (v) => {
      let i = 0; for (let k = 0; k < n; k++) if (xs[k] <= v) i = k; i = Math.max(0, Math.min(n - 2, i));
      const h = xs[i + 1] - xs[i], t = Math.max(0, Math.min(1, (v - xs[i]) / h)), t2 = t * t, t3 = t2 * t;
      return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * m[i] + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * m[i + 1];
    };
  }

  function warp(dd, anchors) {
    const half = 30 / 1440; const xs = [0], ys = [0];
    const add = (x, y) => { if (x > xs[xs.length - 1] + 1e-4 && x < 1 - 1e-4 && y > ys[ys.length - 1]) { xs.push(x); ys.push(y); } };
    add(dd.ev.FAJR - half, anchors[0]); add(dd.ev.SHURUQ, anchors[1]); add(dd.ev.MAGHRIB, anchors[2]); add(dd.ev.ISHA + half, anchors[3]);
    xs.push(1); ys.push(1);
    return monotone(xs, ys);
  }

  // Ridge profiles, 0..1 over the width.
  function rng(seed) { let s = seed >>> 0 || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
  function noise1(r, n) { const v = Array.from({ length: n + 4 }, r); return (x) => { const i = Math.floor(x), f = x - i; const u = f * f * (3 - 2 * f); return v[(i % n + n) % n] * (1 - u) + v[((i + 1) % n + n) % n] * u; }; }
  // terrain: 'dunes' | 'hills' | 'mountains' | 'sea' (true/false still mean dunes/hills). A close port of LandProfile.kt.
  function ridges(seed, terrain, N) {
    if (terrain === true) terrain = 'dunes'; if (terrain === false || terrain === undefined) terrain = 'hills';
    const dunes = terrain === 'dunes';
    const r = rng(seed * 2654435761 + 7);
    const out = [];
    const norm = (arr, lo) => { const mn = Math.min(...arr), mx = Math.max(...arr); return arr.map(v => lo + (1 - lo) * (v - mn) / (mx - mn + 1e-9)); };
    for (let layer = 0; layer < 5; layer++) {
      const arr = new Array(N).fill(0);
      if (terrain === 'sea' && layer <= 2) {
        if (layer === 0 && r() < 0.7) {
          const left = r() < 0.5, width = 0.18 + 0.16 * r(), n1 = noise1(r, 64);
          for (let i = 0; i < N; i++) { const u = i / (N - 1); const t = (left ? u : 1 - u) / width; const s = 1 - t, q = Math.min(s / 0.4, 1), tip = q * q * (3 - 2 * q); arr[i] = t >= 1 ? 0 : Math.min(1, Math.max(0, Math.pow(s, 0.7) * tip * (0.75 + 0.25 * n1(u * 14)))); }
        }
        out.push(arr); continue;
      }
      if (terrain === 'sea') {
        const f = [0, 0, 0, 1.6, 0.9][layer]; const ns = [noise1(r, 64), noise1(r, 64), noise1(r, 64)];
        for (let i = 0; i < N; i++) { const u = i / (N - 1); arr[i] = 0.6 * ns[0](u * f * 2) + 0.3 * ns[1](u * f * 5.1) + 0.1 * ns[2](u * f * 13); }
        out.push(norm(arr, 0.1)); continue;
      }
      if (terrain === 'mountains' && layer <= 1) {
        const ns = [noise1(r, 64), noise1(r, 64), noise1(r, 64), noise1(r, 64), noise1(r, 64), noise1(r, 64)]; const broad = noise1(r, 64);
        const sharp = layer === 0 ? 1.6 : 1.25;
        for (let i = 0; i < N; i++) {
          const u = i / (N - 1); let s = 0, a = 0.55, fq = layer === 0 ? 2.6 : 3.4, w = 1;
          for (let o = 0; o < 6; o++) { const v = Math.pow(1 - Math.abs(ns[o](u * fq) * 2 - 1), sharp); s += a * v * w; w = Math.min(1, v * 2.0); a *= 0.5; fq *= 2.1; }
          arr[i] = s * (0.45 + 0.55 * broad(u * 1.3));
        }
        out.push(norm(arr, 0.0)); continue;
      }
      if (dunes) {
        const f = [3.4, 2.5, 1.8, 1.25, 0.85][layer];
        const ph = r(), n1 = noise1(r, 64), n2 = noise1(r, 64);
        for (let i = 0; i < N; i++) {
          const u = i / (N - 1);
          const x = u * f + ph + 0.18 * n1(u * 6);
          const p = x - Math.floor(x);
          const k = 0.70 + 0.08 * (n2(u * 3) - 0.5);
          let h = p < k ? Math.pow(p / k, 1.15) : 1 - Math.pow((p - k) / (1 - k), 0.85);
          h = h * h * (3 - 2 * h);
          const amp = 0.55 + 0.45 * n2(u * 2.2 + 3);
          arr[i] = h * amp + 0.08 * n1(u * 2 + 9);
        }
        out.push(norm(arr, 0.0));
      } else if (layer === 0) {
        // A range: a broad massif with several summits, sharp ridgelines, saddles between.
        const ns = [noise1(r, 64), noise1(r, 64), noise1(r, 64), noise1(r, 64), noise1(r, 64), noise1(r, 64)];
        const broad = noise1(r, 64);
        for (let i = 0; i < N; i++) {
          const u = i / (N - 1); let s = 0, a = 0.5, fq = 3.1, w = 1;
          for (let o = 0; o < 6; o++) { const v = 1 - Math.abs(ns[o](u * fq) * 2 - 1); s += a * v * v * w; w = Math.min(1, v * 1.8); a *= 0.48; fq *= 2.07; }
          arr[i] = s * (0.55 + 0.45 * broad(u * 1.6));
        }
        out.push(norm(arr, 0.0));
      } else {
        const f = [0, 1.7, 1.25, 0.95, 0.65][layer];
        const ns = [noise1(r, 64), noise1(r, 64), noise1(r, 64)];
        for (let i = 0; i < N; i++) {
          const u = i / (N - 1);
          arr[i] = 0.62 * ns[0](u * f * 2) + 0.28 * ns[1](u * f * 4.3) + 0.10 * ns[2](u * f * 9.7);
        }
        out.push(norm(arr, 0.05));
      }
    }
    return out;
  }

  root.Scene = { day, warp, ridges, solarEl };
})(typeof window !== 'undefined' ? window : globalThis);
