// R13: as r12_totals.js (default R13) plus the drawing marks of each type, for r13_compare.py. Opens a model (default R13) in the web oracle through its own Open path and writes the totals the owner checks:
// bodies, part / assembly marks, weight (v66 Tekla rules, bolts excluded), bolts by size, drawing list counts, page errors.
//   node tools/golden/r13_totals.js [model.ss] [out.json]     -> build/r13/v69_totals.json
'use strict';
const fs = require('fs'), path = require('path');
const { REPO, openV69, openSS } = require('./lib/v69');
const MODEL = process.argv[2] || path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R13.ss');
const OUT = process.argv[3] || path.join(REPO, 'build', 'r13', 'v69_totals.json');
(async () => {
  const { browser, page, errors } = await openV69({ log: false });
  try {
    const n = await openSS(page, MODEL);
    const d = await page.evaluate(() => {
      const S = __ss.S, st = __st; const w = st.weights();
      const bySize = {}; let bolts = 0;
      for (const b of S.bodies) { if (!b.st || b.st.kind !== 'bolts') continue; const k = `M${b.st.d} ${b.st.grade} L${b.st.L}`; bySize[k] = (bySize[k] || 0) + (+b.st.n || 0); bolts += +b.st.n || 0; }
      const X = st.tkd.idx(); const dw = st.tkd.all(); const byType = {}, marks = {}; for (const x of dw) { byType[x.type] = (byType[x.type] || 0) + 1; (marks[x.type] = marks[x.type] || []).push(x.mark); }
      return { bodies: S.bodies.length, conns: (S.stConns || []).length, weightKg: w.weight_t * 1000, netKg: w.net_t * 1000, grossKg: w.gross_t * 1000,
               assemblies: X.asm.size, partMarks: X.pos.size, drawings: dw.length, byType, marks, bolts, bySize, tkWelds: (S.tkWelds || []).length };
    });
    d.model = path.basename(MODEL); d.opened = n; d.pageErrors = errors;
    fs.mkdirSync(path.dirname(OUT), { recursive: true }); fs.writeFileSync(OUT, JSON.stringify(d, null, 1)); console.log(JSON.stringify(d, null, 1));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
