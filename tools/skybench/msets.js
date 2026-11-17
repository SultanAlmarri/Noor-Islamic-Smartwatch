// Port of app/src/main/java/com/noor/app/ui/today/SetClouds.kt (0.19.1): the medium graphics' clouds are the real
// sky's baked sets (app/src/main/assets/sky2/clouds/<id>), lit for the moment into one picture per set and laid over
// the sky. Keep in step with the Kotlin (the same numbers in the same order).
(function (root) {
  const DEG = Math.PI / 180;
  const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  const PITCH = 64;
  const SCALE = 2;   // the pictures are read at half their size (inSampleSize 2)

  // LightWeights (RealSkyFrame.kt).
  const SUN_EL = [0.7, 2.5, 5.0, 9.0, 14.0, 22.0, 34.0, 50.0], UNDER_EL = [-1.5, -4.0];
  function weights(el, side, under, glow) {
    const wr = smooth(-1, 1, side), wl = 1 - wr;
    let a, b, f, top = 0;
    if (el >= SUN_EL[0] || !under) {
      const e = Math.max(SUN_EL[0], Math.min(SUN_EL[SUN_EL.length - 1], el));
      let i = 0; while (i < SUN_EL.length - 2 && e > SUN_EL[i + 1]) i++;
      f = (e - SUN_EL[i]) / (SUN_EL[i + 1] - SUN_EL[i]); a = '' + i; b = '' + (i + 1);
      top = 4 * wl * wr * smooth(24, 46, e) * 0.85;
    } else if (el >= UNDER_EL[0]) { f = (el - UNDER_EL[0]) / (SUN_EL[0] - UNDER_EL[0]); a = 'u0'; b = '0'; }
    else { const e = Math.max(el, UNDER_EL[1]); f = (e - UNDER_EL[1]) / (UNDER_EL[0] - UNDER_EL[1]); a = 'u1'; b = 'u0'; }
    return { names: ['l' + a, 'l' + b, 'r' + a, 'r' + b, 'top'], w: [wl * (1 - top) * (1 - f), wl * (1 - top) * f, wr * (1 - top) * (1 - f), wr * (1 - top) * f, top], glow: [wl * glow, wr * glow] };
  }

  // Smooth noise for the ragged ends of a set (where it gives way to the next).
  function hash2(x, y) { let h = (Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263)) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return (h >>> 8) / 16777216; }
  function vnoise(x, y) {
    const ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
    const ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
    const a = hash2(ix, iy), b = hash2(ix + 1, iy), c = hash2(ix, iy + 1), d = hash2(ix + 1, iy + 1);
    return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy;
  }

  // A set's pictures, one channel each, at half size. imgs: {name: HTMLImageElement}
  function load(id, meta, imgs) {
    const W = Math.ceil(meta.width / SCALE), H = Math.ceil(meta.height / SCALE);
    const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    const g = cv.getContext('2d', { willReadFrequently: true });
    g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'medium';
    const ch = {};
    for (const [name, im] of Object.entries(imgs)) {
      g.clearRect(0, 0, W, H); g.drawImage(im, 0, 0, W, H);
      const d = g.getImageData(0, 0, W, H).data; const c = new Uint8Array(W * H);
      for (let i = 0; i < W * H; i++) c[i] = d[i * 4 + 1];
      ch[name] = c;
    }
    return { id, meta, W, H, ch, level: meta.layer === 'low' ? 2 : meta.layer };
  }

  const linear = (x) => x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);

  // Lights one set for the moment: an ImageData in the screen's orientation, and where to draw it.
  // L: { el, sunAz (rad, screen), sunElSeen (deg, where it is drawn, in this frame), side, night, moonW, moon (rgb),
  //      sunlight(h, el) -> rgb, ambTop, ambLow, sunGround, glowCol, haze, sunPart, sky(xPx, yPx) -> display rgb 0..1 }
  // V: { W, hy, F, rtl }
  function compose(set, L, leg, V) {
    const { W: Wc, H: Hc, meta, ch } = set;
    const level = set.level;
    const azHalf = meta.azHalf, elMin = meta.elMin, elMax = meta.elMax, rMax = meta.rMax || 4;
    const m = V.rtl ? -1 : 1, t = leg.turned ? -1 : 1;
    const lw = weights(L.moonLight ? L.moonEl : L.el, L.side * m * t, true, L.glow);
    const pics = []; for (let i = 0; i < 5; i++) if (lw.w[i] > 0.002) pics.push([ch['s_' + lw.names[i]], lw.w[i] * rMax]);
    const glows = []; if (lw.glow[0] > 0.002) glows.push([ch.s_gl, lw.glow[0] * rMax]); if (lw.glow[1] > 0.002) glows.push([ch.s_gr, lw.glow[1] * rMax]);
    const tanTop = Math.tan(elMax * DEG), tanBot = Math.tan(elMin * DEG);
    // Rows: equal steps on the screen (the picture's rows are equal steps of angle).
    const rowOf = new Int32Array(Hc), rowF = new Float32Array(Hc), elOf = new Float32Array(Hc);
    for (let oy = 0; oy < Hc; oy++) {
      const te = tanTop - (oy + 0.5) / Hc * (tanTop - tanBot);
      const el = Math.atan(te) / DEG; elOf[oy] = el;
      const r = Math.min(Hc - 1.001, Math.max(0, (elMax - el) / (elMax - elMin) * Hc - 0.5));
      rowOf[oy] = Math.floor(r); rowF[oy] = r - Math.floor(r);
    }
    // The Sun's light on a cloud, by the height of the Sun there and the cloud's own height (a small table).
    const HS = [0.6, 1.5, 3.0, 6.0, 12.0], E0 = L.el - 3, EN = 48, ES = 0.25;
    const direct = new Float32Array(HS.length * EN * 3);
    for (let hi = 0; hi < HS.length; hi++) for (let k = 0; k < EN; k++) {
      const s = L.sunlight(HS[hi], E0 + k * ES);
      for (let c = 0; c < 3; c++) direct[(hi * EN + k) * 3 + c] = s[c] * (1 - L.moonW) + L.moon[c] * L.moonW;
    }
    const lumD = (v) => 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    const ambient = 1 + (L.ambK - 1) * (1 - L.night);
    let dome = L.ambLow.map((x, c) => (x + (L.ambTop[c] - x) * 0.6) * ambient);
    const ev = 0.65 * smooth(7, -2, L.el), dl = lumD(dome);
    dome = dome.map((x, c) => x + (dl * [0.78, 0.95, 1.38][c] - x) * ev);
    const sinEl = Math.max(Math.sin(L.el * DEG), 0);
    const ground = dome.map((x, c) => 0.30 * (L.sunGround[c] * sinEl + x));
    const glowK = 1 + (0.55 - 1) * smooth(2, -3, L.el);
    const lowSun = smooth(26, 6, L.el);
    const thinKind = level === 2 ? 0.45 : 1.0;
    const hazeK = level === 2 ? 0.92 : level === 1 ? 0.80 : 0.68;
    const share = level === 2 ? 1.0 : level === 1 ? 0.6 : 0.0;
    const sd = [Math.cos(L.sunElSeen * DEG) * Math.sin(L.sunAz), Math.sin(L.sunElSeen * DEG), Math.cos(L.sunElSeen * DEG) * Math.cos(L.sunAz)];
    const hg = (mu, g) => { const g2 = g * g; return (1 - g2) / (4 * Math.PI * Math.pow(Math.max(1 + g2 - 2 * g * mu, 1e-4), 1.5)); };
    const out = new ImageData(Wc, Hc); const o = out.data;
    const A = ch.a, K = ch.k, G = ch.g, Fd = ch.f;
    const tmp = [0, 0, 0], disp = [0, 0, 0];
    const offR = leg.offset;
    for (let oy = 0; oy < Hc; oy++) {
      const el = elOf[oy], sy = rowOf[oy], fy = rowF[oy], fy0 = 1 - fy;
      const te = Math.tan(el * DEG), ce = Math.cos(el * DEG), se = Math.sin(el * DEG);
      const yPx = V.hy - V.F * te;
      for (let ox = 0; ox < Wc; ox++) {
        const u = (ox + 0.5) / Wc;
        const saz = m * (u - 0.5) * 2 * azHalf;
        const sx = Math.min(Wc - 1, Math.max(0, Math.floor((0.5 + t * saz / (2 * azHalf)) * Wc)));
        const idx = sy * Wc + sx, idx2 = idx + Wc;
        const rd = (p) => (p[idx] * fy0 + p[idx2] * fy) / 255;
        const a0 = rd(A);
        if (a0 < 0.004) continue;
        let a = a0;
        // Where this set gives way to its neighbour: in patches, as a sheet of cloud really ends (layer.frag).
        const wander = (vnoise(el * 0.11 + 3 * level, saz * 0.035 + 5) - 0.5) * 22 + (vnoise(el * 0.37 + 7, saz * 0.1) - 0.5) * 7;
        const gone = smooth(0.5 * PITCH - 14, 0.5 * PITCH + 14, Math.abs(saz) + Math.sign(saz) * 0.2 * (el - 18) + wander);
        if (gone > 0) {
          if (gone >= 1) continue;
          const bx = saz * 0.021, by = el * 0.050 + 0.37 * level;
          const lobes = (vnoise(bx * 0.31 * 8 + 1.7, by * 0.31 * 8 + 1.7) - 0.5) * 3.2;
          const cells = vnoise(bx * 0.9 * 8, by * 0.9 * 8 + 40) * 0.7 + vnoise(bx * 2.7 * 8 + 3, by * 2.7 * 8 + 80) * 0.3;
          const patches = Math.max(0, Math.min(1, 0.5 + lobes * 0.55 + (cells - 0.45) * 1.5));
          a *= smooth(-0.20, 0.16, patches - 1.4 * gone + 0.16);
          if (a < 0.004) continue;
        }
        const azScr = ((u - 0.5) * 2 * azHalf - m * offR) * DEG;   // radians, + right on the screen
        // Lit by the Sun (the pictures the moment lies between), by the glow round a low Sun, the sky, the ground.
        let S = 0; for (const [p, w] of pics) { const v = rd(p); S += v * v * w; }
        let GL = 0; for (const [p, w] of glows) { const v = rd(p); GL += v * v * w; }
        const kv = rd(K), gv = rd(G);
        const Ksky = kv * kv * rMax, Kgr = gv * gv * rMax;
        const far = Fd ? rd(Fd) : 0.3;
        const km = -30 * Math.log(Math.max(1 - far, 1e-3));
        const elThere = L.el + km * Math.cos(azScr - L.sunAz) / 111.2;
        const high = level === 2 ? Math.max(0.2, Math.min(14, Math.max(1.2, km * Math.max(se, 0) + km * km / 12742))) : level === 1 ? 5.5 : 9.5;
        let hi = 0; while (hi < HS.length - 2 && high > HS[hi + 1]) hi++;
        const hf = Math.max(0, Math.min(1, (high - HS[hi]) / (HS[hi + 1] - HS[hi])));
        const ek = Math.max(0, Math.min(EN - 1.001, (elThere - E0) / ES)); const ei = Math.floor(ek), efr = ek - ei;
        const vx = ce * Math.sin(azScr), vy = se, vz = ce * Math.cos(azScr);
        const mu = vx * sd[0] + vy * sd[1] + vz * sd[2];
        const through = 0.7 * hg(mu, 0.80) + 0.3 * hg(mu, 0.45);
        const under = smooth(3.5, -0.5, elThere);
        for (let c = 0; c < 3; c++) {
          const d0 = direct[(hi * EN + ei) * 3 + c] + (direct[(hi * EN + ei + 1) * 3 + c] - direct[(hi * EN + ei) * 3 + c]) * efr;
          const d1 = direct[((hi + 1) * EN + ei) * 3 + c] + (direct[((hi + 1) * EN + ei + 1) * 3 + c] - direct[((hi + 1) * EN + ei) * 3 + c]) * efr;
          const dc = d0 + (d1 - d0) * hf;
          let v = (S * dc + GL * L.glowCol[c] * glowK) * 1.15 + Ksky * dome[c] * 0.9 + Kgr * ground[c] * 0.8;
          v += dc * Ksky * through * 0.15 * thinKind * (0.06 + 0.94 * lowSun);
          v += dc * (0.09 * Kgr + 2.6 * S) * thinKind * under;
          tmp[c] = v / a0 * L.expose;
        }
        if (L.debug && a0 > 0.5 && Math.random() < 0.0003) console.log(JSON.stringify({set: set.id, S: S.toFixed(3), GL: GL.toFixed(3), K: Ksky.toFixed(3), G: Kgr.toFixed(3), km: km.toFixed(1), dir: [0,1,2].map(c => direct[(hi * EN + ei) * 3 + c].toFixed(3)), dome: dome.map(x => x.toFixed(3)), tmp: tmp.map(x => x.toFixed(3))}));
        Atmo.tone(tmp, disp);
        // Far clouds fade into the air in front of them, which has the colour of the sky behind them.
        const ta = Math.tan(azScr);
        const xPx = V.W / 2 + V.F * azScr;
        const up = km * Math.max(te, 0) / Math.sqrt(1 + te * te);
        const thin = (x) => x < 1e-3 ? 1 : (1 - Math.exp(-x)) / x;
        const fog = (1 - Math.exp(-(0.55 * L.haze + 0.25) * km / 30 * (0.25 * thin(up / 8) + 0.75 * thin(up / 1.6)))) * hazeK;
        const sky = L.sky(xPx, Math.max(yPx, 0));
        // Round the Sun the lower clouds stand apart when the weather lets it through (scene.frag parted()).
        if (share > 0 && L.sunPart > 0) {
          const th = Math.acos(Math.max(-1, Math.min(1, mu)));
          const thinA = 0.85 * share * L.sunPart * Math.exp(-(th / 0.21) * (th / 0.21));
          a = Math.max(0, Math.min(1, (a - thinA) / (1 - thinA))) * (1 - share * L.sunPart * Math.exp(-(th / 0.075) * (th / 0.075)));
        }
        a *= leg.weight;
        for (let c = 0; c < 3; c++) disp[c] = disp[c] + (sky[c] - disp[c]) * fog;
        // The glare round the Sun lies over the clouds too (a camera's, as the sky's own).
        if (L.glare > 0) {
          const r = Math.hypot(xPx - L.sunX, yPx - L.sunY) / L.dp;
          const gk = (0.55 * Math.exp(-Math.max(r - 7.5, 0) / 9) + 0.22 * Math.exp(-r / 55)) * L.glare;
          for (let c = 0; c < 3; c++) disp[c] = 1 - (1 - disp[c]) * (1 - L.glareCol[c] * gk);
        }
        // The text written on the sky stays readable: behind it the brightest parts of the clouds are eased down.
        if (L.textY > 0) {
          const qx = (xPx - V.W / 2) / (0.44 * V.W), qy = (yPx - L.textY) / L.textR;
          const lm = 1 - (1 - L.textCap) * smooth(1.70, 0.25, Math.hypot(qx, qy));
          if (lm < 1) {
            const lin = [linear(disp[0]), linear(disp[1]), linear(disp[2])];
            const l = 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2];
            if (l > lm * 0.5) {
              const x = (l - lm * 0.5) / (lm * 0.5), y = lm * 0.5 + lm * 0.5 * (1 - Math.exp(-x));
              for (let c = 0; c < 3; c++) disp[c] = Atmo.srgb(lin[c] * y / l);
            }
          }
        }
        const p = (oy * Wc + ox) * 4;
        for (let c = 0; c < 3; c++) o[p + c] = Math.round(255 * Math.max(0, Math.min(1, disp[c])));
        o[p + 3] = Math.round(255 * Math.max(0, Math.min(1, a)));
      }
    }
    const cx = V.W / 2 - m * V.F * offR * DEG, half = V.F * azHalf * DEG;
    return { image: out, x0: cx - half, x1: cx + half, y0: V.hy - V.F * tanTop, y1: V.hy - V.F * tanBot };
  }

  root.MSets = { load, compose, weights, PITCH, vnoise };
})(typeof window !== 'undefined' ? window : globalThis);
