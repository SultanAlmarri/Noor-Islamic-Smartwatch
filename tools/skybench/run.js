// node run.js scenes.json outdir  — renders each scene with the repo's shader files.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const RAW = path.join(__dirname, '../../app/src/main/res/raw/');
(async () => {
  const scenes = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const outDir = process.argv[3]; fs.mkdirSync(outDir, { recursive: true });
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  p.on('console', m => console.log('page:', m.text()));
  await p.setContent('<html><body></body></html>');
  for (const f of ['atmo.js', 'scene.js', 'render.js']) await p.addScriptTag({ content: fs.readFileSync(path.join(__dirname, f), 'utf8') });
  // The catalogue's bright stars (app/src/main/res/raw/stars.bin: count, then RA, Dec, V, K).
  const sb = fs.readFileSync(RAW + 'stars.bin'); const n = sb.readInt32BE(0); const stars = [];
  for (let i = 0; i < n; i++) { const o = 4 + i * 14; stars.push([sb.readFloatBE(o), sb.readFloatBE(o + 4), sb.readFloatBE(o + 8), sb.readInt16BE(o + 12)]); }
  await p.evaluate((st) => { window.STARS = st; }, stars);
  const src = { common: fs.readFileSync(RAW + 'sky_common.agsl', 'utf8'), sky: fs.readFileSync(RAW + 'sky_layer.agsl', 'utf8'), land: fs.readFileSync(RAW + 'land_layer.agsl', 'utf8') };
  for (const sc of scenes) {
    try {
      const r = await p.evaluate(([cfg, src]) => window.renderFrame(cfg, src), [sc, src]);
      fs.writeFileSync(path.join(outDir, sc.name + '.png'), Buffer.from(r.url.split(',')[1], 'base64'));
      console.log(sc.name, 'el', r.el.toFixed(1), 'night', r.night.toFixed(2), 'ms', r.ms.map(x => x.toFixed(0)).join('/'));
    } catch (e) { console.log('FAIL', sc.name, e.message); }
  }
  await b.close();
})();
