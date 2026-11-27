// v70 C05 · Tube gusset (20), the tube end (docs/steel/PARITY.md "C05"): R12's own node (three braces on one gusset on the rafter) and
// a single strut on a rafter (plate in the rafter's plane), then the same settings on other sizes and a V node.
'use strict';
const member = (set, extra) => ({ set, extra });
const C05 = { bolt: 16, tend: 'end', blrule: 'tekla', tpos: 'gusset' };
const RAF = member('RAFTER', { prof: 'BI220-8-150-10', p1: [39848.613, 22355, 7422.572], p2: [46290.5, 22355, 8887.194], rot: 'Top', plane: 'Middle', depth: 'Middle' });
module.exports = [
  {
    name: 't20_r12_node_r3019',
    description: 'R12 R3019 node: HZ6014 (plate 285, e 30, p 50, g 50), HZ6004 and strut ST5008 (plane: rafter) on one gusset; plates against the gusset.',
    members: [RAF,
      member('BRACING', { name: 'HZ Bracing', prof: 'SHS100*3', p1: [41609.01, 24871.307, 7833.07], p2: [42819.298, 22586.496, 8108.24], rot: 'Top', rAng: 191.298, plane: 'Middle', depth: 'Middle' }),
      member('BRACING', { name: 'HZ Bracing', prof: 'SHS100*3', p1: [45984.326, 27712.652, 8827.837], p2: [43258.451, 22604.743, 8208.085], rot: 'Top', rAng: 191.279, plane: 'Middle', depth: 'Middle' }),
      member('BRACING', { name: 'Strut', prof: 'SHS100*3', p1: [43035.665, 22664.389, 8147.745], p2: [43035.665, 27616.278, 8147.745], rot: 'Top', rAng: 167.191, plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ L: 285, ins: 150, e: 30, p: 50, g: 50, side: 1 }, C05)], ['Ttg', [0, 2], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, side: 1 }, C05)],
            ['Ttg', [0, 3], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, plane: 'main', side: 1 }, C05)]],
  },
  {
    name: 't20_r12_single_strut',
    description: 'R12 R3023 / ST5012: one strut on the rafter, its plate in the rafter plane on a fin (Gusset plane: main part plane).',
    members: [member('RAFTER', { prof: 'BI220-8-150-10', p1: [52682.387, 22355, 7433.94], p2: [46290.5, 22355, 8887.194], rot: 'Top', plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'Strut', prof: 'SHS100*3', p1: [49545.335, 16984, 8147.745], p2: [49545.335, 22156, 8147.745], rot: 'Top', rAng: 192.809, plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ L: 315, ins: 150, e: 40, p: 60, g: 60, plane: 'main', side: 1 }, C05)]],
  },
  {
    name: 't20_hea200_shs80_45deg',
    description: 'HEA200 column, SHS80*4 brace at 45 deg ending 250 from the column axis: R12 settings (plate 12 x 140, 315 / 150, bolts 40 + 60, gauge 60) on another size.',
    members: [member('COLUMN', { prof: 'HEA200', p1: [0, 0, 0], p2: [0, 0, 6000] }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS80*4', p1: [176.8, 0, 3176.8], p2: [3000, 0, 6000], plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({}, C05)]],
  },
  {
    name: 't20_ipe300_v_node_shs120',
    description: 'IPE300 beam, two SHS120*5 braces meeting at its mid-span from below (V node): one gusset, the second plate against it.',
    members: [member('BEAM', { prof: 'IPE300', p1: [0, 0, 6000], p2: [6000, 0, 6000] }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS120*5', p1: [2700, 0, 5400], p2: [0, 0, 1000], plane: 'Middle', depth: 'Middle' }),
              member('BRACING', { name: 'VL Bracing', prof: 'SHS120*5', p1: [3300, 0, 5400], p2: [6000, 0, 1000], plane: 'Middle', depth: 'Middle' })],
    conns: [['Ttg', [0, 1], Object.assign({ side: 1 }, C05)], ['Ttg', [0, 2], Object.assign({ side: 1 }, C05)]],
  },
];
