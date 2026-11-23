// S7 data: the component catalog sheet and the component dialogs, taken from the live v69 page.
//   node tools/golden/s7_catalog.js          -> steel/src/main/assets/steel/tk_ui.json + steel/src/main/assets/steel/thumbs/*.webp
//   node tools/golden/s7_catalog.js --check  -> compares the files on disk with v69 (no writes)
// tk_ui.json holds, per catalog key: CONN entry (label, tekla, pick prompts, family, group, def, ro), the live dialog
// spec tkSpec(k) (tabs, groups, fields; field get/set functions are named accessors the app implements), the catalog
// icon (SS_ICONS svg), plus the sheet's group order (ST_CATALOG after every chapter), SVG_PIC, TKPG and the v58 thumbs.
'use strict';
const fs = require('fs');
const path = require('path');
const { openV69 } = require('./lib/v69');

const REPO = path.resolve(__dirname, '..', '..');
const HTML = path.join(REPO, 'reference', 'solidsketch_v69.html');
const ASSETS = path.join(REPO, 'steel', 'src', 'main', 'assets', 'steel');

// SVG_PIC (with DIM) and TKPG are plain literals in v69: evaluate their source here, unchanged.
function literals() {
  const L = fs.readFileSync(HTML, 'utf8').split('\n');
  const grab = (start) => { const i = L.findIndex(l => l.startsWith(start)); let j = i; while (!/^  };\s*$/.test(L[j])) j++; return L.slice(i, j + 1).join('\n'); };
  const dim = L.find(l => l.startsWith('  const DIM = '));
  const src = `${dim}\n${grab('  const SVG_PIC = {')}\n${grab('  const TKPG = {')}\nreturn { SVG_PIC, TKPG };`;
  return Function(src)();
}

(async () => {
  const check = process.argv.includes('--check');
  const { SVG_PIC, TKPG } = literals();
  const { browser, page } = await openV69({ log: false });
  let out;
  try {
    out = await page.evaluate(() => {
      const st = window.__st, CONN = st.CONN, T = window.SS_THUMBS || {}, I = window.SS_ICONS;
      // the accessors (get/set pairs) v69's TKD fields use: name them by their source text
      const accName = new Map(); const ACC = {};
      const fieldOf = f => { const o = {}; for (const [k, v] of Object.entries(f)) { if (typeof v === 'function') continue; o[k] = v; }
        if (f.get || f.set) { const key = String(f.get) + '|' + String(f.set); if (!accName.has(key)) { const n = 'acc' + accName.size; accName.set(key, n); ACC[n] = { get: String(f.get), set: String(f.set) }; } o.acc = accName.get(key); }
        return o; };
      const specOf = k => { const s = st.tkSpec(k); return { help: s.help, generic: !!s.generic, tabs: s.tabs.map(t => { const o = {}; for (const [k2, v] of Object.entries(t)) {
        if (k2 === 'groups') o.groups = v.map(([h, fs]) => [h, fs.map(fieldOf)]);
        else if (k2 === 'dims' || k2 === 'anchors' || k2 === 'fields') o[k2] = v.map(fieldOf);
        else if (k2 === 'parts') o.parts = v; else if (typeof v !== 'function') o[k2] = v; } return o; }) }; };
      const items = [...new Set(st.ST_CATALOG.flatMap(x => x.items))];
      const conn = {}, specs = {}, icons = {};
      for (const k of items) { const c = CONN[k]; if (!c) continue;
        conn[k] = { label: c.label, tekla: c.tekla, parts: c.parts, fam: c.fam || null, grp: c.grp || null, gen: !!c.gen, def: c.def, ro: c.ro || [] };
        specs[k] = specOf(k);
        try { icons[k] = I && I.icons && I.icons[k] ? I.svg(k, false) : null; } catch (e) { icons[k] = null; } }
      const thumbs = {}; for (const part of ['t', 'p']) for (const [k, v] of Object.entries(T[part] || {})) (thumbs[k] = thumbs[k] || {})[part] = v;
      for (const [k, v] of Object.entries(T.a || {})) (thumbs[k] = thumbs[k] || {}).a = v;
      return { catalog: st.ST_CATALOG.map(x => ({ g: x.g, items: x.items.slice() })), conn, specs, icons, acc: ACC, thumbs, sym: I ? I.sym : {} };
    });
  } finally { await browser.close(); }
  // thumbnails: data:image/webp;base64 -> files
  const thumbs = {}; const files = {};
  for (const [k, v] of Object.entries(out.thumbs)) { thumbs[k] = {}; for (const part of ['t', 'p']) if (v[part]) { const m = /^data:image\/(\w+);base64,(.*)$/.exec(v[part]); if (!m) continue; const f = `thumbs/${k}_${part}.${m[1]}`; files[f] = Buffer.from(m[2], 'base64'); thumbs[k][part] = f; } if (v.a) thumbs[k].a = v.a; }
  const ui = { source: 'web v69 live page: __st.ST_CATALOG, __st.CONN, __st.tkSpec(k), SS_ICONS.svg(k), SS_ICONS.sym, SS_THUMBS; SVG_PIC / TKPG from the source (tools/golden/s7_catalog.js)',
    catalog: out.catalog, conn: out.conn, specs: out.specs, acc: out.acc, icons: out.icons, sym: out.sym, pics: SVG_PIC, pg: TKPG, thumbs };
  const text = JSON.stringify(ui) + '\n';
  const nItems = new Set(ui.catalog.flatMap(g => g.items)).size;
  console.log(`groups ${ui.catalog.length}, items ${nItems}, components ${Object.keys(ui.conn).length}, accessors ${Object.keys(ui.acc).length}, thumbs ${Object.keys(files).length}, tk_ui.json ${text.length} bytes`);
  if (check) {
    let bad = 0; const cmp = (rel, buf) => { const p = path.join(ASSETS, rel); if (!fs.existsSync(p) || !fs.readFileSync(p).equals(Buffer.from(buf))) { console.log('DIFFERS: ' + rel); bad++; } };
    cmp('tk_ui.json', text); for (const [f, b] of Object.entries(files)) cmp(f, b);
    console.log(bad ? `${bad} file(s) differ from v69` : 'all S7 catalog files match v69'); process.exit(bad ? 1 : 0);
  }
  fs.mkdirSync(path.join(ASSETS, 'thumbs'), { recursive: true });
  fs.writeFileSync(path.join(ASSETS, 'tk_ui.json'), text);
  for (const [f, b] of Object.entries(files)) fs.writeFileSync(path.join(ASSETS, f), b);
})().catch(e => { console.error(e); process.exit(1); });
