// S10 golden: applications through the live v69 chain (portal frame with girts, gables, crane runway; stairs; railing;
// GEN applications; v69's own connection types). Each case:
//   members: [[ST_SETS name, overrides], ...]   parts added first (their ids by index: #0, #1, ...)
//   stGen:   { span, ... }                       S.stGen set before the run (the haunch length 0 = span / 20 rule)
//   pre:     [[key, [member indexes], q], ...]   connections added before the run
//   run:     one of { portal: q } | { stairs: o } | { railing: o } | { gen: [key, [member indexes], pts] } | { connect: [key, [member indexes], q] }
//            (in stairs / railing, topPart / on may be a member index: { idx: 1 })
// Dumped: every body after the run (id, full st without meshes, volume, box, v69 part weight), every connection (id, type,
// parts, q, welds), the grid, S.stGen and totals (bodies per kind, weight).
//   node tools/golden/app_cases.js <cases.js> <out.json>     e.g. tools/golden/cases/s10_portal.js -> tests/steel/golden/app/s10_portal.json
'use strict';
const fs = require('fs');
const path = require('path');
const { openV69 } = require('./lib/v69');

async function run(cases) {
  const { browser, page } = await openV69({ log: false });
  try {
    return await page.evaluate(({ cases }) => {
      const S = __ss.S, st = __st; st.noAutoCheck = true; const out = {};
      const R = v => Math.round(v * 1000) / 1000;
      const RR = v => Array.isArray(v) ? v.map(RR) : typeof v === 'number' ? R(v) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, RR(x)])) : v;
      const info = m => { try { const bb = m.boundingBox(); return { volume: R(Math.abs(m.volume())), bbox: [...Array.from(bb.min), ...Array.from(bb.max)].map(R) }; } catch (e) { return { error: String(e) }; } };
      const W = id => { try { const w = st.partWeight(id); return w == null ? null : RR(w); } catch (e) { return { error: String(e) }; } };
      for (const [name, c] of Object.entries(cases)) {
        S.bodies = []; S.stConns = []; S.stNextConn = 1; S.stGen = c.stGen ? Object.assign({}, c.stGen) : null; S.stGrid = c.grid ? Object.assign({}, c.grid) : null;
        let ids = [];
        try { ids = (c.members || []).map(([set, o]) => st.add(Object.assign({ kind: 'member' }, st.ST_SETS[set], o), 'golden').id); }
        catch (e) { out[name] = { error: 'members: ' + e }; continue; }
        const idx = v => v && typeof v === 'object' && v.idx != null ? ids[v.idx] : v;
        let ret = null, err = null;
        try {
          for (const [k2, ix, q2] of c.pre || []) { const r2 = st.connect(k2, ix.map(i => ids[i]), q2 || {}); if (!r2.ok) throw new Error('pre ' + k2 + ' not built'); }
          if (c.pre_portal) st.portal(Object.assign({}, st.PF_DEF, c.pre_portal));   // a frame made before (Create twice)
          const r = c.run || {};
          if (r.portal) ret = st.portal(Object.assign({}, st.PF_DEF, r.portal));
          else if (r.stairs) ret = st.stairs(Object.assign({}, r.stairs, { topPart: idx(r.stairs.topPart) }));
          else if (r.railing) ret = st.railing(Object.assign({}, r.railing, r.railing.on != null ? { on: idx(r.railing.on) } : {}));
          else if (r.gen) ret = st.genRun(r.gen[0], r.gen[1].map(i => ids[i]), r.gen[2] || []);
          else if (r.connect) ret = st.connect(r.connect[0], r.connect[1].map(i => ids[i]), r.connect[2] || {});
        } catch (e) { err = String(e && e.stack || e); }
        const strip = s => { if (!s) return s; const o = Object.assign({}, s); return RR(o); };
        const bodies = S.bodies.map(b => Object.assign({ id: b.id, type: b.type, st: strip(b.st) }, info(b.man), { w: b.st ? W(b.id) : null }));
        const conns = (S.stConns || []).map(k => ({ id: k.id, type: k.type, parts: k.parts, q: RR(k.q || {}), nSteps: k.nSteps, welds: (k.welds || []).map(w => ({ name: w.name, a: w.a, len: w.len, type: w.type, ws: w.ws, around: w.around })) }));
        const tot = {}; let kg = 0; for (const b of bodies) { const k = (b.st && b.st.kind) || 'other'; tot[k] = (tot[k] || 0) + 1; if (b.w && typeof b.w.weight === 'number' && b.st && b.st.kind !== 'bolts') kg += b.w.weight; }
        out[name] = { members: c.members, stGen0: c.stGen, pre_portal: c.pre_portal, run: c.run, ret: ret && typeof ret === 'object' ? RR(ret) : ret, error: err, grid: S.stGrid, stGen: S.stGen, bodies, conns, totals: Object.assign(tot, { kg: R(kg) }) };
      }
      return out;
    }, { cases });
  } finally { await browser.close(); }
}

if (require.main === module) {
  (async () => {
    const [src, dst] = process.argv.slice(2);
    if (!src || !dst) { console.error('usage: node tools/golden/app_cases.js <cases.js> <out.json>'); process.exit(2); }
    const cases = require(path.resolve(src));
    const out = await run(cases);
    fs.mkdirSync(path.dirname(path.resolve(dst)), { recursive: true });
    fs.writeFileSync(path.resolve(dst), JSON.stringify({ source: `web v69: tools/golden/app_cases.js ${src}`, cases: out }) + '\n');
    for (const [k, v] of Object.entries(out)) console.log(`${k}: ${v.error ? 'ERROR ' + v.error.split('\n')[0] : JSON.stringify(v.totals)}`);
  })().catch(e => { console.error(e); process.exit(1); });
}
module.exports = { run };
