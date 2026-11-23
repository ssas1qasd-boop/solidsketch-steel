// S9: GU gusset bracing (32 components). Pick order: main part, brace (ms), or main part 1, main part 2, brace (corner: mmb).
// Per component: standard (a brace onto an HEA300 column's flange, in the x-z plane), odd profile (another brace section, or a
// brace from below onto an IPE400 beam), and a skewed brace (out of the column's plane), plus option cases.
const COL = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7000] }];
const BEAM = ['BEAM', { prof: 'IPE400', p1: [0, 0, 6000], p2: [8000, 0, 6000] }];
const BR = (p1, p2, prof) => ['BRACING', { prof, p1, p2 }];
const std = prof => [COL, BR([150, 0, 3000], [4150, 0, 6000], prof)];
const odd = prof => [BEAM, BR([4000, 0, 5600], [1000, 0, 2600], prof)];
const skew = prof => [COL, BR([150, 0, 3000], [3150, 2000, 5500], prof)];
// corner: column + beam + brace into the corner
const CB = ['BEAM', { prof: 'IPE400', p1: [0, 0, 6000], p2: [6000, 0, 6000] }];
const cstd = prof => [COL, CB, BR([300, 0, 5500], [4000, 0, 1500], prof)];
const codd = prof => [COL, ['RAFTER', { prof: 'IPE360', p1: [0, 0, 6000], p2: [6000, 0, 6600] }], BR([300, 0, 5500], [4000, 0, 1500], prof)];
const cskew = prof => [COL, CB, BR([300, 0, 5500], [4000, 1200, 1500], prof)];
// crossing braces: main part = brace 1, secondary = brace 2
const X1 = prof => BR([0, 0, 0], [6000, 0, 4000], prof);
const cases = {};
const add = (name, k, members, q) => { cases[name] = { k, members, q: q || {} }; };
const tri = (k, a, b, c, q) => { const t = k.toLowerCase(); add(`${t}_standard`, k, std(a), q); add(`${t}_odd_profile`, k, odd(b), q); add(`${t}_skewed`, k, skew(c), q); };
// bolted / bent / tee / angle / welded / direct: angle braces, then a channel / I / hollow
for (const k of ['T11', 'T62', 'T110', 'T169', 'T196']) tri(k, 'L100*100*10', 'UPN160', 'L80*80*8');
for (const k of ['T12d', 'T140']) tri(k, 'L100*100*10', 'IPE200', 'L90*90*9');
for (const k of ['T1050', 'T1051', 'Tgt']) tri(k, 'L100*100*10', 'UPN140', 'L80*80*8');
tri('T10', 'L100*100*10', 'CHS88.9x4', 'UPN120');
tri('T5', 'L100*100*10', 'UPN160', 'L80*80*8');
// tube slotted onto the gusset
for (const k of ['T20', 'T67', 'T105']) tri(k, 'CHS88.9x4', 'SHS100x100x5', 'RHS120x80x5');
// rods, fork, strips, squeezed tube ends
for (const k of ['T1w', 'T16']) tri(k, 'D24', 'D30', 'D20');
tri('T7t', 'D30', 'CHS60.3x4', 'D24');
tri('T52', 'CHS88.9x4', 'CHS76.1x4', 'CHS60.3x3.2');
for (const k of ['T102t', 'T103t']) tri(k, 'CHS76.1x4', 'CHS60.3x3.2', 'CHS88.9x4');
// corner gussets (column + beam + brace)
for (const [k, a, b, c] of [['T56', 'CHS88.9x4', 'SHS100x100x5', 'CHS76.1x4'], ['T59', 'CHS88.9x4', 'RHS120x80x5', 'CHS76.1x4'], ['T57', 'L100*100*10', 'UPN160', 'L80*80*8'],
  ['T58', 'L100*100*10', 'IPE200', 'L80*80*8'], ['T63', 'L100*100*10', 'UPN140', 'L80*80*8'], ['T165', 'L100*100*10', 'UPN160', 'L90*90*9'], ['T60w', 'L100*100*10', 'UPN160', 'L80*80*8']]) {
  const t = k.toLowerCase(); add(`${t}_standard`, k, cstd(a)); add(`${t}_odd_profile`, k, codd(b)); add(`${t}_skewed`, k, cskew(c));
}
// crossing braces (cross / central): angles offset in y (packing / centre gusset), tubes in one plane (bolt only), a skewed pair
for (const k of ['T19', 'T22', 'T61', 'T18']) {
  const t = k.toLowerCase();
  add(`${t}_standard`, k, [X1('L100*100*10'), BR([0, 120, 4000], [6000, 120, 0], 'L100*100*10')]);
  add(`${t}_odd_profile`, k, [X1('CHS88.9x4'), BR([0, 0, 4000], [6000, 0, 0], 'CHS88.9x4')]);
  add(`${t}_skewed`, k, [X1('L80*80*8'), BR([0, 400, 4000], [6000, -200, 0], 'L80*80*8')]);
}
// options
add('t11_nb3_side', 'T11', std('L100*100*10'), { nb: 3, side: -1, t: 15, e: 50 });
add('t11_ibrace', 'T11', std('IPE200'));
add('t11_beam_rhs', 'T11', odd('RHS120x80x5'));
add('t11_web', 'T11', [['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7000], rot: 'Top' }], BR([150, 0, 3000], [4150, 0, 6000], 'L100*100*10')]);
add('t62_noclip_lap', 'T62', std('L100*100*10'), { clip: 0, lap: 250, setback: 40 });
add('t20_lap_slot', 'T20', std('CHS88.9x4'), { lap: 200, slot: 5, clear: 30 });
add('t10_sloped_beam', 'T10', [['RAFTER', { prof: 'IPE360', p1: [0, 0, 6000], p2: [8000, 0, 6700] }], BR([4000, 0, 6100], [1000, 0, 2600], 'L100*100*10')]);
add('t7t_nb3', 'T7t', std('D30'), { nb: 3, pb: 60 });
add('t102t_rhs', 'T102t', std('RHS120x80x5'));
add('t16_lap', 'T16', std('D24'), { lap: 300 });
add('t52_lap', 'T52', std('CHS88.9x4'), { lap: 180, t: 15 });
add('t5_side', 'T5', std('L100*100*10'), { side: -1, e: 50, pb: 80 });
add('t19_bolt24', 'T19', [X1('L100*100*10'), BR([0, 120, 4000], [6000, 120, 0], 'L100*100*10')], { bolt: 24 });
module.exports = cases;
