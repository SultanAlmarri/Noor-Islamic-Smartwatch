// node run.js scenes.json outdir  — renders each scene with the repo's shader files.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const RAW = process.env.RAW_DIR || path.join(__dirname, '../../app/src/main/res/raw/');
(async () => {
  const scenes = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const outDir = process.argv[3]; fs.mkdirSync(outDir, { recursive: true });
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  p.on('console', m => console.log('page:', m.text()));
  await p.setContent('<html><body></body></html>');
  for (const f of ['atmo.js', 'scene.js', 'panorama.js', 'clouds.js', 'msets.js', 'render.js']) await p.addScriptTag({ content: fs.readFileSync(path.join(__dirname, f), 'utf8') });
  // The catalogue's bright stars (app/src/main/res/raw/stars.bin: count, then RA, Dec, V, K).
  const sb = fs.readFileSync(RAW + 'stars.bin'); const n = sb.readInt32BE(0); const stars = [];
  for (let i = 0; i < n; i++) { const o = 4 + i * 14; stars.push([sb.readFloatBE(o), sb.readFloatBE(o + 4), sb.readFloatBE(o + 8), sb.readInt16BE(o + 12)]); }
  await p.evaluate((st) => { window.STARS = st; }, stars);
  // The clouds (app/src/main/res/raw/clouds.webp, cloud_atlas.txt), or a scratch copy given as CLOUDS_DIR.
  const cdir = process.env.CLOUDS_DIR || RAW;
  const urls = ['clouds.webp'].map(f => 'data:image/webp;base64,' + fs.readFileSync(path.join(cdir, f)).toString('base64'));
  const table = fs.readFileSync(path.join(cdir, 'cloud_atlas.txt'), 'utf8');
  await p.evaluate(async ([u, table]) => {
    const load = (u) => new Promise((ok, no) => { const im = new Image(); im.onload = () => ok(im); im.onerror = no; im.src = u; });
    window.CLOUDS = { image: await load(u), table: Clouds.parse(table) };
  }, [...urls, table]);
  // (0.19.1) The baked cloud sets the scenes name (app/src/main/assets/sky2/clouds/<id>), read like the app reads them.
  const setsDir = process.env.SETS_DIR || path.join(__dirname, '../../app/src/main/assets/sky2/clouds/');
  const ids = [...new Set(scenes.flatMap(sc => (sc.legs || []).map(l => l.set)))];
  for (const id of ids) {
    const dir = path.join(setsDir, id);
    const meta = JSON.parse(fs.readFileSync(path.join(dir, 'sky.json'), 'utf8'));
    const files = {};
    for (const f of fs.readdirSync(dir)) if (f.endsWith('.webp')) files[f.replace('.webp', '')] = 'data:image/webp;base64,' + fs.readFileSync(path.join(dir, f)).toString('base64');
    await p.evaluate(async ([id, meta, files]) => {
      const load = (u) => new Promise((ok, no) => { const im = new Image(); im.onload = () => ok(im); im.onerror = no; im.src = u; });
      const imgs = {}; for (const [k, u] of Object.entries(files)) imgs[k] = await load(u);
      window.MSETS = window.MSETS || {}; window.MSETS[id] = MSets.load(id, meta, imgs);
    }, [id, meta, files]);
  }
  // (0.22) The medium graphics' painted sceneries the scenes name (cfg.painted: app/src/main/assets/medium/<id>, or a
  // folder of PNGs given as PAINTED_DIR/<id>, e.g. a painter's delivery before it is packed).
  const pdir = process.env.PAINTED_DIR || path.join(__dirname, '../../app/src/main/assets/medium/');
  for (const id of [...new Set(scenes.map(sc => sc.painted).filter(Boolean))]) {
    const files = {};
    for (const st of ['night', 'twilight', 'golden_left', 'day_left', 'noon', 'day_right', 'golden_right']) {
      const f = ['.webp', '.png'].map(e => path.join(pdir, id, st + e)).find(f => fs.existsSync(f));
      if (f) files[st] = 'data:image/' + (f.endsWith('.png') ? 'png' : 'webp') + ';base64,' + fs.readFileSync(f).toString('base64');
    }
    await p.evaluate(async ([id, files]) => {
      const load = (u) => new Promise((ok, no) => { const im = new Image(); im.onload = () => ok(im); im.onerror = no; im.src = u; });
      const imgs = {}; for (const [k, u] of Object.entries(files)) imgs[k] = await load(u);
      window.PAINTED = window.PAINTED || {}; window.PAINTED[id] = imgs;
    }, [id, files]);
  }
  const src = { common: fs.readFileSync(RAW + 'sky_common.agsl', 'utf8'), sky: fs.readFileSync(RAW + 'sky_layer.agsl', 'utf8'), land: fs.readFileSync(RAW + 'land_layer.agsl', 'utf8') };
  for (const sc of scenes) {
    try {
      const r = await p.evaluate(([cfg, src]) => window.renderFrame(cfg, src), [sc, src]);
      fs.writeFileSync(path.join(outDir, sc.name + '.png'), Buffer.from(r.url.split(',')[1], 'base64'));
      console.log(sc.name, 'el', r.el.toFixed(1), 'night', r.night.toFixed(2), 'ms', r.ms.map(x => x.toFixed(0)).join('/'), r.perf[0] ? 'frame sky/land ' + r.perf.map(x => x.toFixed(1)).join('/') : '', 'clouds', r.clouds, r.wx ? Object.entries(r.wx).map(([k, v]) => k + ' ' + v.toFixed(2)).join(' ') : '');
    } catch (e) { console.log('FAIL', sc.name, e.message); }
  }
  await b.close();
})();
