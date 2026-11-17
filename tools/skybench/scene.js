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
    if (terrain === 'forest' || terrain === 'arctic' || terrain === 'canyon') {
      for (let layer = 0; layer < 5; layer++) out.push(NEW[terrain](r, layer, N, norm));
      return out;
    }
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

  // The 0.19 sceneries: ports of LandProfile.forest, arctic and canyon (same shapes, the bench's own random numbers).
  const smoothJ = (e0, e1, x) => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
  function rangeJ(r, N, norm, fq0, sharp, broadFq, broadMin) {
    const ns = [0, 1, 2, 3, 4, 5].map(() => noise1(r, 64)); const broad = noise1(r, 64); const a = new Array(N);
    for (let i = 0; i < N; i++) {
      const u = i / (N - 1); let s = 0, amp = 0.55, fq = fq0, w = 1;
      for (let o = 0; o < 6; o++) { const v = Math.pow(1 - Math.abs(ns[o](u * fq) * 2 - 1), sharp); s += amp * v * w; w = Math.min(1, v * 2); amp *= 0.5; fq *= 2.1; }
      a[i] = s * (broadMin + (1 - broadMin) * broad(u * broadFq));
    }
    return norm(a, 0);
  }
  function massifsJ(r, N, norm, count, wMin, wMax, fq, low) {
    const detail = rangeJ(r, N, norm, fq, 1.2, 1, 1);
    const m = Array.from({ length: count }, (_, k) => [(k + 0.5 + (r() - 0.5) * 0.6) / count, wMin + (wMax - wMin) * r(), low + (1 - low) * r()]);
    return norm(Array.from({ length: N }, (_, i) => { const u = i / (N - 1); let env = 0;
      for (const k of m) { const t = (u - k[0]) / k[1]; env = Math.max(env, k[2] * Math.pow(Math.max(0, 1 - Math.abs(t)), 1.25)); }
      return env * (0.72 + 0.28 * detail[i]) + 0.05 * detail[i]; }), 0);
  }
  function rollingJ(r, N, norm, f, lo) {
    const ns = [noise1(r, 64), noise1(r, 64), noise1(r, 64)];
    return norm(Array.from({ length: N }, (_, i) => { const u = i / (N - 1); return 0.62 * ns[0](u * f * 2) + 0.28 * ns[1](u * f * 4.3) + 0.10 * ns[2](u * f * 9.7); }), lo);
  }
  const NEW = {
    forest(r, layer, N, norm) {
      if (layer === 0) return rangeJ(r, N, norm, 2.4, 1.45, 1.2, 0.4);
      if (layer === 1) return rollingJ(r, N, norm, 1.5, 0.12);
      if (layer === 2) return new Array(N).fill(0);
      if (layer === 3) {
        const sides = Math.floor(r() * 3), wl = 0.16 + 0.2 * r(), wr = 0.16 + 0.2 * r(), n1 = noise1(r, 64), n2 = noise1(r, 64);
        return Array.from({ length: N }, (_, i) => { const u = i / (N - 1);
          const l = sides !== 1 ? 1 - smoothJ(wl * 0.55, wl, u) : 0, rr = sides !== 0 ? smoothJ(1 - wr, 1 - wr * 0.55, u) : 0;
          const knoll = Math.max(l, rr) * (0.7 + 0.3 * n1(u * 9));
          return Math.min(1, Math.max(0, 0.04 + 0.05 * n2(u * 23) + 0.9 * knoll)); });
      }
      const n1 = noise1(r, 64), n2 = noise1(r, 64);
      return Array.from({ length: N }, (_, i) => { const u = i / (N - 1);
        const edge = Math.max(1 - smoothJ(0, 0.12, u), smoothJ(0.88, 1, u));
        return Math.min(1, Math.max(0, 0.12 + 0.25 * n1(u * 3.1) + 0.08 * n2(u * 17) + 0.55 * edge)); });
    },
    arctic(r, layer, N, norm) {
      if (layer === 0) return massifsJ(r, N, norm, 3, 0.17, 0.27, 3.4, 0.8);
      if (layer === 1) return massifsJ(r, N, norm, 2, 0.09, 0.17, 4.2, 0.55);
      if (layer === 2) return new Array(N).fill(0);
      if (layer === 3) {
        const n1 = noise1(r, 64), n2 = noise1(r, 64); const a = Array.from({ length: N }, (_, i) => 0.02 * n1(i / (N - 1) * 5));
        for (let k = 0; k < 12; k++) {
          const c = r(), hw = 0.008 + 0.025 * r(), hgt = 0.55 + 0.45 * r();
          for (let i = 0; i < N; i++) { const d = (i / (N - 1) - c) / hw; if (Math.abs(d) < 1) a[i] = Math.max(a[i], hgt * Math.pow(1 - Math.abs(d), 0.35) * (0.72 + 0.28 * n2(i / (N - 1) * 90))); }
        }
        return a;
      }
      return rollingJ(r, N, norm, 0.9, 0.1);
    },
    canyon(r, layer, N, norm) {
      const a = new Array(N).fill(0); const n1 = noise1(r, 64), n2 = noise1(r, 64);
      if (layer >= 3) {
        for (let i = 0; i < N; i++) { const u = i / (N - 1); a[i] = 0.7 * n1(u * (layer === 3 ? 2.6 : 1.6)) + 0.3 * n2(u * 7); }
        return norm(a, 0.1);
      }
      for (let i = 0; i < N; i++) a[i] = 0.03 + 0.04 * n1(i / (N - 1) * 11);
      const count = [6, 5, 3][layer], wMin = [0.06, 0.015, 0.03][layer], wMax = [0.2, 0.08, 0.09][layer], wall = [0.003, 0.006, 0.008][layer];
      for (let k = 0; k <= count; k++) {
        const spire = k === count;
        if (spire && r() < 0.4) continue;
        const c = r();
        const hw = spire ? 0.004 + 0.006 * r() : wMin + (wMax - wMin) * r();
        const top = spire ? 0.8 + 0.2 * r() : 0.5 + 0.5 * r();
        const scree = (spire ? 0.5 : 0.28 + 0.14 * r()) * top;
        const slope = (layer === 0 ? 0.02 : 0.035) + 0.04 * r();
        const tier = !spire && r() < 0.4;
        const tierW = hw * (0.3 + 0.4 * r()), tierC = c + (hw - tierW) * (r() * 2 - 1);
        for (let i = 0; i < N; i++) {
          const u = i / (N - 1), d = Math.abs(u - c) - hw;
          let v = d <= 0 && spire ? top * (1 - 0.35 * Math.pow(Math.abs((u - c) / hw), 1.5)) : d <= 0 ? top * (0.97 + 0.03 * n2(u * 40)) : d < wall ? top - (top - scree) * (d / wall) : d < wall + slope ? scree * Math.pow(1 - (d - wall) / slope, 1.7) : 0;
          if (tier) { const dt = Math.abs(u - tierC) - tierW; if (dt < wall) v = Math.max(v, top + 0.22 * (dt <= 0 ? 1 : 1 - dt / wall)); }
          a[i] = Math.max(a[i], v);
        }
      }
      return norm(a, 0);
    },
  };

  root.Scene = { day, warp, ridges, solarEl };
})(typeof window !== 'undefined' ? window : globalThis);
