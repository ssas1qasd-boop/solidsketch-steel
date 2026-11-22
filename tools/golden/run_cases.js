// S1: scripted golden cases. Each case places members with the v42 batch API (__st.addMany),
// adds components (__st.connectMany), numbers the model (__st.number) and dumps everything.
// Usage: node tools/golden/run_cases.js [case-name-filter]
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { openV69, writeGolden, GOLDEN } = require('./lib/v69');
const { dumpModel } = require('./lib/dump');

const filter = process.argv[2] || '';
const cases = fs.readdirSync(path.join(__dirname, 'cases')).filter(f => f.endsWith('.js')).sort()
  .flatMap(f => require(path.join(__dirname, 'cases', f))).filter(c => c.name.includes(filter));

(async () => {
  let failed = 0;
  for (const c of cases) {
    // Fresh browser (and so fresh storage) for every case: v69 restores its autosave on reload.
    const { browser, page, errors } = await openV69({ log: false });
    try {
      const res = await page.evaluate(({ c, dumpSrc }) => {
        const st = __st;
        const specs = c.members.map(m => Object.assign({ kind: 'member' }, st.ST_SETS[m.set], m.extra));
        const add = st.addMany(specs, c.name);
        if (add.errs.length) return { error: 'addMany: ' + JSON.stringify(add.errs) };
        const conn = st.connectMany(c.conns.map(([k, ix, q]) => [k, ix.map(i => add.ids[i]), Object.assign({}, st.CONN[k] && st.CONN[k].def, q)]));
        st.number();
        const dump = (0, eval)('(' + dumpSrc + ')')({ drawingData: true });
        return { add, conn, dump };
      }, { c, dumpSrc: dumpModel.toString() });
      if (res.error || res.conn.some(r => !r.ok)) { failed++; console.error(`FAIL ${c.name}:`, res.error || JSON.stringify(res.conn)); }
      const d = res.dump || {};
      const nc = d.nc && d.nc.files ? d.nc.files : [];
      if (d.nc && d.nc.files) {
        d.nc.manifest = nc.map(f => ({ path: f.path, lines: f.text.split('\n').length, sha256: crypto.createHash('sha256').update(f.text).digest('hex') }));
        for (const f of nc) { const p = path.join(GOLDEN, 'cases', c.name, 'nc', f.path); fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, f.text); }
        delete d.nc.files;
      }
      writeGolden(path.join('cases', c.name, 'case.json'), { name: c.name, description: c.description, members: c.members, conns: c.conns, connResult: res.conn || null, pageErrors: errors.slice() });
      writeGolden(path.join('cases', c.name, 'dump.json'), d);
      console.log(`${res.error ? 'FAIL' : 'ok  '} ${c.name}: ${d.counts ? JSON.stringify(d.counts) : ''} drawings ${d.drawings ? d.drawings.length : 0} nc ${nc.length}`);
    } finally {
      await browser.close();
    }
  }
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
