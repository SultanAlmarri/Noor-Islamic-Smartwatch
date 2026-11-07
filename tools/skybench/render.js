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

  function draw(canvas, fs, uniforms, lut, ridge) {
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
    for (const [k, v] of Object.entries(uniforms)) {
      const l = gl.getUniformLocation(pr, k); if (l === null) continue;
      const a = Array.isArray(v) ? v : [v];
      [gl.uniform1f, gl.uniform2f, gl.uniform3f, gl.uniform4f][a.length - 1].call(gl, l, ...a);
    }
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    gl.finish();
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
    const yOf = (e) => e >= 0 ? hy - (e / maxEl) * (hy - arcTop) : hy + (-e / -minEl) * (groundY - hy);
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
    const sunDisc = fr.sunGround.map(x => x * 2.2);
    const nightAmb = [0.010, 0.017, 0.045].map(x => x * night * 1.6);
    const wind = (cfg.time || 0) * 0.02;
    const common = {
      uFov: fov, uLutSize: [nx, ny], uLutPow: lutPow, uSunEl: el, uSunGround: fr.sunGround,
      uAmbTop: fr.ambTop.map((x, i) => x + nightAmb[i]), uAmbLow: fr.ambHor.map((x, i) => x + nightAmb[i] * 1.5),
      uNight: night, uTime: cfg.time || 0, uWeather: weather, uWind: wind,
    };
    const skyU = Object.assign({}, common, {
      uSize: [SW, SH], uDp: dp * s, uHorizon: hy * s, uAltTop: altTop, uAltPow: altPow,
      uSunPos: [sunX * s, sunY * s], uSunDir: sd, uSunDisc: sunDisc, uSunCloud: fr.sunCloud, uSunHigh: fr.sunHigh,
      uCloudTop: (arcTop - 8 * dp) * s, uMoon: [mx * s, my * s, 10 * dp * s, moonVis], uMoonLight: moonLight,
      uMilky: smooth(0.55, 1, night), uTextBottom: (arcTop - 16 * dp) * s,
    });
    // Land geometry.
    const dunes = !!cfg.dunes;
    const layers = dunes ? [[16, 3], [20, 16], [24, 32], [26, 50], [26, 72]] : [[40, 3], [28, 16], [24, 32], [22, 50], [24, 72]];
    const dists = dunes ? [22, 7, 2.8, 1.1, 0.35] : [48, 15, 6, 2.2, 0.6];
    const deepest = 72;
    const spread = Math.max(0.55, Math.min(1.4, ((cfg.hDp - cfg.horizonDp) - 12) / deepest));
    const base = layers.map(l => hy + l[1] * spread * dp), amp = layers.map(l => l[0] * dp);
    const N = 1024;
    const rid = Scene.ridges(cfg.landSeed || 7, dunes, N);
    const rdata = new Uint8Array(N * 2 * 4);
    for (let i = 0; i < N; i++) { rdata[i * 4 + 3] = 255; rdata[(N + i) * 4 + 3] = 255; for (let l = 0; l < 5; l++) {
      const row = l < 3 ? 0 : 1, ch = l < 3 ? l : l - 3;
      rdata[(row * N + i) * 4 + ch] = Math.round(255 * rid[l][i]);
    } }
    const page = cfg.dark === false ? [0.957, 0.945, 0.918] : [0.031, 0.047, 0.078];
    const morning = f < dd.noonF;
    const nightish = night;
    const mist = morning ? 0.85 * smooth(0, 1, 1 - Math.abs(el + 1) / 13) + 0.18 * nightish : 0.22 * nightish + 0.12 * smooth(0, 1, 1 - Math.abs(el - 1) / 10);
    const landU = Object.assign({}, common, {
      uSize: [W, H], uDp: dp, uHorizon: hy, uBaseA: base.slice(0, 4), uBaseB: base[4], uAmpA: amp.slice(0, 4), uAmpB: amp[4],
      uDistA: dists.slice(0, 4), uDistB: dists[4], uRidgeN: N, uDunes: dunes ? 1 : 0, uSunPos: [sunX, sunY],
      uPage: page, uMist: mist, uSeed: (cfg.landSeed || 7) % 97,
    });
    const lut = { w: nx, h: ny, data: new Uint8Array(fr.lut.buffer) };
    const ridge = { w: N, h: 2, data: rdata };

    const skyC = document.createElement('canvas'); skyC.width = SW; skyC.height = SH;
    const t0 = performance.now();
    draw(skyC, PRELUDE + src.common + src.sky + FOOTER, skyU, lut, ridge);
    const t1 = performance.now();
    const landC = document.createElement('canvas'); landC.width = W; landC.height = H;
    draw(landC, PRELUDE + src.common + src.land + FOOTER, landU, lut, ridge);
    const t2 = performance.now();

    const out = document.createElement('canvas'); out.width = W; out.height = H + (cfg.pageBelowDp || 0) * dp;
    const g = out.getContext('2d');
    g.fillStyle = `rgb(${page.map(x => Math.round(x * 255)).join(',')})`; g.fillRect(0, 0, out.width, out.height);
    g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high';
    const fade = cfg.fade ?? 1, pathA = cfg.pathAlpha ?? 1, marksA = cfg.marksAlpha ?? 1, lift = (cfg.landLift ?? 0) * dp;
    g.save(); g.beginPath(); g.rect(0, 0, W, H); g.clip();
    g.globalAlpha = fade;
    g.drawImage(skyC, 0, 0, W, H);
    // Sun path above the horizon (behind the land): dashed, gold where the day has passed.
    g.save(); g.beginPath(); g.rect(0, 0, W, hy); g.clip();
    const path = new Path2D(); dd.samples.forEach(([fq, e], i) => { const x = xOf(fq), y = yOf(e); i ? path.lineTo(x, y) : path.moveTo(x, y); });
    g.setLineDash([2 * dp, 5 * dp]); g.lineCap = 'round'; g.lineWidth = 1.3 * dp; g.strokeStyle = `rgba(255,255,255,${0.5 * pathA})`; g.stroke(path);
    g.setLineDash([]);
    g.save(); g.beginPath(); g.rect(0, 0, sunX, hy); g.clip();
    g.lineWidth = 6.4 * dp; g.strokeStyle = 'rgba(255,213,140,0.20)'; g.stroke(path);
    g.lineWidth = 1.6 * dp; g.strokeStyle = 'rgba(255,237,194,0.95)'; g.stroke(path);
    g.restore(); g.restore();
    g.drawImage(landC, 0, lift);
    // Prayer marks: in the sky, or on the skyline when the Sun is down.
    const skyline = (x) => { let m = 1e9; for (let l = 0; l < 5; l++) { const h = rid[l][Math.round(Math.max(0, Math.min(1, x / W)) * (N - 1))]; m = Math.min(m, base[l] - amp[l] * h); } return m; };
    const next = cfg.next || 'ASR';
    g.globalAlpha = fade * marksA;
    for (const [p, fq] of Object.entries(dd.ev)) {
      const x = xOf(fq), e = dd.elAt(fq), sy = yOf(e), onLand = Math.min(skyline(x), hy) - 2 * dp;
      const y = e > 0.5 && sy < onLand ? sy : onLand;
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
    return { url: out.toDataURL('image/png'), ms: [t1 - t0, t2 - t1], el, night, exposure: fr.exposure };
  };
  function smooth(a, b, x) { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
})();
