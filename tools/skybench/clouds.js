// Port of app/src/main/java/com/noor/app/ui/today/CloudField.kt (layout and colour of the home sky's photographed
// clouds, 0.9.9) and of CloudPainter's drawing, for the design bench. Keep in step with the Kotlin.
(function (root) {
  const KINDS = 8, HUMILIS = 0, MEDIOCRIS = 1, CONGESTUS = 2, CUMULONIMBUS = 3, FRACTUS = 6, ROW = 7;
  const ELEVATION_GAIN = 1.6, EARTH_R = 6360, EYE_H = 0.25, ALT_TOP = 80, ALT_POW = 1.25, NX = 48, NY = 32, LUT_POW = 2;

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
  const seedOf = (w) => (Math.floor(w.seedX * 1000) * 31 + Math.floor(w.seedY * 1000)) | 0;

  function kindOf(regime, h, far, i) {
    switch (regime) {
      case 1: return (far > 0.8 && h < 0.3) ? ROW : h < 0.55 ? HUMILIS : h < 0.93 ? MEDIOCRIS : FRACTUS;
      case 2: return (far > 0.75 && h < 0.25) ? ROW : (far > 0.5 && h > 0.85) ? CONGESTUS : h < 0.6 ? MEDIOCRIS : HUMILIS;
      case 3: return (i === 0 || (i === 1 && h > 0.6)) ? CUMULONIMBUS : (far > 0.35 && h < 0.4) ? CONGESTUS : h < 0.8 ? MEDIOCRIS : HUMILIS;
      case 4: return (far > 0.45 && h < 0.45) ? CONGESTUS : h < 0.85 ? HUMILIS : FRACTUS;
      case 5: return (far > 0.7 && h < 0.4) ? ROW : h < 0.8 ? MEDIOCRIS : HUMILIS;
      default: return HUMILIS;
    }
  }

  const SPEC = [
    [1.6, 3.0, 1.0, 1.4, 6, 55], [3.0, 5.5, 1.0, 1.4, 7, 55], [0, 0, 4.5, 7.0, 25, 75], [0, 0, 10, 13, 60, 110],
    [0, 0, 0, 0, 1, 1], [0, 0, 0, 0, 1, 1], [0.9, 1.8, 0.8, 1.2, 6, 40], [0, 0, 2.2, 3.2, 40, 70],
  ];
  const SHADE_FILL = 0.35, MOONLIT = [0.42, 0.46, 0.56], NIGHT_FLOOR = [0.006, 0.008, 0.014];
  const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
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

  // f: { w, h, horizonY, dp, fov, time, weather: {regime, clouds, seedX, seedY}, sunAz, sunEl, light: {sunCloud, ambTop, ambLow}, lut, night, moonLight }
  function layout(table, f, tone) {
    const T = Object.assign({ gain: ELEVATION_GAIN, fill: SHADE_FILL }, root.CLOUD_TUNE || {});
    const out = [];
    const regime = f.weather.regime, count = Math.max(0, Math.min(40, f.weather.clouds));
    if (count === 0 || !table.sprites.length) return out;
    const seed = seedOf(f.weather), pxPerRad = f.w / f.fov, window_ = f.fov + 0.6, D2R = Math.PI / 180;
    const se = f.sunEl * D2R;
    const sunL = [Math.cos(se) * Math.sin(f.sunAz), Math.sin(se), Math.cos(se) * Math.cos(f.sunAz)];
    const sunX = f.w * (0.5 + f.sunAz / f.fov);
    const L = f.light, amb = [0, 1, 2].map(j => (L.ambTop[j] * 0.85 + L.ambLow[j] * 0.4) * 1.3);
    const dAmb = tone(amb.slice(), [0, 0, 0]);
    const night = f.night, moonB = f.moonLight;
    for (let i = 0; i < count; i++) {
      const h = (k) => hash(seed, i, k);
      const h1 = h(1), h2 = h(2), h3 = h(3), h4 = h(4), h5 = h(5), h6 = h(6), h7 = h(7), stretch = 0.86 + 0.28 * h(8);
      const t = (i + 0.5 * h1) / count;
      let kind = kindOf(regime, h3, 1 - t, i);
      if (!table.byKind[kind].length) kind = HUMILIS;
      const kinds = table.byKind[kind]; if (!kinds.length) continue;
      const sp = SPEC[kind];
      const big = kind === CUMULONIMBUS || kind === CONGESTUS || kind === ROW;
      const d = big ? sp[4] + (sp[5] - sp[4]) * (kind === CUMULONIMBUS ? h1 : 1 - t) : sp[4] * Math.pow(sp[5] / sp[4], 1 - t) * (0.92 + 0.16 * h2);
      const si = kinds[Math.min(Math.floor(h6 * kinds.length), kinds.length - 1)];
      const s = table.sprites[si];
      const heightKm = sp[2] + (sp[3] - sp[2]) * h5;
      const az0 = kind === CUMULONIMBUS ? (h4 - 0.5) * (f.fov - 0.6) : (((i * 0.618034 + h4 * 0.5) % 1) - 0.5) * window_ + f.time * 0.007 / d;
      const az = az0 - window_ * Math.floor(az0 / window_ + 0.5);
      const cx = f.w * (0.5 + az / f.fov);
      const drop = d * d / (2 * EARTH_R);
      let wPx, k, yb;
      if (s.cut) {
        yb = f.horizonY + 4 * f.dp;
        const yTop = yOfAltitude(f, Math.atan((heightKm - EYE_H - drop) / d) / D2R * T.gain);
        k = (yb - yTop) / s.h; wPx = s.w * k * stretch;
      } else {
        const wKm = (sp[0] + (sp[1] - sp[0]) * h2) * Math.pow(s.group, 0.8);
        wPx = wKm / d * pxPerRad; k = wPx / s.w / stretch;
        yb = yOfAltitude(f, Math.atan((heightKm - EYE_H - drop) / d) / D2R * T.gain) + s.base * k;
      }
      if (wPx < 2 * f.dp) continue;
      if (cx + wPx / 2 < 0 || cx - wPx / 2 > f.w) continue;
      const top = yb - s.h * k;
      if (top > f.h) continue;
      const c = { sprite: si, distance: d, left: cx - wPx / 2, right: cx + wPx / 2, top, bottom: yb, kind };
      c.flip = (s.side !== 0 && f.sunEl > -2 && f.sunEl < 60) ? ((s.side > 0) !== (sunX > cx)) : h7 > 0.5;
      let lv = s.levels[0]; for (const q of s.levels) if (q[2] >= wPx * 0.8) lv = q;
      c.src = lv;
      const altM = altitudeAt(f, (yb + top) / 2) / T.gain * D2R;
      const fx = Math.cos(altM) * Math.sin(az), fy = Math.sin(altM), fz = Math.cos(altM) * Math.cos(az);
      const fwd = Math.min(hg(fx * sunL[0] + fy * sunL[1] + fz * sunL[2], 0.6) * 4 * Math.PI, 5);
      const dLit = tone([0, 1, 2].map(j => L.sunCloud[j] * 2.6 * (0.85 + 0.15 * fwd) + amb[j]), [0, 0, 0]);
      const sky = sample(f.lut, cx / f.w, rowOf(altitudeAt(f, (yb + top) / 2)));
      const haze = 0.03 + 0.55 * (1 - Math.exp(-d / 45));
      c.slope = [0, 1, 2].map(j => (dLit[j] - T.fill * dAmb[j]) * (1 - haze) * (1 - night) + MOONLIT[j] * moonB * night);
      c.bias = [0, 1, 2].map(j => (T.fill * dAmb[j] * (1 - haze) + haze * sky[j]) * (1 - night) + (sky[j] * 0.45 + NIGHT_FLOOR[j] + MOONLIT[j] * 0.25 * moonB) * night);
      c.alpha = (1 - 0.15 * night) * smooth(2 * f.dp, 6 * f.dp, wPx);
      out.push(c);
    }
    out.sort((a, b) => b.distance - a.distance);
    return out;
  }

  const VS = `#version 300 es
in vec2 p; in vec2 uv; out vec2 vuv; out float vy; uniform vec2 uSize;
void main() { vuv = uv; vy = p.y; gl_Position = vec4(p.x / uSize.x * 2.0 - 1.0, 1.0 - p.y / uSize.y * 2.0, 0.0, 1.0); }`;
  const FS = `#version 300 es
precision highp float;
in vec2 vuv; in float vy; out vec4 o;
uniform sampler2D tA; uniform vec3 uSlope; uniform vec3 uBias; uniform float uAlpha; uniform vec2 uFade;
void main() {
  vec4 t = texture(tA, vuv);
  float a = t.a; float g = a > 0.0 ? t.r / a : 0.0;
  vec3 col = clamp(uSlope * g + uBias, 0.0, 1.0);
  float m = clamp((vy - uFade.x) / max(uFade.y - uFade.x, 1e-3), 0.0, 1.0);
  float al = clamp(a * uAlpha, 0.0, 1.0) * m;
  o = vec4(col * al, al);
}`;

  // Draws [clouds] onto a new transparent W × H canvas (premultiplied), as CloudPainter does over the sky.
  function draw(W, H, clouds, img, fadeTop, fadeBottom) {
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
    const IW = img.width, IH = img.height;
    for (const c of clouds) {
      const [sx, sy, sw, sh] = c.src;
      let u0 = sx / IW, u1 = (sx + sw) / IW; const v0 = sy / IH, v1 = (sy + sh) / IH;
      if (c.flip) [u0, u1] = [u1, u0];
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([c.left, c.top, u0, v0, c.right, c.top, u1, v0, c.left, c.bottom, u0, v1, c.right, c.bottom, u1, v1]), gl.STREAM_DRAW);
      const fadeOn = c.top < fadeBottom;
      gl.uniform2f(gl.getUniformLocation(pr, 'uFade'), fadeOn ? fadeTop : -1e6, fadeOn ? fadeBottom : -1e6 + 1);
      gl.uniform1f(gl.getUniformLocation(pr, 'uAlpha'), c.alpha);
      gl.uniform3fv(gl.getUniformLocation(pr, 'uSlope'), c.slope);
      gl.uniform3fv(gl.getUniformLocation(pr, 'uBias'), c.bias);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
    gl.finish();
    return cv;
  }

  root.Clouds = { parse, layout, draw, hash, seedOf };
})(typeof window !== 'undefined' ? window : globalThis);
