// Reads one month of Kemenag's official prayer timetable (SIHAT, Kementerian Agama RI) for Kota Jakarta from the
// public page https://bimasislam.kemenag.go.id/jadwalshalat, as a visitor would: choose the province and city, press
// "Tampilkan Jadwal", read the table. One page visit. Writes CSV to stdout.
// usage: NODE_PATH=$(npm root -g) node accuracy/tools/fetch_kemenag_month.cjs > accuracy/data/kemenag_jakarta_2026-11.csv
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch({ args: ['--no-sandbox'] });
  const p = await (await b.newContext()).newPage();
  await p.goto('https://bimasislam.kemenag.go.id/jadwalshalat', { waitUntil: 'networkidle', timeout: 90000 });
  await p.waitForTimeout(3000);
  await p.getByText('Tolak Semua').click().catch(() => {});
  await p.getByText('Pilih provinsi', { exact: true }).click(); await p.waitForTimeout(800);
  await p.getByText('DKI JAKARTA', { exact: true }).first().click(); await p.waitForTimeout(1500);
  await p.locator('button').filter({ hasText: /Pilih kota\/kabupaten/ }).first().click(); await p.waitForTimeout(800);
  await p.getByText('KOTA JAKARTA', { exact: true }).first().click(); await p.waitForTimeout(800);
  await p.locator('button.bi-sihat-submit').click(); await p.waitForTimeout(5000);
  const text = (await p.innerText('main')).replace(/\s+/g, ' ');
  const head = text.match(/Jadwal Sholat (Kota [^—]+— \w+ \d{4}) Sumber: ([^·]+)· ([^U]+)Unduh/);
  console.log('# Kemenag RI official prayer timetable (SIHAT), ' + (head ? head[1] + '; source: ' + head[2].trim() + '; point ' + head[3].trim() : ''));
  console.log('# read ' + new Date().toISOString().slice(0, 10) + ' from https://bimasislam.kemenag.go.id/jadwalshalat (page visit). Columns: Subuh = Fajr, Terbit = sunrise; Asia/Jakarta (UTC+7)');
  console.log('date,fajr,shuruq,dhuhr,asr,maghrib,isha');
  const re = /(\d\d)\/(\d\d)\/(\d{4}) (\d\d:\d\d) (\d\d:\d\d) (\d\d:\d\d) (\d\d:\d\d) (\d\d:\d\d) (\d\d:\d\d) (\d\d:\d\d) (\d\d:\d\d)/g;
  let m;
  while ((m = re.exec(text))) console.log([`${m[3]}-${m[2]}-${m[1]}`, m[5], m[6], m[8], m[9], m[10], m[11]].join(','));
  await b.close();
})();
