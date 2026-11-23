// S7 golden: the v58 rule checks per component (tkStatus: bolt edge distance 1.5·d, bolt spacing 2.2·d0, bolts outside
// the plates, weld a ≤ 0.7·t, plate overlapping a part > 3 %) and the symbol position (stSymPos), from the live v69.
//   node tools/golden/s7_status.js -> tests/steel/golden/s7/status.json
'use strict';
const fs = require('fs');
const path = require('path');
const { GOLDEN, openV69 } = require('./lib/v69');

const STD = [['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }], ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]];
const SLOPED = [['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }], ['RAFTER', { prof: 'IPE360', p1: [0, 0, 7500], p2: [13195, 0, 9000] }]];
const CASES = {
  ok: { k: 'T144', members: STD, q: {} },
  edge: { k: 'T144', members: STD, q: { side: 15 } },
  spacing: { k: 'T144', members: STD, q: { pitch: 40, rows: 3 } },
  weld: { k: 'T144', members: STD, q: { a: 10 } },
  outside: { k: 'T144', members: STD, q: { e: -100 } },
  overlap: { k: 'T144', members: STD, q: { gap: -5 } },
  sloped: { k: 'T144', members: SLOPED, q: {} },
  sloped_edge_weld: { k: 'T144', members: SLOPED, q: { side: 20, a: 9, e: 25 } },
};

(async () => {
  const { browser, page } = await openV69({ log: false });
  let out;
  try {
    out = await page.evaluate(({ cases }) => {
      const S = __ss.S, st = __st; const res = {}; st.noAutoCheck = true;
      for (const [name, c] of Object.entries(cases)) {
        S.bodies = []; S.stConns = []; S.stNextConn = 1;
        const ids = c.members.map(([set, o]) => st.add(Object.assign({ kind: 'member' }, st.ST_SETS[set], o), 'golden').id);
        const r = st.connect(c.k, ids, c.q); const cn = S.stConns[S.stConns.length - 1];
        const s = st.status(cn.id); const p = st.symPos(cn.id);
        res[name] = { k: c.k, members: c.members, q: c.q, ok: r.ok, status: s.s, msgs: s.msgs, symPos: p && p.map(v => Math.round(v * 100) / 100) };
      }
      return res;
    }, { cases: CASES });
  } finally { await browser.close(); }
  const dir = path.join(GOLDEN, 's7'); fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'status.json'), JSON.stringify({ source: 'web v69: __st.connect, __st.status (tkStatus, deep), __st.symPos (tools/golden/s7_status.js)', cases: out }, null, 1) + '\n');
  for (const [k, v] of Object.entries(out)) console.log(k, v.status, JSON.stringify(v.msgs), JSON.stringify(v.symPos));
})().catch(e => { console.error(e); process.exit(1); });
