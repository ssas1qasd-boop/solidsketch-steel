// v70 C06 · Tube gusset (20), the gusset (docs/steel/PARITY.md "C06"): R12's node R3019 and a single-brace gusset on R3019 with Tekla's
// outline (edges square to the braces 20 before the end plates, brought back onto the rafter web), then the same rule on other sizes,
// a gusset centred on the braces ('middle'), and a shortened edge (gwb).
'use strict';
const member = (set, extra) => ({ set, extra });
const T = { bolt: 16, tend: 'end', blrule: 'tekla', tpos: 'gusset', gshape: 'tekla' };
const RAF = member('RAFTER', { prof: 'BI220-8-150-10', p1: [39848.613, 22355, 7422.572], p2: [46290.5, 22355, 8887.194], rot: 'Top', plane: 'Middle', depth: 'Middle' });
module.exports = [
  {
    name: 't20g_r12_node_r3019',
    description: 'R12 R3019 node (gusset 6532 PL12*280.4): HZ6014 (285), HZ6004, strut ST5008 (rafter plane); Tekla outline.',
    members: [RAF,
      member('BRACING', { name: 'HZ Bracing', prof: 'SHS100*3', p1: [41609.01, 24871.307, 7833.07], p2: [42819.298, 22586.496, 8108.24], rot: 'Top', rAng: 191.298, plane: 'Middle', depth: 'Middle' }),
      member('BRACING', { name: 'HZ Bracing', prof: 'SHS100*3', p1: [45984.326, 27712.652, 8827.837], p2: [43258.451, 22604.743, 8208.085], rot: 'Top', rAng: 191.279, plane: 'Middle', depth: 'Middle' }),
      member('BRACING', { name: 'Strut', prof: 'SHS100*3', p1: [43035.665, 22664.389, 8147.745], p2: [43035.665, 27616.278, 8147.745], rot: 'Top', rAng: 167.191, plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ L: 285, ins: 150, e: 30, p: 50, g: 50, side: 1 }, T)], ['Ttg', [0, 2], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, side: 1 }, T)],
            ['Ttg', [0, 3], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, plane: 'main', side: 1 }, T)]],
  },
  {
    name: 't20g_r12_single_r3019',
    description: 'R12 R3019 single brace HZ (gusset PL12*213.4): one edge square to the brace, one side square to the rafter, the other along the brace.',
    members: [RAF, member('BRACING', { name: 'HZ Bracing', prof: 'SHS100*3', p1: [45984.326, 22567.348, 8827.837], p2: [44754.089, 24872.641, 8548.131], rot: 'Top', rAng: 168.721, plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, side: 1 }, T)]],
  },
  {
    name: 't20g_hea240_two_braces_middle',
    description: 'HEA240 column, two SHS80*4 braces from one node at +3000 (45 deg up and down), gusset centred on the braces (middle), Tekla outline.',
    members: [member('COLUMN', { prof: 'HEA240', p1: [0, 0, 0], p2: [0, 0, 6000] }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS80*4', p1: [450, 0, 3330], p2: [3000, 0, 6000], plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS80*4', p1: [450, 0, 2670], p2: [3000, 0, 0], plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ side: 1, gpos: 'middle' }, T)], ['Ttg', [0, 2], Object.assign({ side: 1, gpos: 'middle' }, T)]],
  },
  {
    name: 't20g_ipe360_v_node_gwb55',
    description: 'IPE360 beam, two SHS100*3 braces meeting below it (V node), Tekla outline with the outer edge of the first brace shortened (gwb 55).',
    members: [member('BEAM', { prof: 'IPE360', p1: [0, 0, 6000], p2: [6000, 0, 6000] }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS100*3', p1: [2650, 0, 5300], p2: [0, 0, 1000], plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS100*3', p1: [3350, 0, 5300], p2: [6000, 0, 1000], plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ side: 1, gwb: 55 }, T)], ['Ttg', [0, 2], Object.assign({ side: 1 }, T)]],
  },
  // C06 gable nodes (gplane 'perp'): braces not in one plane; the gusset square to the strut, every plate turned to it
  {
    name: 't20g_r13_gable_vl_lead',
    description: 'R13 gable node 3419 (gusset PL12*222.9, mark 12): column C 39693 / 5645, VL6012 first (plates through its middle, gmid; its plate pofs off its axis), strut ST5007 skewed 0.51 deg.',
    members: [member('COLUMN', { prof: 'BI220-8-150-10', p1: [39693, 5645, 230], p2: [39693, 5645, 7522.736], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS100*3', p1: [39693, 5433.822, 6909.337], p2: [39693, 3122.387, 5467.473], rot: 'Top', plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'Strut', prof: 'SHS100*3', p1: [39690.738, 5393.024, 7180], p2: [39645.264, 327.197, 7180], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, side: 1, gplane: 'perp', gmid: 279.28, pofs: -12.019 }, T)],
            ['Ttg', [0, 2], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, side: 1, gplane: 'perp' }, T)]],
  },
  {
    name: 't20g_r13_gable_strut_lead',
    description: 'R13 gable node 3425 (gusset PL12*223.1, mark 11): column C 39693 / 75, strut ST5007 first (plate on its axis), VL6014 48 mm off the plates\' plane, its plate crossing it at its gusset edge.',
    members: [member('COLUMN', { prof: 'BI220-8-150-10', p1: [39693, 75, 230], p2: [39693, 75, 7522.736], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'Strut', prof: 'SHS100*3', p1: [39690.738, 5393.024, 7180], p2: [39645.264, 327.197, 7180], rot: 'Top', plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS100*3', p1: [39693, 286.755, 6910.776], p2: [39693, 5433.806, 3738.95], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, side: -1, gplane: 'perp' }, T)],
            ['Ttg', [0, 2], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, side: -1, gplane: 'perp' }, T)]],
  },
  {
    name: 't20g_hea200_skew2deg_perp',
    description: 'HEA200 column, an SHS80*4 strut skewed 2 deg out of the wall and an SHS80*4 diagonal in the wall, Tekla gable-node gusset (gplane perp), plates at their gusset edges.',
    members: [member('COLUMN', { prof: 'HEA200', p1: [0, 0, 0], p2: [0, 0, 6000] }),
              member('BRACING', { name: 'Strut', prof: 'SHS80*4', p1: [0, 300, 5000], p2: [174.5, 5300, 5000], plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS80*4', p1: [0, 250, 4600], p2: [0, 2500, 1600], plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ side: -1, gplane: 'perp' }, T)], ['Ttg', [0, 2], Object.assign({ side: -1, gplane: 'perp' }, T)]],
  },
];
