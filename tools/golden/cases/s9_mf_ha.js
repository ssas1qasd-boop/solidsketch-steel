// S9: MF moment connections (T43, T130, T134 bolted, T181), HA haunches (T40, T51, T102 knee; T106 apex).
// Pick order: main part, secondary part. Per component: standard, odd profile, skewed or sloped, plus option cases.
const COL = (prof, o) => ['COLUMN', Object.assign({ prof: prof || 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }, o || {})];
const BM = (prof, p1, p2, o) => ['BEAM', Object.assign({ prof, p1, p2 }, o || {})];
const RF = (prof, p1, p2, o) => ['RAFTER', Object.assign({ prof, p1, p2 }, o || {})];
const cases = {};
const add = (name, k, members, q, extra) => { cases[name] = Object.assign({ k, members, q: q || {} }, extra || {}); };
const tri = (k, s, o, w, q) => { const t = k.toLowerCase(); add(`${t}_standard`, k, s, q); add(`${t}_odd_profile`, k, o, q); add(`${t}_skewed`, k, w, q); };

// ---- MF: beam into a column flange (continuity plates); HEB beam into a turned column's web; beam into a main beam, skewed
const mfS = [COL(), BM('IPE360', [0, 0, 7000], [6000, 0, 7000])];
const mfO = [COL('HEB300', { rot: 'Top' }), BM('HEA240', [0, 0, 6500], [5000, 0, 6500])];
const mfW = [BM('IPE500', [0, -3000, 7000], [0, 3000, 7000]), BM('IPE300', [0, 0, 7000], [5638, 2052, 7000])];
for (const k of ['T43', 'T130', 'T134', 'T181']) tri(k, mfS, mfO, mfW);
add('t130_sloped', 'T130', [COL(), RF('IPE360', [0, 0, 7000], [6000, 0, 7600])]);
add('t130_lap_rows', 'T130', mfS, { lap: 300, rows: 2, tfp: 15, colstiff: 0, a: 6 });
add('t134_bolt27', 'T134', mfS, { bolt: 27, rows: 4, pitch: 60, grade: '8.8', t: 12, gap: 10 });
add('t43_bolt_options', 'T43', mfS, { tol: 3, blen: 5, stag: 0, ts: 15 });

// ---- HA knee: rafter from the column top; turned HEB column + deeper rafter (depth Middle); rafter skewed in plan
const kS = [COL('HEA300', { p2: [0, 0, 6000] }), RF('IPE360', [0, 0, 6000], [10000, 0, 6500])];
const kO = [COL('HEB300', { p2: [0, 0, 6000], rot: 'Top' }), RF('IPE450', [0, 0, 6000], [9000, 0, 6800], { depth: 'Middle' })];
const kW = [COL('HEA300', { p2: [0, 0, 6000] }), RF('IPE360', [0, 0, 6000], [8000, 4000, 6800])];
for (const k of ['T40', 'T51', 'T102']) tri(k, kS, kO, kW);
add('t40_reversed_rafter', 'T40', [COL('HEA300', { p2: [0, 0, 6000] }), RF('IPE360', [10000, 0, 6500], [0, 0, 6000])]);
add('t40_weld_tab', 'T40', kS, { W: { '*': { a: 4, type: 'Fillet' } }, grade: '10.9' });
// ---- HA apex: two rafters meet at the ridge
const aS = [RF('IPE360', [0, 0, 6000], [10000, 0, 6875]), RF('IPE360', [20000, 0, 6000], [10000, 0, 6875])];
const aO = [RF('HEA300', [0, 0, 6000], [8000, 0, 6700], { depth: 'Middle' }), RF('HEA300', [16000, 0, 6000], [8000, 0, 6700], { depth: 'Middle' })];
const aW = [RF('IPE400', [0, 0, 6000], [7071, 7071, 6875]), RF('IPE400', [14142, 14142, 6000], [7071, 7071, 6875])];
tri('T106', aS, aO, aW);
add('t106_flat', 'T106', [RF('IPE300', [0, 0, 6000], [6000, 0, 6000]), RF('IPE300', [12000, 0, 6000], [6000, 0, 6000])]);
module.exports = cases;
