// C14 (Shear plate 146, joist <- girder) member bodies in the web oracle: the 68 joints' joists and girders from the R12 rebuild
// inputs, the 68 T146 connections (row 14), and the meshes of every member they touch and every part they make.
//   node tools/golden/c14_bodies.js -> build/c14/web_bodies.json (measured by tools/golden/c14_check.py against R12)
'use strict';
const fs = require('fs'), path = require('path');
const { REPO, openV69 } = require('./lib/v69');
(async () => {
  const inp = JSON.parse(fs.readFileSync(path.join(REPO, 'tests', 'steel', 'golden', 'r12', 'rebuild_inputs.json'), 'utf8'));
  const list = inp.steps.find(s => s.op === 'connectMany').list.filter(e => e[0] === 'T146' && e[3] === '14');
  const need = [...new Set(list.flatMap(e => e[1]))].sort((a, b) => a - b);
  const { browser, page } = await openV69({ log: false });
  try {
    const out = await page.evaluate(({ grid, members, need, list }) => {
      const st = __st; if (grid) st.setGrid ? st.setGrid(grid) : null;
      const r = st.addMany(need.map(i => Object.assign({}, members[i].st)), 'C14 members'); const id = {}; need.forEach((i, k) => { id[i] = r.ids[k]; });
      st.connectMany(list.map(e => [e[0], e[1].map(i => id[i]), e[2]]));
      const mesh = b => { const m = b.man.getMesh(); const n = m.numProp, vp = m.vertProperties, v = []; for (let i = 0; i < vp.length; i += n) v.push([vp[i], vp[i + 1], vp[i + 2]]); return { v, t: Array.from(m.triVerts) }; };
      const bodies = __ss.S.bodies.filter(b => b.st).map(b => ({ id: b.id, st: { kind: b.st.kind, name: b.st.name, prof: b.st.prof, conn: b.st.conn, pts: b.st.pts, ax: b.st.ax, L: b.st.L }, m: mesh(b) }));
      return { members: Object.fromEntries(need.map(i => [i, id[i]])), bodies };
    }, { grid: null, members: inp.members, need, list });
    fs.mkdirSync(path.join(REPO, 'build', 'c14'), { recursive: true }); fs.writeFileSync(path.join(REPO, 'build', 'c14', 'web_bodies.json'), JSON.stringify(out));
    console.log('C14 web:', out.bodies.length, 'bodies');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
