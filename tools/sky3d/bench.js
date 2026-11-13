// The design bench for Noor's real sky: runs the very shader files the phone runs (app/src/main/assets/sky) in WebGL 2,
// with the same passes in the same order as the Android renderer (RealSkyRenderer.kt). Serve the repository root with
// tools/sky3d/serve.py and open /tools/sky3d/index.html. Drive it from the console: bench.set({...}), bench.settle(),
// bench.save('name.png'), bench.sheet('name.png', [...]).

const DIR = '/app/src/main/assets/sky/';
const q = new URLSearchParams(location.search);
const W = +(q.get('w') || 1080), H = +(q.get('h') || 2340);
const canvas = document.getElementById('c');
canvas.width = W; canvas.height = H;
const gl = canvas.getContext('webgl2', { antialias: false, alpha: false, preserveDrawingBuffer: true });
gl.getExtension('EXT_color_buffer_float'); gl.getExtension('EXT_color_buffer_half_float'); gl.getExtension('OES_texture_float_linear');
const errors = [];
const HEAD = '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler3D;\n';
const INT_UNIFORMS = new Set(['uFrame', 'uAmort', 'uMode', 'uBiome']);

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
  cover: 0.42, kind: 0.42, base: 1.4, density: 30, mid: 0.0, midForm: 0.3, cluster: 0.5, anvil: 1.0,
  cirrus: 0.35, veil: 0.0, showers: 0.0, seed: 3.0, haze: 1.0, time: 100, windLow: 0.012, windMid: 0.02,
  biome: 0, season: 0.3, snow: 0.0, wet: 0.0, mist: 0.0, rain: 0.0, windForce: 0.3, dust: 0.0,
  aurora: 0.0, flash: 0.0, rainbow: 0.0, waterRough: 0.3, waterClear: 0.2,
  focal: 1.15, eye: 62.8, horizon: 911 / 2340, arcTop: 596 / 2340, ground: 1110 / 2340, textBottom: 554 / 2340, panelTop: 1126 / 2340,
  panelTint: 0.5, light: 0, cloudScale: 0.5, sceneScale: 1.0, steps: 1.0, text: 1, panel: 1,
};
// The sceneries: where the camera stands in each (m above the datum), its lens, and the water's character.
const BIOMES = [
  { id: 'dunes', eye: 62.8, focal: 1.15, water: [0.3, 0.2] },
  { id: 'pnw', eye: 28.0, focal: 1.15, water: [0.10, 0.15] },
  { id: 'arctic', eye: 4.2, focal: 1.15, water: [0.08, 0.10] },
  { id: 'ocean', eye: 9.0, focal: 1.15, water: [0.55, 0.25] },
  { id: 'savanna', eye: 2.3, focal: 1.15, water: [0.3, 0.2] },
  { id: 'canyon', eye: 3.0, focal: 1.15, water: [0.3, 0.2] },
];
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
function pitch() { return Math.atan((P.horizon * H - 0.5 * H) / (P.focal * W)); }
function viewRay(px, py) {
  let x = px - 0.5 * W; if (P.rtl) x = -x;
  const d = norm([x, 0.5 * H - py, P.focal * W]);
  const c = Math.cos(pitch()), s = Math.sin(pitch());
  return [d[0], d[1] * c + d[2] * s, d[2] * c - d[1] * s];
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
  const sunLightEl = el <= 0 ? el : Math.max(drawnEl, Math.min(el, 50));
  let light = dirAzEl(az, sunLightEl * DEG);
  if (el < -6) light = moonVis > 0 ? moonDir : [0, 1, 0];
  Object.assign(u, {
    uView: [W, H], uTime: P.time, uFrame: G.frame,
    uCam: [P.focal * W, P.horizon * H, P.eye, P.rtl ? 1 : 0],
    uGapAz: Math.atan(0.3024 / P.focal), uWater: [P.waterRough, P.waterClear, 0, 0],
    uSunDir: drawn, uLightDir: light, uSunTrue: dirAzEl(az, el * DEG), uSunEl: el,
    uMoon: [...moonDir, illum], uMoonLit: [lx * Math.sin(pa), ly * Math.sin(pa), Math.cos(pa), moonVis],
    uNight: night, uHaze: P.haze,
    uCloudA: [P.cover, P.kind, P.base, P.density], uCloudB: [P.mid, P.midForm, P.cluster, P.anvil],
    uCloudC: [P.seed, P.cirrus, P.veil, P.showers],
    uWind: [P.time * P.windLow + P.tod * 24 * 9, P.time * P.windLow * 0.25, P.time * P.windMid + P.tod * 24 * 14, P.time * P.windMid * 0.2],
    uCloudRes: [T.tCloudA.w, T.tCloudA.h], uSteps: P.steps,
    uSky: [P.aurora, 0, P.flash, P.rainbow], uLand: [P.biome, P.season, P.snow, P.wet], uAir: [P.mist, P.rain, P.windForce, P.dust],
    uTouch: [0, 0, 1e3, 0], uBiome: P.biome,
    uText: [P.text ? P.textBottom * H : 0, 0, 0, 0],
    uPanel: [P.panel ? P.panelTop * H : H * 2, 30 * 2.625, P.panelTint, P.light],
    uPanelTint: P.light ? [0.965, 0.955, 0.935] : [0.027, 0.039, 0.078], uFade: 1,
    uCelX: [1, 0, 0], uCelY: [0, 0.42, 0.9], uCelZ: [0, 0.9, -0.42], uGalN: norm([0.5, 0.3, -0.6]), uGalC: norm([0.35, 0.45, 0.82]),
  });
  const key = [el.toFixed(3), sx.toFixed(1), sy.toFixed(1), P.haze, P.hijriDay, P.rtl, P.focal, P.horizon].join();
  if (key !== G.lastLightKey) { G.lastLightKey = key; G.dirty.light = true; }
  if (P.haze !== G.lastHaze) { G.lastHaze = P.haze; G.dirty.trans = true; G.dirty.light = true; }
  G.info = { el, sx, sy, drawnEl, night, mel, illum };
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

async function init() {
  const p = G.progs;
  p.noise = await program('noise', ['noise.frag']);
  p.trans = await program('trans', ['common.glsl', 'atmosphere.glsl', 'trans.frag']);
  p.meter = await program('meter', ['common.glsl', 'atmosphere.glsl', 'meter.frag']);
  p.sky = await program('sky', ['common.glsl', 'atmosphere.glsl', 'sky.frag']);
  p.cloud = await program('cloud', ['common.glsl', 'atmosphere.glsl', 'clouds.glsl', 'cloud.frag']);
  p.scene = await program('scene', ['common.glsl', 'atmosphere.glsl', 'clouds.glsl', 'scene.frag']);
  p.blur = await program('blur', ['common.glsl', 'blur.frag']);
  for (const b of BIOMES) p['g_' + b.id] = await program('g_' + b.id, ['common.glsl', 'land.glsl', 'land_' + b.id + '.glsl', 'gbuffer.frag']);
  p.final = await program('final', ['common.glsl', 'atmosphere.glsl', 'final.frag']);
  if (errors.length) { document.getElementById('log').textContent = errors.join('\n'); return false; }

  // The cloud volume and the flat noise tile, built once.
  const N = 128;
  const n3 = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_3D, n3);
  gl.texImage3D(gl.TEXTURE_3D, 0, gl.RGBA8, N, N, N, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
  const f3 = gl.createFramebuffer();
  gl.useProgram(p.noise.p);
  for (let z = 0; z < N; z++) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, f3);
    gl.framebufferTextureLayer(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, n3, 0, z);
    gl.viewport(0, 0, N, N);
    gl.uniform1i(gl.getUniformLocation(p.noise.p, 'uMode'), 0);
    gl.uniform1f(gl.getUniformLocation(p.noise.p, 'uSlice'), (z + 0.5) / N);
    gl.uniform1f(gl.getUniformLocation(p.noise.p, 'uSize'), N);
    draw();
  }
  gl.bindTexture(gl.TEXTURE_3D, n3);
  gl.generateMipmap(gl.TEXTURE_3D);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  for (const a of [gl.TEXTURE_WRAP_S, gl.TEXTURE_WRAP_T, gl.TEXTURE_WRAP_R]) gl.texParameteri(gl.TEXTURE_3D, a, gl.REPEAT);
  T.tNoise3D = { t: n3, target: gl.TEXTURE_3D };

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
  T.tMeter = hdr(8, 1, gl.NEAREST); G.fMeter = fbo(T.tMeter);
  T.tSky = hdr(Math.ceil(W / 8), Math.ceil(H / 8)); G.fSky = fbo(T.tSky);
  T.tStars = ldr(4, 4);
  sizeBuffers();
  return true;
}

function sizeBuffers() {
  const cw = Math.ceil(W * P.cloudScale), chh = Math.ceil(H * P.cloudScale);
  T.tCloudA = hdr(cw, chh); T.tCloudB = hdr(cw, chh); G.fCloud = fbo(T.tCloudA, T.tCloudB);
  gl.clearColor(0, 0, 0, 1); gl.clear(gl.COLOR_BUFFER_BIT);
  const sw = Math.ceil(W * P.sceneScale), sh = Math.ceil(H * P.sceneScale);
  T.tScene = hdr(sw, sh); G.fScene = fbo(T.tScene);
  T.tG0 = ldr(sw, sh, gl.NEAREST); T.tG1 = ldr(sw, sh, gl.NEAREST); T.tG2 = ldr(sw, sh, gl.NEAREST);
  G.fG = fbo(T.tG0, T.tG1, T.tG2);
  G.dirty.gbuf = true;
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
  const landKey = [P.biome, P.season, P.snow, P.focal, P.eye, P.horizon, P.rtl].join();
  if (G.dirty.gbuf || landKey !== G.landKey) {
    G.landKey = landKey; G.dirty.gbuf = false;
    const b = BIOMES[P.biome] || BIOMES[0];
    const t0 = performance.now();
    use(p['g_' + b.id], G.fG, { uTile: [0, G.fG.h, 0, 0] });
    gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    draw(); gl.finish();
    G.gbufMs = performance.now() - t0;
  }
  use(p.cloud, G.fCloud, { uAmort: amort }); draw();
  use(p.scene, G.fScene); draw();
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
    if (o.biome !== undefined && o.biome !== P.biome) { const b = BIOMES[o.biome]; Object.assign(P, { eye: b.eye, focal: b.focal, waterRough: b.water[0], waterClear: b.water[1] }); }
    Object.assign(P, o); return bench;
  },
  frame,
  // Draws until every cloud pixel has been redone for the current state.
  settle() { for (let i = 0; i < 16; i++) frame(16); gl.finish(); return bench; },
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
window.bench = bench;

init().then(ok => {
  window.benchReady = ok;
  if (ok) { bench.settle(); document.getElementById('log').textContent = 'ready\n' + bench.compileTimes(); }
});
