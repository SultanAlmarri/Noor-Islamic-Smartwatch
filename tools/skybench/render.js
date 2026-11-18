// Runs in the page: renders one home-sky frame with the real shader files. window.renderFrame(cfg, src) -> dataURL
(function () {
  const PRELUDE = `#version 300 es
precision highp float;
#define float2 vec2
#define float3 vec3
#define float4 vec4
#define half float
#define half2 vec2
#define half3 vec3
#define half4 vec4
#define float2x2 mat2
#define float3x3 mat3
uniform sampler2D tLut; uniform vec2 tLutSize;
vec4 lutAt(vec2 p) { return texture(tLut, p / tLutSize); }
uniform sampler2D tRidge; uniform vec2 tRidgeSize;
vec4 ridgeTex(vec2 p) { return texture(tRidge, p / tRidgeSize); }
uniform sampler2D tStars; uniform vec2 tStarsSize;
vec4 starAt(vec2 p) { return texture(tStars, p / tStarsSize); }
`;
  const FOOTER = `
out vec4 fragColor;
void main() { fragColor = render(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y)); }
`;
  const VS = `#version 300 es
in vec2 p; void main() { gl_Position = vec4(p, 0.0, 1.0); }`;

  function program(gl, fs) {
    const mk = (type, s) => { const sh = gl.createShader(type); gl.shaderSource(sh, s); gl.compileShader(sh);
      if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) { const log = gl.getShaderInfoLog(sh); const lines = s.split('\n'); const m = /0:(\d+)/.exec(log); throw new Error(log + (m ? '\n>> ' + lines[+m[1] - 1] : '')); } return sh; };
    const pr = gl.createProgram();
    gl.attachShader(pr, mk(gl.VERTEX_SHADER, VS)); gl.attachShader(pr, mk(gl.FRAGMENT_SHADER, fs)); gl.linkProgram(pr);
    if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(pr));
    return pr;
  }

  function tex(gl, unit, w, h, data, linear) {
    const t = gl.createTexture(); gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, data);
    const f = linear ? gl.LINEAR : gl.NEAREST;
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, f); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, f);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return t;
  }

  // (0.19.1) With reps > 0, the program is drawn that many times more after the first draw (which includes compiling
  // it) and the median time of one frame is returned (each waited for with a one-pixel read).
  function draw(canvas, fs, uniforms, lut, ridge, stars, reps) {
    const gl = canvas.getContext('webgl2', { premultipliedAlpha: true, preserveDrawingBuffer: true, alpha: true });
    const pr = program(gl, fs);
    gl.useProgram(pr);
    const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const loc = gl.getAttribLocation(pr, 'p'); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
    tex(gl, 0, lut.w, lut.h, lut.data, true);
    gl.uniform1i(gl.getUniformLocation(pr, 'tLut'), 0); gl.uniform2f(gl.getUniformLocation(pr, 'tLutSize'), lut.w, lut.h);
    tex(gl, 1, ridge.w, ridge.h, ridge.data, true);
    gl.uniform1i(gl.getUniformLocation(pr, 'tRidge'), 1); gl.uniform2f(gl.getUniformLocation(pr, 'tRidgeSize'), ridge.w, ridge.h);
    const st = stars || { w: 1, h: 1, data: new Uint8Array(4) };
    tex(gl, 2, st.w, st.h, st.data, true);
    gl.uniform1i(gl.getUniformLocation(pr, 'tStars'), 2); gl.uniform2f(gl.getUniformLocation(pr, 'tStarsSize'), st.w, st.h);
    for (const [k, v] of Object.entries(uniforms)) {
      const l = gl.getUniformLocation(pr, k); if (l === null) continue;
      const a = Array.isArray(v) ? v : [v];
      [gl.uniform1f, gl.uniform2f, gl.uniform3f, gl.uniform4f][a.length - 1].call(gl, l, ...a);
    }
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    gl.finish();
    if (!reps) return 0;
    const px = new Uint8Array(4); gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
    const times = [];
    for (let k = 0; k < reps; k++) {
      const t = performance.now();
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
      times.push(performance.now() - t);
    }
    times.sort((a, b) => a - b);
    return times[Math.floor(times.length / 2)];
  }

  window.renderFrame = function (cfg, src) {
    const DEG = Math.PI / 180;
    const dp = cfg.density;
    const W = Math.round(cfg.wDp * dp), H = Math.round(cfg.hDp * dp);
    const s = cfg.skyScale;
    const SW = Math.round(W * s), SH = Math.round(H * s);
    const dd = Scene.day(cfg.place, cfg.date);
    const warpF = Scene.warp(dd, cfg.anchors || [0.08, 0.22, 0.78, 0.92]);
    const xOf = (fr) => (-0.04 + 1.08 * warpF(Math.max(0, Math.min(1, fr)))) * W;
    let f = cfg.fraction;
    if (cfg.xFrac !== undefined) { let lo = 0, hi = 1; for (let i = 0; i < 40; i++) { const m = (lo + hi) / 2; if (xOf(m) < cfg.xFrac * W) lo = m; else hi = m; } f = (lo + hi) / 2; }
    const el = dd.elAt(f);
    const hy = cfg.horizonDp * dp, arcTop = cfg.arcTopDp * dp, groundY = (cfg.hDp - 6) * dp;
    const maxEl = Math.max(dd.maxEl, 12), minEl = Math.min(dd.minEl, -24);
    const refr = (el) => { const h = Math.max(el, -1.9); return 1.02 / Math.tan((h + 10.3 / (h + 5.11)) * DEG) / 60; };
    const hLift = (el) => { if (el >= -0.8333) return refr(el) * (0.5667 / refr(-0.8333)) + 0.2667; const t = Math.max(0, Math.min(1, (el + 4) / (4 - 0.8333))); return 0.8333 * t * t * (3 - 2 * t); };
    const app = (e) => e + hLift(e); const maxApp = app(maxEl);
    const yOf = (e0) => { const e = app(e0); return e >= 0 ? hy - (e / maxApp) * (hy - arcTop) : hy + (-e / -minEl) * (groundY - hy); };
    const sunX = xOf(f), sunY = yOf(el);
    const altTop = 80, altPow = 1.25;
    const altAt = (y) => altTop * Math.pow(Math.max(0, Math.min(1, (hy - y) / hy)), altPow);
    const fov = Math.max(1.6, Math.min(2.6, cfg.wDp * 0.0051));
    const sunAz = (sunX / W - 0.5) * fov;
    const altDraw = el > 0 ? altAt(sunY) : el;
    const nx = 48, ny = 32, lutPow = 2;
    const fr = Atmo.frame({ sunEl: el, sunAz, sunAltDraw: altDraw * DEG, fov, apow: lutPow, nx, ny, capAlt: altAt(arcTop - 16 * dp) });
    const sd = Atmo.dir(sunAz, altDraw * DEG);
    // Weather from the seed.
    const rnd = (k) => { const x = Math.sin(cfg.seed * 12.9898 + k * 78.233) * 43758.5453; return x - Math.floor(x); };
    const cover = cfg.cover ?? (cfg.dunes ? 0.18 + 0.15 * rnd(1) : 0.28 + 0.22 * rnd(1));
    const cirrus = cfg.cirrus ?? (0.25 + 0.6 * rnd(2));
    const weather = [cover, cirrus, (rnd(3) * 40) % 40, (rnd(4) * 40) % 40];
    // The weather of the day (SkyLook.kt Weather) and of this moment: cfg.weatherType 0 clear … 7 mixed; cfg.wx overrides.
    const wDay = Clouds.weather(cfg.weatherType ?? 1, weather[2], weather[3], cirrus);
    const wNow = Object.assign(Clouds.weatherAt(wDay, f), cfg.wx || {});
    weather[0] = wNow.cumulus; weather[1] = wNow.cirrus;
    // (0.19.1) With the baked sets the shaders draw no clouds of their own; the sky's state is the director's.
    if (!cfg.oldClouds) { weather[0] = 0; weather[1] = 0; Object.assign(wNow, { cumulus: 0, cirrus: 0, layer: 0, overcast: cfg.overcast ?? 0, rain: cfg.rain ?? 0, storm: 0 }); }
    const night = 1 - smooth(-14, -6, el);
    const hijriDay = cfg.hijriDay ?? 14;
    const age = (((hijriDay - 1) + 0.5) / 29.53);
    let moonF = ((f - age) % 1 + 1) % 1;
    const moonEl = dd.elAt(moonF);
    const moonVis = moonEl > 2 ? (0.35 + 0.65 * night) * smooth(2, 8, moonEl) : 0;
    const phase = 2 * Math.PI * (((hijriDay - 1) + 1) / 29.53);
    const mx = xOf(moonF), my = yOf(moonEl);
    let lx = sunX - mx, ly = sunY - my; const ll = Math.hypot(lx, ly) || 1; lx /= ll; ly /= ll;
    const pa = Math.PI - phase; // phase angle
    const moonLight = [lx * Math.sin(pa), ly * Math.sin(pa), Math.cos(pa)];
    const sunDisc = fr.sunDisc;
    const nightAmb = [0.010, 0.017, 0.045].map(x => x * night * 1.6);
    const wind = (cfg.time || 0) * 0.02;
    const common = {
      uFov: fov, uLutSize: [nx, ny], uLutPow: lutPow, uSunEl: el, uSunGround: fr.sunGround,
      uAmbTop: fr.ambTop.map((x, i) => x + nightAmb[i]), uAmbLow: fr.ambHor.map((x, i) => x + nightAmb[i] * 1.5),
      uNight: night, uTime: cfg.time || 0, uWeather: weather, uWind: wind, uWx: [wNow.overcast, wNow.layer, wNow.rain, 0],
    };
    // The real night sky (port of NightSky.kt): the moment, sidereal time, the view frame.
    const D2R = Math.PI / 180;
    const [yy, mm, ddn] = cfg.date.split('-').map(Number);
    const atMs = Date.UTC(yy, mm - 1, ddn) + (f * 24 - cfg.place.tz) * 3600e3;
    const jd = atMs / 86400000 + 2440587.5, dd0 = jd - 2451545, T = dd0 / 36525;
    const gmst = ((280.46061837 + 360.98564736629 * dd0 + 0.000387933 * T * T - T * T * T / 38710000) % 360 + 360) % 360;
    const lst = gmst + cfg.place.lon, lat = cfg.place.lat, a0 = cfg.rtl ? 0 : 180;
    const view = (ra, dec) => { const h = (lst - ra) * D2R, d = dec * D2R, phi = lat * D2R;
      const up = Math.sin(phi) * Math.sin(d) + Math.cos(phi) * Math.cos(d) * Math.cos(h);
      const north = Math.cos(phi) * Math.sin(d) - Math.sin(phi) * Math.cos(d) * Math.cos(h);
      const east = -Math.cos(d) * Math.sin(h);
      return [east * Math.cos(a0 * D2R) - north * Math.sin(a0 * D2R), up, east * Math.sin(a0 * D2R) + north * Math.cos(a0 * D2R)]; };
    const starC = document.createElement('canvas'); starC.width = SW; starC.height = SH;
    const sg = starC.getContext('2d');
    for (const st of (window.STARS || [])) {
      const v = view(st[0], st[1]); const alt = Math.asin(Math.max(-1, Math.min(1, v[1]))) / D2R;
      if (alt < 0.5 || alt >= altTop) continue;
      const az = Math.atan2(v[0], v[2]); if (Math.abs(az) > fov / 2 + 0.05) continue;
      const x = SW * (0.5 + az / fov), y = hy * s - hy * s * Math.pow(alt / altTop, 1 / altPow);
      const air = Math.pow(Math.max(0, Math.min(1, (alt - 0.5) / 12)), 0.7);
      const flux = Math.min(4, Math.pow(10, -0.4 * (st[2] - 1))) * air;
      const a = Math.min(1, 0.12 + 0.60 * Math.sqrt(flux)); const r = (0.35 + 0.50 * Math.pow(flux, 0.4)) * dp * s;
      if (a < 0.04) continue;
      const tK = st[3] / 100; let R, G, B;
      if (tK <= 66) { R = 255; G = 99.47 * Math.log(tK) - 161.12; B = tK <= 19 ? 0 : 138.52 * Math.log(tK - 10) - 305.04; } else { R = 329.7 * Math.pow(tK - 60, -0.1332); G = 288.12 * Math.pow(tK - 60, -0.0755); B = 255; }
      const c = (x) => Math.round(Math.max(0, Math.min(255, x)) * 0.45 + 255 * 0.55);
      const gr = sg.createRadialGradient(x, y, 0, x, y, r * 2.4);
      gr.addColorStop(0, `rgba(${c(R)},${c(G)},${c(B)},${a})`); gr.addColorStop(0.32, `rgba(${c(R)},${c(G)},${c(B)},${a * 0.35})`); gr.addColorStop(1, `rgba(${c(R)},${c(G)},${c(B)},0)`);
      sg.fillStyle = gr; sg.beginPath(); sg.arc(x, y, r * 2.4, 0, 2 * Math.PI); sg.fill();
    }
    const starsTex = { w: SW, h: SH, data: new Uint8Array(sg.getImageData(0, 0, SW, SH).data.buffer) };
    const skyU = Object.assign({}, common, {
      uCelX: view(0, 0), uCelY: view(90, 0), uCelZ: view(0, 90), uGalN: view(192.85948, 27.12825), uGalC: view(266.40499, -28.93617),
      uMoonIllum: (1 - Math.cos(phase)) / 2,
      uSize: [SW, SH], uDp: dp * s, uHorizon: hy * s, uAltTop: altTop, uAltPow: altPow,
      uSunPos: [sunX * s, sunY * s], uSunDir: sd, uSunDisc: sunDisc, uSunGlow: fr.sunGlow, uSunCloud: fr.sunCloud, uSunHigh: fr.sunHigh,
      uCloudTop: (arcTop - 8 * dp) * s, uMoon: [mx * s, my * s, 10 * dp * s, moonVis], uMoonLight: moonLight,
      uMilky: smooth(0.55, 1, night), uTextBottom: (arcTop - 16 * dp) * s,
    });
    // Land geometry.
    const terrain = cfg.terrain || (cfg.dunes ? 'dunes' : 'hills');
    const dunes = terrain === 'dunes';
    const SPEC = { dunes: [[16, 3], [20, 16], [24, 32], [26, 50], [26, 72]], hills: [[26, 3], [34, 13], [1, 24], [30, 52], [18, 74]],
      mountains: [[58, 2], [40, 14], [30, 30], [24, 50], [24, 72]], sea: [[16, 0], [1, 8], [1, 20], [12, 54], [18, 74]],
      forest: [[40, 2], [18, 12], [1, 24], [30, 54], [16, 74]], arctic: [[52, 3], [36, 14], [1, 22], [16, 56], [14, 74]], canyon: [[16, 3], [60, 12], [10, 30], [18, 52], [14, 74]] };
    const DIST = { dunes: [22, 7, 2.8, 1.1, 0.35], hills: [40, 12, 4, 1.2, 0.4], mountains: [34, 14, 6, 2.2, 0.6], sea: [40, 10, 3, 0.8, 0.3],
      forest: [22, 8, 3, 0.9, 0.3], arctic: [10, 5, 3, 0.8, 0.3], canyon: [20, 6, 3, 1.2, 0.4] };
    const layers = SPEC[terrain], dists = DIST[terrain];
    const deepest = 72;
    const spread = Math.max(0.55, Math.min(1.4, ((cfg.hDp - cfg.horizonDp) - 12) / deepest));
    const base = layers.map(l => hy + l[1] * spread * dp), amp = layers.map(l => l[0] * dp);
    const N = 1024;
    const rid = Scene.ridges(cfg.landSeed || 7, terrain, N);
    const rdata = new Uint8Array(N * 2 * 4);
    for (let i = 0; i < N; i++) { rdata[i * 4 + 3] = 255; rdata[(N + i) * 4 + 3] = 255; for (let l = 0; l < 5; l++) {
      const row = l < 3 ? 0 : 1, ch = l < 3 ? l : l - 3;
      rdata[(row * N + i) * 4 + ch] = Math.round(255 * rid[l][i]);
    } }
    const page = cfg.dark === false ? [0.957, 0.945, 0.918] : [0.031, 0.047, 0.078];
    const morning = f < dd.noonF;
    const nightish = night;
    const mist = morning ? 0.85 * smooth(0, 1, 1 - Math.abs(el + 1) / 13) + 0.18 * nightish : 0.22 * nightish + 0.12 * smooth(0, 1, 1 - Math.abs(el - 1) / 10);
    const landU = Object.assign({}, common, { uWeather: [wNow.cumulus * (1 - wNow.overcast), wNow.cirrus, weather[2], weather[3]] }, {
      uSize: [W, H], uDp: dp, uHorizon: hy, uBaseA: base.slice(0, 4), uBaseB: base[4], uAmpA: amp.slice(0, 4), uAmpB: amp[4],
      uDistA: dists.slice(0, 4), uDistB: dists[4], uRidgeN: N, uDunes: dunes ? 1 : 0, uSunPos: [sunX, sunY],
      uPage: page, uMist: mist, uSeed: (cfg.landSeed || 7) % 97, uSunrise: [xOf(dd.ev.SHURUQ), xOf(dd.ev.MAGHRIB)],
      uTerrain: { dunes: 0, hills: 1, mountains: 2, sea: 3, forest: 4, arctic: 5, canyon: 6 }[terrain], uAltTop: altTop, uAltPow: altPow,
      uMoonL: [mx, my, moonVis, (1 - Math.cos(phase)) / 2],
    });
    const lut = { w: nx, h: ny, data: new Uint8Array(fr.lut.buffer) };
    const ridge = { w: N, h: 2, data: rdata };

    const skyC = document.createElement('canvas'); skyC.width = SW; skyC.height = SH;
    const t0 = performance.now();
    const perfSky = draw(skyC, PRELUDE + src.common + src.sky + FOOTER, skyU, lut, ridge, starsTex, cfg.perf || 0);
    const t1 = performance.now();
    const landC = document.createElement('canvas'); landC.width = W; landC.height = H;
    // (0.19.1) As the app builds it (SkyShaders.landSource): the terrain made a constant, so the other terrains' code
    // goes (cfg.allTerrains: the source as it is).
    const tid = landU.uTerrain;
    const landSrc = cfg.allTerrains ? src.land : src.land.replace('float terrainId() { return uTerrain; }', `float terrainId() { return ${tid}.0; }`);
    const perfLand = draw(landC, PRELUDE + src.common + landSrc + FOOTER, landU, lut, ridge, null, cfg.perf || 0);
    const t2 = performance.now();

    const out = document.createElement('canvas'); out.width = W; out.height = H + (cfg.pageBelowDp || 0) * dp;
    const g = out.getContext('2d');
    g.fillStyle = `rgb(${page.map(x => Math.round(x * 255)).join(',')})`; g.fillRect(0, 0, out.width, out.height);
    g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high';
    const fade = cfg.fade ?? 1, pathA = cfg.pathAlpha ?? 1, marksA = cfg.marksAlpha ?? 1, lift = (cfg.landLift ?? 0) * dp;
    g.save(); g.beginPath(); g.rect(0, 0, W, H); g.clip();
    g.globalAlpha = fade;
    g.drawImage(skyC, 0, 0, W, H);
    // The clouds. (0.19.1) The real sky's baked sets, lit for the moment (SetClouds.kt, msets.js); cfg.oldClouds: the
    // 0.9.9 photographed clouds (CloudField.kt, clouds.js).
    const t3 = performance.now();
    let cloudMs = 0;
    if (!cfg.oldClouds && window.MSETS) {
      const F = 1.15 * W;
      const lutAt = (xPx, yPx) => {
        const alt = altAt(yPx); const v = Math.pow(Math.max(0, Math.min(1, alt / 90)), 1 / lutPow);
        const fx = Math.max(0, Math.min(1, xPx / W)) * (nx - 1), fy = v * (ny - 1);
        const i = Math.min(nx - 2, Math.floor(fx)), j = Math.min(ny - 2, Math.floor(fy)), a = fx - i, b = fy - j;
        const L = fr.lut, o = [0, 0, 0];
        for (let c = 0; c < 3; c++) {
          const v0 = L[(j * nx + i) * 4 + c] * (1 - a) + L[(j * nx + i + 1) * 4 + c] * a;
          const v1 = L[((j + 1) * nx + i) * 4 + c] * (1 - a) + L[((j + 1) * nx + i + 1) * 4 + c] * a;
          o[c] = (v0 * (1 - b) + v1 * b) / 255;
        }
        return o;
      };
      const moonLight = el < -6;
      const moonW = smooth(-6, -10, el);
      const illum = (1 - Math.cos(phase)) / 2;
      const mk = cfg.moonK ?? 0.9;
      const L = {
        el, sunAz: (sunX - W / 2) / F, sunElSeen: el > 0 ? Math.atan((hy - sunY) / F) / DEG : el,
        side: moonLight ? Math.max(-1, Math.min(1, (mx - W / 2) / (0.2 * W))) : Math.max(-1, Math.min(1, (f - dd.noonF) / 0.07)) * (cfg.rtl ? -1 : 1),
        moonLight, moonEl: Math.max(0.7, Math.atan((hy - my) / F) / DEG), glow: smooth(0, 1, (12 - el) / 10),
        moonW, moon: [0.050, 0.062, 0.095].map(x => x * mk * illum * moonVis),
        sunlight: fr.sunlight, ambTop: common.uAmbTop, ambLow: common.uAmbLow, sunGround: fr.sunGround, glowCol: fr.glow,
        haze: cfg.haze ?? 1.0, sunPart: cfg.sunPart ?? 0.6, expose: cfg.cloudExpose ?? 1.0, ambK: cfg.ambK ?? 3.0, night: fr.night, sky: lutAt,
        sunX, sunY, dp, glare: (cfg.glare ?? 0.8) * smooth(-5, 0.5, el), glareCol: Atmo.tone(fr.sunGlow.slice(), [0, 0, 0]),
        textY: arcTop - 16 * dp - 0.085 * H, textR: 0.125 * H, textCap: cfg.textCap ?? 0.34, debug: !!cfg.debug,
      };
      const V = { W, hy, F, rtl: !!cfg.rtl };
      const cc = document.createElement('canvas'); cc.width = W; cc.height = H;
      const cg = cc.getContext('2d'); cg.imageSmoothingEnabled = true; cg.imageSmoothingQuality = 'high';
      const tc = performance.now();
      // High, middle, low: each level's sets, the farther behind.
      const legs = (cfg.legs || []).map(l => Object.assign({ weight: 1, offset: 0, turned: false }, l)).filter(l => window.MSETS[l.set]);
      legs.sort((a, b) => window.MSETS[a.set].level - window.MSETS[b.set].level);
      for (const leg of legs) {
        const r = MSets.compose(window.MSETS[leg.set], L, leg, V);
        const pc = document.createElement('canvas'); pc.width = r.image.width; pc.height = r.image.height;
        pc.getContext('2d').putImageData(r.image, 0, 0);
        cg.drawImage(pc, r.x0, r.y0, r.x1 - r.x0, r.y1 - r.y0);
      }
      cloudMs = performance.now() - tc;
      g.drawImage(cc, 0, 0);
    } else if (window.CLOUDS) {
      const cl = window.CLOUDS; window.DEBUG_CLOUDS = !!cfg.debugClouds;
      const laid = Clouds.layout(cl.table, {
        w: W, h: H, horizonY: hy, dp, fov, time: cfg.time || 0, weather: wDay, state: wNow,
        sunAz, sunEl: el, light: { sunCloud: fr.sunCloud, ambTop: common.uAmbTop, ambLow: common.uAmbLow },
        lut: fr.lut, night, moonLight: moonVis * ((1 - Math.cos(phase)) / 2) * night,
      }, Atmo.tone);
      const textBottom = arcTop - 16 * dp;
      g.drawImage(Clouds.draw(W, H, laid, cl.image, textBottom - 6 * dp, textBottom + 34 * dp, dp, night), 0, 0);
      window.LAST_CLOUDS = laid.draws;
    }
    const t4 = performance.now();
    // Sun path above the horizon (behind the land): dashed, gold where the day has passed.
    g.save(); g.beginPath(); g.rect(0, 0, W, hy); g.clip();
    const path = new Path2D(); dd.samples.forEach(([fq, e], i) => { const x = xOf(fq), y = yOf(e); i ? path.lineTo(x, y) : path.moveTo(x, y); });
    g.setLineDash([2 * dp, 5 * dp]); g.lineCap = 'round'; g.lineWidth = 1.3 * dp; g.strokeStyle = `rgba(255,255,255,${0.5 * pathA})`; g.stroke(path);
    g.setLineDash([]);
    g.save(); g.beginPath(); g.rect(0, 0, sunX, hy); g.clip();
    g.lineWidth = 6.4 * dp; g.strokeStyle = `rgba(255,213,140,${0.20 * pathA})`; g.stroke(path);
    g.lineWidth = 1.6 * dp; g.strokeStyle = `rgba(255,237,194,${0.95 * pathA})`; g.stroke(path);
    g.restore(); g.restore();
    let landMs = 0;
    if (cfg.painted && window.PAINTED && window.PAINTED[cfg.painted]) {
      // (0.22, PaintedLand.weights) The two lights of the moment, one faded over the other; horizon on row 900 of 1080.
      const P = window.PAINTED[cfg.painted], ev = f > dd.noonF;
      const sm = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
      const gold = ev ? 'golden_right' : 'golden_left', day = ev ? 'day_right' : 'day_left';
      const [A, B, t] = el < -3 ? ['night', 'twilight', sm(-11, -3, el)] : el < 3 ? ['twilight', gold, sm(-3, 3, el)] : el < 15 ? [gold, day, sm(3, 15, el)] : [day, 'noon', sm(22, 50, el)];
      const k = W / 1080, top = hy - 900 * k + lift;
      if (P[A]) { g.globalAlpha = fade; g.drawImage(P[A], 0, top, W, 1440 * k); }
      if (P[B]) { g.globalAlpha = fade * (P[A] ? t : 1); g.drawImage(P[B], 0, top, W, 1440 * k); }
      g.globalAlpha = fade;
    } else g.drawImage(landC, 0, lift);
    Clouds.rain(g, W, H, wNow.rain, cfg.time || 0, dp, night);
    // Prayer marks: in the sky, or on the skyline when the Sun is down.
    const skyline = (x) => { let m = 1e9; for (let l = 0; l < 5; l++) { const h = rid[l][Math.round(Math.max(0, Math.min(1, x / W)) * (N - 1))]; m = Math.min(m, base[l] - amp[l] * h); } return m; };
    const next = cfg.next || 'ASR';
    g.globalAlpha = fade * marksA;
    for (const [p, fq] of Object.entries(dd.ev)) {
      const x = xOf(fq), y = yOf(dd.elAt(fq));
      if (y > hy + 2 * dp) { g.beginPath(); g.fillStyle = 'rgba(0,0,0,0.28)'; g.arc(x, y, 7 * dp, 0, 7); g.fill(); }
      g.beginPath();
      if (p === next) { g.fillStyle = 'rgba(246,214,150,0.25)'; g.arc(x, y, 10.5 * dp, 0, 7); g.fill(); g.beginPath(); g.strokeStyle = '#F6D696'; g.lineWidth = 1.8 * dp; g.arc(x, y, 5.5 * dp, 0, 7); g.stroke(); }
      else if (fq <= f) { g.fillStyle = 'rgba(255,255,255,0.95)'; g.arc(x, y, 3 * dp, 0, 7); g.fill(); }
      else { g.strokeStyle = 'rgba(255,255,255,0.8)'; g.lineWidth = 1.2 * dp; g.arc(x, y, 3.4 * dp, 0, 7); g.stroke(); }
    }
    g.restore();
    // Text, roughly as the app sets it.
    if (cfg.scrub) {
      const mins = Math.round(f * 1440); const hh = Math.floor(mins / 60) % 24, mm = mins % 60;
      const h12 = ((hh + 11) % 12) + 1, ap = hh < 12 ? 'AM' : 'PM';
      g.fillStyle = 'white'; g.textAlign = 'center'; g.shadowColor = 'rgba(8,21,48,0.25)'; g.shadowBlur = 10; g.shadowOffsetY = 1.5 * dp;
      g.font = `600 ${15 * dp}px sans-serif`; g.fillText(cfg.placeName || 'Dubai', W / 2, (cfg.topDp + 34) * dp);
      g.font = `600 ${12 * dp}px sans-serif`; g.fillStyle = 'rgba(255,255,255,0.7)';
      g.fillText(`SUN ${Math.abs(Math.round(el))}° ${el >= 0 ? 'ABOVE' : 'BELOW'} THE HORIZON`, W / 2, (cfg.topDp + 84) * dp);
      g.fillStyle = 'white'; g.font = `300 ${62 * dp}px sans-serif`; g.fillText(`${h12}:${String(mm).padStart(2, '0')} ${ap}`, W / 2, (cfg.topDp + 160) * dp);
    } else if (cfg.text !== false) {
      g.fillStyle = 'white'; g.textAlign = 'center'; g.shadowColor = 'rgba(8,21,48,0.25)'; g.shadowBlur = 10; g.shadowOffsetY = 1.5 * dp;
      g.font = `600 ${15 * dp}px sans-serif`; g.fillText(cfg.placeName || 'Dubai', W / 2, (cfg.topDp + 34) * dp);
      g.font = `600 ${28 * dp}px sans-serif`; g.fillText(cfg.title || 'Asr  ·  3:28 PM', W / 2, (cfg.topDp + 92) * dp);
      g.font = `300 ${68 * dp}px sans-serif`; g.fillText(cfg.countdown || '1:52:07', W / 2, (cfg.topDp + 168) * dp);
    }
    return { url: out.toDataURL('image/png'), ms: [t1 - t0, t2 - t1, t4 - t3, cloudMs, landMs], perf: [perfSky, perfLand], el, night, exposure: fr.exposure, clouds: (window.LAST_CLOUDS || []).length, wx: wNow };
  };
  function smooth(a, b, x) { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
})();
