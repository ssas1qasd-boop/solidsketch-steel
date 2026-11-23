// S9: end plates (EP: the 15 components other than 144), clip angles (CA, 11) and bent plates (BE, 2).
// Pick order ms (main, secondary) or mss (main, secondary 1, secondary 2). Per component: standard (beam into the web of an HEA300
// column, the default rot Front), odd profile (into a turned HEB300 column's flange, or a beam into a main beam's web: coped), and a sloped
// rafter / skewed beam, plus option cases.
const COL = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }];
const COLF = ['COLUMN', { prof: 'HEB300', p1: [0, 0, 0], p2: [0, 0, 7500], rot: 'Top' }];      // turned: the beams land on its flanges (the default rot Front: on the web)
const MB = ['BEAM', { prof: 'IPE500', p1: [0, -3000, 7000], p2: [0, 3000, 7000] }];            // main beam along y
const bx = (prof, s, o) => ['BEAM', Object.assign({ prof, p1: [0, 0, 7000], p2: [s * 6000, 0, 7000] }, o || {})];
const raf = (prof, s) => ['RAFTER', { prof, p1: [0, 0, 7000], p2: [s * 6000, 0, 8000] }];
const skew = (prof, s) => ['BEAM', { prof, p1: [0, 0, 7000], p2: [s * 5638, 2052, 7000] }];
const cases = {};
const add = (name, k, members, q) => { cases[name] = { k, members, q: q || {} }; };
// ---- EP single sided
for (const k of ['T27s', 'T29', 'T32', 'T48', 'T50', 'T65', 'T97', 'T101', 'T111', 'T119']) {
  const t = k.toLowerCase();
  add(`${t}_standard`, k, [COL, bx('IPE360', 1)]);
  add(`${t}_odd_profile`, k, [COLF, bx('IPE300', 1, { p1: [0, 0, 6500], p2: [6000, 0, 6500] })]);
  add(`${t}_sloped`, k, [COL, raf('IPE360', 1)]);
  add(`${t}_beam_web`, k, [MB, bx('IPE300', 1)]);
}
add('t29_skewed', 'T29', [MB, skew('IPE300', 1)]);
add('t101_shim_cols4', 'T101', [COL, bx('IPE450', 1)], { shim: 3, cols: 4, gauge2: 60, side: 25 });
add('t32_hea', 'T32', [['COLUMN', { prof: 'HEB400', p1: [0, 0, 0], p2: [0, 0, 7500] }], bx('HEA260', 1)], { rows: 3, ext: 20, extb: 20 });
// ---- EP two sided
for (const k of ['T24', 'T34', 'T112', 'T115', 'T142']) {
  const t = k.toLowerCase();
  add(`${t}_standard`, k, [COL, bx('IPE360', 1), bx('IPE360', -1)]);
  add(`${t}_odd_profile`, k, [COLF, bx('IPE300', 1), bx('IPE270', -1)]);
  add(`${t}_sloped`, k, [COL, raf('IPE360', 1), raf('IPE360', -1)]);
  add(`${t}_beam_web`, k, [MB, bx('IPE300', 1), bx('IPE300', -1)]);
}
add('t142_skewed', 'T142', [MB, skew('IPE300', 1), skew('IPE300', -1)]);
// ---- CA single sided (T82 / T68: single angle)
for (const k of ['T3', 'T31', 'T44', 'T82', 'T116', 'T141', 'T68']) {
  const t = k.toLowerCase();
  add(`${t}_standard`, k, [COL, bx('IPE360', 1)]);
  add(`${t}_odd_profile`, k, [MB, bx('IPE300', 1)]);
  add(`${t}_skewed`, k, [MB, skew('IPE300', 1)]);
  add(`${t}_sloped`, k, [COL, raf('IPE360', 1)]);
}
add('t141_col_flange', 'T141', [COLF, bx('IPE300', 1)], { rows: 3 });
add('t3_weldmain', 'T3', [COL, bx('IPE400', 1)], { weldMain: 1, leg: 100, t: 12 });
add('t82_hea', 'T82', [COL, bx('HEA240', 1)], { pitch: 60, e: 35 });
// ---- CA two sided
for (const k of ['T25', 'T33', 'T117', 'T143']) {
  const t = k.toLowerCase();
  add(`${t}_standard`, k, [COL, bx('IPE360', 1), bx('IPE360', -1)]);
  add(`${t}_odd_profile`, k, [MB, bx('IPE300', 1), bx('IPE270', -1)]);
  add(`${t}_skewed`, k, [MB, skew('IPE300', 1), skew('IPE300', -1)]);
  add(`${t}_sloped`, k, [COL, raf('IPE360', 1), raf('IPE360', -1)]);
}
add('t143_col_flange', 'T143', [COLF, bx('IPE300', 1), bx('IPE300', -1)]);
// ---- BE bent plates
for (const k of ['T151', 'T190']) {
  const t = k.toLowerCase();
  add(`${t}_standard`, k, [COL, bx('IPE360', 1)]);
  add(`${t}_odd_profile`, k, [MB, bx('IPE300', 1)]);
  add(`${t}_skewed`, k, [MB, skew('IPE300', 1)]);
  add(`${t}_sloped`, k, [COL, raf('IPE360', 1)]);
}
add('t151_leg0', 'T151', [COL, bx('IPE400', 1)], { leg: 0, t: 12 });
add('t190_two', 'T190', [MB, bx('IPE300', 1), bx('IPE300', -1)], { t: 8 });
module.exports = cases;
