// S9b: side-by-side page of v69 (tests/steel/ui/v69, tools/golden/ui_capture.js) and the app (tests/steel/ui/app,
// UiScreensTest) on the owner's phone size, plus the differences that remain (tests/steel/ui/differences.json).
//   node tools/golden/ui_compare.js -> tests/steel/ui/compare.html
'use strict';
const fs = require('fs');
const path = require('path');
const UI = path.resolve(__dirname, '..', '..', 'tests', 'steel', 'ui');
const SCREENS = [['01_main', 'Main screen'], ['02_steel_grid', 'Steel mode with the grid'], ['03_grid_sheet', 'Grid sheet'], ['04_catalog', 'Catalog'],
  ['05_dialog_t144', 'End plate 144 dialog'], ['06_dialog_t144_bolts', 'End plate 144 · Bolts tab'], ['07_menu', '⋮ menu']];
const diffs = fs.existsSync(path.join(UI, 'differences.json')) ? JSON.parse(fs.readFileSync(path.join(UI, 'differences.json'), 'utf8')) : {};
const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const rows = SCREENS.map(([k, t]) => `<section><h2>${esc(t)}</h2><div class="pair">
<figure><img src="v69/${k}.png" alt="v69 ${esc(t)}"><figcaption>web v69 (Chrome, 412 × 891 CSS px, DPR 2.625, dark)</figcaption></figure>
<figure><img src="app/${k}.png" alt="app ${esc(t)}"><figcaption>app (412 × 891 dp, 420 dpi, dark)</figcaption></figure></div>
${(diffs[k] || []).length ? `<ul>${diffs[k].map(d => `<li>${esc(d)}</li>`).join('')}</ul>` : '<p class="ok">No difference found.</p>'}</section>`).join('\n');
const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Steel UI vs v69</title><style>
:root{--bg:#eef1f5;--fg:#1a1d24;--muted:#5b6270;--card:#fff}
@media (prefers-color-scheme:dark){:root{--bg:#1c1e23;--fg:#eef0f4;--muted:#a2a9b6;--card:#22252c}}
body{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:15px system-ui,sans-serif}
h1{font-size:20px;margin:0 0 4px} p.note{color:var(--muted);margin:0 0 16px}
section{background:var(--card);border-radius:12px;padding:12px;margin:0 0 16px} h2{font-size:16px;margin:0 0 8px}
.pair{display:flex;gap:12px;overflow-x:auto} figure{margin:0;flex:1 1 0;min-width:180px} img{width:100%;max-width:360px;display:block;border-radius:8px;border:1px solid rgba(127,127,127,.3)}
figcaption{font-size:12px;color:var(--muted);margin-top:4px} ul{margin:10px 0 0;padding-left:20px} li{margin:3px 0} .ok{color:#2f9e44;margin:8px 0 0}
</style></head><body><h1>Steel UI vs web v69</h1><p class="note">Same screens on the owner's phone size (1080 × 2340 px). Left: v69 rendered by tools/golden/ui_capture.js. Right: the app rendered by UiScreensTest (no OpenGL in the test: the 3D view is a flat sketch). Differences that remain are listed under each pair (tests/steel/ui/differences.json).</p>
${rows}
${(diffs.general || []).length ? `<section><h2>General</h2><ul>${diffs.general.map(d => `<li>${esc(d)}</li>`).join('')}</ul></section>` : ''}
</body></html>
`;
fs.writeFileSync(path.join(UI, 'compare.html'), html);
console.log('wrote', path.join(UI, 'compare.html'));
