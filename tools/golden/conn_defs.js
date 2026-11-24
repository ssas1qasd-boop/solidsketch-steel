// S10: the live CONN entries of v69's own connection types (after every chapter's changes): label, tekla, parts, def.
//   node tools/golden/conn_defs.js -> tests/steel/golden/app/conn_defs.json
'use strict';
const fs = require('fs'); const path = require('path'); const { REPO, openV69 } = require('./lib/v69');
(async () => { const { browser, page } = await openV69({ log: false });
  try { const r = await page.evaluate(() => { const C = __st.CONN; const out = {};
      for (const k of ['base', 'knee', 'apex', 'cleat', 'wgusset', 'tgusset', 'tieend', 'gabletop', 'craneseat', 'railclip', 'stairtreads', 'weldonly', 'endplate', 'shearplate', 'clip', 'stiff', 'splice', 'fitting'])
        if (C[k]) out[k] = { label: C[k].label, tekla: C[k].tekla, parts: C[k].parts, def: C[k].def, fam: C[k].fam || null }; return out; });
    fs.writeFileSync(path.join(REPO, 'tests/steel/golden/app/conn_defs.json'), JSON.stringify({ source: 'web v69 CONN (tools/golden/conn_defs.js)', conn: r }, null, 1) + '\n'); console.log(Object.keys(r).join(' '));
  } finally { await browser.close(); } })();
