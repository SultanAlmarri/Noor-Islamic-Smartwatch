// node bench.cjs <script.js>  : opens the bench, waits for ready, runs the script's text in the page (async), prints its result
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const t0 = Date.now();
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--enable-webgl'] });
  const page = await browser.newPage({ viewport: { width: 1400, height: 1000 } });
  page.on('console', m => { if (m.type() === 'error') console.log('console error:', m.text().slice(0, 500)); });
  page.on('pageerror', e => console.log('pageerror:', String(e).slice(0, 500)));
  await page.goto('http://127.0.0.1:' + (process.env.BENCH_PORT || 8765) + '/tools/sky3d/index.html');
  await page.waitForFunction(() => window.benchReady !== undefined, null, { timeout: 600000 });
  console.log('ready', await page.evaluate(() => window.benchReady), ((Date.now() - t0) / 1000).toFixed(1) + 's');
  const code = fs.readFileSync(process.argv[2], 'utf8');
  const r = await page.evaluate(`(async () => { const args = ${process.argv[3] || "{}"}; ${code} })()`);
  console.log(typeof r === 'string' ? r : JSON.stringify(r));
  console.log('done', ((Date.now() - t0) / 1000).toFixed(1) + 's');
  await browser.close();
})().catch(e => { console.error(String(e).slice(0, 2000)); process.exit(1); });
