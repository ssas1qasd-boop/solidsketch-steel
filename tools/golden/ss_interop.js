// S3: .ss both ways with web v69.
//   1. ./gradlew :core-io:testDebugUnitTest --tests '*SsInteropTest*'   (writes core-io/build/ss-interop/native/*.ss)
//   2. node tools/golden/ss_interop.js                                    (this script: v69 opens them; v69 saves R10)
//   3. ./gradlew :core-io:testDebugUnitTest --tests '*SsInteropTest*'   (native reads the file v69 saved)
'use strict';
const fs = require('fs');
const path = require('path');
const { REPO, openV69, openSS, writeGolden } = require('./lib/v69');

const DIR = path.join(REPO, 'core-io', 'build', 'ss-interop');
const R10 = path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R10.ss');
const golden = JSON.parse(fs.readFileSync(path.join(REPO, 'tests', 'steel', 'golden', 'r10', 'summary.json'), 'utf8'));
const totals = page => page.evaluate(() => {
  const S = __ss.S, w = __st.weights(), dw = __st.tkd.all(), X = __st.tkd.idx();
  return { bodies: S.bodies.length, assemblies: X.asm.size, partMarks: X.pos.size, weightKg: Math.round(w.weight_t * 1000), weightExact: w.weight_t * 1000, drawings: dw.length,
    nextId: S.nextId, ids: S.bodies.map(b => b.id), names: S.bodies.map(b => b.name), conns: (S.stConns || []).length };
});

(async () => {
  const report = {}; let failed = 0;
  const check = (name, ok, detail) => { report[name] = { ok, detail }; console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}: ${JSON.stringify(detail)}`); if (!ok) failed++; };
  for (const f of ['R10_resaved.ss', 'R10_edited.ss', 'native_new.ss']) if (!fs.existsSync(path.join(DIR, 'native', f))) throw new Error(`missing ${f}: run step 1 first`);

  // Original R10 in v69, and the weight of body 1 (deleted in R10_edited).
  let v = await openV69({ log: false });
  await openSS(v.page, R10);
  const t0 = await totals(v.page);
  const w1 = await v.page.evaluate(() => __st.partWeight(1).weight);
  // v69 saves R10 (for step 3).
  const b64 = await v.page.evaluate(() => __st.ssExport('R10_web'));
  fs.mkdirSync(path.join(DIR, 'web'), { recursive: true });
  fs.writeFileSync(path.join(DIR, 'web', 'R10_web.ss'), Buffer.from(b64, 'base64'));
  await v.browser.close();

  // 1. Native resave of R10: same totals as the golden summary.
  v = await openV69({ log: false });
  await openSS(v.page, path.join(DIR, 'native', 'R10_resaved.ss'));
  const t1 = await totals(v.page); await v.browser.close();
  check('R10_resaved opens with the golden totals', t1.bodies === golden.bodies && t1.assemblies === golden.assemblies && t1.partMarks === golden.partMarks && t1.weightKg === golden.weightKg && t1.drawings === golden.drawings && t1.conns === 3680,
    { bodies: t1.bodies, assemblies: t1.assemblies, partMarks: t1.partMarks, weightKg: t1.weightKg, drawings: t1.drawings, conns: t1.conns });

  // 2. Native edit: body 1 gone, NATIVE BOX (id 8297) added, nextId moved on, weight down by body 1's weight.
  v = await openV69({ log: false });
  await openSS(v.page, path.join(DIR, 'native', 'R10_edited.ss'));
  const t2 = await totals(v.page); await v.browser.close();
  const boxIx = t2.ids.indexOf(8297);
  check('R10_edited opens with the native edit', t2.bodies === 6638 && !t2.ids.includes(1) && boxIx >= 0 && t2.names[boxIx] === 'NATIVE BOX' && t2.nextId === 8298 && Math.abs(t0.weightExact - w1 - t2.weightExact) < 1,   /* weights() totals are rounded to whole kg */
    { bodies: t2.bodies, box: boxIx >= 0 ? t2.names[boxIx] : null, nextId: t2.nextId, weightBefore: +t0.weightExact.toFixed(2), body1: +w1.toFixed(2), weightAfter: +t2.weightExact.toFixed(2) });

  // 3. A file made only in this app.
  v = await openV69({ log: false });
  await openSS(v.page, path.join(DIR, 'native', 'native_new.ss'));
  const t3 = await totals(v.page);
  const vols = await v.page.evaluate(() => __ss.S.bodies.map(b => Math.round(Math.abs(b.man.volume()))));
  await v.browser.close();
  check('native_new opens', t3.bodies === 2 && t3.names.join() === 'COLUMN,BEAM' && vols.join() === '27000000,8000000', { bodies: t3.bodies, names: t3.names, volumes: vols });

  writeGolden('ss_interop/report.json', report);
  console.log(failed ? `${failed} check(s) failed` : 'all checks passed; now run step 3');
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
