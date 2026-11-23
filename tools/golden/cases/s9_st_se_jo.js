// S9: families ST (the 34 shear plate components besides 146), SE (seats, 11) and JO (joists, 6). Per component: a standard
// case, an odd profile (other section, turned column, tube ...) and a skewed or sloped one, plus option cases.
const COL = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }];
const COLW = ['COLUMN', { prof: 'HEM300', p1: [0, 0, 0], p2: [0, 0, 7500], rot: 'Top' }];          // beam into the web
const COLB = ['COLUMN', { prof: 'HEB260', p1: [0, 0, 0], p2: [0, 0, 7500], rot: 'Top' }];
const B360 = ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] }];
const B240 = ['BEAM', { prof: 'IPE240', p1: [0, 0, 6500], p2: [6000, 0, 6500] }];
const BHEA = ['BEAM', { prof: 'HEA200', p1: [0, 0, 6500], p2: [5000, 0, 6500] }];
const MB = ['BEAM', { prof: 'IPE500', p1: [0, -3000, 7000], p2: [0, 3000, 7000] }];
const MB4 = ['BEAM', { prof: 'IPE450', p1: [0, -3000, 7000], p2: [0, 3000, 7000] }];
const SKEW = ['BEAM', { prof: 'IPE300', p1: [0, 0, 7000], p2: [5638, 2052, 7000] }];
const PERP = ['BEAM', { prof: 'IPE300', p1: [0, 0, 7000], p2: [6000, 0, 7000] }];
const SLOPE = ['RAFTER', { prof: 'IPE330', p1: [0, 0, 6800], p2: [6000, 0, 7325] }];
const SHS = ['COLUMN', { prof: 'SHS200x200x10', p1: [0, 0, 0], p2: [0, 0, 7500] }];
const CHS = ['COLUMN', { prof: 'CHS273x10', p1: [0, 0, 0], p2: [0, 0, 7500] }];
const RHS = ['COLUMN', { prof: 'RHS300x200x10', p1: [0, 0, 0], p2: [0, 0, 7500] }];
const cases = {};
const add = (k, name, members, q, extra) => { cases[k.toLowerCase() + '_' + name] = Object.assign({ k, members, q: q || {} }, extra || {}); };

// ---- ST: generic triple (column flange, turned column web with another section, skewed into a main beam)
const stPlain = ['T34s', 'T35', 'T40s', 'T64', 'T103', 'T17', 'T43w', 'T129', 'T32t', 'T38'];
for (const k of stPlain) { add(k, 'standard', [COL, B360]); add(k, 'odd_profile', [COLW, B240], { rows: 3 }); add(k, 'skewed', [MB, SKEW]); }
add('T17', 'sloped', [COL, SLOPE]); add('T43w', 'main_beam', [MB, PERP]); add('T129', 'web', [COLB, BHEA]); add('T32t', 'sloped', [COL, SLOPE], { gap: 20 });
add('T35', 'sloped', [COL, SLOPE], { cols: 2 }); add('T103', 'side_minus', [COL, B360], { side: -1, bolt: 16, e: 35 });
// top / full depth: also into a main beam (coped)
for (const k of ['T36', 'T38t', 'T147', 'T149', 'T39f', 'T81', 'T184', 'T185', 'T185s', 'T11j']) {
  add(k, 'standard', [COL, B360]); add(k, 'odd_profile', [MB4, ['BEAM', { prof: 'HEA240', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]]); add(k, 'skewed', [MB, SKEW]);
}
add('T147', 'sloped', [COL, SLOPE]); add('T81', 'main_beam', [MB, PERP], { cols: 2 }); add('T184', 'web', [COLW, B240]); add('T185s', 'web', [COLW, B240]);
// column stiffeners: flange, web, sloped
for (const k of ['T41s', 'T80', 'T131', 'T182', 'T186', 'T187', 'T188']) { add(k, 'standard', [COL, B360]); add(k, 'odd_profile', [COLW, B240]); add(k, 'sloped', [COL, SLOPE]); }
add('T186', 'web_hea', [COLB, BHEA], { t: 12 }); add('T41s', 'skewed', [MB, SKEW]);
// tube column (through plate)
for (const k of ['T47', 'T189']) { add(k, 'standard', [SHS, PERP]); add(k, 'odd_profile', [CHS, BHEA]); add(k, 'sloped', [RHS, SLOPE]); }
add('T189', 'two', [SHS, PERP], { two: 1 });
// two sided (main part, secondary 1, secondary 2)
add('T118', 'standard', [COL, B360, ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [-6000, 0, 7000] }]]);
add('T118', 'odd_profile', [COLW, B240, ['BEAM', { prof: 'IPE270', p1: [0, 0, 6500], p2: [-6000, 0, 6500] }]]);
add('T118', 'skewed', [MB, SKEW, ['BEAM', { prof: 'IPE300', p1: [0, 0, 7000], p2: [-5638, 2052, 7000] }]]);
add('T118', 'one_beam', [COL, B360]);
// stanchions on a stringer: stringer (UPN, sloped) is the main part, the stanchion lands on it
const STR = ['BEAM', { prof: 'UPN200', p1: [0, 0, 3000], p2: [4000, 0, 5500] }];
for (const k of ['T69', 'T84', 'T86', 'T87']) {
  add(k, 'standard', [COL, B360]);
  add(k, 'odd_profile', [STR, ['COLUMN', { prof: 'SHS100x100x6', p1: [2000, 0, 4350], p2: [2000, 0, 5500] }]], { rows: 1 });
  add(k, 'sloped', [COL, SLOPE]);
}
add('T87', 'skewed', [MB, SKEW]);

// ---- SE: seats under the secondary
for (const k of ['T60', 'T72', 'T73', 'T74', 'T75', 'T170', 'T170r', 'T7', 'T12', 'T28']) {
  add(k, 'standard', [COL, B360]); add(k, 'odd_profile', [COLW, BHEA]); add(k, 'skewed', [MB, SKEW]);
}
for (const k of ['T60', 'T72', 'T7', 'T12', 'T28']) add(k, 'sloped', [COL, SLOPE]);
add('T60', 'options', [COL, B360], { L: 150, t: 15, w: 200, gap: 15, bolt: 24, top: 1 });
add('T12', 'options', [COL, B360], { L: 120, t: 15, w: 180, ts: 15, a: 8 });
add('T28', 'crane', [COL, ['BEAM', { prof: 'HEB300', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]], { L: 150, ts: 10 });
add('T104', 'standard', [SHS, PERP]); add('T104', 'odd_profile', [CHS, BHEA]); add('T104', 'sloped', [RHS, SLOPE]); add('T104', 'skewed', [MB, SKEW]);

// ---- JO: joists (cap 0: seat angle with a top angle; cap 1: cap plate on the column head)
const JOI = ['BEAM', { prof: 'IPE200', p1: [0, 0, 7000], p2: [6000, 0, 7000] }];
const JSKEW = ['BEAM', { prof: 'IPE220', p1: [0, 0, 7000], p2: [5638, 2052, 7000] }];
const JSLOPE = ['RAFTER', { prof: 'IPE200', p1: [0, 0, 7000], p2: [6000, 0, 7400] }];
for (const k of ['T58j', 'T160', 'T163']) { add(k, 'standard', [COL, JOI]); add(k, 'odd_profile', [COLW, BHEA]); add(k, 'skewed', [MB, JSKEW]); add(k, 'sloped', [COL, JSLOPE]); }
add('T58j', 'options', [COL, JOI], { L: 120, t: 12, bolt: 20 }); add('T160', 'main_beam', [MB, ['BEAM', { prof: 'IPE200', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]]);
const JTOP = ['BEAM', { prof: 'IPE200', p1: [-3000, 0, 7700], p2: [3000, 0, 7700] }];
const JTOPS = ['BEAM', { prof: 'IPE240', p1: [-2598, -1500, 7740], p2: [2598, 1500, 7740] }];
const JTOPL = ['RAFTER', { prof: 'IPE200', p1: [-3000, 0, 7600], p2: [3000, 0, 7900] }];
for (const k of ['T161', 'T164']) {
  add(k, 'standard', [COL, JTOP]); add(k, 'odd_profile', [['COLUMN', { prof: 'RHS300x200x10', p1: [0, 0, 0], p2: [0, 0, 7500], rot: 'Top' }], ['BEAM', { prof: 'HEA200', p1: [-2500, 0, 7690], p2: [2500, 0, 7690] }]]);
  add(k, 'skewed', [COL, JTOPS]); add(k, 'sloped', [COL, JTOPL]);
}
add('T161', 'bolt', [COL, JTOP], { bolt: 20 });
const JL = ['BEAM', { prof: 'IPE200', p1: [0, 0, 7700], p2: [-5000, 0, 7700] }], JR = ['BEAM', { prof: 'IPE200', p1: [0, 0, 7700], p2: [5000, 0, 7700] }];
add('T162', 'standard', [COL, JL, JR]);
add('T162', 'odd_profile', [['COLUMN', { prof: 'HEB240', p1: [0, 0, 0], p2: [0, 0, 7500] }], ['BEAM', { prof: 'HEA200', p1: [0, 0, 7690], p2: [-5000, 0, 7690] }], ['BEAM', { prof: 'IPE240', p1: [0, 0, 7740], p2: [5000, 0, 7740] }]]);
add('T162', 'skewed', [COL, ['BEAM', { prof: 'IPE200', p1: [0, 0, 7700], p2: [-4330, -2500, 7700] }], ['BEAM', { prof: 'IPE200', p1: [0, 0, 7700], p2: [4330, 2500, 7700] }]]);
add('T162', 'one_joist', [COL, JTOP]);
module.exports = cases;
