// S6 golden: Fitting (Tekla 13) through the live v69 chain: the secondary part cut at the main part's face + gap.
// cutBox: the cut solid v69 makes (60 m box); secondary: the part after v69's regen (Manifold subtract).
//   node tools/golden/s6_fittings.js   -> tests/steel/golden/s6/fittings.json
'use strict';
const fs = require('fs');
const path = require('path');
const { GOLDEN, openV69 } = require('./lib/v69');

// Three cases per component (CLAUDE.md): standard, odd profile, sloped. Members are ST_SETS + overrides.
const CASES = {
  fitting_standard: { a: ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }], b: ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] }], q: {} },
  fitting_web_gap: { a: ['COLUMN', { prof: 'HEM300', p1: [0, 0, 0], p2: [0, 0, 7500], rot: 'Top' }], b: ['BEAM', { prof: 'IPE300', p1: [0, 0, 6500], p2: [6000, 0, 6500] }], q: { gap: 25 } },
  fitting_sloped: { a: ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }], b: ['RAFTER', { prof: 'IPE360', p1: [0, 0, 7500], p2: [13195, 0, 9000] }], q: { gap: 5 } },
};

(async () => {
  const { browser, page } = await openV69({ log: false });
  let out;
  try {
    out = await page.evaluate(({ cases }) => {
      const R = v => Math.round(v * 100) / 100; const S = __ss.S; const res = {};
      const info = m => { const bb = m.boundingBox(); return { volume: R(Math.abs(m.volume())), bbox: [...Array.from(bb.min), ...Array.from(bb.max)].map(R) }; };
      for (const [k, c] of Object.entries(cases)) {
        S.bodies = []; S.stConns = [];
        const mk = ([set, o]) => __st.add(Object.assign({ kind: 'member' }, __st.ST_SETS[set], o), 'golden');
        const A = mk(c.a), B = mk(c.b);
        const cn = __st.connAdd('fitting', [A.id, B.id], c.q); const cutBox = __st.live.connGeom(cn).cuts.get(B.id).map(info); __st.regen([A.id, B.id]);
        const a = S.bodies.find(b => b.id === A.id), b = S.bodies.find(x => x.id === B.id);
        res[k] = { a: c.a, b: c.b, q: c.q, cutBox, main: info(a.man), secondary: info(b.man) };
      }
      return res;
    }, { cases: CASES });
  } finally { await browser.close(); }
  const dir = path.join(GOLDEN, 's6'); fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'fittings.json'), JSON.stringify({ source: 'web v69: __st.add the two members (ST_SETS + overrides), connAdd fitting, regen; the parts after their cuts', cases: out }, null, 1) + '\n');
  console.log(JSON.stringify(out, null, 0).slice(0, 900));
})().catch(e => { console.error(e); process.exit(1); });
