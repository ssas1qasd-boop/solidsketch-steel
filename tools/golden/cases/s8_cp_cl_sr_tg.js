// S8: families CP (cap plates / seating on a column head, crossing beams), CL (purlin cleat Tcl), SR (sag rod Tsag),
// TG (tongue plate tube end Ttg, tube crossing Ttgx). Per component: standard, odd profile, skewed or sloped, plus options.
const COL = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 6000] }];
const BTOP = ['BEAM', { prof: 'IPE300', p1: [-3000, 0, 6300], p2: [3000, 0, 6300] }];
const COLR = ['COLUMN', { prof: 'RHS300x200x10', p1: [0, 0, 0], p2: [0, 0, 6000], rot: 'Top' }];
const BHEA = ['BEAM', { prof: 'HEA240', p1: [-2500, 0, 6230], p2: [2500, 0, 6230] }];
const BSKEW = ['BEAM', { prof: 'IPE360', p1: [-2598, -1500, 6360], p2: [2598, 1500, 6360] }];
const BSLOPE = ['RAFTER', { prof: 'IPE330', p1: [-3000, 0, 6068], p2: [3000, 0, 6593] }];
// crossing beams: lower beam (main) along X, upper beam along Y with a 20 mm gap
const XLO = ['BEAM', { prof: 'IPE300', p1: [-3000, 0, 6000], p2: [3000, 0, 6000] }];
const XHI = ['BEAM', { prof: 'IPE240', p1: [0, -3000, 6260], p2: [0, 3000, 6260] }];
const cpStd = k => ({ k, members: [COL, BTOP], q: {} });
const cpOdd = k => ({ k, members: [COLR, BHEA], q: {} });
const cpSkew = k => ({ k, members: [COL, BSKEW], q: {} });
const cpSlope = k => ({ k, members: [COL, BSLOPE], q: {} });
const cases = {};
for (const k of ['T2', 'T27', 'T37', 'T39', 'T5w', 'T100']) {
  cases[k.toLowerCase() + '_standard'] = cpStd(k); cases[k.toLowerCase() + '_odd_profile'] = cpOdd(k); cases[k.toLowerCase() + '_skewed'] = cpSkew(k);
}
Object.assign(cases, {
  t2_sloped: cpSlope('T2'),
  t2_gx_gy_ext: { k: 'T2', members: [COL, BTOP], q: { gx: 200, gy: 50, ext: 40, tp: 25 } },
  t27_stiff_ts: { k: 'T27', members: [['COLUMN', { prof: 'HEB240', p1: [0, 0, 0], p2: [0, 0, 6000] }], BTOP], q: { ts: 12 } },
  t100_tube: { k: 'T100', members: [['COLUMN', { prof: 'CHS273x10', p1: [0, 0, 0], p2: [0, 0, 6000] }], BTOP], q: {} },
  t100_tube_skew: { k: 'T100', members: [['COLUMN', { prof: 'CHS219.1x8', p1: [0, 0, 0], p2: [0, 0, 6000] }], BSKEW], q: { ext: 15 } },
  t14_standard: { k: 'T14', members: [COL, ['BEAM', { prof: 'IPE300', p1: [0, 0, 6300], p2: [-6000, 0, 6300] }], ['BEAM', { prof: 'IPE300', p1: [0, 0, 6300], p2: [6000, 0, 6300] }]], q: {} },
  t14_odd_profile: { k: 'T14', members: [COLR, ['BEAM', { prof: 'HEA240', p1: [0, 0, 6230], p2: [-5000, 0, 6230] }], ['BEAM', { prof: 'IPE270', p1: [0, 0, 6270], p2: [5000, 0, 6270] }]], q: {} },
  t14_skewed: { k: 'T14', members: [COL, ['BEAM', { prof: 'IPE300', p1: [0, 0, 6300], p2: [-5196, -3000, 6300] }], ['BEAM', { prof: 'IPE300', p1: [0, 0, 6300], p2: [5196, 3000, 6300] }]], q: { stiff: 1 } },
  t14_one_beam: { k: 'T14', members: [COL, BTOP], q: {} },
  t30_standard: { k: 'T30', members: [XLO, XHI], q: {} },
  t30_odd_profile: { k: 'T30', members: [['BEAM', { prof: 'HEA300', p1: [-3000, 0, 6000], p2: [3000, 0, 6000] }], ['BEAM', { prof: 'IPE200', p1: [0, -3000, 6200], p2: [0, 3000, 6200] }]], q: {} },
  t30_skewed: { k: 'T30', members: [XLO, ['BEAM', { prof: 'IPE240', p1: [-1500, -2598, 6270], p2: [1500, 2598, 6270] }]], q: {} },
  t30_on_column: { k: 'T30', members: [COL, BTOP], q: {} },
  t36n_standard: { k: 'T36n', members: [XLO, XHI], q: {} },
  t36n_odd_profile: { k: 'T36n', members: [['BEAM', { prof: 'IPE400', p1: [-3000, 0, 6000], p2: [3000, 0, 6000] }], ['BEAM', { prof: 'HEB200', p1: [0, -3000, 6215], p2: [0, 3000, 6215] }]], q: { bolt: 16 } },
  t36n_skewed: { k: 'T36n', members: [['BEAM', { prof: 'IPE300', p1: [-3000, 0, 5800], p2: [3000, 0, 6200] }], XHI], q: {} },
  t39_lp: { k: 'T39', members: [COL, BTOP], q: { Lp: 430, tp: 16, bolt: 16 } },
  t39_lp_xsat_wp: { k: 'T39', members: [COL, ['BEAM', { prof: 'IPE400', p1: [-3000, 0, 6400], p2: [3000, 0, 6400] }]], q: { Lp: 500, Wp: 220, xsAt: '-185 -115 115 185', gy: 45 } },
  t39_lp_skewed: { k: 'T39', members: [COL, BSKEW], q: { Lp: 400 } },
  t39_lp_sloped: { k: 'T39', members: [COL, BSLOPE], q: { Lp: 400, Wp: 200 } },
  t39_stiff: { k: 'T39', members: [COL, BTOP], q: { stiff: 1, ts: 8 } },
  t39_cross: { k: 'T39', members: [XLO, XHI], q: { cross: 1 } },
  t39_bdel: { k: 'T39', members: [COL, BTOP], q: { Lp: 430, xsAt: '-170 -100 100 170', bdel: '2' } },
  // CL: rafter (top at its line), purlin on the rafter top (bottom at its line)
  tcl_standard: { k: 'Tcl', members: [['RAFTER', { p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { prof: 'Z200-2-20-70', p1: [3000, -3000, 6000], p2: [3000, 3000, 6000] }]], q: {} },
  tcl_odd_profile: { k: 'Tcl', members: [['RAFTER', { prof: 'HEA260', p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { prof: 'C200-2.5-20-75', p1: [3000, -3000, 6000], p2: [3000, 3000, 6000] }]], q: {} },
  tcl_ipe_purlin: { k: 'Tcl', members: [['RAFTER', { p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { p1: [3000, -3000, 6000], p2: [3000, 3000, 6000] }]], q: {} },
  tcl_sloped: { k: 'Tcl', members: [['RAFTER', { p1: [0, 0, 6000], p2: [6000, 0, 7058] }], ['PURLIN', { prof: 'Z200-2-20-70', p1: [3000, -3000, 6529], p2: [3000, 3000, 6529] }]], q: {} },
  tcl_skewed: { k: 'Tcl', members: [['RAFTER', { p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { prof: 'Z200-2-20-70', p1: [2000, -3000, 6000], p2: [4000, 3000, 6000] }]], q: {} },
  tcl_two_purlins: { k: 'Tcl', members: [['RAFTER', { p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { prof: 'Z200-2-20-70', p1: [3000, 0, 6000], p2: [3000, -6000, 6000] }], ['PURLIN', { prof: 'Z200-2-20-70', p1: [3000, 0, 6000], p2: [3000, 6000, 6000] }]], q: {} },
  tcl_one_end: { k: 'Tcl', members: [['RAFTER', { p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { prof: 'Z200-2-20-70', p1: [3000, 0, 6000], p2: [3000, 6000, 6000] }]], q: {} },
  tcl_options: { k: 'Tcl', members: [['RAFTER', { p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { prof: 'Z200-2-20-70', p1: [3000, -3000, 6000], p2: [3000, 3000, 6000] }]], q: { stiff: 0, L1: 160, L2: 80, t: 8, w: 150, e: 45, h1: 50, h2: 130, bolt: 16, side: -1 } },
  // SR: purlin along Y, sag rod along X through the web
  tsag_standard: { k: 'Tsag', members: [['PURLIN', { prof: 'Z200-2-20-70', p1: [0, -3000, 6000], p2: [0, 3000, 6000] }], ['BRACING', { prof: 'D12', p1: [-1500, 0, 6100], p2: [1500, 0, 6100] }]], q: {} },
  tsag_odd_profile: { k: 'Tsag', members: [['PURLIN', { prof: 'C200-2.5-20-75', p1: [0, -3000, 6000], p2: [0, 3000, 6000] }], ['BRACING', { prof: 'D16', p1: [-1500, 0, 6100], p2: [1500, 0, 6100] }]], q: {} },
  tsag_ipe: { k: 'Tsag', members: [['PURLIN', { prof: 'IPE160', p1: [0, -3000, 6000], p2: [0, 3000, 6000] }], ['BRACING', { prof: 'D12', p1: [1500, 0, 6080], p2: [-1500, 0, 6080] }]], q: {} },
  tsag_skewed: { k: 'Tsag', members: [['PURLIN', { prof: 'Z200-2-20-70', p1: [0, -3000, 6000], p2: [0, 3000, 6000] }], ['BRACING', { prof: 'D12', p1: [-1500, -800, 6100], p2: [1500, 800, 6100] }]], q: {} },
  tsag_sloped: { k: 'Tsag', members: [['PURLIN', { prof: 'Z200-2-20-70', p1: [0, -3000, 6000], p2: [0, 3000, 6000] }], ['BRACING', { prof: 'D12', p1: [-1500, 0, 6000], p2: [1500, 0, 6200] }]], q: {} },
  tsag_no_nut: { k: 'Tsag', members: [['PURLIN', { prof: 'Z200-2-20-70', p1: [0, -3000, 6000], p2: [0, 3000, 6000] }], ['BRACING', { prof: 'D12', p1: [-1500, 0, 6100], p2: [1500, 0, 6100] }]], q: { nut: 0, tol: 4 } },
  tsag_no_washer: { k: 'Tsag', members: [['PURLIN', { prof: 'Z200-2-20-70', p1: [0, -3000, 6000], p2: [0, 3000, 6000] }], ['BRACING', { prof: 'D12', p1: [-1500, 0, 6100], p2: [1500, 0, 6100] }]], q: { washer: 0, side: -1 } },
  // TG: a CHS brace from a column face
  ttg_standard: { k: 'Ttg', members: [COL, ['BRACING', { prof: 'CHS88.9x4', p1: [150, 0, 3000], p2: [4150, 0, 6000] }]], q: {} },
  ttg_odd_profile: { k: 'Ttg', members: [['BEAM', { prof: 'IPE400', p1: [0, 0, 6000], p2: [8000, 0, 6000] }], ['BRACING', { prof: 'RHS120x80x5', p1: [4000, 0, 5600], p2: [7000, 0, 2600] }]], q: {} },
  ttg_web: { k: 'Ttg', members: [COL, ['BRACING', { prof: 'CHS76.1x3.2', p1: [0, 100, 3000], p2: [0, 4100, 6000] }]], q: {} },
  ttg_skewed: { k: 'Ttg', members: [COL, ['BRACING', { prof: 'CHS88.9x4', p1: [150, 0, 3000], p2: [3150, 2000, 5500] }]], q: {} },
  ttg_flat: { k: 'Ttg', members: [COL, ['BRACING', { prof: 'CHS88.9x4', p1: [150, 0, 3000], p2: [4150, 600, 3200] }]], q: {} },
  ttg_side_plane: { k: 'Ttg', members: [COL, ['BRACING', { prof: 'CHS88.9x4', p1: [150, 0, 3000], p2: [4150, 0, 6000] }]], q: { side: -1, plane: 'main' } },
  ttg_vert: { k: 'Ttg', members: [['BEAM', { prof: 'IPE400', p1: [0, 0, 6000], p2: [8000, 0, 6000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [4000, 0, 5600], p2: [4000, 3000, 2600] }]], q: { plane: 'vert' } },
  ttg_options: { k: 'Ttg', members: [COL, ['BRACING', { prof: 'CHS114.3x5', p1: [150, 0, 3000], p2: [4150, 0, 6000] }]], q: { t: 15, w: 160, L: 350, ins: 170, tf: 15, fw: 200, e: 45, p: 70, g: 80, bolt: 20, clear: 30, cap: 0, a: 5 } },
  ttg_node_off: { k: 'Ttg', members: [COL, ['BRACING', { prof: 'CHS88.9x4', p1: [150, 0, 3000], p2: [4150, 0, 6000] }]], q: { node: 0, cap: 8 } },
  // TG crossing: continuous brace, two halves meeting at the crossing
  ttgx_standard: { k: 'Ttgx', members: [['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 0], p2: [6000, 0, 4000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 4000], p2: [3000, 0, 2000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [3000, 0, 2000], p2: [6000, 0, 0] }]], q: {} },
  ttgx_odd_profile: { k: 'Ttgx', members: [['BRACING', { prof: 'RHS120x80x5', p1: [0, 0, 0], p2: [6000, 0, 4000] }], ['BRACING', { prof: 'SHS80x5', p1: [0, 0, 4000], p2: [3000, 0, 2000] }], ['BRACING', { prof: 'SHS80x5', p1: [3000, 0, 2000], p2: [6000, 0, 0] }]], q: {} },
  ttgx_skewed: { k: 'Ttgx', members: [['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 0], p2: [6000, 0, 3000] }], ['BRACING', { prof: 'CHS76.1x3.2', p1: [1000, 0, 4000], p2: [3000, 0, 1500] }], ['BRACING', { prof: 'CHS76.1x3.2', p1: [3000, 0, 1500], p2: [5000, 0, -1000] }]], q: {} },
  ttgx_plan: { k: 'Ttgx', members: [['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 6000], p2: [6000, 4000, 6000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [0, 4000, 6000], p2: [3000, 2000, 6000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [3000, 2000, 6000], p2: [6000, 0, 6000] }]], q: {} },
  ttgx_one_half: { k: 'Ttgx', members: [['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 0], p2: [6000, 0, 4000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 4000], p2: [3000, 0, 2000] }]], q: {} },
  ttgx_options: { k: 'Ttgx', members: [['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 0], p2: [6000, 0, 4000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [0, 0, 4000], p2: [3000, 0, 2000] }], ['BRACING', { prof: 'CHS88.9x4', p1: [3000, 0, 2000], p2: [6000, 0, 0] }]], q: { t: 10, w: 120, L: 300, ins: 140, e: 35, p: 55, g: 50, bolt: 12, clear: 30, cap: 0 } },
  // v70 · Tekla parity, C04 (docs/steel/PARITY.md): C / U purlins get Cleat location Front (the Tekla default); four R10 cleats as
  // they are in the model (rafter / column + purlins from tests/steel/golden/r10/rebuild_inputs.json), one purlin, and Back (= v69)
  tcl_odd_profile_back: { k: 'Tcl', members: [['RAFTER', { prof: 'HEA260', p1: [0, 0, 6000], p2: [6000, 0, 6000] }], ['PURLIN', { prof: 'C200-2.5-20-75', p1: [3000, -3000, 6000], p2: [3000, 3000, 6000] }]], q: { loc: 'back' } },
  tcl_r10_toes_down: {"k": "Tcl", "members": [["RAFTER", {"prof": "BI220-8-150-10", "p1": [237.306, 22355.0, 7416.591], "p2": [6705.5, 22355.0, 8887.192], "rot": "Top", "rAng": 0, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [6507.808, 22365, 9057.605], "p2": [6507.808, 28100, 9057.605], "rot": "Top", "rAng": 347.191, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [6507.808, 16795, 9057.605], "p2": [6507.808, 22345, 9057.605], "rot": "Top", "rAng": 347.191, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}]], "q": {"bolt": 12}},
  tcl_r10_toes_up: {"k": "Tcl", "members": [["RAFTER", {"prof": "BI220-8-150-10", "p1": [13406.5, 22355.0, 7410.724], "p2": [19900.5, 22355.0, 8887.192], "rot": "Top", "rAng": 0, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [18608.684, 28100, 8808.846], "p2": [18608.684, 22365, 8808.846], "rot": "Top", "rAng": 12.809, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [18608.684, 22345, 8808.846], "p2": [18608.684, 16795, 8808.846], "rot": "Top", "rAng": 12.809, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}]], "q": {"bolt": 12}},
  tcl_r10_column: {"k": "Tcl", "members": [["RAFTER", {"prof": "BI150-6-120-8", "p1": [18956.72, 22355.0, 8803.07], "p2": [18956.72, 22355.0, 9579.8], "rot": "Top", "rAng": 0, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [18781.722, 22365, 9509.523], "p2": [18781.722, 28100, 9509.523], "rot": "Top", "rAng": 270, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [18781.722, 16795, 9509.523], "p2": [18781.722, 22345, 9509.523], "rot": "Top", "rAng": 270, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}]], "q": {"bolt": 12}},
  tcl_r10_slope15: {"k": "Tcl", "members": [["RAFTER", {"prof": "BI150-6-120-8", "p1": [19021.616, 5645.0, 9499.433], "p2": [19900.5, 5645.0, 9735.415], "rot": "Below", "rAng": 0, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [19398.624, -100, 9781.859], "p2": [19398.624, 5635, 9781.859], "rot": "Top", "rAng": 344.9704, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [19398.624, 5655, 9781.859], "p2": [19398.624, 11205, 9781.859], "rot": "Top", "rAng": 344.9704, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}]], "q": {"bolt": 12}},
  tcl_r10_one_purlin: {"k": "Tcl", "members": [["RAFTER", {"prof": "BI220-8-150-10", "p1": [237.306, 22355.0, 7416.591], "p2": [6705.5, 22355.0, 8887.192], "rot": "Top", "rAng": 0, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [6507.808, 22365, 9057.605], "p2": [6507.808, 28100, 9057.605], "rot": "Top", "rAng": 347.191, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}]], "q": {"bolt": 12}},
  tcl_r10_back: {"k": "Tcl", "members": [["RAFTER", {"prof": "BI220-8-150-10", "p1": [237.306, 22355.0, 7416.591], "p2": [6705.5, 22355.0, 8887.192], "rot": "Top", "rAng": 0, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [6507.808, 22365, 9057.605], "p2": [6507.808, 28100, 9057.605], "rot": "Top", "rAng": 347.191, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}], ["PURLIN", {"prof": "CC200-2-20-60", "p1": [6507.808, 16795, 9057.605], "p2": [6507.808, 22345, 9057.605], "rot": "Top", "rAng": 347.191, "plane": "Middle", "depth": "Middle", "pOff": 0, "dOff": 0, "dx1": 0, "dx2": 0}]], "q": {"bolt": 12, "loc": "back"}},
});
module.exports = cases;
