// S10.5: every option of the S10 hardware components on their S9 standard geometry: brace-end hardware (v45: T102t /
// T103t squeeze, T7t fork, T1w / T16 rod, T52 strips), cold-formed purlin laps and sleeves (v47: T1c overlap, T2c sleeve),
// stub splices (v48: T133 / T135 stub). Each option of the component's def varied on its own (comp_cases.js format).
const s9 = Object.assign({}, require('./s9_gu.js'), require('./s9_sp_pu.js'));
const VARY = {
  GU: { t: 15, setback: 40, lap: 200, nb: 3, pb: 90, e: 55, bolt: 24, grade: '10.9', clear: 25, slot: 6, side: -1, clip: 1, a: 8 },
  PU: { lap: 450, ts: 6, t: 10, h: 180, w: 110, leg: 90, bolt: 16, grade: '10.9', side: -1, a: 5 },
  SP: { gap: 20, tp: 25, tfp: 20, twp: 12, cols: 3, wrows: 3, pitch: 100, e: 50, ext: 40, bolt: 24, grade: '10.9', n: 6, Ls: 750 },
};
const FAM = { T102t: 'GU', T103t: 'GU', T7t: 'GU', T1w: 'GU', T16: 'GU', T52: 'GU', T1c: 'PU', T2c: 'PU', T133: 'SP', T135: 'SP' };
const out = {};
for (const [k, fam] of Object.entries(FAM)) {
  const base = Object.entries(s9).find(([n, c]) => c.k === k && /standard/.test(n)) || Object.entries(s9).find(([, c]) => c.k === k);
  if (!base) throw new Error('no S9 case for ' + k);
  const [, c] = base;
  for (const [o, v] of Object.entries(VARY[fam])) out[`${k.toLowerCase()}_${o}_${String(v).replace(/[^a-z0-9]/gi, '')}`] = Object.assign({}, c, { q: Object.assign({}, c.q || {}, { [o]: v }) });
}
module.exports = out;
