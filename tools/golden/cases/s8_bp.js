// S8 / S9: base plates, family BP (live chain v54 L15798 → v46 L15467 → v40 L15012). Pick order: one part (the column, or
// for the bearing plate 1044 a beam). Per component: standard (HEA300 column), odd profile (another section / turned), and a
// sloped column (vertical-ish: plate stays level; steep: plate square to the column), plus dialog options for 1042 / 1004 / 1014.
const col = (prof, o) => ['COLUMN', Object.assign({ prof, p1: [0, 0, 0], p2: [0, 0, 6000] }, o || {})];
const COL = col('HEA300');
const ODD = col('HEB400', { rot: 'Front' });
const SLOPE = ['COLUMN', { prof: 'HEA260', p1: [0, 0, 0], p2: [1200, 500, 6000] }];   // |a.z| > 0.8: level plate (v54 / v40 vertical branch)
const STEEP = ['COLUMN', { prof: 'IPE300', p1: [0, 0, 0], p2: [4000, 0, 3000] }];   // |a.z| < 0.8: plate square to the member
const cases = {};
const add = (name, k, members, q) => { cases[name] = { k, members, q: q || {} }; };
for (const k of ['T55', 'T71', 'T1004', 'T1014', 'T1016', 'T1031', 'T1042', 'T1047', 'T1053', 'T1066', 'T1068']) {
  const t = k.toLowerCase();
  add(`${t}_standard`, k, [COL]); add(`${t}_odd_profile`, k, [ODD]); add(`${t}_sloped`, k, [SLOPE]);
}
add('t1068_tapered', 'T1068', [col('PHI500-300-10-200-15')]);
add('t1053_shs', 'T1053', [col('SHS200*10')]);
add('t1066_rhs', 'T1066', [col('RHS300*200*10', { rAng: 30 })]);
add('t55_steep', 'T55', [STEEP]);
add('t1016_steep', 'T1016', [STEEP]);
// 1052 circular: round plate (CHS or shape round)
add('t1052_standard', 'T1052', [col('CHS273*10')]);
add('t1052_odd_profile', 'T1052', [COL]);
add('t1052_sloped', 'T1052', [['COLUMN', { prof: 'CHS219.1*8', p1: [0, 0, 0], p2: [1200, 500, 6000] }]]);
add('t1052_steep_stiff', 'T1052', [['COLUMN', { prof: 'CHS219.1*8', p1: [0, 0, 0], p2: [4000, 0, 3000] }]], { stiff: 'flange', anchors: 8 });
// 1044 bearing plate: a beam end on the plate; a column falls back to the base plate
add('t1044_standard', 'T1044', [['BEAM', { prof: 'IPE360', p1: [0, 0, 4000], p2: [6000, 0, 4000] }]]);
add('t1044_odd_profile', 'T1044', [['BEAM', { prof: 'HEB240', p1: [0, 0, 4000], p2: [0, 5000, 4000], rot: 'Front' }]]);
add('t1044_sloped', 'T1044', [['RAFTER', { prof: 'IPE300', p1: [0, 0, 4000], p2: [6000, 2000, 4600] }]]);
add('t1044_column', 'T1044', [COL]);
// 1042 / 1004 / 1014 dialogs: geometry options
add('t1042_anchors6_gauge', 'T1042', [COL], { anchors: 6, gx: 400, gy: 200 });
add('t1042_anchors8_grout', 'T1042', [COL], { anchors: 8, gt: 40, gm: 25 });
add('t1042_anchors2', 'T1042', [COL], { anchors: 2, t: 30, ext: 100, side: 80 });
add('t1042_cast_grout_pw', 'T1042', [COL], { cast: 15, gt: 30, pw: 1, bolt: 24 });
add('t1042_pw_sloped', 'T1042', [SLOPE], { pw: 1, gx: 360 });
add('t1042_stiff_box', 'T1042', [COL], { stiff: 'box', ts: 12, hs: 200, bolt: 27 });
add('t1042_stiff_web_grout', 'T1042', [ODD], { stiff: 'web', gt: 30 });
add('t1042_round', 'T1042', [COL], { shape: 'round', anchors: 6, stiff: 'web' });
add('t1042_holes_tab', 'T1042', [COL], { hn: 2, hx1: 40, hy1: 50, hx2: -40, hy2: 50, hd1: 22, hd2: 30 });
add('t1042_holes4_grout', 'T1042', [COL], { hn: 4, hx1: 60, hx2: 60, gt: 30 });
// v58 bolt options through stBolts (TKB_Q = the component's settings)
add('t1042_oversized', 'T1042', [COL], { htype: 'Oversized', sl2: 6 });
add('t1042_no_washer_nut_blen', 'T1042', [COL], { ba_w: 0, ba_n: 0, blen: 40, gt: 30 });
add('t1042_slotted_rot_tol', 'T1042', [COL], { sl1: 20, srot: 1, tol: 4 });
add('t1042_bdel_pw', 'T1042', [COL], { anchors: 8, bdel: '2 5', pw: 1 });
add('t1042_bdir_tapped', 'T1042', [COL], { bdir: 2, htype: 'Tapped', thr: 0, sw: 'Workshop', bcmt: 'AB' });
add('t1004_stiff_flange_t40', 'T1004', [COL], { stiff: 'flange', t: 40, hs: 250 });
add('t1004_anchors8_m30', 'T1004', [ODD], { anchors: 8, bolt: 30, grade: '8.8', anch: 600 });
add('t1004_cast_gauge', 'T1004', [COL], { cast: 10, gx: 420, gy: 260 });
add('t1004_grout_holes', 'T1004', [SLOPE], { gt: 50, gm: 40, hn: 1, hx1: 0, hy1: 0, hd1: 40 });
add('t1014_box_ext', 'T1014', [COL], { stiff: 'box', ext: 120, side: 70, anchors: 6 });
add('t1014_web_ts', 'T1014', [ODD], { stiff: 'web', ts: 20, hs: 120 });
add('t1014_gauge_drops_stiff', 'T1014', [COL], { gx: 450, gy: 220, pw: 1 });
add('t1014_none_anchors2', 'T1014', [SLOPE], { stiff: 'none', anchors: 2, t: 20 });
add('t1014_steep_box', 'T1014', [STEEP], { stiff: 'box', anchors: 8 });
add('t1014_holes_oversized', 'T1014', [COL], { hn: 2, htype: 'Oversized', sl2: 8 });
module.exports = cases;
