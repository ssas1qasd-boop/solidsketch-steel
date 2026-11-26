// C14 Shear plate (146) cases (owner method step 5: the R12 settings on other member sizes), web v69 + v70; each case: girder along X,
// joist along Y ending at the girder, then T146 with R12's settings (Bolts tab Top 77.2, edge 30, horizontal edge 35, gap 20, M16,
// 2 x 2 at 70 / 60, tab 10, near side). Dumps every body (mesh) and connection, like c04_workflow.js, for geom_checks.py (rule B)
// and C14CasesTest (app = web).
//   node tools/golden/c14_cases.js -> build/c14/case_<name>-web.json
'use strict';
const fs = require('fs'), path = require('path');
const { REPO, openV69 } = require('./lib/v69');
const Q = { t: 10, bolt: 16, rows: 2, e: 30, pitch: 70, cols: 2, pitchH: 60, gap: 20, side: -1, refV: 'Top', posV: 77.2, eh: 35 };
const CASES = {
  r12: { girder: ['BI320-8-120-10', [334, 16785, 3840], [13127, 16785, 3840]], joist: ['BI220-8-140-10', [888.28, 16809, 3890], [888.28, 22331, 3890]] },
  ipe450_ipe240: { girder: ['IPE450', [0, 0, 4000], [6000, 0, 4000]], joist: ['IPE240', [3000, 0, 4105], [3000, 5000, 4105]] },
  hea300_ipe300: { girder: ['HEA300', [0, 0, 4000], [6000, 0, 4000]], joist: ['IPE300', [3000, 0, 3955], [3000, 5000, 3955]] },
};
(async () => {
  const { browser, page } = await openV69({ log: false });
  try {
    for (const [name, C] of Object.entries(CASES)) {
      const out = await page.evaluate(({ C, Q }) => {
        const S = __ss.S, st = __st; st.noAutoCheck = true; S.bodies = []; S.stConns = []; S.stNextConn = 1;
        const mem = (p, a, b) => st.add(Object.assign({ kind: 'member' }, st.ST_SETS.BEAM, { prof: p, p1: a, p2: b, depth: 'Middle' }), 'c14').id;
        const G = mem(...C.girder), J = mem(...C.joist); st.connect('T146', [G, J], Q);
        const mesh = b => { const g = b.man.getMesh(); const n = g.numProp; const v = []; for (let i = 0; i < g.vertProperties.length; i += n) v.push([g.vertProperties[i], g.vertProperties[i + 1], g.vertProperties[i + 2]]); return { v, t: Array.from(g.triVerts) }; };
        return { bodies: S.bodies.map(b => ({ id: b.id, name: b.st && b.st.name, kind: b.st && b.st.kind, st: Object.fromEntries(Object.entries(b.st || {}).filter(([k]) => ['prof', 'p1', 'p2', 'conn', 'pts', 'ax', 'd', 'L', 'n', 'grade'].includes(k))), m: mesh(b) })),
          conns: S.stConns.map(c => ({ id: c.id, type: c.type, parts: c.parts, q: c.q })), G, J };
      }, { C, Q });
      const f = path.join(REPO, 'build', 'c14', `case_${name}-web.json`); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(out)); console.log(f, out.bodies.length, 'bodies');
    }
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
module.exports = { CASES, Q };
