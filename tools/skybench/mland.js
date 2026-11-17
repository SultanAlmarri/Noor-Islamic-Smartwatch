// Port of app/src/main/java/com/noor/app/ui/today/PictureLand.kt (0.20 candidate): the medium graphics' land drawn
// from the real sky's own baked land pictures (app/src/main/assets/sky2/land/<id>), lit for the moment with the medium
// sky's light, with still water mirroring the sky and the land. Keep in step with the Kotlin.
(function (root) {
  const DEG = Math.PI / 180;
  const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  const SCALE = 2;          // the pictures are read at half their size
  const FRAME_W = 1080, FRAME_H0 = 1056;   // the real sky's frame: its width and its horizon row (land.json)
  const ROWS_BELOW = 440;   // how far below the horizon (frame px) the medium header can reach
  const lin = (x) => x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
  const LIN8 = Array.from({ length: 256 }, (_, i) => Math.pow(i / 255, 2.2));

  // The pictures of one scenery, the band the medium header shows, at half size. imgs: {name: HTMLImageElement}
  function load(id, meta, imgs) {
    const top = meta.top, rows = Math.min(meta.height, FRAME_H0 + ROWS_BELOW - top);
    const W = Math.ceil(meta.width / SCALE), H = Math.ceil(rows / SCALE);
    const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    const g = cv.getContext('2d', { willReadFrequently: true });
    g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'medium';
    // The band, at W × H. (Every picture covers the whole land picture, whatever its own size: the real sky samples
    // them all by the same coordinates. The sea's mask is not of the picture's proportions.)
    const read = (im) => {
      g.clearRect(0, 0, W, H);
      g.drawImage(im, 0, 0, im.width, rows * im.height / meta.height, 0, 0, W, H);
      return g.getImageData(0, 0, W, H).data;
    };
    const ch = { W, H, top, rows, meta, id, suns: {} };
    const a = read(imgs.a);
    ch.alb = new Uint8Array(W * H * 3); ch.alpha = new Uint8Array(W * H);
    for (let i = 0; i < W * H; i++) { ch.alb[i * 3] = a[i * 4]; ch.alb[i * 3 + 1] = a[i * 4 + 1]; ch.alb[i * 3 + 2] = a[i * 4 + 2]; ch.alpha[i] = a[i * 4 + 3]; }
    const k = read(imgs.k);
    ch.k = new Uint8Array(W * H * 3); for (let i = 0; i < W * H; i++) { ch.k[i * 3] = k[i * 4]; ch.k[i * 3 + 1] = k[i * 4 + 1]; ch.k[i * 3 + 2] = k[i * 4 + 2]; }
    // Distance (m): 16 bits, log (scene.frag landDist). Read without smoothing (it would mix the two bytes).
    g.imageSmoothingEnabled = false;
    const gd = read(imgs.g);
    g.imageSmoothingEnabled = true;
    ch.dist = new Float32Array(W * H);
    for (let i = 0; i < W * H; i++) ch.dist[i] = Math.pow(2, (gd[i * 4] * 256 + gd[i * 4 + 1]) / 65535 * 18);
    // Water: the mask's red where the scenery has a mask (the lake, the fjord, the pool); on the open sea (no mask), where
    // the ground the picture shows lies under the sea's level (`water`: metres under the eye), as the real sky's beach.
    if (imgs.m && meta.mask) { const m = read(imgs.m); ch.water = new Uint8Array(W * H); for (let i = 0; i < W * H; i++) ch.water[i] = m[i * 4]; }
    else if (meta.water) {
      ch.water = new Uint8Array(W * H);
      const F = 1.15 * meta.width;
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
        const i = y * W + x;
        const rx = (x + 0.5) * SCALE - meta.width / 2, ry = FRAME_H0 - (top + (y + 0.5) * SCALE), l = Math.hypot(rx, ry, F);
        const zb = meta.water + ry / l * ch.dist[i];          // the ground's height over the sea's level (m)
        ch.water[i] = Math.round(255 * smooth(0.12, -0.05, zb));
      }
    }
    for (const [n, im] of Object.entries(imgs)) if (n.startsWith('s_')) { const s = read(im); const c = new Uint8Array(W * H); for (let i = 0; i < W * H; i++) c[i] = s[i * 4 + 1]; ch.suns[n.slice(2)] = c; }
    return ch;
  }

  // Lights the land for the moment. L: { el, side, moonLight, moonEl, glow, moonW, moon[3], sunGround, ambTop, ambLow,
  // glowCol, night, shade, overcast, haze, fog, sky(xPx, altDeg) -> display rgb, sunX, sunY, dp, page[3], lightK, ambK }
  // V: { W, hy, H, rtl, altAt(y) }. Returns { image, x0, y0, x1, y1 } (px of the view).
  function compose(P, L, V) {
    const { W: w, H: h, meta } = P;
    const k = V.W / FRAME_W;                     // view px per frame px
    const F = 1.15 * V.W;
    const eye = (meta.eye || [0, 0, 2])[2];
    const sMax = meta.sMax || 2;
    const m = V.rtl ? -1 : 1;
    const lw = MSets.weights(L.moonLight ? L.moonEl : Math.max(L.el, 0.7), L.side * m, false, L.glow);
    const pics = []; for (let i = 0; i < 5; i++) if (lw.w[i] > 0.002 && P.suns[lw.names[i]]) pics.push([P.suns[lw.names[i]], lw.w[i] * sMax]);
    const glows = []; if (lw.glow[0] > 0.002 && P.suns.gl) glows.push([P.suns.gl, lw.glow[0] * sMax]); if (lw.glow[1] > 0.002 && P.suns.gr) glows.push([P.suns.gr, lw.glow[1] * sMax]);
    const moonW = L.moonW;
    const light = L.sunGround.map((x, c) => (x * (0.70 + 0.14 * smooth(6, 28, L.el)) * (1 - moonW) + L.moon[c] * moonW) * L.lightK);
    const night = L.night;
    let amb = L.ambLow.map((x, c) => (x + (L.ambTop[c] - x) * 0.6) * L.ambK + [0.034, 0.052, 0.105][c] * night * 0.35);
    const la = 0.2126 * amb[0] + 0.7152 * amb[1] + 0.0722 * amb[2];
    amb = amb.map(x => x + (la * 2.3 - x) * L.overcast * 0.6);
    const sh = 1 - 0.85 * L.shade;
    const shadeK = 0.78 + 0.22 * Math.max(night, smooth(10, 0, L.el));
    const hour = smooth(10, 2.5, L.el) * smooth(-8, -2.5, L.el);
    const lift = 1 + 0.3 * hour;
    const out = new ImageData(w, h); const o = out.data;
    const lit = new Float32Array(w * h * 3); const isLand = new Uint8Array(w * h); const wet = new Float32Array(w * h);
    const tmp = [0, 0, 0], disp = [0, 0, 0];
    const HH = 1.3, HM = 0.07;
    const ya = eye * 0.001;
    const sd = L.sunDir;
    // The air's colour at the horizon at each column (linear), as the real sky takes it.
    const hazeAt = new Float32Array(w * 3);
    for (let ox = 0; ox < w; ox++) {
      const xs = V.rtl ? V.W - (ox + 0.5) * SCALE * k : (ox + 0.5) * SCALE * k;
      const a = L.sky(xs, 0.8), b = L.sky(xs, 4.5);
      for (let c = 0; c < 3; c++) hazeAt[ox * 3 + c] = lin(a[c]) * 0.4 + lin(b[c]) * 0.6;
    }
    for (let oy = 0; oy < h; oy++) {
      const fy = P.top + (oy + 0.5) * SCALE;          // frame row
      const sy = V.hy + (fy - FRAME_H0) * k;            // view y
      if (sy > V.H + 2) break;
      for (let ox = 0; ox < w; ox++) {
        const i = oy * w + ox;
        const al = P.alpha[i] / 255;
        if (al < 0.004) continue;
        const xs = (ox + 0.5) * SCALE * k;              // unmirrored view x
        const rx = xs - V.W / 2, ry = V.hy - sy, rl = Math.hypot(rx, ry, F);
        const rd = [rx / rl, ry / rl, F / rl];
        let S = 0; for (const [p, wt] of pics) { const v = p[i] / 255; S += v * v * wt; }
        let G = 0; for (const [p, wt] of glows) { const v = p[i] / 255; G += v * v * wt; }
        const t = P.dist[i];
        const tk = t * 0.001;
        const yb = Math.max(eye + rd[1] * t, 0) * 0.001;
        const layer = Math.abs(yb - ya) > 1e-3 ? HH / (yb - ya) * (Math.exp(-ya / HH) - Math.exp(-yb / HH)) : Math.exp(-ya / HH);
        const low = Math.abs(yb - ya) > 1e-3 ? HM / (yb - ya) * (Math.exp(-ya / HM) - Math.exp(-yb / HM)) : Math.exp(-ya / HM);
        const fog = Math.min(1 - Math.exp(-tk * (0.006 + (0.022 + 0.024 * (L.haze - 1)) * layer + 0.5 * L.fog * low)), 0.94) * 0.95;
        const glowS = Math.pow(Math.max(0, rd[0] * m * sd[0] + rd[1] * sd[1] + rd[2] * sd[2]), 8);
        const hx = V.rtl ? w - 1 - ox : ox;
        for (let c = 0; c < 3; c++) {
          const alb = LIN8[P.alb[i * 3 + c]], K = LIN8[P.k[i * 3 + c]];
          let v = (alb * (S * light[c] * sh + G * L.glowCol[c]) + K * amb[c] * shadeK) * lift;
          const air = (hazeAt[hx * 3 + c] * (1 + 0.35 * glowS) * 0.74 + amb[c] * 0.26) * 0.93;
          tmp[c] = v + (air - v) * fog;
        }
        Atmo.tone(tmp, disp);
        const oxs = V.rtl ? w - 1 - ox : ox;
        const j = oy * w + oxs;
        lit[j * 3] = disp[0]; lit[j * 3 + 1] = disp[1]; lit[j * 3 + 2] = disp[2];
        isLand[j] = P.water && P.water[i] > 20 ? 2 : 1;
        wet[j] = P.water ? P.water[i] / 255 : 0;
        o[j * 4 + 3] = Math.round(255 * al);
      }
    }
    // Still water: the sky and the land beyond it upside down, about the water's far edge in each column; darker and
    // bluer than what it mirrors, more so where it is seen from above (a soft Fresnel); the Sun's light on it.
    // (The water's own colour: the light of the sky scattered back out of it, blue-green, dark.)
    const deep = Atmo.tone(L.ambTop.map((x, c) => x * 0.10 * L.ambK + L.sunGround[c] * 0.012), [0, 0, 0]).map((x, c) => x * [0.55, 0.85, 0.95][c]);
    for (let ox = 0; ox < w; ox++) {
      let edge = -1;
      for (let oy = 0; oy < h; oy++) {
        const j = oy * w + ox;
        if (isLand[j] === 2) {
          if (edge < 0 && wet[j] > 0.5) edge = oy;
          if (edge < 0) continue;
          const fy = P.top + (oy + 0.5) * SCALE, sy = V.hy + (fy - FRAME_H0) * k;
          if (sy > V.H + 2) break;
          const my = 2 * edge - oy - 1;
          let refl;
          if (edge < 0 || wet[oy * w + ox] < 0.5) { /* (wet sand before the water's edge: mirrors only a little, below) */ }
          if (my >= 0 && isLand[my * w + ox] === 1 && o[(my * w + ox) * 4 + 3] > 127) refl = [lit[(my * w + ox) * 3], lit[(my * w + ox) * 3 + 1], lit[(my * w + ox) * 3 + 2]];
          else {
            const yEdge = V.hy + (P.top + edge * SCALE - FRAME_H0) * k;
            const xs = (ox + 0.5) * SCALE * k;
            refl = L.sky(xs, V.altAt(Math.min(2 * yEdge - sy, V.hy - 0.5)));
          }
          const dep = Math.atan((sy - V.hy) / F);
          const fres = 0.02 + 0.98 * Math.pow(1 - Math.sin(Math.max(dep, 0.002)), 5);
          const mixK = Math.max(0, Math.min(1, 0.30 + 0.70 * fres)) * 0.82;
          let c3 = [0, 1, 2].map(c => deep[c] + (refl[c] * [0.86, 0.93, 0.97][c] - deep[c]) * mixK);
          // The Sun's (or the moon's) light on the water: a soft glade under it, finer towards the horizon.
          if (L.glade > 0) {
            const xs = (V.rtl ? w - 1 - ox + 0.5 : ox + 0.5) * SCALE * k;
            const width = (5 + (sy - V.hy) / L.dp * 0.6) * L.dp;
            const lane = Math.exp(-Math.pow((xs - L.sunX) / width, 2));
            c3 = c3.map((x, c) => 1 - (1 - x) * (1 - L.gladeCol[c] * lane * L.glade));
          }
          // Under water (the mask's red: 1), the mirror; where the sand is only wet, a little of it.
          const sea = smooth(0.80, 0.98, wet[oy * w + ox]), film = 0.55 * smooth(0.08, 0.8, wet[oy * w + ox]);
          const q = Math.max(sea, film);
          const j3 = (oy * w + ox) * 3;
          for (let c = 0; c < 3; c++) lit[j3 + c] = lit[j3 + c] + (c3[c] - lit[j3 + c]) * q;
          o[(oy * w + ox) * 4 + 3] = 255;
        }
      }
    }
    // The nearest land fades into the page at the foot of the header (mixed in linear light).
    for (let oy = 0; oy < h; oy++) {
      const fy = P.top + (oy + 0.5) * SCALE, sy = V.hy + (fy - FRAME_H0) * k;
      const f = smooth(V.H - 22 * L.dp, V.H - 1 * L.dp, sy);
      for (let ox = 0; ox < w; ox++) {
        const j = oy * w + ox;
        if (o[j * 4 + 3] === 0) continue;
        for (let c = 0; c < 3; c++) {
          const v = f > 0 ? Atmo.srgb(lin(lit[j * 3 + c]) * (1 - f) + lin(L.page[c]) * f) : lit[j * 3 + c];
          o[j * 4 + c] = Math.round(255 * Math.max(0, Math.min(1, v)));
        }
      }
    }
    return { image: out, x0: 0, x1: V.W, y0: V.hy + (P.top - FRAME_H0) * k, y1: V.hy + (P.top + h * SCALE - FRAME_H0) * k };
  }

  root.MLand = { load, compose, SCALE };
})(typeof window !== 'undefined' ? window : globalThis);
