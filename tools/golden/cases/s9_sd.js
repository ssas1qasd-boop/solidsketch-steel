// S9: family SD (details at a picked point on one part, 26 components). Per component: a standard case, an odd profile
// (other section, tube, channel, turned) and a skewed or sloped one; most with a realistic picked point (c.pt), some without.
const BM = ['BEAM', { prof: 'IPE300', p1: [0, 0, 4000], p2: [6000, 0, 4000] }];          // top at z 4000, centre 3850
const BHEA = ['BEAM', { prof: 'HEA260', p1: [0, 0, 4000], p2: [5000, 0, 4000] }];
const UPN = ['BEAM', { prof: 'UPN240', p1: [0, 0, 4000], p2: [5000, 0, 4000] }];
const SLOPE = ['RAFTER', { prof: 'IPE360', p1: [0, 0, 6000], p2: [6000, 0, 6600] }];     // top line z = 6000 + 0.1·x
const SKEW = ['BEAM', { prof: 'IPE300', p1: [0, 0, 4000], p2: [4698, 1710, 4000] }];
const COL = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 6000] }];
const COLW = ['COLUMN', { prof: 'HEB240', p1: [0, 0, 0], p2: [0, 0, 6000], rot: 'Top' }];
const SHS = ['COLUMN', { prof: 'SHS200x200x10', p1: [0, 0, 0], p2: [0, 0, 6000] }];
const CHS = ['COLUMN', { prof: 'CHS273x10', p1: [0, 0, 0], p2: [0, 0, 6000] }];
const RHS = ['BEAM', { prof: 'RHS300x200x10', p1: [0, 0, 4000], p2: [6000, 0, 4000] }];
const LEAN = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [600, 300, 6000] }];
const cases = {};
const add = (k, name, members, q, pt) => { cases[k.toLowerCase() + '_' + name] = Object.assign({ k, members, q: q || {} }, pt ? { pt } : {}); };

// picked points: top flange / web side / bottom of the beams, sides of the columns
const P = { bmTop: [2200, 0, 4000], bmWeb: [2700, 60, 3850], bmBot: [3300, 0, 3700], bmEnd2: [5850, 0, 4000], bmEnd1: [150, 40, 3850],
  slTop: [3000, 0, 6300], slWeb: [2400, 70, 6060], slEnd1: [200, 0, 6020], skTop: [2349, 855, 4000], skWeb: [1860, 733, 3850],
  colF: [0, 150, 1800], colW: [150, 0, 2500], colTop: [0, 140, 5900], colBot: [0, 150, 120], leanF: [300, 300, 3000] };

// stiffeners / hole / ring / doubler / studs: along the member at the picked point
for (const k of ['T1030', 'T1041', 'T1017', 'T1022', 'Tpd', 'T1060']) {
  add(k, 'standard', [BM], {}, P.bmWeb); add(k, 'odd_profile', [BHEA]); add(k, 'sloped', [SLOPE], {}, P.slWeb); add(k, 'skewed', [SKEW], {}, P.skTop);
}
add('T1030', 'column', [COL], { t: 15 }, P.colF); add('T1041', 'channel', [UPN], {}, [1200, 0, 4000]); add('T1017', 'options', [BM], { L: 500, t: 10 }, P.bmTop);
add('T1022', 'options', [COLW], { L: 600, t: 15 }, P.colW); add('Tpd', 'column', [COL], {}, P.colW); add('T1060', 'options', [BM], { d: 40, t: 10 }, P.bmTop);
add('T1060', 'column', [COL], {}, P.colF);
add('T1064', 'standard', [BM]); add('T1064', 'odd_profile', [COL], { s: 1500, t: 15 }); add('T1064', 'sloped', [SLOPE], { s: 800 }); add('T1064', 'skewed', [SKEW], { s: 700 }, P.skWeb);
add('T1010', 'standard', [BM], {}, P.bmTop); add('T1010', 'odd_profile', [BHEA], { s: 300 }); add('T1010', 'sloped', [SLOPE], { s: 250 }, P.slTop); add('T1010', 'skewed', [SKEW], { s: 400 });
add('Trp', 'standard', [CHS], {}, [0, 140, 3000]); add('Trp', 'odd_profile', [SHS], { t: 15 }); add('Trp', 'sloped', [SLOPE], {}, P.slWeb); add('Trp', 'skewed', [LEAN], { t: 20 }, P.leanF);
// at the member end nearest the picked point
for (const k of ['T1002', 'Tcap', 'T1069', 'T1020']) {
  add(k, 'standard', [BM], {}, P.bmEnd2); add(k, 'odd_profile', [SHS]); add(k, 'sloped', [SLOPE], {}, P.slEnd1); add(k, 'skewed', [SKEW], {}, [150, 60, 3850]);
  add(k, 'column', [COL], {}, k === 'Tcap' ? P.colTop : P.colBot);
}
add('T1002', 'options', [BHEA], { t: 25 }, [200, 0, 3740]); add('Tcap', 'chs', [CHS], { t: 10 }, [0, 0, 5800]); add('T1069', 'lean', [LEAN], {}, [30, 160, 100]);
add('T1020', 'channel', [UPN], {}, [4900, 0, 3880]);
// on the face at the picked point
for (const k of ['T1040', 'T1048', 'T1049', 'T1026', 'T114', 'T1065', 'T171', 'T1011', 'T1013', 'T1015', 'T1067', 'T1031l']) {
  add(k, 'standard', [COL], {}, P.colF); add(k, 'odd_profile', [RHS], {}, [2000, 0, 3900]); add(k, 'sloped', [SLOPE], {}, P.slTop); add(k, 'skewed', [SKEW], {}, P.skWeb);
}
for (const k of ['T1040', 'T1026', 'T1065', 'T171', 'T1011', 'T1015', 'T1031l']) add(k, 'web', [COL], {}, P.colW);
for (const k of ['T1048', 'T114', 'T1013', 'T1067']) add(k, 'no_point', [BHEA]);
add('T1026', 'options', [BM], { h: 200, w: 250, t: 15 }, P.bmBot); add('T1065', 'options', [BM], { h: 250, w: 300, t: 10 }, P.bmWeb);
add('T171', 'channel', [UPN], {}, [2500, 20, 3880]); add('T1011', 'chs', [CHS], {}, [140, 0, 2000]); add('T1015', 'options', [BM], { t: 25 }, P.bmBot);
// stiffeners in the main part at a secondary's flanges
const B360 = ['BEAM', { prof: 'IPE360', p1: [0, 0, 4500], p2: [6000, 0, 4500] }];
add('T1003', 'standard', [COL, B360]);
add('T1003', 'odd_profile', [COLW, ['BEAM', { prof: 'HEA240', p1: [0, 0, 4500], p2: [6000, 0, 4500] }]], { t: 15 });
add('T1003', 'sloped', [COL, ['RAFTER', { prof: 'IPE330', p1: [0, 0, 4300], p2: [6000, 0, 4825] }]]);
add('T1003', 'skewed', [['BEAM', { prof: 'IPE500', p1: [0, -3000, 4500], p2: [0, 3000, 4500] }], ['BEAM', { prof: 'IPE300', p1: [0, 0, 4500], p2: [5638, 2052, 4500] }]]);
add('T1003', 'one_part', [COL], {}, P.colF);
module.exports = cases;
