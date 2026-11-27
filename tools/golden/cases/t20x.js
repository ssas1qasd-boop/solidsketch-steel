// v70 C07 · Tube crossing as Tekla 20 (docs/steel/PARITY.md "C07"): R12's horizontal and vertical crossings, then other sizes and angles.
'use strict';
const member = (set, extra) => ({ set, extra });
const X = { tend: 'end', blrule: 'tekla', side: 1 };
const br = (name, prof, p1, p2, rAng) => member('BRACING', { name, prof, p1, p2, rot: 'Top', rAng: rAng || 0, plane: 'Middle', depth: 'Middle' });
module.exports = [
  {
    name: 't20x_r12_hz6004',
    description: 'R12 roof crossing HZ6004 (continuous) with halves HZ6002 / HZ6006: gusset PL12*330.1 through the continuous tube, plates 315 / 150.',
    members: [br('HZ Bracing', 'SHS100*3', [45984.326, 27712.652, 8827.837], [43258.451, 22604.743, 8208.085], 191.279),
              br('HZ Bracing', 'SHS100*3', [44468.733, 25407.359, 8483.253], [43258.451, 27675.257, 8208.085], 168.721),
              br('HZ Bracing', 'SHS100*3', [45984.326, 22567.348, 8827.837], [44754.089, 24872.641, 8548.131], 168.721)],
    conns: [['Ttgx', [0, 1, 2], Object.assign({ kind: 'cross', L: 315, ins: 150, e: 40, p: 60, g: 60 }, X)]],
  },
  {
    name: 't20x_r12_vl_gable',
    description: 'R12 wall crossing on grid line 6 (VL, continuous + two halves), gusset through the continuous tube.',
    members: [br('VL Bracing', 'SHS100*3', [52888, 287.789, 3187.357], [52888, 5432.211, 562.949]),
              br('VL Bracing', 'SHS100*3', [52888, 2576.263, 1733.952], [52888, 287.775, 563.224]),
              br('VL Bracing', 'SHS100*3', [52888, 5458.933, 3208.646], [52888, 3136.003, 2020.299])],
    conns: [['Ttgx', [0, 1, 2], Object.assign({ kind: 'cross', L: 315, ins: 150, e: 40, p: 60, g: 60 }, X)]],
  },
  {
    name: 't20x_shs80_x_60deg',
    description: 'SHS80*4 X-bracing at 60 deg in a vertical plane: R12 settings, halves ending 330 from the crossing (230 makes the tongue clash with the continuous tube at 60 deg, as it would in Tekla).',
    members: [br('VL Bracing', 'SHS80*4', [0, 0, 0], [3000, 0, 5196]),
              br('VL Bracing', 'SHS80*4', [1665, 0, 2312.2], [3000, 0, 0]),
              br('VL Bracing', 'SHS80*4', [1335, 0, 2883.8], [0, 0, 5196])],
    conns: [['Ttgx', [0, 1, 2], Object.assign({ kind: 'cross', L: 315, ins: 150, e: 40, p: 60, g: 60 }, X)]],
  },
  {
    name: 't20x_shs120_x_90deg_side2',
    description: 'SHS120*5 square X (90 deg) in a horizontal plane, gusset on side 2, plates 285 / 150 (e 30, p 50, g 50).',
    members: [br('HZ Bracing', 'SHS120*5', [-3000, -3000, 6000], [3000, 3000, 6000]),
              br('HZ Bracing', 'SHS120*5', [250, -250, 6000], [3000, -3000, 6000]),
              br('HZ Bracing', 'SHS120*5', [-250, 250, 6000], [-3000, 3000, 6000])],
    conns: [['Ttgx', [0, 1, 2], Object.assign({ kind: 'cross', L: 285, ins: 150, e: 30, p: 50, g: 50, side: -1 }, X)]],
  },
];
