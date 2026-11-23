// S9 golden: the GEN components (v69 stGenRun, L15170). Each case = a component key, its members (ST_SETS name +
// overrides) and q; v69 adds the members, saves q as the defaults for new components (localStorage ss.connDef, read by
// stCQ) and runs __st.genRun(k, member ids, pts || []). Dumped: the toast, every body (kind, name, prof, p1, p2 of members,
// volume, bbox; component plates' role / prof / mat / cls / asm; bolt groups) and every component in S.stConns (type,
// parts as body indexes, q, welds).
//   node tools/golden/gen_cases.js tools/golden/cases/s9_gen.js tests/steel/golden/gen/s9_gen.json
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
      const info = m => { try { const bb = m.boundingBox(); return { volume: R(Math.abs(m.volume())), bbox: [...Array.from(bb.min), ...Array.from(bb.max)].map(R) }; } catch (e) { return { error: String(e) }; } };
      for (const [name, c] of Object.entries(cases)) {
        S.bodies = []; S.stConns = []; S.stNextConn = 1; localStorage.removeItem('ss.connDef');
        let ids;
        try { ids = c.members.map(([set, o]) => st.add(Object.assign({ kind: 'member' }, st.ST_SETS[set], o), 'golden').id); }
        catch (e) { out[name] = { k: c.k, members: c.members, q: c.q, error: 'members: ' + e }; continue; }
        const tEl = document.getElementById('toast'); tEl.textContent = '';
        try { if (c.q) localStorage.setItem('ss.connDef', JSON.stringify({ [c.k]: c.q })); st.genRun(c.k, ids, c.pts || []); }
        catch (e) { out[name] = { k: c.k, members: c.members, q: c.q, error: 'genRun: ' + e }; continue; }
        finally { localStorage.removeItem('ss.connDef'); }
        const toast = tEl.textContent;   // v69 toast() writes #toast; '' = no toast
        const ix = id => S.bodies.findIndex(b => b.id === id);
        const bodies = S.bodies.map(b => Object.assign({ id: b.id, kind: b.st && b.st.kind, name: b.st && b.st.name }, info(b.man),
          b.st && b.st.kind === 'member' ? { prof: b.st.prof, p1: b.st.p1.map(R), p2: b.st.p2.map(R), depth: b.st.depth, rot: b.st.rot, type: b.type } : {},
          b.st && b.st.kind === 'conn' ? { role: b.st.role, prof: b.st.prof, mat: b.st.mat, cls: b.st.cls, asm: b.st.asm } : {},
          b.st && b.st.kind === 'bolts' ? { n: b.st.n, d: b.st.d, d0: b.st.d0, L: b.st.L, grade: b.st.grade, ax: b.st.ax.map(R), pts: b.st.pts.map(p => p.map(R)) } : {}));
        const conns = (S.stConns || []).map(k2 => ({ type: k2.type, parts: k2.parts.map(ix), q: k2.q, welds: (k2.welds || []).map(w => ({ name: w.name, a: w.a, len: w.len, type: w.type, ws: w.ws, around: w.around })) }));
        out[name] = { k: c.k, members: c.members, q: c.q || {}, pts: c.pts || [], toast, nMembers: ids.length, bodies, conns, folders: S.folders.filter(f => /^st/.test(f.type)).map(f => f.type) };
      }
      return out;
    }, { cases });
  } finally { await browser.close(); }
}

if (require.main === module) {
  (async () => {
    const [src, dst] = process.argv.slice(2);
    if (!src || !dst) { console.error('usage: node tools/golden/gen_cases.js <cases.js> <out.json>'); process.exit(2); }
    const out = await run(require(path.resolve(src)));
    fs.mkdirSync(path.dirname(path.resolve(dst)), { recursive: true });
    fs.writeFileSync(path.resolve(dst), JSON.stringify({ source: `web v69: tools/golden/gen_cases.js ${src}`, cases: out }) + '\n');
    let n = 0, bad = 0; for (const [k, v] of Object.entries(out)) { n++; if (v.error) { bad++; console.log(`${k}: ${v.error}`); } else console.log(`${k}: ${v.bodies.length - v.nMembers} new bodies, ${v.conns.length} conns, toast ${JSON.stringify(v.toast)}`); }
    console.log(`${n} cases, ${n - bad} run in v69 -> ${dst}`);
  })().catch(e => { console.error(e); process.exit(1); });
}
module.exports = { run };
