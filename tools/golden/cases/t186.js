// v70 C20 · Column with stiffeners (186) (docs/steel/PARITY.md "C20"): R12's own joints (mezzanine column C2020 with its BI324 beam,
// gable column EGC2002 with its BI320 beam), then the same settings on other sizes, a skewed beam and the Tekla defaults.
'use strict';
const member = (set, extra) => ({ set, extra });
const R12C20 = { t: 10, bolt: 16, rows: 4, e: 30, pitch: 50, cols: 2, pitchH: 60, gap: 20, side: -1, eh: 35, tb: 150, refV: 'Middle', posV: 0,
  scr: 'both', ssh: 'line', sct: 'line', sc1: 5.6, sc2: 5.6, sgap2: 2, sprep: 'K', stt1: 10, stb1: 70, stt2: 10, stb2: 70, stt3: 10, stb3: 70, stt4: 10, stb4: 70 };
const R12EGC = { t: 10, bolt: 16, rows: 4, e: 30, pitch: 50, cols: 1, gap: 10, side: 1, eh: 45, tb: 100, refV: 'Middle', posV: 0,
  scr: 'both', ssh: 'line', sct: 'line', sc1: 4.2, sc2: 4.2, sgap2: 2, sprep: 'K', stt1: 10, stb1: 90, stt2: 10, stb2: 90, stt3: 10, stb3: 90, stt4: 10, stb4: 90 };
const R12SET = { t: 10, tb: 150, scr: 'both', sgap2: 2, sprep: 'K' };   // R12's stiffener settings with sizes left to the component
module.exports = [
  {
    name: 't186_r12_c2020',
    description: 'R12 C2020: BI320-8-150-10 column, BI324-8-250-12 beam on its web at +3838: tab 10 x 150, 2 x 4 M16 (55 / 115 from the web face, 50 apart), stiffeners 10 x 70, gap 2, chamfer 5.6, K prep.',
    members: [member('COLUMN', { prof: 'BI320-8-150-10', p1: [52838, 16785, 230], p2: [52838, 16785, 7547.745], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('BEAM', { prof: 'BI324-8-250-12', p1: [52838, 16809, 3838], p2: [52838, 22331, 3838], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['T186', [0, 1], R12C20]],
  },
  {
    name: 't186_r13_c2021_eb4007',
    description: 'R13 C2021 / EB4007 (the fixed girder, PARITY.md "C12 / C20 on R13"): Tekla placed the new web 30 mm along, 10 mm past the column web face (Picture 1 = -10); the 186 cuts it at the face (no clash), the bolts stay 35 from the web end (25 / 85 from the web face, NC 4514), stiffeners created.',
    members: [member('COLUMN', { prof: 'BI320-8-150-10', p1: [52838, 27925, 230], p2: [52838, 27925, 7547.745], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('BEAM', { prof: 'BI324-8-250-12', p1: [52838, 22409, 3838], p2: [52838, 27931, 3838], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['T186', [0, 1], Object.assign({}, R12C20, { gap: -10 })]],
  },
  {
    name: 't186_r12_egc2002',
    description: 'R12 EGC2002: BI220-6-200-10 gable column, BI320-8-120-10 beam on its web at +3840: tab 10 x 100, 1 x 4 M16, stiffeners 10 x 90, chamfer 4.2, K prep.',
    members: [member('COLUMN', { prof: 'BI220-6-200-10', p1: [3705.5, 27890, 230], p2: [3705.5, 27890, 8043.935], rot: 'Front', plane: 'Middle', depth: 'Middle' }),
              member('BEAM', { prof: 'BI320-8-120-10', p1: [3728.5, 27925, 3840], p2: [9682.5, 27925, 3840], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['T186', [0, 1], R12EGC]],
  },
  {
    name: 't186_hea300_ipe300',
    description: 'HEA300 column, IPE300 beam on its web: R12 stiffener settings, width / length / chamfer left blank (b = outstand - gap 1, chamfer 10).',
    members: [member('COLUMN', { prof: 'HEA300', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 6000] }),
              member('BEAM', { prof: 'IPE300', p1: [0, 0, 4000], p2: [0, 6000, 4000] })],
    conns: [['T186', [0, 1], R12SET]],
  },
  {
    name: 't186_heb240_ipe240_tekla_defaults',
    description: 'HEB240 column, IPE240 beam on its web: Stiffener creation on, every other field the Tekla default (square-free line chamfer 10, no gaps, no weld prep).',
    members: [member('COLUMN', { prof: 'HEB240', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 5000] }),
              member('BEAM', { prof: 'IPE240', p1: [0, 0, 3000], p2: [0, -5000, 3000] })],
    conns: [['T186', [0, 1], { scr: 'both' }]],
  },
  {
    name: 't186_hea260_ipe270_skew15',
    description: 'HEA260 column, IPE270 beam on its web skewed 15 deg in plan: stiffeners parallel to the beam (Tekla default orientation), R12 settings.',
    members: [member('COLUMN', { prof: 'HEA260', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 6000] }),
              member('BEAM', { prof: 'IPE270', p1: [0, 0, 3500], p2: [1552.9, 5795.6, 3500] })],
    conns: [['T186', [0, 1], R12SET]],
  },
];
