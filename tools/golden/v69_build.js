// Builds a list of steel part records (st) with the web oracle's live stBuild and writes their mesh vertices.
//   node tools/golden/v69_build.js in.json out.json      in: [{id, st}, ...]   out: {id: {verts: [[x,y,z],...], bbox, volume} | {error}}
// Used by tools/golden/r12_build.py to prove the member fields it derives from Tekla's IFC (same solid as Tekla's).
'use strict';
const fs = require('fs');
const { openV69 } = require('./lib/v69');

(async () => {
  const [inp, out] = process.argv.slice(2);
  const list = JSON.parse(fs.readFileSync(inp, 'utf8'));
  const { browser, page } = await openV69({ log: false });
  const res = {};
  try {
    for (let k = 0; k < list.length; k += 200) {
      Object.assign(res, await page.evaluate(chunk => {
        const L = __st.live, R = v => Math.round(v * 1000) / 1000, o = {};
        for (const { id, st } of chunk) {
          try {
            const m = L.build(st); const mesh = m.getMesh(); const n = mesh.numProp, vp = mesh.vertProperties; const verts = [];
            for (let i = 0; i < vp.length; i += n) verts.push([R(vp[i]), R(vp[i + 1]), R(vp[i + 2])]);
            const bb = m.boundingBox(); o[id] = { verts, volume: R(Math.abs(m.volume())), bbox: [...Array.from(bb.min), ...Array.from(bb.max)].map(R) };
          } catch (e) { o[id] = { error: String(e.message || e) }; }
        }
        return o;
      }, list.slice(k, k + 200)));
    }
  } finally { await browser.close(); }
  fs.writeFileSync(out, JSON.stringify(res));
  console.error(`v69_build: ${Object.keys(res).length} parts -> ${out}`);
})().catch(e => { console.error(e); process.exit(1); });
