// S4: export the steel data tables from web v69 (the oracle) as JSON assets for the app.
//   node tools/golden/data_export.js           write steel/src/main/assets/steel/*.json + tests/steel/golden/data/*
//   node tools/golden/data_export.js --check   fail if the committed files differ from what v69 gives now
// Tables come from the running page (the live values after every chapter's patches), not from parsing source.
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const zlib = require('zlib');
const { REPO, openV69 } = require('./lib/v69');

const ASSETS = path.join(REPO, 'steel', 'src', 'main', 'assets', 'steel');
const GOLD = path.join(REPO, 'tests', 'steel', 'golden', 'data');
const R10 = path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R10.ss');
const check = process.argv.includes('--check');

// Profile names for the parser golden: one or more per grammar branch of the live stProf chain (base + v41).
const SYNTHETIC = ['IPE300', 'ipe300', 'HEA200', 'PL20', 'PL10*200', 'pl12x150', 'FLT20*150', 'FLT8*300', 'D20', 'RD16', 'ROD12',
  'L60*6', 'L140*60*6', 'L100*100*10', 'SHS100*5', 'SHS100x100x5', 'RHS200*100*6', 'CHS48*3', 'PD50*5', 'PIPE114.3*4',
  'UPN240', 'U200', 'UPE300', 'C200-2-20-60', 'CC200-2-20-60', 'Z200-2-20-60', 'ZZ250-2.5-20-70', 'BI320-8-200-12',
  'BI400-10-250-15-200-12', 'PHI300-600-8-200-12', 'PHI300-600-8-200-12-180-10', 'NOTAPROFILE', ''];

(async () => {
  // Names that occur in R10 (prof and Tekla tprof), so the parser is tested on real data too.
  const r10 = JSON.parse(zlib.gunzipSync(fs.readFileSync(R10)).toString('utf8'));
  const r10Names = new Set();
  for (const b of r10.doc.bodies) for (const k of ['prof', 'tprof']) if (b.st && typeof b.st[k] === 'string') r10Names.add(b.st[k]);

  const { browser, page } = await openV69({ log: false });
  let d;
  try {
    d = await page.evaluate(({ synthetic, r10Names }) => {
      const st = __st, X = st.live.data();
      const cat = Object.keys(st.ST_CAT).map(k => [k, st.ST_CAT[k]]);
      const num = o => Object.keys(o).map(k => [+k, o[k]]);
      const apps = st.ST_CATALOG.flatMap(g => g.items).filter(i => i[0] === '@');
      const names = [...new Set([...cat.map(c => c[0]), ...st.profiles.UPN, ...st.profiles.UPE, ...Object.values(st.ST_SETS).map(s => s.prof), ...synthetic, ...r10Names])];
      return {
        profiles: { cat, upn: num(X.UPN_T), upe: num(X.UPE_T) },
        propertySets: { sets: Object.keys(st.ST_SETS).map(k => st.ST_SETS[k]), types: X.ST_TYPE },
        classes: num(X.ST_CLASS),
        tk: { components: st.TK, groups: [...new Set(st.TK.map(t => t.grp))], catalog: st.ST_CATALOG, famNames: X.TK_FAMNAME, pick: X.TK_PICK, icons: X.TK_ICON,
          apps: apps.map(a => ({ id: a, label: (st.ST_CATALOG.find(g => g.items.includes(a)) || {}).g })),
          // Catalog items that are not TK entries: CONN components v69 adds to the sheet itself (data fields only).
          connItems: st.ST_CATALOG.flatMap(g => g.items).filter(i => i[0] !== '@' && !st.TK.some(t => 'T' + t.n === i))
            .map(i => Object.assign({ id: i }, JSON.parse(JSON.stringify(st.CONN[i], (k, v) => typeof v === 'function' ? undefined : v)))) },
        weightRules: { kgm: st.weightRules.TK_KGM, roundArea: st.weightRules.TK_RAREA, densOverrides: st.weightRules.TK_DENS },
        profileCases: names.map(n => [n, st.prof(n)]),
      };
    }, { synthetic: SYNTHETIC, r10Names: [...r10Names].sort() });
  } finally { await browser.close(); }

  // Materials: v69 has no single table. Grades = the Property sheet's list (stPropSheet, line 13798);
  // density = v66 tkDensity (line 17161): TK_DENS overrides, else 0 for concrete/grout names, else 7850.
  const materials = {
    source: 'web v69: grades from stPropSheet (Material select); density from tkDensity (v66 Tekla weights)',
    grades: ['S235', 'S275', 'S355'],
    density: { defaultKgM3: 7850, overrides: d.weightRules.densOverrides, zeroIfMatches: '^C\\d{2}/\\d{2}|CONCRETE|GROUT', caseInsensitive: true },
    teklaKgPerM: d.weightRules.kgm,
    teklaRoundAreaMm2: d.weightRules.roundArea,
  };
  const outputs = {
    [path.join(ASSETS, 'profiles.json')]: { source: 'web v69 ST_CAT, UPN_T, UPE_T (live values)', ...d.profiles },
    [path.join(ASSETS, 'property_sets.json')]: { source: 'web v69 ST_SETS, ST_TYPE', ...d.propertySets },
    [path.join(ASSETS, 'classes.json')]: { source: 'web v69 ST_CLASS (class number -> colour 0xRRGGBB)', classes: d.classes },
    [path.join(ASSETS, 'materials.json')]: materials,
    [path.join(ASSETS, 'tk_catalog.json')]: { source: 'web v69 TK, ST_CATALOG, TK_FAMNAME, TK_PICK, TK_ICON, CONN (catalog items that are not TK), live values', ...d.tk },
    [path.join(GOLD, 'profile_cases.json')]: { source: 'web v69 __st.prof(name) for every name (live stProf chain: base + v41)', cases: d.profileCases },
  };
  const text = o => JSON.stringify(o, null, 1) + '\n';
  const manifest = {
    counts: { profiles: d.profiles.cat.length, upn: d.profiles.upn.length, upe: d.profiles.upe.length, propertySets: d.propertySets.sets.length, classes: d.classes.length,
      tkComponents: d.tk.components.length, tkGroups: d.tk.groups.length, catalogGroups: d.tk.catalog.length, catalogItems: d.tk.catalog.reduce((n, g) => n + g.items.length, 0),
      families: Object.keys(d.tk.famNames).length, connItems: d.tk.connItems.length, apps: d.tk.apps.length, profileCases: d.profileCases.length },
    sha256: Object.fromEntries(Object.entries(outputs).filter(([f]) => f.startsWith(ASSETS)).map(([f, o]) => [path.basename(f), crypto.createHash('sha256').update(text(o)).digest('hex')])),
  };
  outputs[path.join(GOLD, 'manifest.json')] = manifest;

  let diffs = 0;
  for (const [f, o] of Object.entries(outputs)) {
    const t = text(o);
    if (check) { const cur = fs.existsSync(f) ? fs.readFileSync(f, 'utf8') : null; if (cur !== t) { diffs++; console.error('DIFFERS from v69: ' + path.relative(REPO, f)); } }
    else { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, t); }
  }
  console.log(JSON.stringify(manifest.counts));
  if (check) { console.log(diffs ? `${diffs} file(s) differ from v69` : 'all data files match v69'); process.exit(diffs ? 1 : 0); }
})().catch(e => { console.error(e); process.exit(1); });
