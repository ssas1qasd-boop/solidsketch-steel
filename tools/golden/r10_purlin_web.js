// C04 follow-up: which side of each R10 C purlin its web is on, in R10's own Tekla mesh (st.shape) and in the solid the
// web build makes from the same input (p1, p2, rot, rAng, plane, depth). Side = sign of the web along R10's st.v axis
// (v = the section's flange direction; the lips are the vertices with 70 < |u| < 90 at the other end).
//   node tools/golden/r10_purlin_web.js  -> tests/steel/golden/r10/purlin_web.json
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { REPO, GOLDEN, openV69 } = require('./lib/v69');
const M = process.env.SS_MODEL || 'R12';
const r10 = JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(REPO, `tests/steel/models/Frame_28x53m_${M}.ss`))).toString());
const purl = r10.doc.bodies.filter(b => b.st && b.st.kind === 'member' && /^(C|CC|Z|ZZ)\d/.test(b.st.prof || ''));
const side = (vp, st) => {   // web side along st.v: -1 / +1, and the web face position along v (outer face)
  const o = st.p1, u = st.u, v = st.v; const P = []; for (let i = 0; i < vp.length; i += 3) P.push([vp[i] - o[0], vp[i + 1] - o[1], vp[i + 2] - o[2]]);
  const dv = p => p[0] * v[0] + p[1] * v[1] + p[2] * v[2], du = p => p[0] * u[0] + p[1] * u[1] + p[2] * u[2];
  const vs = P.map(dv), lo = Math.min(...vs), hi = Math.max(...vs); const H = Math.max(...P.map(du)) - Math.min(...P.map(du));
  const lips = P.filter(p => Math.abs(du(p)) > H / 2 - 30 && Math.abs(du(p)) < H / 2 - 5).map(dv);
  const lipHi = lips.length && lips.every(x => x > (lo + hi) / 2), lipLo = lips.length && lips.every(x => x < (lo + hi) / 2);
  return lipHi ? { web: -1, face: lo } : lipLo ? { web: 1, face: hi } : { web: 0, face: null };
};
(async () => {
  const { browser, page } = await openV69({ log: false });
  const app = await page.evaluate(list => { const S = __ss.S, st = __st; S.bodies = []; S.stConns = []; const out = {};
    for (const [id, s] of list) { const b = st.add(Object.assign({}, s), 'w'); const m = (S.bodies.find(x => x.id === b.id) || {}).man;
      const g = m.getMesh(); const n = g.numProp; const vp = []; for (let i = 0; i < g.vertProperties.length; i += n) vp.push(g.vertProperties[i], g.vertProperties[i + 1], g.vertProperties[i + 2]); out[id] = vp; }
    return out; }, purl.map(b => [b.id, Object.fromEntries(Object.entries(b.st).filter(([k]) => ['kind', 'name', 'prof', 'mat', 'cls', 'p1', 'p2', 'rot', 'rAng', 'plane', 'depth', 'pOff', 'dOff', 'dx1', 'dx2'].includes(k)))]));
  await browser.close();
  const rows = purl.map(b => { const a = side(b.shape.vp, b.st), w = side(app[b.id], b.st); return { id: b.id, prof: b.st.prof, rot: b.st.rot, rAng: b.st.rAng, r10: a.web, web: w.web, r10Face: a.face, webFace: w.face, mirrored: a.web !== w.web }; });
  const n = rows.length, mir = rows.filter(r => r.mirrored).length;
  const byRot = {}; for (const r of rows) { const k = `${r.prof} rot ${r.rot} rAng ${Math.round(r.rAng * 100) / 100}`; (byRot[k] = byRot[k] || { n: 0, mirrored: 0 }); byRot[k].n++; if (r.mirrored) byRot[k].mirrored++; }
  fs.mkdirSync(path.join(GOLDEN, M.toLowerCase()), { recursive: true }); fs.writeFileSync(path.join(GOLDEN, M.toLowerCase(), 'purlin_web.json'), JSON.stringify({ source: 'tools/golden/r10_purlin_web.js', n, mirrored: mir, byRot, rows }, null, 1) + '\n');
  console.log(`C / Z purlins: ${n}, web on the other side in the web build: ${mir}`); console.log(JSON.stringify(byRot, null, 1));
})();
