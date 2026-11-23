// S9: SP splices (19 components) and PU purlin / rail cleats, laps, sleeves (9 components). Pick order: main part, secondary part.
// SP: two members end to end (standard: a beam splice; odd profile: a column splice or another section; skewed: a sloped rafter
// that also runs skew in plan). Stub splices (133 / 135): a beam on a column (the stub is welded to the column).
// PU: a purlin across a rafter's top flange (standard: cold-formed C on an IPE360 rafter; odd profile: I / Z / tube purlin;
// skewed: a sloped rafter with a purlin skewed in plan); overlap (1c) joins a second purlin (third member).
const cases = {};
const add = (name, k, members, q, extra) => { cases[name] = Object.assign({ k, members, q: q || {} }, extra || {}); };
// ---- SP
const pair = (set, prof, a, m, b, o) => [[set, Object.assign({ prof, p1: a, p2: m }, o || {})], [set, Object.assign({ prof, p1: m, p2: b }, o || {})]];
const beam = prof => pair('BEAM', prof, [0, 0, 6000], [3000, 0, 6000], [6000, 0, 6000]);
const col = prof => pair('COLUMN', prof, [0, 0, 0], [0, 0, 4000], [0, 0, 8000]);
const raf = prof => pair('RAFTER', prof, [0, 0, 6000], [3000, 1000, 6500], [6000, 2000, 7000]);
const brc = prof => pair('BRACING', prof, [0, 0, 3000], [3000, 0, 5000], [6000, 0, 7000]);
const brs = prof => pair('BRACING', prof, [0, 0, 3000], [2500, 1500, 4000], [5000, 3000, 5000]);
const tri = (k, a, b, c, q, f) => { const t = k.toLowerCase(); const [fa, fb, fc] = f || [beam, col, raf]; add(`${t}_standard`, k, fa(a), q); add(`${t}_odd_profile`, k, fb(b), q); add(`${t}_skewed`, k, fc(c), q); };
// end plates (14j, 26, 124 round) and tube end plates (6)
tri('T14j', 'IPE300', 'HEB300', 'IPE360');
tri('T26', 'HEA260', 'SHS200x200x8', 'IPE400', {}, [beam, col, raf]);
tri('T124', 'IPE300', 'CHS219.1x8', 'HEA240');
tri('T6', 'CHS168.3x6', 'SHS150x150x8', 'RHS200x100x8');
add('t14j_ext_bolts', 'T14j', beam('IPE450'), { ext: 40, tp: 25, bolt: 24 });
add('t6_n6', 'T6', col('CHS219.1x8'), { n: 6, ext: 10 });
// web tab (33t, 12j), cover plates (42, 45, 53, 77, 125, 132), erection plates + site weld (137), angles (152, 175)
for (const k of ['T33t', 'T12j', 'T137', 'T152', 'T175']) tri(k, 'IPE300', 'HEB300', 'IPE400');
for (const k of ['T42', 'T45', 'T53', 'T77', 'T125', 'T132']) tri(k, 'IPE300', 'HEB300', 'IPE400');
add('t42_cols3_wrows3', 'T42', beam('IPE500'), { cols: 3, wrows: 3, pitch: 70, tfp: 20 });
add('t42_wrows1', 'T42', beam('HEA200'), { wrows: 1, cols: 1 });
add('t152_cols3', 'T152', beam('HEB400'), { cols: 3, gap: 6 });
add('t33t_gap', 'T33t', beam('IPE450'), { gap: 20, e: 50, twp: 12 });
// turnbuckle (126) and lap (176) on bracing
tri('T126', 'D24', 'CHS60.3x3.2', 'D20', {}, [brc, brc, brs]);
tri('T176', 'L80*80*8', 'UPN160', 'L100*100*10', {}, [brc, brc, brs]);
// stub splices (133, 135): column + beam
const C1 = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }];
const stubStd = prof => [C1, ['BEAM', { prof, p1: [0, 0, 7000], p2: [6000, 0, 7000] }]];
const stubOdd = prof => [['COLUMN', { prof: 'HEB240', p1: [0, 0, 0], p2: [0, 0, 7500] }], ['BEAM', { prof, p1: [0, 0, 7000], p2: [0, 6000, 7000] }]];
const stubSkew = prof => [C1, ['RAFTER', { prof, p1: [0, 0, 7000], p2: [6000, 0, 7600] }]];
tri('T133', 'IPE300', 'IPE240', 'IPE360', {}, [stubStd, stubOdd, stubSkew]);
tri('T135', 'IPE330', 'HEA200', 'IPE300', {}, [stubStd, stubOdd, stubSkew]);
add('t133_ls_cols', 'T133', stubStd('IPE400'), { Ls: 700, cols: 3, wrows: 3 });
// ---- PU: rafter (main) + purlin
const RAF = ['RAFTER', { prof: 'IPE360', p1: [0, 0, 6000], p2: [8000, 0, 6000] }];
const RAFS = ['RAFTER', { prof: 'IPE400', p1: [0, 0, 6000], p2: [8000, 0, 6800] }];
const pStd = prof => [RAF, ['PURLIN', { prof, p1: [4000, -3000, 6000], p2: [4000, 3000, 6000] }]];
const pOdd = prof => [RAF, ['PURLIN', { prof, p1: [4000, -3000, 6000], p2: [4000, 3000, 6000] }]];
const pSkew = prof => [RAFS, ['PURLIN', { prof, p1: [3500, -3000, 6400], p2: [4500, 3000, 6400] }]];
const puTri = (k, a, b, c, q) => { const t = k.toLowerCase(); add(`${t}_standard`, k, pStd(a), q); add(`${t}_odd_profile`, k, pOdd(b), q); add(`${t}_skewed`, k, pSkew(c), q); };
for (const k of ['T61p', 'T93', 'Tpc']) puTri(k, 'C200-2.5-20-70', 'IPE160', 'Z200-2.5-20-70');
for (const k of ['T8', 'T70']) puTri(k, 'C200-2.5-20-70', 'IPE180', 'Z200-2.5-20-70');
puTri('T113', 'RHS120x80x5', 'SHS100x100x5', 'RHS120x80x5');
puTri('T15', 'IPE160', 'C200-2.5-20-70', 'IPE200');
puTri('T2c', 'C200-2.5-20-70', 'C250-3-25-80', 'Z200-2.5-20-70');
add('t61p_side_h', 'T61p', pOdd('IPE200'), { side: -1, h: 150, w: 100, t: 10 });
add('t70_leg', 'T70', pStd('C250-3-25-80'), { leg: 100, h: 160 });
add('t2c_ts0', 'T2c', pStd('C200-2.5-20-70'), { ts: 0, lap: 400 });
// overlap (1c): two purlins lapped over the rafter (third member = second purlin)
const ovl = (raf, x, z, prof, o2) => [raf, ['PURLIN', { prof, p1: [x, -6000, z], p2: [x, 600, z] }], ['PURLIN', Object.assign({ prof, p1: [x, -600, z], p2: [x, 6000, z] }, o2 || {})]];
add('t1c_standard', 'T1c', ovl(RAF, 4000, 6000, 'C200-2.5-20-70', { pOff: 2.5 }));
add('t1c_odd_profile', 'T1c', ovl(RAF, 4000, 6000, 'Z200-2.5-20-70', { pOff: 3 }));
add('t1c_skewed', 'T1c', [RAFS, ['PURLIN', { prof: 'C250-3-25-80', p1: [3500, -3000, 6400], p2: [4060, 600, 6400] }], ['PURLIN', { prof: 'C250-3-25-80', p1: [3906, -600, 6400], p2: [4500, 3000, 6400], pOff: 3 }]]);
add('t1c_two_parts', 'T1c', pStd('C200-2.5-20-70'), { lap: 400 });
module.exports = cases;
