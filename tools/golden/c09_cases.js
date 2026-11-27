// C02 / C09 / C13 End plate (144), C02h Teh and C20 Column with stiffeners (186) cases for rule B (owner method: the R12 settings on other sizes / slopes): the t144_c0* cases of
// tools/golden/cases/t144.js built in web v70, every body dumped with its mesh and every connection, like c14_cases.js, for
// geom_checks.py.   node tools/golden/c09_cases.js -> build/c09/case_<name>-web.json
'use strict';
const fs = require('fs'), path = require('path');
const { REPO, openV69 } = require('./lib/v69');
const CASES = require('./cases/t144.js').filter(c => /^t144_c(0[29]|13)_/.test(c.name)).concat(require('./cases/teh.js'), require('./cases/t186.js'), require('./cases/t20.js'), require('./cases/t20g.js'), require('./cases/t20x.js'), require('./cases/t1042.js'), require('./cases/t18.js'), require('./cases/t106.js'), require('./cases/t146h.js'));
(async () => {
  const { browser, page } = await openV69({ log: false });
  try {
    for (const c of CASES) {
      const out = await page.evaluate(c => {
        const S = __ss.S, st = __st; st.noAutoCheck = true; S.bodies = []; S.stConns = []; S.stNextConn = 1;
        const ids = c.members.map(m => st.add(Object.assign({ kind: 'member' }, st.ST_SETS[m.set], m.extra), 'c09').id);
        for (const [k, p, q] of c.conns) st.connect(k, p.map(i => ids[i]), q);
        const mesh = b => { const g = b.man.getMesh(); const n = g.numProp; const v = []; for (let i = 0; i < g.vertProperties.length; i += n) v.push([g.vertProperties[i], g.vertProperties[i + 1], g.vertProperties[i + 2]]); return { v, t: Array.from(g.triVerts) }; };
        return { bodies: S.bodies.map(b => ({ id: b.id, name: b.st && b.st.name, kind: b.st && b.st.kind, st: Object.fromEntries(Object.entries(b.st || {}).filter(([k]) => ['prof', 'p1', 'p2', 'conn', 'pts', 'ax', 'd', 'L', 'n', 'grade', 'grip', 'hole'].includes(k))), m: mesh(b) })),
          conns: S.stConns.map(c => ({ id: c.id, type: c.type, parts: c.parts, q: c.q })) };
      }, c);
      const f = path.join(REPO, 'build', 'c09', `case_${c.name}-web.json`); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(out)); console.log(f, out.bodies.length, 'bodies');
    }
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
