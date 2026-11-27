// Group 1 video-faithful cases (owner 2026-11-28): a case built with a video's own values carries `videoFaithful: true` and is not
// gated by rule B (tools/golden/c09_cases.js skips it). Instead our rule checks (v58 tkStatus, live web v70) must give the warnings
// Tekla shows in that video, as listed in the case's `videoStatus`:
//   expect: [{ check: 'edge' | 'spacing' | 'outside' | 'weld' | 'overlap', plate?: 'END PLATE', row?: 'lowest' | 'highest' }]
//   level:  2 (yellow) / 3 (red) / 0 (green) when the video shows the symbol's colour; left out when it cannot be read
//   tekla, symbol, noWarning: what the video shows (recorded, not checked)
// Writes tests/steel/golden/s7/video_status.json (the same shape as status.json, so S7StatusTest checks app = web) and fails if a
// case does not give the expected warnings.      node tools/golden/video_status.js
'use strict';
const fs = require('fs'), path = require('path');
const { GOLDEN, openV69 } = require('./lib/v69');

const CASES = fs.readdirSync(path.join(__dirname, 'cases')).filter(f => f.endsWith('.js')).sort()
  .flatMap(f => require(path.join(__dirname, 'cases', f))).filter(c => c && c.videoFaithful);
const RX = {
  edge: /^Bolt edge distance of the bolts? ([\d, ]+) \(M[\d.]+\) in (.+) is too small/,
  spacing: /^Bolt spacing/, outside: /outside/, weld: /^Weld/, overlap: / overlaps /,
};

(async () => {
  const { browser, page } = await openV69({ log: false });
  let out;
  try {
    out = await page.evaluate(({ cases }) => {
      const S = __ss.S, st = __st; st.noAutoCheck = true; const res = {};
      for (const c of cases) {
        S.bodies = []; S.stConns = []; S.stNextConn = 1;
        const members = c.members.map(m => [m.set, m.extra]);
        const ids = members.map(([set, o]) => st.add(Object.assign({ kind: 'member' }, st.ST_SETS[set], o), 'video').id);
        const [k, ix, q] = c.conns[0]; const r = st.connect(k, ix.map(i => ids[i]), q); const cn = S.stConns[S.stConns.length - 1];
        const s = st.status(cn.id); const p = st.symPos(cn.id);
        const bolts = S.bodies.filter(b => b.st && b.st.conn === cn.id && b.st.kind === 'bolts' && b.st.pts && b.st.pts.length).map(b => b.st.pts.map(P => P[2]));
        res[c.name] = { k, members, q, ok: r.ok, status: s.s, msgs: s.msgs.map(m => m.replace(/^.*?#\d+: /, '')), rawMsgs: s.msgs, symPos: p && p.map(v => Math.round(v * 100) / 100), boltZ: bolts };
      }
      return res;
    }, { cases: CASES });
  } finally { await browser.close(); }
  let bad = 0; const gold = {};
  for (const c of CASES) {
    const o = out[c.name], vs = c.videoStatus || {}, fails = [];
    for (const e of vs.expect || []) {
      const hits = o.msgs.filter(m => RX[e.check].test(m));
      if (!hits.length) { fails.push(`no ${e.check} warning`); continue; }
      if (e.check === 'edge' && (e.plate || e.row)) {
        const ok = hits.some(m => { const [, idl, plate] = m.match(RX.edge); if (e.plate && plate !== e.plate) return false; if (!e.row) return true;
          const zs = o.boltZ[0] || []; const want = e.row === 'lowest' ? Math.min(...zs) : Math.max(...zs);
          const row = zs.map((z, i) => Math.abs(z - want) < 1 ? i + 1 : 0).filter(Boolean); const got = idl.split(',').map(s => +s.trim());
          return row.length === got.length && row.every(i => got.includes(i)); });
        if (!ok) fails.push(`edge warning not on ${e.plate || ''} ${e.row || ''} row: ${hits.join(' | ')}`);
      }
    }
    if (vs.level != null && o.status !== vs.level) fails.push(`symbol level ${o.status}, video ${vs.level}`);
    console.log(`${fails.length ? 'FAIL' : 'ok  '} ${c.name}: level ${o.status} ${JSON.stringify(o.msgs)}${fails.length ? ' -> ' + fails.join('; ') : ''}`);
    if (fails.length) bad++;
    gold[c.name] = { k: o.k, members: o.members, q: o.q, ok: o.ok, status: o.status, msgs: o.rawMsgs, symPos: o.symPos, video: vs };
  }
  const dir = path.join(GOLDEN, 's7'); fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'video_status.json'), JSON.stringify({ source: 'web v70: tkStatus on the video-faithful cases (tools/golden/video_status.js)', cases: gold }, null, 1) + '\n');
  process.exit(bad ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
