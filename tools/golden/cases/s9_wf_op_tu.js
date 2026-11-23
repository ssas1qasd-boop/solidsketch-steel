// S9: WF welded / fitting / notch (20 components incl. Ttc), OP openings (5), TU tube connections (7).
// Pick order: main part, secondary part (ms) or one part (m). Per component: standard, odd profile, skewed or sloped.
const COL = (prof, o) => ['COLUMN', Object.assign({ prof: prof || 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }, o || {})];
const BM = (prof, p1, p2, o) => ['BEAM', Object.assign({ prof, p1, p2 }, o || {})];
const RF = (prof, p1, p2, o) => ['RAFTER', Object.assign({ prof, p1, p2 }, o || {})];
const BR = (prof, p1, p2, o) => ['BRACING', Object.assign({ prof, p1, p2 }, o || {})];
const MAINB = prof => BM(prof, [0, -3000, 7000], [0, 3000, 7000]);
const cases = {};
const add = (name, k, members, q, extra) => { cases[name] = Object.assign({ k, members, q: q || {} }, extra || {}); };
const tri = (k, s, o, w, q) => { const t = k.toLowerCase(); add(`${t}_standard`, k, s, q); add(`${t}_odd_profile`, k, o, q); add(`${t}_skewed`, k, w, q); };

// ---- WF fit: beam into a column flange; a small HEA into a column web; a sloped, skewed rafter
const fitS = [COL(), BM('IPE300', [0, 0, 6000], [6000, 0, 6000])];
const fitO = [COL('HEB240', { rot: 'Top' }), BM('HEA160', [0, 0, 6000], [5000, 0, 6000])];
const fitW = [COL(), RF('IPE300', [0, 0, 6000], [5000, 1500, 6800])];
for (const k of ['T13', 'T85', 'T192', 'T193', 'T194', 'T195']) tri(k, fitS, fitO, fitW);
add('t13_main_beam_skewed', 'T13', [MAINB('IPE500'), BM('IPE300', [0, 0, 7000], [5638, 2052, 7000])], { gap: 5, a: 4 });
// round tube: tube into tube goes the saddle way
tri('T23', [COL('CHS219.1x8'), BM('CHS114.3x5', [0, 0, 5000], [4000, 0, 5000], { depth: 'Middle' })],
  [COL('SHS200x200x8'), BM('CHS101.6x5', [0, 0, 5000], [4000, 0, 5000], { depth: 'Middle' })],
  [COL('CHS219.1x8'), BR('CHS88.9x4', [0, 0, 3000], [3000, 1000, 5500])]);
add('t23_beam_into_column', 'T23', fitS, {});
// ---- prep (weld access holes, backing bars unless coped)
const prepS = [COL(), BM('IPE300', [0, 0, 6000], [6000, 0, 6000])];
const prepO = [MAINB('IPE500'), BM('HEA200', [0, 0, 7000], [5000, 0, 7000])];
const prepW = [COL(), RF('IPE330', [0, 0, 6000], [5000, 0, 7000])];
for (const k of ['T44w', 'T123', 'T183', 'T9']) tri(k, prepS, prepO, prepW);
add('t44w_gap_r', 'T44w', prepS, { gap: 6, r: 35 });
// ---- colweld (column stiffeners when on the flange)
for (const k of ['T31w', 'T128']) tri(k, [COL(), BM('IPE360', [0, 0, 6000], [6000, 0, 6000])], fitO, fitW);
// ---- notch: beam into a main beam, top flush
for (const k of ['T49', 'T76', 'T1006']) tri(k, [MAINB('IPE450'), BM('IPE300', [0, 0, 7000], [6000, 0, 7000])],
  [MAINB('HEB400'), BM('HEA240', [0, 0, 7000], [5000, 0, 7000])],
  [MAINB('IPE500'), BM('IPE300', [0, 0, 7000], [5638, 2052, 7000])]);
// ---- mitre: cranked beam
tri('T41', [BM('IPE300', [0, 0, 5000], [4000, 0, 5000]), RF('IPE300', [4000, 0, 5000], [8000, 0, 6000])],
  [BM('HEA240', [0, 0, 5000], [4000, 0, 5000]), RF('HEA240', [4000, 0, 5000], [8000, 0, 5800])],
  [BM('IPE300', [0, 0, 5000], [4000, 0, 5000]), RF('IPE300', [4000, 0, 5000], [7000, 2500, 5800])]);
add('t41_plate', 'T41', [BM('IPE300', [0, 0, 5000], [4000, 0, 5000]), RF('IPE300', [4000, 0, 5000], [8000, 0, 6000])], { tp: 20, a: 5 });
tri('Ttc', [BR('SHS150x150x8', [0, 0, 5000], [4000, 0, 5000]), BR('SHS150x150x8', [4000, 0, 5000], [7000, 0, 7000])],
  [BR('CHS168.3x6', [0, 0, 5000], [4000, 0, 5000]), BR('CHS168.3x6', [4000, 0, 5000], [7000, 0, 7000])],
  [BR('RHS200x100x6', [0, 0, 5000], [4000, 0, 5000]), BR('RHS200x100x6', [4000, 0, 5000], [6500, 2000, 6500])]);
add('ttc_plate', 'Ttc', [BR('SHS150x150x8', [0, 0, 5000], [4000, 0, 5000]), BR('SHS150x150x8', [4000, 0, 5000], [7000, 0, 7000])], { tp: 15 });
// ---- cross: two crossing beams, a seating plate in the gap (T30 crossing)
tri('T4', [BM('IPE300', [0, -3000, 5000], [0, 3000, 5000]), BM('IPE240', [-3000, 0, 5260], [3000, 0, 5260])],
  [BM('HEA300', [0, -3000, 5000], [0, 3000, 5000]), BM('UPN200', [-3000, 0, 5215], [3000, 0, 5215])],
  [BM('IPE300', [0, -3000, 5000], [0, 3000, 5000]), BM('IPE240', [-2500, -1500, 5260], [2500, 1500, 5260])]);
add('t4_touching', 'T4', [BM('IPE300', [0, -3000, 5000], [0, 3000, 5000]), BM('IPE240', [-3000, 0, 5240], [3000, 0, 5240])], { a: 8 });
// ---- chamfer (one part; the end nearest the picked point)
add('t1057_standard', 'T1057', [BM('IPE300', [0, 0, 5000], [6000, 0, 5000])]);
add('t1057_odd_profile', 'T1057', [BM('HEA200', [0, 0, 5000], [6000, 0, 5000], { rot: 'Front' })], {}, { pt: [100, 0, 4900] });
add('t1057_skewed', 'T1057', [RF('IPE360', [0, 0, 5000], [5000, 2000, 6500])], {}, { pt: [4900, 1960, 6400] });

// ---- OP
tri('T92', [BM('IPE500', [0, 0, 5000], [6000, 0, 5000]), BR('CHS114.3x5', [3000, -500, 4750], [3000, 500, 4750])],
  [BM('HEA400', [0, 0, 5000], [6000, 0, 5000]), BR('RHS120x80x5', [3000, -500, 4810], [3000, 500, 4810])],
  [BM('IPE500', [0, 0, 5000], [6000, 0, 5000]), BR('CHS88.9x4', [2700, -520, 4750], [3300, 520, 4750])]);
add('t92_gap', 'T92', [BM('IPE500', [0, 0, 5000], [6000, 0, 5000]), BR('CHS114.3x5', [3000, -500, 4750], [3000, 500, 4750])], { gap: 25 });
add('t1029_standard', 'T1029', [BM('IPE500', [0, 0, 5000], [6000, 0, 5000])]);
add('t1029_odd_profile', 'T1029', [BM('HEA400', [0, 0, 5000], [6000, 0, 5000])], { d: 120 }, { pt: [2000, 0, 4800] });
add('t1029_skewed', 'T1029', [RF('IPE600', [0, 0, 5000], [6000, 2000, 6500])], {}, { pt: [3000, 1000, 5400] });
for (const k of ['T1058', 'T1059']) { const t = k.toLowerCase();
  add(`${t}_standard`, k, [COL('SHS200x200x8', { p2: [0, 0, 4000] })]);
  add(`${t}_odd_profile`, k, [COL('CHS219.1x8', { p2: [0, 0, 4000] })], { t: 15 }, { pt: [0, 0, 1500] });
  add(`${t}_skewed`, k, [RF('RHS200x100x6', [0, 0, 5000], [5000, 1500, 6200], { depth: 'Middle' })], {}, { pt: [2500, 750, 5600] });
  add(`${t}_i_column`, k, [COL('HEB300', { p2: [0, 0, 4000] })]); }
add('trh_standard', 'Trh', [BM('IPE400', [0, 0, 5000], [6000, 0, 5000])]);
add('trh_odd_profile', 'Trh', [BM('HEA300', [0, 0, 5000], [6000, 0, 5000])], { d: 40 }, { pt: [1500, 0, 4850] });
add('trh_skewed', 'Trh', [RF('IPE400', [0, 0, 5000], [5000, 2000, 6500])], { d: 60 }, { pt: [2500, 1000, 5600] });

// ---- TU: saddles (with the hole into the main tube for Tth / Ttm)
const sadS = [COL('CHS219.1x8'), BM('CHS114.3x5', [0, 0, 5000], [4000, 0, 5000], { depth: 'Middle' })];
const sadO = [COL('SHS200x200x8'), BM('RHS120x80x5', [0, 0, 5000], [4000, 0, 5000], { depth: 'Middle' })];
const sadW = [COL('CHS219.1x8'), BR('CHS88.9x4', [0, 0, 3000], [3000, 1000, 5500])];
for (const k of ['Tts', 'Tth', 'Ttm']) tri(k, sadS, sadO, sadW);
// slotted hole: a plate through a tube end
tri('Ttsl', [BR('CHS114.3x5', [0, 0, 3000], [3000, 0, 3000]), ['PLATE', { prof: 'PL15*200', p1: [-200, 0, 3000], p2: [300, 0, 3000] }]],
  [BR('SHS100x100x5', [0, 0, 3000], [3000, 0, 3000]), ['PLATE', { prof: 'PL10*80', p1: [-150, 0, 3000], p2: [250, 0, 3000] }]],
  [BR('CHS114.3x5', [0, 0, 3000], [3000, 1000, 4500]), ['PLATE', { prof: 'PL15*200', p1: [-180, -60, 2910], p2: [270, 90, 3135] }]]);
// end plate on a tube (Hql, plate with nail: front plate)
for (const k of ['T46', 'T47n', 'T47p']) tri(k, [COL(), BM('SHS150x150x8', [0, 0, 6000], [5000, 0, 6000])],
  [COL(), BM('CHS168.3x6', [0, 0, 6000], [5000, 0, 6000], { depth: 'Middle' })],
  [COL(), RF('RHS200x100x6', [0, 0, 6000], [5000, 1200, 6800])]);
add('t46_bolt24', 'T46', [COL(), BM('SHS150x150x8', [0, 0, 6000], [5000, 0, 6000])], { bolt: 24, grade: '10.9' });
module.exports = cases;
