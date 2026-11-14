// The design bench for Noor's real sky: runs the very shader files the phone runs (app/src/main/assets/sky) in WebGL 2,
// with the same passes in the same order as the Android renderer (RealSkyRenderer.kt). Serve the repository root with
// tools/sky3d/serve.py and open /tools/sky3d/index.html. Drive it from the console: bench.set({...}), bench.settle(),
// bench.save('name.png'), bench.sheet('name.png', [...]).

const DIR = '/app/src/main/assets/sky2/';
const q = new URLSearchParams(location.search);
const W = +(q.get('w') || 1080), H = +(q.get('h') || 2340);
const canvas = document.getElementById('c');
canvas.width = W; canvas.height = H;
const gl = canvas.getContext('webgl2', { antialias: false, alpha: false, preserveDrawingBuffer: true });
gl.getExtension('EXT_color_buffer_float'); gl.getExtension('EXT_color_buffer_half_float'); gl.getExtension('OES_texture_float_linear');
const errors = [];
const HEAD = '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler3D;\n';
const INT_UNIFORMS = new Set(['uFrame', 'uAmort', 'uMode', 'uBiome']);
const LAND_DIR = q.get('land') || '/app/src/main/assets/sky2/land/';
const SKY_DIR = '/app/src/main/assets/sky2/clouds/';
const SKIES = {};      // cloud sets by id: sky.json and pictures as textures

const cache = {};
async function src(name) { if (!cache[name]) cache[name] = await (await fetch(DIR + name)).text(); return cache[name]; }

function compile(type, text, label) {
  const s = gl.createShader(type);
  gl.shaderSource(s, text); gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(s);
    errors.push(label + ': ' + log);
    console.error(label, log);
    return null;
  }
  return s;
}

async function program(label, parts, defines = '') {
  const vs = compile(gl.VERTEX_SHADER, HEAD + await src('quad.vert'), label + '.vert');
  let body = HEAD + defines;
  for (const p of parts) body += '\n// ---- ' + p + '\n' + await src(p);
  const t0 = performance.now();
  const fs = compile(gl.FRAGMENT_SHADER, body, label);
  if (!vs || !fs) return null;
  const p = gl.createProgram();
  gl.attachShader(p, vs); gl.attachShader(p, fs); gl.linkProgram(p);
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) { errors.push(label + ' link: ' + gl.getProgramInfoLog(p)); console.error(gl.getProgramInfoLog(p)); return null; }
  const uniforms = [];
  const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < n; i++) {
    const info = gl.getActiveUniform(p, i);
    const name = info.name.replace(/\[0\]$/, '');
    uniforms.push({ name, type: info.type, loc: gl.getUniformLocation(p, info.name) });
  }
  return { p, uniforms, label, ms: performance.now() - t0, chars: body.length };
}

function tex2D(w, h, internal, format, type, filter, wrap, mips = false) {
  const t = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texImage2D(gl.TEXTURE_2D, 0, internal, w, h, 0, format, type, null);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, mips ? gl.LINEAR_MIPMAP_LINEAR : filter);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, filter);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, wrap); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, wrap);
  return { t, w, h, target: gl.TEXTURE_2D };
}
const hdr = (w, h, filter = gl.LINEAR) => tex2D(w, h, gl.RGBA16F, gl.RGBA, gl.HALF_FLOAT, filter, gl.CLAMP_TO_EDGE);
const ldr = (w, h, filter = gl.LINEAR) => tex2D(w, h, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, filter, gl.CLAMP_TO_EDGE);

function fbo(...texs) {
  const f = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, f);
  texs.forEach((t, i) => gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0 + i, gl.TEXTURE_2D, t.t, 0));
  gl.drawBuffers(texs.map((_, i) => gl.COLOR_ATTACHMENT0 + i));
  const st = gl.checkFramebufferStatus(gl.FRAMEBUFFER);
  if (st !== gl.FRAMEBUFFER_COMPLETE) errors.push('fbo incomplete ' + st.toString(16));
  return { f, w: texs[0].w, h: texs[0].h };
}

// ---- State -------------------------------------------------------------------------------------------------------

const P = {
  tod: 0.5, doy: 318, lat: 25.2, hijriDay: 5, rtl: false,
  cover: 0.42, kind: 0.42, base: 1.4, density: 60, mid: 0.0, midForm: 0.3, cluster: 0.5, anvil: 1.0,
  cirrus: 0.35, veil: 0.0, showers: 0.0, seed: 3.0, haze: 1.0, time: 100, windLow: 0.012, windMid: 0.02,
  biome: 0, season: 0.3, snow: 0.0, wet: 0.0, mist: 0.0, rain: 0.0, windForce: 0.3, dust: 0.0,
  aurora: 0.0, flash: 0.0, rainbow: 0.0, waterRough: 0.3, waterClear: 0.2,
  skyHigh: null, skyMid: null, skyLow: null, slideHigh: 0, slideMid: 0, slideLow: 0,     // cloud sets (ids) and how far each has slid (px)
  focal: 1.15, eye: 60.0, horizon: 911 / 2340, arcTop: 596 / 2340, ground: 1110 / 2340, textBottom: 554 / 2340, panelTop: 1126 / 2340,
  panelTint: 0.5, light: 0, cloudScale: 1.0, sceneScale: 1.0, steps: 1.0, text: 1, panel: 1,
};
// The sceneries: where the camera stands in each (m above the datum), its lens, and the water's character.
const BIOMES = [
  { id: 'dunes', water: [0.3, 0.2] },
  { id: 'pnw', water: [0.10, 0.15] },
  { id: 'arctic', water: [0.08, 0.10] },
  { id: 'ocean', water: [0.55, 0.25] },
  { id: 'meadow', water: [0.3, 0.2] },
  { id: 'canyon', water: [0.3, 0.2] },
];
const LAND = {};       // per scenery: its land.json and its pictures as textures
const u = {};          // uniform values by name
const T = {};          // textures by sampler name
const G = { frame: 0, progs: {}, dirty: { noise: true, trans: true, light: true, gbuf: true }, lastHaze: -1, lastLightKey: '' };

const DEG = Math.PI / 180;
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const norm = v => { const l = Math.hypot(...v); return v.map(x => x / l); };

function elevationAt(f) {
  const decl = -23.44 * Math.cos(2 * Math.PI * (P.doy + 10) / 365) * DEG;
  const phi = P.lat * DEG, h = (f - 0.5) * 2 * Math.PI;
  return Math.asin(Math.sin(phi) * Math.sin(decl) + Math.cos(phi) * Math.cos(decl) * Math.cos(h)) / DEG;
}
function crossing(el, rising) {      // fraction of the day when the Sun crosses elevation el
  let lo = rising ? 0 : 0.5, hi = rising ? 0.5 : 1;
  for (let i = 0; i < 40; i++) { const m = (lo + hi) / 2; if ((elevationAt(m) < el) === rising) lo = m; else hi = m; }
  return (lo + hi) / 2;
}
function chart() {
  const rise = crossing(-0.833, true), set = crossing(-0.833, false), fajr = crossing(-18, true), isha = crossing(-18, false);
  const half = 30 / 1440;
  const xs = [0, fajr - half, rise, set, isha + half, 1], ys = [0, 0.08, 0.22, 0.78, 0.92, 1];
  const warp = f => { for (let i = 1; i < xs.length; i++) if (f <= xs[i]) return ys[i - 1] + (ys[i] - ys[i - 1]) * (f - xs[i - 1]) / (xs[i] - xs[i - 1]); return 1; };
  const maxEl = Math.max(elevationAt(0.5), 12), minEl = Math.min(elevationAt(0), -24);
  const hy = P.horizon * H, at = P.arcTop * H, gy = P.ground * H;
  const x = f => { const uu = (-0.04 + 1.08 * warp(f)) * W; return P.rtl ? W - uu : uu; };
  const y = el => el >= 0 ? hy - el / maxEl * (hy - at) : hy + (-el / -minEl) * (gy - hy);
  return { x, y, rise, set, fajr, isha };
}
function viewRay(px, py) {
  let x = px - 0.5 * W; if (P.rtl) x = -x;
  return norm([x, P.horizon * H - py, P.focal * W]);
}
function dirAzEl(az, el) { return [Math.cos(el) * Math.sin(az), Math.sin(el), Math.cos(el) * Math.cos(az)]; }

function updateUniforms() {
  const ch = chart();
  const f = ((P.tod % 1) + 1) % 1;
  const el = elevationAt(f);
  const sx = ch.x(f), sy = ch.y(el);
  const drawn = viewRay(sx, sy);
  const az = Math.atan2(drawn[0], drawn[2]);
  const drawnEl = Math.asin(drawn[1]) / DEG;
  const night = 1 - smooth(-14, -1, el);
  // The moon rides the Sun's path, as far behind it as it is old.
  const age = ((P.hijriDay - 1) + 0.5) / 29.53;
  const mf = (((f - age) % 1) + 1) % 1, mel = elevationAt(mf);
  const mx = ch.x(mf), my = ch.y(mel);
  const moonDir = viewRay(mx, my);
  const phase = 2 * Math.PI * (P.hijriDay / 29.53), pa = Math.PI - phase;
  let lx = sx - mx, ly = sy - my; const ll = Math.max(1, Math.hypot(lx, ly)); lx /= ll; ly /= ll;
  const moonVis = mel > 2 ? Math.min(1, 0.35 + 0.65 * night) * smooth(2, 8, mel) : 0;
  const illum = (1 - Math.cos(phase)) / 2;
  // The light falls from where the land was rendered under it (bake.py): low Suns stand where the Sun is seen, higher
  // ones swing to the side, the morning's on the left and the afternoon's on the right, crossing over the top at noon.
  const moonLight = el < -6;
  const lightEl = moonLight ? Math.max(mel, 0.7) : Math.max(el, 0.7);
  const lightF = moonLight ? mf : f;
  const noonF = moonLight ? (0.5 + age) % 1 : 0.5;
  let dF = lightF - noonF; dF -= Math.round(dF);
  const side = Math.max(-1, Math.min(1, dF / 0.07)) * (P.rtl ? -1 : 1);
  const lw = lightWeights(lightEl, side, false);
  // (Clouds are lit from under the horizon too, after sunset and before sunrise.)
  const cw = lightWeights(moonLight ? Math.max(mel, 0.7) : el, side, true);
  let light = lw.dir;
  if (moonLight && moonVis <= 0) light = [0, 1, 0];
  G.landLight = lw; G.cloudLight = cw;
  Object.assign(u, {
    uView: [W, H], uTime: P.time, uFrame: G.frame,
    uCam: [P.focal * W, P.horizon * H, P.eye, P.rtl ? 1 : 0],
    uWater: [P.waterRough, P.waterClear, 0, 0],
    uSunDir: drawn, uLightDir: light, uSunTrue: dirAzEl(az, el * DEG), uSunEl: el,
    uMoon: [...moonDir, illum], uMoonLit: [lx * Math.sin(pa), ly * Math.sin(pa), Math.cos(pa), moonVis],
    uNight: night, uHaze: P.haze,
    uCloudA: [P.cover, P.kind, P.base, P.density], uCloudB: [P.mid, P.midForm, P.cluster, P.anvil],
    uCloudC: [P.seed, P.cirrus, P.veil, P.showers],
    uWind: [P.time * P.windLow + P.tod * 24 * 9, P.time * P.windLow * 0.25, P.time * P.windMid + P.tod * 24 * 14, P.time * P.windMid * 0.2],
    uSteps: P.steps,
    uSky: [P.aurora, 0, P.flash, P.rainbow], uLand: [P.biome, P.season, P.snow, P.wet], uAir: [P.mist, P.rain, P.windForce, P.dust],
    uTouch: [0, 0, 1e3, 0], uBiome: P.biome,
    uText: [P.text ? P.textBottom * H : 0, 0, 0, 0],
    uPanel: [P.panel ? P.panelTop * H : H * 2, 30 * 2.625, P.panelTint, P.light],
    uPanelTint: P.light ? [0.965, 0.955, 0.935] : [0.027, 0.039, 0.078], uFade: 1,
  }, celestial());
  const key = [el.toFixed(3), sx.toFixed(1), sy.toFixed(1), P.haze, P.hijriDay, P.rtl, P.focal, P.horizon].join();
  if (key !== G.lastLightKey) { G.lastLightKey = key; G.dirty.light = true; }
  if (P.haze !== G.lastHaze) { G.lastHaze = P.haze; G.dirty.trans = true; G.dirty.light = true; }
  G.info = { el, sx, sy, drawnEl, night, mel, illum };
}

// Which of the rendered Suns the light of the moment lies between, and how much of each: `el` its elevation (degrees),
// `side` -1 in the morning (light from the left) .. +1 in the afternoon. `under`: the picture also has Suns under the
// horizon (clouds have: lu0, lu1, ru0, ru1); the land keeps its lowest Sun's shading then. Mirrors RealSkyFrame.
const SUN_EL = [0.7, 2.5, 5.0, 9.0, 14.0, 22.0, 34.0, 50.0];
const UNDER_EL = [-1.5, -4.0];
const sunAz = e => 14.7 + 62 * smooth(8, 40, e);
function lightWeights(el, side, under) {
  const wr = smooth(-1, 1, side), wl = 1 - wr;
  let a, b, f, ea, eb, top = 0;
  if (el >= SUN_EL[0] || !under) {
    const e = Math.min(Math.max(el, SUN_EL[0]), SUN_EL[SUN_EL.length - 1]);
    let i = 0; while (i < SUN_EL.length - 2 && e > SUN_EL[i + 1]) i++;
    f = (e - SUN_EL[i]) / (SUN_EL[i + 1] - SUN_EL[i]);
    a = '' + i; b = '' + (i + 1); ea = SUN_EL[i]; eb = SUN_EL[i + 1];
    top = 4 * wl * wr * smooth(24, 46, e) * 0.85;
  } else if (el >= UNDER_EL[0]) {
    f = (el - UNDER_EL[0]) / (SUN_EL[0] - UNDER_EL[0]); a = 'u0'; b = '0'; ea = UNDER_EL[0]; eb = SUN_EL[0];
  } else {
    const e = Math.max(el, UNDER_EL[1]);
    f = (e - UNDER_EL[1]) / (UNDER_EL[0] - UNDER_EL[1]); a = 'u1'; b = 'u0'; ea = UNDER_EL[1]; eb = UNDER_EL[0];
  }
  const names = ['l' + a, 'l' + b, 'r' + a, 'r' + b, 'top'];
  const w = [wl * (1 - top) * (1 - f), wl * (1 - top) * f, wr * (1 - top) * (1 - f), wr * (1 - top) * f, top];
  const dirs = [dirAzEl(-sunAz(ea) * DEG, ea * DEG), dirAzEl(-sunAz(eb) * DEG, eb * DEG), dirAzEl(sunAz(ea) * DEG, ea * DEG), dirAzEl(sunAz(eb) * DEG, eb * DEG), dirAzEl(0, 68 * DEG)];
  let d = [0, 0, 0];
  for (let k = 0; k < 5; k++) for (let c = 0; c < 3; c++) d[c] += dirs[k][c] * w[k];
  if (el < SUN_EL[0]) d = [d[0], Math.sin(el * DEG), d[2]];
  return { names, w, glow: [wl, wr], dir: norm(d) };
}

async function loadPicture(url, filter = gl.LINEAR) {
  const bmp = await createImageBitmap(await (await fetch(url + '?' + Date.now())).blob(), { premultiplyAlpha: 'none', colorSpaceConversion: 'none' });
  const t = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, t);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, bmp);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, filter); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, filter);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  return { t, w: bmp.width, h: bmp.height, target: gl.TEXTURE_2D };
}

async function loadLand(id) {
  if (LAND[id] !== undefined) return LAND[id];
  LAND[id] = null;
  try {
    const meta = await (await fetch(LAND_DIR + id + '/land.json?' + Date.now())).json();
    const load = (name, filter) => loadPicture(LAND_DIR + id + '/' + name, filter);
    const L = { meta, a: await load('a.webp'), k: await load('k.webp'), g: await load('g.png', gl.NEAREST), s: {} };
    for (const sun of meta.suns) L.s[sun.name] = await load('s_' + sun.name + '.webp');
    for (const n of ['gl', 'gr']) { try { L.s[n] = await load('s_' + n + '.webp'); } catch (e) { L.s[n] = T.tZero; } }
    L.m = meta.mask ? await load('m.webp') : null;
    L.lit = tex2D(L.a.w, L.a.h, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, gl.LINEAR, gl.CLAMP_TO_EDGE);
    L.fLit = fbo(L.lit);
    LAND[id] = L;
  } catch (e) { console.warn('no land for', id, e); }
  return LAND[id];
}

// A set of clouds (tools/scenery/skybake.py).
async function loadSky(id) {
  if (SKIES[id] !== undefined) return SKIES[id];
  SKIES[id] = null;
  try {
    const meta = await (await fetch(SKY_DIR + id + '/sky.json?' + Date.now())).json();
    const load = name => loadPicture(SKY_DIR + id + '/' + name);
    const S = { meta, a: await load('a.webp'), f: await load('f.webp'), k: await load('k.webp'), g: await load('g.webp'), s: {} };
    for (const sun of meta.suns) S.s[sun.name] = await load('s_' + sun.name + '.webp');
    S.lit = tex2D(S.a.w, S.a.h, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, gl.LINEAR, gl.CLAMP_TO_EDGE);
    S.fLit = fbo(S.lit);
    S.out = hdr(S.a.w, S.a.h);
    S.fOut = fbo(S.out);
    SKIES[id] = S;
  } catch (e) { console.warn('no cloud set', id, e); }
  return SKIES[id];
}

function use(prog, target, extra = {}) {
  gl.bindFramebuffer(gl.FRAMEBUFFER, target ? target.f : null);
  const w = target ? target.w : W, h = target ? target.h : H;
  gl.viewport(0, 0, w, h);
  gl.useProgram(prog.p);
  const vals = Object.assign({}, u, { uRes: [w, h] }, extra);
  let unit = 0;
  for (const un of prog.uniforms) {
    if (un.type === gl.SAMPLER_2D || un.type === gl.SAMPLER_3D) {
      const t = extra[un.name] || T[un.name];
      if (!t) { continue; }
      gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(t.target, t.t); gl.uniform1i(un.loc, unit); unit++;
      continue;
    }
    const v = vals[un.name];
    if (v === undefined) continue;
    if (un.type === gl.INT) gl.uniform1i(un.loc, v);
    else if (typeof v === 'number') gl.uniform1f(un.loc, v);
    else if (v.length === 2) gl.uniform2fv(un.loc, v);
    else if (v.length === 3) gl.uniform3fv(un.loc, v);
    else gl.uniform4fv(un.loc, v);
  }
}
const draw = () => gl.drawArrays(gl.TRIANGLES, 0, 3);

// The celestial axes in the view's frame for a place and a moment (NightSky.frame in the app).
function celestial() {
  const lon = 55.3, lat = P.lat;
  const utcHours = (P.tod * 24 - lon / 15 + 24) % 24;
  const d = (Date.UTC(2026, 0, 1) / 86400000 + 2440587.5 - 2451545.0) + (P.doy - 1) + utcHours / 24;
  const lst = (((280.46061837 + 360.98564736629 * d) % 360) + 360) % 360 + lon;
  const a0 = (P.rtl ? 0 : 180) * DEG, phi = lat * DEG;
  const view = (ra, dec) => {
    const h = (lst - ra) * DEG, dd = dec * DEG;
    const up = Math.sin(phi) * Math.sin(dd) + Math.cos(phi) * Math.cos(dd) * Math.cos(h);
    const north = Math.cos(phi) * Math.sin(dd) - Math.sin(phi) * Math.cos(dd) * Math.cos(h);
    const east = -Math.cos(dd) * Math.sin(h);
    const v = [east * Math.cos(a0) - north * Math.sin(a0), up, east * Math.sin(a0) + north * Math.cos(a0)];
    return P.rtl ? [-v[0], v[1], v[2]] : v;
  };
  return { uCelX: view(0, 0), uCelY: view(90, 0), uCelZ: view(0, 90), uGalN: view(192.85948, 27.12825), uGalC: view(266.40499, -28.93617) };
}

async function loadStars() {
  const dv = new DataView(await (await fetch('/app/src/main/res/raw/stars.bin')).arrayBuffer());
  const n = dv.getInt32(0);
  const data = new Float32Array(n * 7);
  let o = 4;
  for (let i = 0; i < n; i++) {
    const ra = dv.getFloat32(o) * DEG, dec = dv.getFloat32(o + 4) * DEG, mag = dv.getFloat32(o + 8), k = dv.getInt16(o + 12) / 100; o += 14;
    let r, g, b;
    if (k <= 66) { r = 255; g = 99.47 * Math.log(k) - 161.12; b = k <= 19 ? 0 : 138.52 * Math.log(k - 10) - 305.04; }
    else { r = 329.70 * Math.pow(k - 60, -0.1332); g = 288.12 * Math.pow(k - 60, -0.0755); b = 255; }
    const c = x => (Math.min(255, Math.max(0, x)) * 0.45 + 255 * 0.55) / 255;
    data.set([Math.cos(dec) * Math.cos(ra), Math.cos(dec) * Math.sin(ra), Math.sin(dec), mag, c(r), c(g), c(b)], i * 7);
  }
  const vao = gl.createVertexArray(), vbo = gl.createBuffer();
  gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER, vbo); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 28, 0);
  gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 4, gl.FLOAT, false, 28, 12);
  gl.bindVertexArray(null);
  G.stars = { vao, n };
}

async function starProgram() {
  const vs = compile(gl.VERTEX_SHADER, HEAD + await src('stars.vert'), 'stars.vert');
  const fs = compile(gl.FRAGMENT_SHADER, HEAD + await src('stars.frag'), 'stars.frag');
  if (!vs || !fs) return null;
  const p = gl.createProgram();
  gl.attachShader(p, vs); gl.attachShader(p, fs); gl.linkProgram(p);
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) { errors.push('stars link: ' + gl.getProgramInfoLog(p)); return null; }
  const uniforms = [];
  const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < n; i++) { const info = gl.getActiveUniform(p, i); uniforms.push({ name: info.name.replace(/\[0\]$/, ''), type: info.type, loc: gl.getUniformLocation(p, info.name) }); }
  return { p, uniforms, label: 'stars', ms: 0, chars: 0 };
}

async function init() {
  const p = G.progs;
  p.noise = await program('noise', ['noise.frag']);
  p.trans = await program('trans', ['common.glsl', 'atmosphere.glsl', 'trans.frag']);
  p.meter = await program('meter', ['common.glsl', 'atmosphere.glsl', 'meter.frag']);
  p.sky = await program('sky', ['common.glsl', 'atmosphere.glsl', 'sky.frag']);
  p.lit = await program('lit', ['common.glsl', 'lit.frag']);
  p.layer = await program('layer', ['common.glsl', 'atmosphere.glsl', 'layer.frag']);
  p.scene = await program('scene', ['common.glsl', 'atmosphere.glsl', 'scene.frag']);
  p.blur = await program('blur', ['common.glsl', 'blur.frag']);
  T.tZero = tex2D(2, 2, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, gl.LINEAR, gl.CLAMP_TO_EDGE);
  { const f = fbo(T.tZero); gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT); }
  for (const b of BIOMES) await loadLand(b.id);
  p.final = await program('final', ['common.glsl', 'atmosphere.glsl', 'final.frag']);
  p.stars = await starProgram();
  await loadStars();
  if (errors.length) { document.getElementById('log').textContent = errors.join('\n'); return false; }

  // The flat noise tile, built once.
  gl.useProgram(p.noise.p);
  T.tNoise2D = tex2D(256, 256, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, gl.LINEAR, gl.REPEAT);
  const f2 = fbo(T.tNoise2D);
  gl.viewport(0, 0, 256, 256);
  gl.uniform1i(gl.getUniformLocation(p.noise.p, 'uMode'), 1);
  gl.uniform1f(gl.getUniformLocation(p.noise.p, 'uSize'), 256);
  draw();
  gl.bindTexture(gl.TEXTURE_2D, T.tNoise2D.t);
  gl.generateMipmap(gl.TEXTURE_2D);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);

  T.tTrans = hdr(256, 64); G.fTrans = fbo(T.tTrans);
  T.tMeter = hdr(10, 1, gl.NEAREST); G.fMeter = fbo(T.tMeter);
  T.tSky = hdr(Math.ceil(W / 8), Math.ceil(H / 8)); G.fSky = fbo(T.tSky);
  T.tStars = ldr(4, 4);
  sizeBuffers();
  return true;
}

function sizeBuffers() {
  const sw = Math.ceil(W * P.sceneScale), sh = Math.ceil(H * P.sceneScale);
  T.tScene = hdr(sw, sh); G.fScene = fbo(T.tScene);
  G.b4 = hdr(Math.ceil(sw / 4), Math.ceil(sh / 4)); G.fB4 = fbo(G.b4);
  G.b8a = hdr(Math.ceil(sw / 8), Math.ceil(sh / 8)); G.fB8a = fbo(G.b8a);
  G.b8b = hdr(Math.ceil(sw / 8), Math.ceil(sh / 8)); G.fB8b = fbo(G.b8b);
  T.tBlur = G.b8a;
  G.sized = [P.cloudScale, P.sceneScale].join();
}

function frame(amort = 16) {
  if (G.sized !== [P.cloudScale, P.sceneScale].join()) sizeBuffers();
  updateUniforms();
  const p = G.progs;
  if (G.dirty.trans) { use(p.trans, G.fTrans); draw(); G.dirty.trans = false; }
  if (G.dirty.light) { use(p.meter, G.fMeter); draw(); use(p.sky, G.fSky); draw(); G.dirty.light = false; }
  const swap = n => n === 'top' ? n : (n[0] === 'l' ? 'r' : 'l') + n.slice(1);
  const lit = (pic, lw, target) => {
    const names = P.rtl ? lw.names.map(swap) : lw.names;
    const gn = P.rtl ? ['gr', 'gl'] : ['gl', 'gr'];
    const glow = smooth(12, 2, G.info.el);
    const sx = n => pic.s[n] || T.tZero;
    use(p.lit, target, { tS0: sx(names[0]), tS1: sx(names[1]), tS2: sx(names[2]), tS3: sx(names[3]), tS4: sx(names[4]), tG0: sx(gn[0]), tG1: sx(gn[1]),
      uLitW: lw.w.slice(0, 4), uLitV: [lw.w[4], lw.glow[0] * glow, lw.glow[1] * glow, P.rtl ? 1 : 0] });
    draw();
  };
  // The land's pictures for this scenery, and its shading under the light of the moment.
  const land = LAND[(BIOMES[P.biome] || BIOMES[0]).id];
  if (land) {
    const m = land.meta;
    u.uLandFrame = [m.width, m.horizon, m.top, m.height];
    u.uLandInfo = [1, m.water || 0, m.sMax, land.m ? 1 : 0];
    T.tLandA = land.a; T.tLandK = land.k; T.tLandG = land.g; T.tLandLit = land.lit; T.tLandM = land.m || T.tZero;
    // (The land's picture is not mirrored here: scene.frag mirrors the lookup. Only the Suns change sides.)
    const rtl = P.rtl; P.rtl = false;
    const lw = Object.assign({}, G.landLight, rtl ? { names: G.landLight.names.map(swap), glow: [G.landLight.glow[1], G.landLight.glow[0]] } : {});
    lit(land, lw, land.fLit);
    P.rtl = rtl;
  } else { u.uLandFrame = [1080, 1056, 0, 1]; u.uLandInfo = [0, 0, 2, 0]; T.tLandA = T.tZero; T.tLandK = T.tZero; T.tLandG = T.tZero; T.tLandLit = T.tZero; T.tLandM = T.tZero; }
  // The three layers of cloud, each in the light of the moment.
  let cover = 0, any = 0;
  [P.skyHigh, P.skyMid, P.skyLow].forEach((id, i) => {
    const S = id ? SKIES[id] : null;
    if (!S) { T['tCloud' + i] = T.tZero; u['uCloudF' + i] = [1080, 1056, 1350, 1080]; return; }
    const m = S.meta;
    lit(S, G.cloudLight, S.fLit);
    gl.bindFramebuffer(gl.FRAMEBUFFER, S.fOut.f); gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    use(p.layer, S.fOut, { tA: S.a, tF: S.f, tK: S.k, tG: S.g, tLit: S.lit, uLay: [m.rMax, m.layer === 'high' ? 2 : m.layer === 'mid' ? 1 : 0, 1, P.rtl ? 1 : 0],
      uLayFrame: [m.viewWidth, m.horizon, m.width, m.height] });
    draw();
    T['tCloud' + i] = S.out; u['uCloudF' + i] = [m.viewWidth, m.horizon, m.width, m.height];
    cover = 1 - (1 - cover) * (1 - m.cover); any = 1;
  });
  u.uCloudD = [P.slideHigh, P.slideMid, P.slideLow, any];
  u.uCover = cover;
  use(p.scene, G.fScene); draw();
  if (u.uNight > 0.05 && G.stars) {
    use(p.stars, G.fScene);
    gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE);
    gl.bindVertexArray(G.stars.vao); gl.drawArrays(gl.POINTS, 0, G.stars.n); gl.bindVertexArray(null);
    gl.disable(gl.BLEND);
  }
  use(p.blur, G.fB4, { tSrc: T.tScene, uMode: 0, uDir: [1 / T.tScene.w, 1 / T.tScene.h] }); draw();
  use(p.blur, G.fB8a, { tSrc: G.b4, uMode: 0, uDir: [1 / G.b4.w, 1 / G.b4.h] }); draw();
  use(p.blur, G.fB8b, { tSrc: G.b8a, uMode: 1, uDir: [1.6 / G.b8a.w, 0] }); draw();
  use(p.blur, G.fB8a, { tSrc: G.b8b, uMode: 1, uDir: [0, 1.6 / G.b8a.h] }); draw();
  use(p.final, null); draw();
  G.frame++;
}

// ---- Console API -------------------------------------------------------------------------------------------------

const bench = {
  P, G, T, gl, errors,
  set(o) {
    if (o.biome !== undefined && typeof o.biome === 'string') o.biome = BIOMES.findIndex(b => b.id === o.biome);
    if (o.biome !== undefined && o.biome !== P.biome) { const b = BIOMES[o.biome]; Object.assign(P, { waterRough: b.water[0], waterClear: b.water[1] }); }
    Object.assign(P, o); return bench;
  },
  frame,
  // Draws until every cloud pixel has been redone for the current state.
  settle() { frame(); frame(); gl.finish(); return bench; },
  time(n = 30, amort = 16) { gl.finish(); const t0 = performance.now(); for (let i = 0; i < n; i++) frame(amort); gl.finish(); return (performance.now() - t0) / n; },
  async save(name, scale = 1) {
    const c = document.createElement('canvas');
    c.width = Math.round(W * scale); c.height = Math.round(H * scale);
    c.getContext('2d').drawImage(canvas, 0, 0, c.width, c.height);
    const data = c.toDataURL(name.endsWith('.jpg') ? 'image/jpeg' : 'image/png', 0.9);
    await fetch('/save/' + name, { method: 'POST', body: data });
    return name;
  },
  // A contact sheet: each entry is a set of overrides; tiles are drawn at `scale` of the full size.
  async sheet(name, list, cols = 4, scale = 0.33, crop = 1) {
    const tw = Math.round(W * scale), th = Math.round(H * scale * crop);
    const rows = Math.ceil(list.length / cols);
    const c = document.createElement('canvas');
    c.width = tw * cols; c.height = th * rows;
    const g = c.getContext('2d');
    list.forEach((o, i) => {
      bench.set(o).settle();
      g.drawImage(canvas, 0, 0, W, H * crop, (i % cols) * tw, Math.floor(i / cols) * th, tw, th);
    });
    await fetch('/save/' + name, { method: 'POST', body: c.toDataURL(name.endsWith('.jpg') ? 'image/jpeg' : 'image/png', 0.9) });
    return name;
  },
  // A cropped picture round the horizon (the scenery cards in Settings): saved at w x h.
  async thumb(name, w = 560, h = 420, above = 0.62) {
    const hy = P.horizon * H;
    const sh = W * h / w, sy = Math.max(0, hy - sh * above);
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    c.getContext('2d').drawImage(canvas, 0, sy, W, sh, 0, 0, w, h);
    const mime = name.endsWith('.webp') ? 'image/webp' : name.endsWith('.jpg') ? 'image/jpeg' : 'image/png';
    await fetch('/save/' + name, { method: 'POST', body: c.toDataURL(mime, 0.86) });
    return name;
  },
  compileTimes() { return Object.values(G.progs).map(p => p && `${p.label}: ${p.ms.toFixed(0)} ms, ${p.chars} chars`).join('\n'); },
};
bench.loadSky = loadSky; bench.SKIES = SKIES; bench.LAND = LAND;
window.bench = bench;

init().then(ok => {
  window.benchReady = ok;
  if (ok) { bench.settle(); document.getElementById('log').textContent = 'ready\n' + bench.compileTimes(); }
});
