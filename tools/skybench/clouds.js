// Port of app/src/main/java/com/noor/app/ui/today/CloudField.kt and Weather (SkyLook.kt) — the home sky's weather,
// its photographed clouds, shafts of rain and lightning (0.9.9) — and of CloudPainter's and RainLayer's drawing, for the
// design bench. Keep in step with the Kotlin.
(function (root) {
  const KINDS = 8, HUMILIS = 0, MEDIOCRIS = 1, CONGESTUS = 2, CUMULONIMBUS = 3, FRACTUS = 6;
  const ELEVATION_GAIN = 1.6, EARTH_R = 6360, EYE_H = 0.25, ALT_TOP = 80, ALT_POW = 1.25, NX = 48, NY = 32, LUT_POW = 2, SLOTS = 44;
  const CLEAR = 0, FAIR = 1, CONVECTIVE = 2, STORMY = 3, LAYERED = 4, OVERCAST = 5, RAINY = 6;

  function parse(text) {
    const sprites = [];
    for (const raw of text.split('\n')) {
      const line = raw.split('#')[0].trim(); if (!line) continue;
      const f = line.split(/\s+/); if (f[0] !== 'cloud') continue;
      const n = f.slice(1).map(Number); const levels = [];
      for (let i = 8; i < n.length; i += 4) levels.push(n.slice(i, i + 4));
      sprites.push({ kind: n[0], w: n[1], h: n[2], base: n[3], cut: n[4] === 1, side: n[5], group: n[7], levels });
    }
    const byKind = []; for (let k = 0; k < KINDS; k++) byKind.push(sprites.map((s, i) => s.kind === k ? i : -1).filter(i => i >= 0));
    return { sprites, byKind };
  }

  function hash(seed, i, k) {
    let x = (Math.imul(seed, 0x9E3779B9) + Math.imul(i, 0x85EBCA77) + Math.imul(k, 0x27d4eb2f)) | 0;
    x ^= x >>> 16; x = Math.imul(x, 0x7feb352d);
    x ^= x >>> 15; x = Math.imul(x, 0x846CA68B);
    x ^= x >>> 16;
    return (x >>> 8) / 16777216;
  }
  const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  const c01 = (x) => Math.max(0, Math.min(1, x));

  // Weather (SkyLook.kt).
  function weather(type, seedX, seedY, cirrusBase) {
    return { type, seedX, seedY, cirrusBase: type === CLEAR ? cirrusBase * 0.6 : cirrusBase, seed: (Math.floor(seedX * 1000) * 31 + Math.floor(seedY * 1000)) | 0 };
  }
  function weatherAt(w, fraction) {
    const h = fraction * 24;
    const noise = (period, k) => { const x = h / period, i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f); return hash(w.seed, i, k) * (1 - u) + hash(w.seed, i + 1, k) * u; };
    const n1 = noise(3, 101), n2 = noise(2.2, 102), n3 = noise(4, 103);
    const conv = smooth(9, 13.5, h) * (1 - smooth(18.5, 22, h));
    const late = smooth(13, 16.5, h) * (1 - smooth(20.5, 23.8, h));
    const day = smooth(5.5, 8.5, h) * (1 - smooth(19, 22, h));
    let cu = 0, size = 0.15, cluster = 0.2, layer = 0, overcast = 0, storm = 0, rain = 0, showers = 0;
    switch (w.type) {
      case CLEAR: cu = 0.04 + 0.14 * conv * n1; size = 0.1; cluster = 0.1; break;
      case FAIR: cu = 0.2 + 0.4 * conv + 0.18 * (n1 - 0.5); size = 0.1 + 0.3 * conv; cluster = 0.1 + 0.55 * n2; layer = 0.15 * smooth(0.6, 0.9, n3); break;
      case CONVECTIVE:
        cu = 0.3 + 0.45 * conv + 0.12 * (n1 - 0.5);
        size = 0.18 + 0.82 * Math.max(conv * 0.55, late) * (0.75 + 0.25 * n2);
        cluster = 0.35 + 0.45 * n2;
        storm = smooth(0.5, 0.95, size) * (0.55 + 0.45 * n3);
        showers = 0.1 + 0.75 * storm + 0.15 * conv * n1;
        rain = storm * smooth(0.6, 0.98, n1) * 0.7;
        overcast = 0.12 * storm; layer = 0.12 * n3; break;
      case STORMY:
        cu = 0.5 + 0.3 * conv; size = 0.62 + 0.38 * n2; cluster = 0.6;
        storm = 0.5 + 0.5 * n1; overcast = 0.2 + 0.4 * n3; layer = 0.25 * n2;
        rain = smooth(0.3, 0.9, n2) * 0.9; showers = 0.85; break;
      case LAYERED:
        layer = 0.5 + 0.4 * n1 * (1 - 0.5 * conv); cu = 0.12 + 0.35 * conv; size = 0.18 + 0.22 * conv;
        cluster = 0.5; overcast = 0.12 * smooth(0.5, 0.9, n3); showers = 0.12 * n2; break;
      case OVERCAST:
        overcast = 0.6 + 0.4 * n1; layer = 0.3 + 0.35 * n2; cu = 0.12 + 0.1 * conv; size = 0.3;
        rain = 0.35 * smooth(0.6, 0.9, n3); showers = 0.25; break;
      case RAINY:
        overcast = 0.78 + 0.22 * n1; layer = 0.4; cu = 0.22; size = 0.45; cluster = 0.5;
        rain = smooth(0.2, 0.8, n2); showers = 0.65; storm = 0.3 * smooth(0.7, 0.95, n3); break;
      default:
        cu = 0.4 + 0.3 * conv + 0.15 * (n1 - 0.5); layer = 0.3 + 0.35 * n2; cluster = 0.55 + 0.35 * n3;
        size = 0.28 + 0.35 * conv; showers = 0.1 + 0.35 * conv * n2; overcast = 0.08;
    }
    cu *= 0.35 + 0.65 * Math.max(day, storm);
    const ci = c01(w.cirrusBase * (0.55 + 0.9 * n3) * (1 - overcast));
    return { cumulus: c01(cu), size: c01(size), cluster: c01(cluster), layer: c01(layer), overcast: c01(overcast), storm: c01(storm), rain: c01(rain), showers: c01(showers), cirrus: ci, hour: h };
  }

  const SPEC = [
    [1.6, 3.0, 1.0, 1.4, 6, 55], [3.0, 5.5, 1.0, 1.4, 7, 55], [0, 0, 4.5, 7.5, 22, 75], [0, 0, 10, 13, 55, 110],
    [0, 0, 0, 0, 1, 1], [0, 0, 0, 0, 1, 1], [0.9, 1.8, 0.8, 1.2, 6, 40], [0, 0, 0, 0, 1, 1],
  ];
  const kindOf = (i, h) => i < 3 ? CUMULONIMBUS : h < 0.08 ? FRACTUS : h < 0.47 ? HUMILIS : h < 0.82 ? MEDIOCRIS : CONGESTUS;
  function presence(i, kind, s, h1) {
    if (kind === CUMULONIMBUS) return smooth(0.3 + 0.22 * i, 0.42 + 0.22 * i, s.storm);
    const enough = smooth(h1 * 0.97 - 0.03, h1 * 0.97 + 0.05, s.cumulus);
    const fits = kind === HUMILIS ? 1 - smooth(0.55, 0.85, s.size) : kind === MEDIOCRIS ? smooth(0.04, 0.22, s.size) : kind === CONGESTUS ? smooth(0.38, 0.62, s.size) : 1;
    return enough * fits;
  }
  const SHADE_FILL = 0.35, MOONLIT = [0.42, 0.46, 0.56], NIGHT_FLOOR = [0.006, 0.008, 0.014], FLASH = [0.85, 0.82, 1.0];
  const hg = (mu, g) => (1 - g * g) / (4 * Math.PI * Math.pow(Math.max(1 + g * g - 2 * g * mu, 1e-4), 1.5));
  const yOfAltitude = (f, alt) => alt >= 0 ? f.horizonY - f.horizonY * Math.pow(Math.min(alt / ALT_TOP, 1), 1 / ALT_POW) : f.horizonY - alt * 6 * f.dp;
  const altitudeAt = (f, y) => ALT_TOP * Math.pow(Math.max(0, Math.min(1, (f.horizonY - y) / f.horizonY)), ALT_POW);
  const rowOf = (alt) => Math.pow(Math.min(Math.max(alt, 0) / 90, 1), 1 / LUT_POW);
  function sample(lut, u, v) {   // lut: RGBA bytes, NX × NY, row 0 at the horizon; display values 0..1
    const x = Math.max(0, Math.min(1, u)) * (NX - 1), y = Math.max(0, Math.min(1, v)) * (NY - 1);
    const i = Math.min(Math.floor(x), NX - 2), j = Math.min(Math.floor(y), NY - 2), a = x - i, b = y - j;
    const out = [0, 0, 0];
    for (let c = 0; c < 3; c++) {
      const ch = (ii, jj) => lut[(jj * NX + ii) * 4 + c];
      const v0 = ch(i, j) * (1 - a) + ch(i + 1, j) * a, v1 = ch(i, j + 1) * (1 - a) + ch(i + 1, j + 1) * a;
      out[c] = Math.max(0, Math.min(255, Math.floor(v0 * (1 - b) + v1 * b + 0.5))) / 255;
    }
    return out;
  }
  function place(seed, i, kind, fov) {
    const sp = SPEC[kind], h2 = hash(seed, i, 2), h4 = hash(seed, i, 4), t = hash(seed, i, 9);
    const big = kind === CUMULONIMBUS || kind === CONGESTUS;
    const d = big ? sp[4] + (sp[5] - sp[4]) * t : sp[4] * Math.pow(sp[5] / sp[4], t) * (0.92 + 0.16 * h2);
    const win = fov + 0.6;
    const az = kind === CUMULONIMBUS ? (h4 - 0.5) * (fov - 0.6) : (((i * 0.618034 + h4 * 0.5) % 1) - 0.5) * win;
    return [d, az];
  }
  function lightning(f) {
    const storm = f.state.storm; if (storm < 0.45) return 0;
    const period = 9 - 5 * storm, k = Math.floor(f.time / period), seed = f.weather.seed;
    if (hash(seed, k, 32) > storm * 1.1 - 0.35) return 0;
    const dt = f.time - k * period - hash(seed, k, 31) * (period - 1.2);
    if (dt < 0 || dt > 0.9) return 0;
    const strokes = 1 + Math.floor(hash(seed, k, 35) * 3); let v = 0;
    for (let q = 0; q < strokes; q++) { const t0 = q * (0.12 + 0.1 * hash(seed, k, 36 + q)), x = dt - t0; if (x >= 0) v = Math.max(v, Math.exp(-x / 0.09)); }
    return v;
  }
  function strike(f, c, flash) {
    const period = 9 - 5 * f.state.storm, k = Math.floor(f.time / period), seed = f.weather.seed;
    const w = c.right - c.left;
    const bolt = { intensity: flash, cx: (c.left + c.right) / 2, cy: c.top + (c.bottom - c.top) * 0.5, radius: w * 0.6, inCloud: hash(seed, k, 34) > 0.65, path: [], branch: [] };
    if (bolt.inCloud) return bolt;
    const x0 = c.left + w * (0.3 + 0.4 * hash(seed, k, 37)), y0 = c.top + (c.bottom - c.top) * (c.bottom > f.horizonY ? 0.42 : 0.62);
    const x1 = x0 + (hash(seed, k, 38) - 0.5) * w * 0.3, y1 = f.horizonY + 2 * f.dp, steps = 22;
    let x = x0;
    for (let q = 0; q <= steps; q++) {
      const y = y0 + (y1 - y0) * q / steps;
      if (q > 0) x += (hash(seed, k * 64 + q, 39) - 0.5) * (y1 - y0) / steps * 1.6 + (x1 - x0) / steps;
      bolt.path.push(x, y);
    }
    const b0 = Math.floor(steps * (0.25 + 0.2 * hash(seed, k, 40)));
    let bx = bolt.path[b0 * 2], by = bolt.path[b0 * 2 + 1]; const dir = hash(seed, k, 41) > 0.5 ? 1 : -1;
    bolt.branch.push(bx, by);
    for (let q = 1; q <= 7; q++) { by += (y1 - y0) / steps * 0.8; bx += dir * (y1 - y0) / steps * (0.5 + hash(seed, k * 64 + q, 42)); bolt.branch.push(bx, by); }
    return bolt;
  }

  // f: { w, h, horizonY, dp, fov, time, weather, state, sunAz, sunEl, light: {sunCloud, ambTop, ambLow}, lut, night, moonLight }
  function layout(table, f, tone) {
    const out = []; let bolt = null;
    if (!table.sprites.length) return { draws: out, bolt };
    const st = f.state, seed = f.weather.seed, pxPerRad = f.w / f.fov, win = f.fov + 0.6, D2R = Math.PI / 180;
    const se = f.sunEl * D2R;
    const sunL = [Math.cos(se) * Math.sin(f.sunAz), Math.sin(se), Math.cos(se) * Math.cos(f.sunAz)];
    const sunX = f.w * (0.5 + f.sunAz / f.fov);
    const L = f.light, sunK = 1 - 0.85 * st.overcast;
    const amb = [0, 1, 2].map(j => (L.ambTop[j] * 0.85 + L.ambLow[j] * 0.4) * 1.3 * (1 + 0.15 * st.overcast));
    const dAmb = tone(amb.slice(), [0, 0, 0]);
    const night = f.night, moonB = f.moonLight;
    const flash = lightning(f); let flashTarget = -1, flashBest = -1;
    for (let i = 0; i < SLOTS; i++) {
      const h = (k) => hash(seed, i, k);
      const h1 = h(1), h2 = h(2), h3 = h(3), h5 = h(5), h6 = h(6), h7 = h(7);
      const kind0 = kindOf(i, h3), vis = presence(i, kind0, st, h1);
      if (vis < 0.01) continue;
      let kind = kind0; if (!table.byKind[kind].length) kind = HUMILIS;
      const kinds = table.byKind[kind]; if (!kinds.length) continue;
      const sp = SPEC[kind], stretch = 0.86 + 0.28 * h(8);
      const si = kinds[Math.min(Math.floor(h6 * kinds.length), kinds.length - 1)], s = table.sprites[si];
      let [d, az0] = place(seed, i, kind, f.fov);
      if (i >= 3 && h(10) < st.cluster && (i - 3) % 4 !== 0) {
        const p = 3 + Math.floor((i - 3) / 4) * 4;
        let pk = kindOf(p, hash(seed, p, 3)); if (pk === CONGESTUS || pk === CUMULONIMBUS) pk = MEDIOCRIS;
        const pp = place(seed, p, pk, f.fov);
        d = pp[0] * (0.9 + 0.2 * h(11));
        const spread = SPEC[MEDIOCRIS][1] / pp[0];
        az0 = pp[1] + (h(12) - 0.5) * 3.2 * spread;
        if (kind === CONGESTUS) d = Math.max(d, sp[4]);
      }
      const heightKm = sp[2] + (sp[3] - sp[2]) * h5;
      az0 += kind === CUMULONIMBUS ? 0 : (f.time * 0.007 + (st.hour ?? 12) * 4) / d;
      const az = az0 - win * Math.floor(az0 / win + 0.5);
      const cx = f.w * (0.5 + az / f.fov), drop = d * d / (2 * EARTH_R);
      let wPx, k, yb;
      if (s.cut) {
        yb = f.horizonY + 4 * f.dp;
        const yTop = yOfAltitude(f, Math.atan((heightKm - EYE_H - drop) / d) / D2R * ELEVATION_GAIN);
        k = (yb - yTop) / s.h; wPx = s.w * k * stretch;
      } else {
        const wKm = (sp[0] + (sp[1] - sp[0]) * h2) * Math.pow(s.group, 0.8);
        wPx = wKm / d * pxPerRad; k = wPx / s.w / stretch;
        yb = yOfAltitude(f, Math.atan((heightKm - EYE_H - drop) / d) / D2R * ELEVATION_GAIN) + s.base * k;
      }
      if (wPx < 2 * f.dp) continue;
      if (cx + wPx / 2 < 0 || cx - wPx / 2 > f.w) continue;
      const top = yb - s.h * k; if (top > f.h) continue;
      const altM = altitudeAt(f, (yb + top) / 2) / ELEVATION_GAIN * D2R;
      const fx = Math.cos(altM) * Math.sin(az), fy = Math.sin(altM), fz = Math.cos(altM) * Math.cos(az);
      const fwd = Math.min(hg(fx * sunL[0] + fy * sunL[1] + fz * sunL[2], 0.6) * 4 * Math.PI, 5);
      const dLit = tone([0, 1, 2].map(j => L.sunCloud[j] * 2.6 * (0.85 + 0.15 * fwd) * sunK + amb[j]), [0, 0, 0]);
      const sky = sample(f.lut, cx / f.w, rowOf(altitudeAt(f, (yb + top) / 2)));
      const haze = (s.cut ? 0.03 + 0.32 * (1 - Math.exp(-d / 80)) : 0.03 + 0.55 * (1 - Math.exp(-d / 45))) + 0.25 * st.rain;
      const alpha = (1 - 0.15 * night) * smooth(2 * f.dp, 6 * f.dp, wPx) * vis;
      const showery = kind === CUMULONIMBUS ? 1 : kind === CONGESTUS ? 0.75 : kind === MEDIOCRIS ? 0.35 : 0;
      if (h(21) < st.showers * showery) {
        const virga = kind !== CUMULONIMBUS && h(22) < 0.45;
        const sw = wPx * (0.35 + 0.3 * h(23)), sx = cx + (h(24) - 0.5) * wPx * 0.35;
        const shTop = s.cut ? top + (yb - top) * 0.55 : yb - (yb - top) * 0.12, shBottom = f.horizonY + 3 * f.dp;
        let a = 0.42 * (1 - 0.5 * haze) * alpha; if (kind === CUMULONIMBUS) a *= 1.3;
        const col = [0, 1, 2].map(j => ((dAmb[j] * 0.55 + dLit[j] * 0.2) * (1 - haze) + sky[j] * haze) * (1 - night) + sky[j] * 0.6 * night);
        out.push({ shaft: true, distance: d + 0.01, left: sx - sw / 2, right: sx + sw / 2, top: shTop, bottom: shBottom,
          slant: (h(25) - 0.5) * (shBottom - shTop) * 0.45, fade: virga ? 0.3 + 0.4 * h(26) : 1, color: col, alpha: Math.min(1, a) });
      }
      const c = { shaft: false, sprite: si, distance: d, left: cx - wPx / 2, right: cx + wPx / 2, top, bottom: yb, kind };
      c.flip = (s.side !== 0 && f.sunEl > -2 && f.sunEl < 60) ? ((s.side > 0) !== (sunX > cx)) : h7 > 0.5;
      let lv = s.levels[0]; for (const q of s.levels) if (q[2] >= wPx * 0.8) lv = q;
      c.src = lv;
      c.slope = [0, 1, 2].map(j => (dLit[j] - SHADE_FILL * dAmb[j]) * (1 - haze) * (1 - night) + MOONLIT[j] * moonB * night);
      c.bias = [0, 1, 2].map(j => (SHADE_FILL * dAmb[j] * (1 - haze) + haze * sky[j]) * (1 - night) + (sky[j] * 0.45 + NIGHT_FLOOR[j] + MOONLIT[j] * 0.25 * moonB) * night);
      c.alpha = alpha;
      out.push(c);
      if (flash > 0 && (kind === CUMULONIMBUS || kind === CONGESTUS) && vis > 0.5) {
        const score = (c.right - c.left) * (kind === CUMULONIMBUS ? 2 : 1) * (0.5 + h(30));
        if (score > flashBest) { flashBest = score; flashTarget = out.length - 1; }
      }
    }
    if (flashTarget >= 0) {
      const c = out[flashTarget];
      for (let j = 0; j < 3; j++) { c.slope[j] += FLASH[j] * 0.7 * flash; c.bias[j] += FLASH[j] * 0.35 * flash; }
      bolt = strike(f, c, flash);
    }
    out.sort((a, b) => b.distance - a.distance);
    return { draws: out, bolt };
  }

  const VS = `#version 300 es
in vec2 p; in vec2 uv; out vec2 vuv; out float vy; uniform vec2 uSize;
void main() { vuv = uv; vy = p.y; gl_Position = vec4(p.x / uSize.x * 2.0 - 1.0, 1.0 - p.y / uSize.y * 2.0, 0.0, 1.0); }`;
  const FS = `#version 300 es
precision highp float;
in vec2 vuv; in float vy; out vec4 o;
uniform sampler2D tA; uniform vec3 uSlope; uniform vec3 uBias; uniform float uAlpha; uniform vec2 uFade;
uniform int uMode; uniform float uShaftFade; uniform float uShaftW;
float h1(float x) { return fract(sin(x * 127.1) * 43758.5453); }
void main() {
  if (uMode == 1) {
    // A shaft of rain: vuv = (across 0..1, down 0..1).
    float g = uShaftFade >= 1.0 ? mix(0.35, 1.0, smoothstep(0.0, 0.15, vuv.y)) * mix(1.0, 0.85, vuv.y)
                                : mix(0.35, 1.0, smoothstep(0.0, 0.18 * uShaftFade, vuv.y)) * (1.0 - smoothstep(0.18 * uShaftFade, uShaftFade, vuv.y));
    float col = floor(vuv.x * 48.0);
    float side = pow(sin(3.14159 * (col + 0.5) / 48.0), 2.0);
    float seg = floor(vuv.y * uShaftW / 30.0 + h1(col) * 7.0);
    float streak = (0.45 + 0.55 * h1(col + 3.1)) * step(0.25, h1(col * 1.7 + seg * 3.3)) * side;
    float al = uAlpha * g * streak;
    o = vec4(uBias * al, al); return;
  }
  vec4 t = texture(tA, vuv);
  float a = t.a; float g = a > 0.0 ? t.r / a : 0.0;
  vec3 col = clamp(uSlope * g + uBias, 0.0, 1.0);
  float m = clamp((vy - uFade.x) / max(uFade.y - uFade.x, 1e-3), 0.0, 1.0);
  float al = clamp(a * uAlpha, 0.0, 1.0) * m;
  o = vec4(col * al, al);
}`;

  // Draws the clouds and shafts onto a new transparent W × H canvas (premultiplied), as CloudPainter does over the sky,
  // then the lightning.
  function draw(W, H, laid, img, fadeTop, fadeBottom, dp, night) {
    const clouds = laid.draws;
    const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    const gl = cv.getContext('webgl2', { premultipliedAlpha: true, preserveDrawingBuffer: true, alpha: true });
    const mk = (type, s) => { const sh = gl.createShader(type); gl.shaderSource(sh, s); gl.compileShader(sh); if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(sh)); return sh; };
    const pr = gl.createProgram(); gl.attachShader(pr, mk(gl.VERTEX_SHADER, VS)); gl.attachShader(pr, mk(gl.FRAGMENT_SHADER, FS)); gl.linkProgram(pr); gl.useProgram(pr);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true); gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL, gl.NONE);
    const tx = gl.createTexture(); gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tx);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.uniform1i(gl.getUniformLocation(pr, 'tA'), 0);
    gl.uniform2f(gl.getUniformLocation(pr, 'uSize'), W, H);
    const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    const lp = gl.getAttribLocation(pr, 'p'), lu = gl.getAttribLocation(pr, 'uv');
    gl.enableVertexAttribArray(lp); gl.enableVertexAttribArray(lu);
    gl.vertexAttribPointer(lp, 2, gl.FLOAT, false, 16, 0); gl.vertexAttribPointer(lu, 2, gl.FLOAT, false, 16, 8);
    gl.viewport(0, 0, W, H); gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    const U = (n) => gl.getUniformLocation(pr, n);
    const IW = img.width, IH = img.height;
    for (const c of clouds) {
      if (c.shaft) {
        gl.uniform1i(U('uMode'), 1);
        gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([c.left, c.top, 0, 0, c.right, c.top, 1, 0, c.left + c.slant, c.bottom, 0, 1, c.right + c.slant, c.bottom, 1, 1]), gl.STREAM_DRAW);
        gl.uniform1f(U('uAlpha'), c.alpha); gl.uniform3fv(U('uBias'), c.color); gl.uniform1f(U('uShaftFade'), c.fade);
        gl.uniform1f(U('uShaftW'), (c.bottom - c.top) / Math.max((c.right - c.left) * 2.2, 1) * 256);
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        continue;
      }
      gl.uniform1i(U('uMode'), 0);
      const [sx, sy, sw, sh] = c.src;
      let u0 = sx / IW, u1 = (sx + sw) / IW; const v0 = sy / IH, v1 = (sy + sh) / IH;
      if (c.flip) [u0, u1] = [u1, u0];
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([c.left, c.top, u0, v0, c.right, c.top, u1, v0, c.left, c.bottom, u0, v1, c.right, c.bottom, u1, v1]), gl.STREAM_DRAW);
      const fadeOn = c.top < fadeBottom;
      gl.uniform2f(U('uFade'), fadeOn ? fadeTop : -1e6, fadeOn ? fadeBottom : -1e6 + 1);
      gl.uniform1f(U('uAlpha'), c.alpha); gl.uniform3fv(U('uSlope'), c.slope); gl.uniform3fv(U('uBias'), c.bias);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
    gl.finish();
    const out = document.createElement('canvas'); out.width = W; out.height = H;
    const g = out.getContext('2d'); g.drawImage(cv, 0, 0);
    const b = laid.bolt;
    if (b && b.intensity > 0.01) {
      const s = (0.05 + 0.16 * night) * b.intensity;
      g.globalCompositeOperation = 'lighter';
      const gr = g.createRadialGradient(b.cx, b.cy, 0, b.cx, b.cy, b.radius);
      gr.addColorStop(0, `rgba(217,209,255,${s})`); gr.addColorStop(0.45, `rgba(191,191,255,${s * 0.35})`); gr.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = gr; g.beginPath(); g.arc(b.cx, b.cy, b.radius, 0, 7); g.fill();
      g.globalCompositeOperation = 'source-over';
      if (!b.inCloud) for (const [pts, wd] of [[b.path, 1.3], [b.branch, 0.8]]) {
        if (pts.length < 4) continue;
        g.lineCap = 'round'; g.lineJoin = 'round';
        for (const [al, col, ww] of [[0.35, '179,173,255', 4.5], [0.95, '247,245,255', 1]]) {
          g.strokeStyle = `rgba(${col},${al * b.intensity})`; g.lineWidth = wd * ww * dp;
          g.beginPath(); g.moveTo(pts[0], pts[1]); for (let q = 2; q < pts.length; q += 2) g.lineTo(pts[q], pts[q + 1]); g.stroke();
        }
      }
    }
    return out;
  }

  // RainLayer: the veil and the streaks, in front of the land.
  function rain(g, W, H, r, t, dp, night) {
    if (r < 0.01) return;
    const mix = (a, b, k) => a.map((x, i) => Math.round(x + (b[i] - x) * k));
    const veil = mix([184, 192, 200], [28, 34, 43], night), drop = mix([230, 235, 240], [94, 104, 116], night);
    g.fillStyle = `rgba(${veil.join(',')},${0.10 * r})`; g.fillRect(0, 0, W, H);
    const n = Math.floor(260 * r);
    for (let k = 0; k < n; k++) {
      const h1 = hash(77, k, 1), h2 = hash(77, k, 2), h3 = hash(77, k, 3);
      const len = (10 + 18 * h3) * dp, speed = (0.8 + 0.5 * h2) * H;
      const y = ((h1 * 7.31 + t * speed / (H + len)) % 1) * (H + len) - len;
      const x = hash(77, k, 4) * (W + 0.2 * H) - 0.1 * H + 0.18 * (y + len);
      g.strokeStyle = `rgba(${drop.join(',')},${(0.07 + 0.13 * hash(77, k, 5)) * r})`; g.lineWidth = (0.6 + 0.7 * hash(77, k, 6)) * dp;
      g.beginPath(); g.moveTo(x, y); g.lineTo(x + 0.18 * len, y + len); g.stroke();
    }
  }

  root.Clouds = { parse, layout, draw, rain, hash, weather, weatherAt };
})(typeof window !== 'undefined' ? window : globalThis);
