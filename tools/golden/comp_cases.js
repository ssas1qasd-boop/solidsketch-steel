// S8 / S9 golden: catalog components through the live v69 chain. Each case = a component key, its members (ST_SETS name +
// overrides) and the settings q; v69 adds the members, connects the component (__st.connect: stCQ(k) + q) and rebuilds.
// Dumped per case: every body the component touches (members after their cuts, its plates, its bolt groups) with
// volume and box, the plates' part fields, the bolt groups (points in v69's order, n, d, d0, L, axis), the welds, and
// the rule-check status.
//   node tools/golden/comp_cases.js <cases.js> <out.json>     e.g. tools/golden/cases/s8_st.js -> tests/steel/golden/comp/s8_st.json
'use strict';
const fs = require('fs');
const path = require('path');
const { GOLDEN, openV69 } = require('./lib/v69');

async function run(cases) {
  const { browser, page } = await openV69({ log: false });
  try {
    return await page.evaluate(({ cases }) => {
      const S = __ss.S, st = __st; st.noAutoCheck = true; const out = {};
      const R = v => Math.round(v * 1000) / 1000;
      const info = m => { try { const bb = m.boundingBox(); return { volume: R(Math.abs(m.volume())), bbox: [...Array.from(bb.min), ...Array.from(bb.max)].map(R) }; } catch (e) { return { error: String(e) }; } };
      for (const [name, c] of Object.entries(cases)) {
        S.bodies = []; S.stConns = []; S.stNextConn = 1;
        let ids;
        try { ids = c.members.map(([set, o]) => st.add(Object.assign({ kind: 'member' }, st.ST_SETS[set], o), 'golden').id); }
        catch (e) { out[name] = { k: c.k, members: c.members, q: c.q, error: 'members: ' + e }; continue; }
        let r; try { r = st.connect(c.k, ids, c.q || {}); } catch (e) { out[name] = { k: c.k, members: c.members, q: c.q, error: 'connect: ' + e }; continue; }
        const cn = S.stConns[S.stConns.length - 1];
        const bodies = S.bodies.map(b => Object.assign({ id: b.id, kind: b.st && b.st.kind, name: b.st && b.st.name }, info(b.man),
          b.st && b.st.kind === 'conn' ? { role: b.st.role, prof: b.st.prof, mat: b.st.mat, cls: b.st.cls, asm: b.st.asm } : {},
          b.st && b.st.kind === 'bolts' ? { n: b.st.n, d: b.st.d, d0: b.st.d0, L: b.st.L, grade: b.st.grade, ax: b.st.ax.map(R), pts: b.st.pts.map(p => p.map(R)) } : {}));
        let s = null; try { s = st.status(cn.id); } catch (e) { s = { s: 0, msgs: ['status: ' + e] }; }
        out[name] = { k: c.k, members: c.members, q: c.q || {}, ok: r.ok, bodies, welds: (cn.welds || []).map(w => ({ name: w.name, a: w.a, len: w.len, type: w.type, ws: w.ws, around: w.around })), status: s.s, msgs: s.msgs };
      }
      return out;
    }, { cases });
  } finally { await browser.close(); }
}

if (require.main === module) {
  (async () => {
    const [src, dst] = process.argv.slice(2);
    if (!src || !dst) { console.error('usage: node tools/golden/comp_cases.js <cases.js> <out.json>'); process.exit(2); }
    const cases = require(path.resolve(src));
    const out = await run(cases);
    fs.mkdirSync(path.dirname(path.resolve(dst)), { recursive: true });
    fs.writeFileSync(path.resolve(dst), JSON.stringify({ source: `web v69: tools/golden/comp_cases.js ${src}`, cases: out }) + '\n');
    let n = 0, bad = 0; for (const [k, v] of Object.entries(out)) { n++; if (v.error || !v.ok) { bad++; console.log(`${k}: ${v.error || 'not built (red symbol in v69)'}`); } }
    console.log(`${n} cases, ${n - bad} built in v69 -> ${dst}`);
  })().catch(e => { console.error(e); process.exit(1); });
}
module.exports = { run };
