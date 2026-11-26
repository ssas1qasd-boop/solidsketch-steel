// C04, the user's own workflow (owner 2026-11-25): two rafters from the default RAFTER property set (IPE360, depth Behind) on a
// 12.8° slope, purlins by "Generation of purlins (50)" (T50p, profile CC200-2-20-60 set as the saved default), then the purlin cleat
// (Tcl) on rafter A and the first purlin. Measures, perpendicular to rafter A: rafter top, each purlin's underside (lowest and
// highest bottom corner over the rafter), and checks the C04 bolts pass the purlin web.
//   node tools/golden/c04_workflow.js [out.json]     (oracle: v69 + v70, tools/golden/lib/v69.js)
'use strict';
const fs = require('fs'), path = require('path');
const { openV69 } = require('./lib/v69');
const CASE = { A: [[0, 0, 7500], [6468, 0, 8970]], B: [[0, 5570, 7500], [6468, 5570, 8970]], prof: 'CC200-2-20-60' };
// the emulator's case (C04RecordingTest): three rafters, two purlin runs (A-B, B-C: the purlins butt over B), Tcl on B and the two purlins there
async function run3() {
  const { browser, page } = await openV69({ log: false });
  try {
    return await page.evaluate((C) => {
      const S = __ss.S, st = __st; st.noAutoCheck = true; S.bodies = []; S.stConns = []; S.stNextConn = 1;
      localStorage.setItem('ss.connDef', JSON.stringify({ T50p: { prof: C.prof } }));
      const R = [0, 5570, 11140].map(y => st.add(Object.assign({ kind: 'member' }, st.ST_SETS.RAFTER, { p1: [0, y, 7500], p2: [6468, y, 8970] }), 'w').id);
      st.genRun('T50p', [R[0], R[1]], []); st.genRun('T50p', [R[1], R[2]], []);
      const pur = S.bodies.filter(b => b.st && b.st.name === 'PURLIN').sort((a, b) => a.st.p1[0] - b.st.p1[0] || a.st.p1[1] - b.st.p1[1]);
      const x0 = pur[0].st.p1[0]; const two = pur.filter(b => Math.abs(b.st.p1[0] - x0) < 1);
      const r = st.connect('Tcl', [R[1], ...two.map(b => b.id)], {});
      const mesh = b => { const g = b.man.getMesh(); const n = g.numProp; const v = []; for (let i = 0; i < g.vertProperties.length; i += n) v.push([g.vertProperties[i], g.vertProperties[i + 1], g.vertProperties[i + 2]]); const t = Array.from(g.triVerts); return { v, t }; };
      return { bodies: S.bodies.map(b => ({ id: b.id, name: b.st && b.st.name, kind: b.st && b.st.kind, st: Object.fromEntries(Object.entries(b.st || {}).filter(([k]) => ['prof', 'p1', 'p2', 'rot', 'rAng', 'plane', 'depth', 'conn', 'pts', 'ax', 'd', 'L', 'n', 'grade'].includes(k))), m: mesh(b) })),
        conns: S.stConns.map(c => ({ id: c.id, type: c.type, parts: c.parts, q: c.q })), A: R[1], B: R[2], cleat: r };
    }, CASE);
  } finally { await browser.close(); }
}
async function run() {
  const { browser, page } = await openV69({ log: false });
  try {
    return await page.evaluate((C) => {
      const S = __ss.S, st = __st; st.noAutoCheck = true; S.bodies = []; S.stConns = []; S.stNextConn = 1;
      localStorage.setItem('ss.connDef', JSON.stringify({ T50p: { prof: C.prof } }));
      const A = st.add(Object.assign({ kind: 'member' }, st.ST_SETS.RAFTER, { p1: C.A[0], p2: C.A[1] }), 'w').id;
      const B = st.add(Object.assign({ kind: 'member' }, st.ST_SETS.RAFTER, { p1: C.B[0], p2: C.B[1] }), 'w').id;
      st.genRun('T50p', [A, B], []);
      const pur = S.bodies.filter(b => b.st && b.st.name === 'PURLIN');
      const r = pur.length ? st.connect('Tcl', [A, pur[Math.floor(pur.length / 2)].id], {}) : null;
      const mesh = b => { const g = b.man.getMesh(); const n = g.numProp; const v = []; for (let i = 0; i < g.vertProperties.length; i += n) v.push([g.vertProperties[i], g.vertProperties[i + 1], g.vertProperties[i + 2]]); const t = Array.from(g.triVerts); return { v, t }; };
      return { bodies: S.bodies.map(b => ({ id: b.id, name: b.st && b.st.name, kind: b.st && b.st.kind, st: Object.fromEntries(Object.entries(b.st || {}).filter(([k]) => ['prof', 'p1', 'p2', 'rot', 'rAng', 'plane', 'depth', 'conn', 'pts', 'ax', 'd', 'L', 'n', 'grade'].includes(k))), m: mesh(b) })),
        conns: S.stConns.map(c => ({ id: c.id, type: c.type, parts: c.parts, q: c.q })), A, B, cleat: r };
    }, CASE);
  } finally { await browser.close(); }
}
if (require.main === module && process.argv[2] === 'three') (async () => { const out = await run3(); const f = path.join(__dirname, '..', '..', 'build', 'c04-workflow3-web.json'); fs.writeFileSync(f, JSON.stringify(out)); console.log(f, out.bodies.length, 'bodies', out.conns.length, 'conns'); })().catch(e => { console.error(e); process.exit(1); });
else if (require.main === module) (async () => { const out = await run(); const f = process.argv[2] || path.join(__dirname, '..', '..', 'build', 'c04-workflow-web.json'); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(out)); console.log(f, out.bodies.length, 'bodies', out.conns.length, 'conns'); })().catch(e => { console.error(e); process.exit(1); });
module.exports = { run, CASE };
