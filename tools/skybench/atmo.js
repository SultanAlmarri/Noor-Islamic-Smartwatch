// Prototype of the sky model (to be ported to Kotlin). Units: km, radians.
// Single scattering (Rayleigh + Mie + ozone absorption) over a spherical Earth with its shadow, a precomputed
// transmittance table, auto exposure and a filmic tone curve. Output: a small LUT of display colours over
// (screen column, view altitude) that the shaders sample.
(function (root) {
  const R = 6360, RA = 6460;
  const HR = 8.0, HM = 1.2;
  const BR = [5.802e-3, 13.558e-3, 33.1e-3];
  const BMS = 3.996e-3, BME = 4.40e-3;
  const BO = [0.650e-3 * 2.2, 1.881e-3 * 2.2, 0.085e-3 * 2.2];
  let SUN = [1.0, 1.0, 1.0];
  const H0 = 0.25; // observer height
  const G = 0.76;
  const MS = 0.06;
  const SAT = 1.25;

  function dens(h, out) {
    out[0] = Math.exp(-h / HR);
    out[1] = Math.exp(-h / HM);
    out[2] = Math.max(0, 1 - Math.abs(h - 25) / 15);
  }

  // Transmittance table T(h, mu) to the top of the atmosphere (0 where the ray meets the ground).
  const TH = 64, TM = 256;
  const table = new Float32Array(TH * TM * 3);
  function hOf(i) { const x = i / (TH - 1); return x * x * (RA - R); }
  function iOfH(h) { return Math.sqrt(Math.max(0, h) / (RA - R)) * (TH - 1); }
  function muOf(j) { const x = j / (TM - 1) * 2 - 1; return Math.sign(x) * x * x; }
  function jOfMu(mu) { const x = Math.sign(mu) * Math.sqrt(Math.abs(mu)); return (x + 1) / 2 * (TM - 1); }
  (function build() {
    const d = [0, 0, 0];
    for (let i = 0; i < TH; i++) {
      const h = hOf(i), r = R + h;
      for (let j = 0; j < TM; j++) {
        const mu = muOf(j);
        // Ground hit?
        const b = r * mu, cG = r * r - R * R;
        const discG = b * b - cG;
        let k = (i * TM + j) * 3;
        if (mu < 0 && discG > 0 && (-b - Math.sqrt(discG)) > 0) { table[k] = table[k + 1] = table[k + 2] = 0; continue; }
        const cA = r * r - RA * RA;
        const L = -b + Math.sqrt(Math.max(0, b * b - cA));
        const N = 60;
        let tr = 0, tm = 0, to = 0;
        for (let s = 0; s < N; s++) {
          const t = (s + 0.5) / N * L;
          const hh = Math.sqrt(r * r + t * t + 2 * r * mu * t) - R;
          dens(hh, d);
          tr += d[0]; tm += d[1]; to += d[2];
        }
        const dt = L / N;
        for (let c = 0; c < 3; c++) table[k + c] = Math.exp(-(BR[c] * tr + BME * tm + BO[c] * to) * dt);
      }
    }
  })();

  // White balance: sunlight at 50 degrees elevation is shown as white, as the eye (or a camera set to daylight) sees it.
  function balance() {
    const T = trans(H0, Math.sin(50 * Math.PI / 180), [0, 0, 0]);
    const l = 0.2126 * T[0] + 0.7152 * T[1] + 0.0722 * T[2];
    SUN = [l / T[0], l / T[1], l / T[2]];
  }
  function trans(h, mu, out) {
    const fi = Math.min(TH - 1.001, iOfH(h)), fj = Math.min(TM - 1.001, Math.max(0, jOfMu(mu)));
    const i = Math.floor(fi), j = Math.floor(fj), a = fi - i, b = fj - j;
    for (let c = 0; c < 3; c++) {
      const v00 = table[(i * TM + j) * 3 + c], v01 = table[(i * TM + j + 1) * 3 + c];
      const v10 = table[((i + 1) * TM + j) * 3 + c], v11 = table[((i + 1) * TM + j + 1) * 3 + c];
      out[c] = (v00 * (1 - b) + v01 * b) * (1 - a) + (v10 * (1 - b) + v11 * b) * a;
    }
    return out;
  }

  balance();
  function phaseR(mu) { return 3 / (16 * Math.PI) * (1 + mu * mu); }
  function phaseM(mu) { const g2 = G * G; return 3 / (8 * Math.PI) * (1 - g2) * (1 + mu * mu) / ((2 + g2) * Math.pow(1 + g2 - 2 * G * mu, 1.5)); }

  function dir(az, alt) { const c = Math.cos(alt); return [c * Math.sin(az), Math.sin(alt), c * Math.cos(az)]; }

  // Radiance arriving at the observer from direction v, sun (light) direction sl for transmittance, sp for phase.
  function radiance(v, sl, sp, out) {
    const ox = 0, oy = R + H0, oz = 0;
    const b = oy * v[1];
    const c = oy * oy - RA * RA;
    const L = -b + Math.sqrt(b * b - c);
    const mu = v[0] * sp[0] + v[1] * sp[1] + v[2] * sp[2];
    const pr = phaseR(mu), pm = phaseM(mu);
    const N = 12;
    let odR = 0, odM = 0, odO = 0;
    let lr0 = 0, lr1 = 0, lr2 = 0;
    const d = [0, 0, 0], T = [0, 0, 0], Tms = [0, 0, 0];
    let prevT = 0;
    for (let s = 0; s < N; s++) {
      const u0 = s / N, u1 = (s + 1) / N;
      const t0 = u0 * u0 * L, t1 = u1 * u1 * L;
      const t = 0.5 * (t0 + t1), dt = t1 - t0;
      const px = ox + v[0] * t, py = oy + v[1] * t, pz = oz + v[2] * t;
      const r = Math.sqrt(px * px + py * py + pz * pz);
      const h = r - R;
      dens(h, d);
      odR += d[0] * dt; odM += d[1] * dt; odO += d[2] * dt;
      const muS = (px * sl[0] + py * sl[1] + pz * sl[2]) / r;
      trans(h, muS, T);
      trans(h, muS + 0.10, Tms);
      for (let k = 0; k < 3; k++) {
        const tv = Math.exp(-(BR[k] * odR + BME * odM + BO[k] * odO));
        const sc = ((BR[k] * d[0] * pr + BMS * d[1] * pm) * T[k] + (BR[k] * d[0] + BMS * d[1]) * MS * Tms[k]) * tv * dt;
        if (k === 0) lr0 += sc; else if (k === 1) lr1 += sc; else lr2 += sc;
      }
    }
    out[0] = lr0 * SUN[0] * 20; out[1] = lr1 * SUN[1] * 20; out[2] = lr2 * SUN[2] * 20;
    return out;
  }

  function lum(c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

  // Filmic curve (ACES fit, Narkowicz) on linear values, then the sRGB transfer.
  function aces(x) { return Math.max(0, Math.min(1, (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14))); }
  function srgb(x) { return x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055; }

  function smooth(a, b, x) { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

  const DEG = Math.PI / 180;

  // Tone curve: filmic on luminance (keeps the hue of a sunset), with a little per-channel roll-off so the
  // brightest light still turns towards white.
  function textLimit() {}
  function tone(c, out) {
    const L = lum(c);
    const t = aces(L);
    const s = L > 1e-6 ? t / L : 0;
    for (let k = 0; k < 3; k++) {
      const hp = Math.min(1, c[k] * s);
      const pc = aces(c[k]);
      out[k] = hp * 0.7 + pc * 0.3;
    }
    // A little more colour, as a photographer would grade it.
    const y = 0.2126 * out[0] + 0.7152 * out[1] + 0.0722 * out[2];
    for (let k = 0; k < 3; k++) out[k] = srgb(Math.max(0, Math.min(1, y + (out[k] - y) * SAT)));
    return out;
  }

  // How bright the zenith is shown, by the Sun's elevation: full daylight, a gentle fall through twilight.
  function targetY(el) {
    const pts = [[-16, 0.0006], [-12, 0.0035], [-9, 0.011], [-6, 0.026], [-3, 0.042], [0, 0.056], [4, 0.068], [10, 0.080], [90, 0.092]];
    if (el <= pts[0][0]) return pts[0][1];
    for (let i = 1; i < pts.length; i++) if (el <= pts[i][0]) {
      const [x0, y0] = pts[i - 1], [x1, y1] = pts[i];
      const t = (el - x0) / (x1 - x0); const u = t * t * (3 - 2 * t);
      return Math.exp(Math.log(y0) + (Math.log(y1) - Math.log(y0)) * u);
    }
    return pts[pts.length - 1][1];
  }

  // Everything the frame needs from the sky model.
  // p: {sunEl (deg, true), sunAz (rad, from screen x), sunAltDraw (rad), fov, apow, nx, ny}
  function frame(p) {
    const sl = dir(p.sunAz, p.sunEl * DEG);
    const sp = dir(p.sunAz, p.sunAltDraw);
    const tmp = [0, 0, 0], tm = [0, 0, 0];
    const z = radiance(dir(0, 89 * DEG), sl, sp, [0, 0, 0]);
    const hz = radiance(dir(p.sunAz + Math.PI * 0.5, 4 * DEG), sl, sp, [0, 0, 0]);
    const exposure = Math.min(600, targetY(p.sunEl) / Math.max(lum(z), 1e-9));
    const night = 1 - smooth(-14, -1, p.sunEl);
    const lut = new Uint8ClampedArray(p.nx * p.ny * 4);
    const nightTop = [0.010, 0.017, 0.045], nightLow = [0.028, 0.044, 0.085];
    const lim = (altD) => altD >= p.capAlt ? 0.30 + (0.18 - 0.30) * smooth(p.capAlt, p.capAlt + 12, altD) : 0.30 + 0.70 * (1 - smooth(p.capAlt - 15, p.capAlt, altD));
    const toLin = (x) => x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    const lin = new Float64Array(p.nx * p.ny * 3);
    for (let j = 0; j < p.ny; j++) {
      const v = j / (p.ny - 1);
      const alt = (Math.pow(v, p.apow) * 90) * DEG;
      const w = Math.pow(1 - v, 2.5);
      for (let i = 0; i < p.nx; i++) {
        const u = i / (p.nx - 1);
        const az = (u - 0.5) * p.fov;
        radiance(dir(az, Math.max(alt, 0.2 * DEG)), sl, sp, tmp);
        for (let c = 0; c < 3; c++) tmp[c] = tmp[c] * exposure + night * (nightTop[c] + (nightLow[c] - nightTop[c]) * w);
        tone(tmp, tm);
        for (let c = 0; c < 3; c++) lin[(j * p.nx + i) * 3 + c] = toLin(tm[c]);
      }
    }
    const capAlt = p.capAlt ?? 90;
    const ratios = [];
    for (let j = 0; j < p.ny; j++) { const altD = Math.pow(j / (p.ny - 1), p.apow) * 90; if (altD < capAlt) continue;
      for (let i = 0; i < p.nx; i++) { const o = (j * p.nx + i) * 3; ratios.push(lum([lin[o], lin[o + 1], lin[o + 2]]) / lim(altD)); } }
    ratios.sort((a, b) => a - b);
    const typical = ratios.length ? ratios[Math.min(ratios.length - 1, Math.floor(ratios.length * 0.7))] : 0;
    const darken = typical > 1 ? 1 / typical : 1;
    for (let j = 0; j < p.ny; j++) {
      const altD = Math.pow(j / (p.ny - 1), p.apow) * 90;
      const kk = 1 - (1 - darken) * smooth(2, capAlt + 4, altD);
      for (let i = 0; i < p.nx; i++) {
        const o = (j * p.nx + i) * 3; const k = j * p.nx + i;
        const l3 = [lin[o] * kk, lin[o + 1] * kk, lin[o + 2] * kk];
        const L = lum(l3), m = lim(altD);
        const f = L > m ? m / L : 1;
        for (let c = 0; c < 3; c++) lut[k * 4 + c] = Math.round(255 * srgb(l3[c] * f));
        lut[k * 4 + 3] = 255;
      }
    }
    const sunAt = (h) => { const T = trans(h, Math.sin(p.sunEl * DEG), [0, 0, 0]); return T.map((t, c) => t * SUN[c] * 20 * exposure / Math.PI); };
    return {
      exposure, night, lut, z,
      sunGround: sunAt(0.3), sunCloud: sunAt(2.0), sunHigh: sunAt(8.0),
      ambTop: z.map(x => x * exposure), ambHor: hz.map(x => x * exposure),
    };
  }

  root.Atmo = { frame, radiance, dir, trans, aces, srgb, DEG };
})(typeof window !== 'undefined' ? window : globalThis);
