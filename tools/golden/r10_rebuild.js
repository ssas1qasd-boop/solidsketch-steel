// S10 acceptance, v69 side, step 2: replay tests/steel/golden/r10/rebuild_inputs.json (tools/golden/r10_inputs.py) in web
// v69 headless and dump the result; then open R10 itself in v69 and dump its parts the same way, so that
// tools/golden/r10_compare.py compares like with like (volumes, boxes and v66 Tekla weights all from v69).
//   node tools/golden/r10_rebuild.js
// Replay (the app replays the same steps through its ports):
//   grid      S.stGrid = { x, y, z } (Tekla syntax)
//   addMany   __st.addMany(members[from..to).st) in ONE step; member index i -> the id v69 gives it
//   connectMany  __st.connectMany([[key, [ids of member indexes], q], ...]) in ONE step (one regen over every touched part)
// Output:
//   tests/steel/golden/r10/rebuild_v69.json  every body after the replay: id, member index (mi) for placed members, type,
//       st (meshes and outlines left out, numbers to 0.001), volume, box [x0 y0 z0 x1 y1 z1], partWeight (v66); every
//       connection (id, type, parts, ok, nKids, welds); totals (bodies per kind, kg, st.weights()); timings
//   tests/steel/golden/r10/r10_parts_v69.json  R10 opened through v69's own Open path (__ss.ssOpenData): per body id,
//       volume, box, partWeight; totals the same way (sum of partWeight over non-bolt bodies, and st.weights())
// Deterministic: the page runs offline with a fixed viewport; two runs give the same files (timings are kept out of them).
'use strict';
const fs = require('fs');
const path = require('path');
const { REPO, GOLDEN, openV69, openSS } = require('./lib/v69');

const M = process.env.SS_MODEL || 'R12';   // R11 = the reference (docs/steel/PARITY.md); SS_MODEL=R10 for the old one
const DIR = path.join(GOLDEN, M.toLowerCase());
const INPUTS = path.join(DIR, 'rebuild_inputs.json');
const MODEL = path.join(REPO, 'tests', 'steel', 'models', `Frame_28x53m_${M}.ss`);

function pageDump(opts) {   // runs in the page
  const S = __ss.S, st = __st;
  const R = v => Math.round(v * 1000) / 1000;
  const RR = v => Array.isArray(v) ? v.map(RR) : typeof v === 'number' ? R(v) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, RR(x)])) : v;
  const info = m => { try { const bb = m.boundingBox(); return { volume: R(Math.abs(m.volume())), box: [...Array.from(bb.min), ...Array.from(bb.max)].map(R) }; } catch (e) { return { error: String(e) }; } };
  const W = b => { if (!b.st || b.st.kind === 'bolts') return null; try { const w = st.partWeight(b.id); return w == null ? null : Object.assign({ weight: R(w.weight), net: R(w.net), gross: R(w.gross), catalog: !!w.catalog }, w.subs ? { subs: w.subs } : {}); } catch (e) { return { error: String(e) }; } };
  const DROP = new Set(['pts3', 'shape', 'ncHdr', 'ncF', 'u', 'v']);
  const stOf = s => { if (!s) return null; const o = {}; for (const [k, v] of Object.entries(s)) if (!DROP.has(k) || (k === 'pts3' && opts.keepPts3)) o[k] = v; return RR(o); };
  const bodies = S.bodies.map(b => Object.assign({ id: b.id, type: b.type }, opts.full ? { st: stOf(b.st) } : { kind: b.st && b.st.kind }, info(b.man), { w: W(b) }));
  const kinds = {}; let kg = 0; for (const b of S.bodies) { const k = (b.st && b.st.kind) || 'other'; kinds[k] = (kinds[k] || 0) + 1; }
  for (const b of bodies) if (b.w && typeof b.w.weight === 'number') kg += b.w.weight;
  let Wt = null; try { const x = st.weights(); Wt = { weight_t: x.weight_t, net_t: x.net_t, gross_t: x.gross_t, parts: x.parts, boltBodiesExcluded: x.boltBodiesExcluded }; } catch (e) { Wt = { error: String(e) }; }
  const conns = opts.full ? (S.stConns || []).map(c => ({ id: c.id, type: c.type, parts: c.parts, ok: (() => { try { return st.connOK(c); } catch (e) { return null; } })(),
    nKids: S.bodies.filter(b => b.st && b.st.conn === c.id).length, welds: (c.welds || []).map(w => ({ name: w.name, a: w.a, len: R(w.len || 0) })) })) : undefined;
  return { bodies, conns, totals: { bodies: S.bodies.length, kinds, kg: R(kg), weights: Wt, connections: (S.stConns || []).length } };
}

async function main() {
  const inp = JSON.parse(fs.readFileSync(INPUTS, 'utf8'));
  const { browser, page, errors } = await openV69({ log: true, timeoutMs: 3600000 });
  try {
    const t0 = Date.now();
    const rep = await page.evaluate(({ inp }) => {
      const S = __ss.S, st = __st; st.noAutoCheck = true; const log = {}; let t = performance.now();
      S.bodies = []; S.stConns = []; S.stNextConn = 1; S.stGen = null; S.stGrid = null;
      let ids = []; const errs = [];
      for (const step of inp.steps) {
        if (step.op === 'grid') { S.stGrid = Object.assign({}, step.grid); }
        else if (step.op === 'addMany') { const list = inp.members.slice(step.from, step.to).map(m => Object.assign({}, m.st)); const r = st.addMany(list, 'R10 rebuild: members'); ids = ids.concat(r.ids); for (const [i, e] of r.errs) errs.push(['member', step.from + i, e]); log.addMany_ms = Math.round(performance.now() - t); t = performance.now(); }
        else if (step.op === 'connectMany') {
          const list = step.list.map(([key, ix, q]) => [key, ix.map(i => ids[i]), q]);
          const res = st.connectMany(list, 'R10 rebuild: components');
          res.forEach((r, i) => { if (!r.ok) errs.push(['conn', i, r.err || 'not built (red symbol)']); });
          log.connectMany_ms = Math.round(performance.now() - t); t = performance.now(); log.connResults = res.map(r => ({ id: r.id, ok: !!r.ok, err: r.err || null }));
        }
      }
      return { ids, errs, log };
    }, { inp });
    console.error(`replayed in ${((Date.now() - t0) / 1000).toFixed(1)} s: ${JSON.stringify(rep.log && { addMany_ms: rep.log.addMany_ms, connectMany_ms: rep.log.connectMany_ms })}, ${rep.errs.length} errors`);
    const dump = await page.evaluate(pageDump, { full: true });
    const mi = new Map(rep.ids.map((id, i) => [id, i]));
    for (const b of dump.bodies) if (mi.has(b.id)) b.mi = mi.get(b.id);
    const out = { source: 'web v69 (reference/solidsketch_v69.html): tools/golden/r10_rebuild.js replaying tests/steel/golden/r10/rebuild_inputs.json',
      memberIds: rep.ids, errors: rep.errs, connResults: rep.log.connResults, grid: inp.grid, bodies: dump.bodies, conns: dump.conns, totals: dump.totals };
    fs.writeFileSync(path.join(DIR, 'rebuild_v69.json'), JSON.stringify(out) + '\n');
    console.error(`rebuild: ${JSON.stringify(dump.totals)}`);

    // R10 itself, opened in v69, dumped the same way (weights and solids from the same code)
    const t1 = Date.now(); const n = await openSS(page, MODEL);
    const r10 = await page.evaluate(pageDump, { full: false });
    console.error(`R10 opened (${n} bodies) and dumped in ${((Date.now() - t1) / 1000).toFixed(1)} s: ${JSON.stringify(r10.totals)}`);
    fs.writeFileSync(path.join(DIR, 'r10_parts_v69.json'), JSON.stringify({ source: `web v69 + v70: ${M} opened with __ss.ssOpenData (tools/golden/r10_rebuild.js)`, bodies: r10.bodies, totals: r10.totals }) + '\n');
    if (errors.length) console.error('page errors:', errors.slice(0, 10));
  } finally { await browser.close(); }
}

main().catch(e => { console.error(e); process.exit(1); });
