// S9b: v69's screens as the owner's phone shows them (1080 × 2340 px, DPR 2.625, dark, touch), for the UI comparison.
//   node tools/golden/ui_capture.js [light]  -> tests/steel/ui/v69/*.png (+ ui_dom.json: positions, sizes, colours of the elements)
'use strict';
const fs = require('fs');
const path = require('path');
const { REPO, openV69 } = require('./lib/v69');
const DPR = 2.625, W = 412, H = 891;   // Chrome on a 1080 × 2340 phone at DPR 2.625 reports 412 × 891 CSS px (address bar hidden)
const scheme = process.argv[2] === 'light' ? 'light' : 'dark';
const OUT = path.join(REPO, 'tests', 'steel', 'ui', scheme === 'dark' ? 'v69' : 'v69-light');

(async () => {
  const { browser, page } = await openV69({ log: false, pageOptions: { viewport: { width: W, height: H }, deviceScaleFactor: DPR, isMobile: true, hasTouch: true, colorScheme: scheme } });
  fs.mkdirSync(OUT, { recursive: true });
  const dom = {};
  // element boxes and computed colours of the UI parts Steel uses (CSS px; × DPR = phone px)
  const measure = async (name) => { dom[name] = await page.evaluate(() => {
    const sel = ['#top', '#top .btn', '#bottom', '#hint', '#tools', '#tools button', '#st-panel', '#st-panel button', '#st-panel .say', '#st-sheet', '#st-sheet button', '#st-sheet h3', '#st-sheet input', '#toast', '#tk-status', '#menu'];
    const out = {}; for (const s of sel) { const els = [...document.querySelectorAll(s)].filter(e => e.offsetParent !== null || getComputedStyle(e).position === 'fixed').slice(0, 6);
      if (!els.length) continue; out[s] = els.map(e => { const r = e.getBoundingClientRect(), c = getComputedStyle(e);
        return { text: (e.innerText || e.value || '').slice(0, 60), x: +r.x.toFixed(1), y: +r.y.toFixed(1), w: +r.width.toFixed(1), h: +r.height.toFixed(1), bg: c.backgroundColor, fg: c.color, font: c.fontSize + ' ' + c.fontWeight, radius: c.borderRadius, border: c.border, pad: c.padding }; }); }
    return out; }); };
  const shot = async (name) => { await page.waitForTimeout(600); await page.screenshot({ path: path.join(OUT, name + '.png') }); await measure(name); console.log('saved', name); };
  try {
    await page.evaluate(() => { try { localStorage.clear(); } catch (e) { /* none */ } });
    await shot('01_main');
    await page.evaluate(() => { const b = [...document.querySelectorAll('#tools button')].find(x => /Steel/.test(x.textContent)); if (b) b.click(); });
    await shot('02_steel_grid');
    await page.evaluate(() => { const b = [...document.querySelectorAll('#st-panel button')].find(x => x.textContent.trim() === 'Grid'); if (b) b.click(); });
    await shot('03_grid_sheet');
    await page.evaluate(() => { __st.catalog(); });
    await shot('04_catalog');
    await page.evaluate(() => { __st.compDialog('T144'); });
    await shot('05_dialog_t144');
    await page.evaluate(() => { const b = [...document.querySelectorAll('#st-sheet .tktabs button')].find(x => x.textContent === 'Bolts'); if (b) b.click(); });
    await shot('06_dialog_t144_bolts');
    await page.evaluate(() => { const c = document.querySelector('#st-sheet'); const b = c && [...c.querySelectorAll('button')].find(x => x.textContent === 'Cancel'); if (b) b.click(); document.querySelector('#more').click(); });
    await shot('07_menu');
  } finally { await browser.close(); }
  fs.writeFileSync(path.join(OUT, 'ui_dom.json'), JSON.stringify({ source: `web v69 at ${W} × ${H} CSS px, DPR ${DPR}, ${scheme} (tools/golden/ui_capture.js)`, screens: dom }, null, 1) + '\n');
})().catch(e => { console.error(e); process.exit(1); });
