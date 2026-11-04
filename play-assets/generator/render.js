// Renders the Play Store graphics.
//   node render.js
// Screens are authored at 1dp = 1px and captured at deviceScaleFactor 3,
// which is exactly an xxhdpi phone: 360x640dp -> 1080x1920px.
const { chromium } = require('playwright');
const path = require('path');

const OUT = path.resolve(__dirname, '..');
const SCREENS = [
  ['s-active', '01-protection-active'],
  ['s-step1',  '02-setup-step-1'],
  ['s-step3',  '03-setup-step-3'],
  ['s-alarm',  '04-alarm-triggered'],
];
// English is the app's default language and the Play listing's primary
// one, so those shots carry no suffix; Hebrew is published alongside.
const LOCALES = [
  { file: 'screens-en.html', suffix: '' },
  { file: 'screens.html',    suffix: '-he' },
];

(async () => {
  const browser = await chromium.launch();

  // ---- phone screenshots, one set per locale ----
  for (const loc of LOCALES) {
    for (const [id, name] of SCREENS) {
      const page = await browser.newPage({
        viewport: { width: 360, height: 640 },
        deviceScaleFactor: 3,
      });
      await page.goto('file://' + path.join(__dirname, loc.file));
      await page.evaluate((i) => {
        document.querySelectorAll('.screen').forEach(s => s.classList.remove('on'));
        document.getElementById(i).classList.add('on');
      }, id);
      await page.waitForTimeout(350);
      const file = path.join(OUT, `screenshot-${name}${loc.suffix}.png`);
      await page.screenshot({ path: file });
      console.log('screenshot ->', path.basename(file));
      await page.close();
    }
  }

  // ---- feature graphics, 1024x500 ----
  for (const [src, out] of [
    ['feature-en.html', 'feature-graphic-1024x500.png'],
    ['feature.html',    'feature-graphic-1024x500-he.png'],
  ]) {
    const fg = await browser.newPage({
      viewport: { width: 1024, height: 500 },
      deviceScaleFactor: 1,
    });
    await fg.goto('file://' + path.join(__dirname, src));
    await fg.waitForTimeout(400);
    await fg.screenshot({ path: path.join(OUT, out) });
    console.log('feature graphic ->', out);
    await fg.close();
  }

  await browser.close();
})();
