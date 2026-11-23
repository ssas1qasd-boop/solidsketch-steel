// S7 golden: the live v58 component dialog of v69 (stCompDialog) driven through its own DOM: values typed into the
// picture's input boxes and the tab rows, on/off boxes, the Welds table, then Modify / Apply. Dumps the component's
// settings (c.q), its welds and its bolt group, and the catalog search results, so the app's dialog can be compared.
//   node tools/golden/s7_dialog.js -> tests/steel/golden/s7/dialog.json
'use strict';
const fs = require('fs');
const path = require('path');
const { GOLDEN, openV69 } = require('./lib/v69');

// Each step: [tab, kind, label, value]: kind 'dim' = an input box on the picture (its red label), 'row' = a field row
// (its label text), 'off' = untick a row's on/off box, 'weld' = the Welds table row (weld name) Size a.
const CASES = {
  t144_modify_unchanged: { k: 'T144', members: 'std', steps: [], action: 'Modify' },
  t144_modify_fields: { k: 'T144', members: 'std', steps: [['Picture', 'dim', 't', '15'], ['Picture', 'dim', '1', '20'], ['Bolts', 'dim', '4', 4], ['Bolts', 'dim', '5', '60 100 60'], ['Bolts', 'dim', '6', '50'],
    ['Bolts', 'row', 'Staggering of bolts', 0], ['Bolts', 'row', 'Tolerance', '3'], ['Bolts', 'off', 'Comment', null], ['Welds', 'weld', 'Beam to end plate', '8'], ['General', 'row', 'Class', '4']], action: 'Modify' },
  t144_modify_pitches: { k: 'T144', members: 'std', steps: [['Bolts', 'dim', 'n', '4'], ['Bolts', 'dim', 's', '80 70 90'], ['Plates', 'part', 'END PLATE.mat', 'S355'], ['Plates', 'part', 'END PLATE.name', 'EP'], ['Holes', 'row', 'Number of holes', 2]], action: 'Modify' },
  t144_apply_defaults: { k: 'T144', members: 'std', steps: [['Picture', 'dim', '2', '15'], ['Picture', 'dim', '3', '5'], ['Bolts', 'row', 'Bolt size', 24]], action: 'Apply', thenConnect: true },
};
const MEMBERS = { std: [['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }], ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]] };
const SEARCH = ['end plate', '144', 'base', '1042', 'tube', 'S7', 'clip angle', 'zzz', 'Purlin', '20 '];

(async () => {
  const { browser, page } = await openV69({ log: false });
  let out;
  try {
    out = await page.evaluate(({ cases, members, search }) => {
      const S = __ss.S, st = __st; const res = { cases: {}, search: {} };
      for (const f of search) { const fl = f.trim().toLowerCase(); res.search[f] = [...new Set(st.ST_CATALOG.flatMap(x => x.items))].filter(k => { const it = st.CONN[k] || { label: { '@numbering': 'Numbering', '@checks': 'Checks' }[k], tekla: { '@numbering': 'Numbering', '@checks': 'Clash check · model checks' }[k] }; return (it.label + ' ' + it.tekla + ' ' + k).toLowerCase().includes(fl); }); }
      const tabBtn = (s, name) => [...s.querySelectorAll('.tktabs button')].find(b => b.textContent === name);
      const setIn = (inp, v) => { if (inp.setv) inp.setv(v); else if (inp.type === 'checkbox') inp.checked = !!v; else inp.value = String(v); };
      for (const [name, c] of Object.entries(cases)) {
        S.bodies = []; S.stConns = []; S.stNextConn = 1; try { localStorage.clear(); } catch (e) { /* none */ }
        const ids = members[c.members].map(([set, o]) => st.add(Object.assign({ kind: 'member' }, st.ST_SETS[set], o), 'golden').id);
        let conn = null; if (c.action === 'Modify') { st.connect(c.k, ids, {}); conn = S.stConns[S.stConns.length - 1]; }
        const s = st.compDialog(c.k, conn); const log = [];
        for (const [tab, kind, lbl, v] of c.steps) {
          tabBtn(s, tab).click(); const pane = [...s.querySelectorAll('.tkpane')].find(p => !p.hidden);
          if (kind === 'dim') { const w = [...pane.querySelectorAll('.tkdim')].find(d => d.querySelector('b').textContent === lbl); setIn(w.querySelector('input,.tkpk') && (w.querySelector('.tkpk') || w.querySelector('input:not(.tkon)')), v); }
          else if (kind === 'row' || kind === 'off') { const r = [...pane.querySelectorAll('.tkrow')].find(d => d.querySelector('label').textContent === lbl); if (kind === 'off') r.querySelector('.tkon').checked = false; else setIn(r.querySelector('.tkpk') || r.querySelector('input:not(.tkon)'), v); }
          else if (kind === 'weld') { const tr = [...pane.querySelectorAll('tr')].find(t => t.firstChild && t.firstChild.textContent.endsWith(lbl)); setIn(tr.querySelectorAll('input')[0], v); }
          else if (kind === 'part') { const [role, x] = lbl.split('.'); const col = { pp: 4, ps: 5, mat: 6, name: 7, fin: 8 }[x]; const tr = [...pane.querySelectorAll('tr')].find(t => t.firstChild && t.firstChild.textContent === ({ 'END PLATE': 'End plate' }[role] || role)); setIn(tr.children[col].querySelector('input'), v); }
          log.push([tab, kind, lbl, v]); }
        [...s.querySelectorAll('.tkbar button')].find(b => b.textContent === c.action).click();
        if (c.thenConnect) { st.connect(c.k, ids, {}); conn = S.stConns[S.stConns.length - 1]; }
        const c2 = S.stConns.find(x => x.id === conn.id);
        const B = S.bodies.find(b => b.st && b.st.conn === c2.id && b.st.kind === 'bolts');
        const R = v => Math.round(v * 100) / 100;
        res.cases[name] = { k: c.k, members: members[c.members], steps: log, action: c.action, q: c2.q, welds: c2.welds || [], connDef: JSON.parse(localStorage.getItem('ss.connDef') || 'null'),
          bolts: B ? { n: B.st.n, d: B.st.d, L: B.st.L, pts: B.st.pts.map(p => p.map(R)) } : null,
          plates: S.bodies.filter(b => b.st && b.st.conn === c2.id && b.st.kind === 'conn').map(b => ({ name: b.st.name, prof: b.st.prof, mat: b.st.mat, cls: b.st.cls })) };
        try { document.querySelector('#st-sheet') && st.close && st.close(); } catch (e) { /* ok */ }
      }
      return res;
    }, { cases: CASES, members: MEMBERS, search: SEARCH });
  } finally { await browser.close(); }
  const dir = path.join(GOLDEN, 's7'); fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'dialog.json'), JSON.stringify({ source: 'web v69: the live stCompDialog driven through its DOM (tools/golden/s7_dialog.js)', ...out }, null, 1) + '\n');
  console.log(JSON.stringify(out).slice(0, 1500));
})().catch(e => { console.error(e); process.exit(1); });
