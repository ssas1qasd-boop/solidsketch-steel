// End plate 144 (Tekla dialog component, TKD). Three cases: standard, odd profile, sloped.
'use strict';
const member = (set, extra) => ({ set, extra });

module.exports = [
  {
    name: 't144_standard',
    description: 'HEA300 column, IPE360 beam level at +7500, End plate 144 with default dialog values.',
    members: [member('COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }),
              member('BEAM', { prof: 'IPE360', p1: [0, 0, 7500], p2: [6000, 0, 7500] })],
    conns: [['T144', [0, 1], {}]],
  },
  {
    name: 't144_odd_profile',
    description: 'HEM300 column (rot Top) running past an IPE450 beam framing into its flange at +7000, End plate 144 with 3 bolt rows and 12 mm column stiffeners.',
    members: [member('COLUMN', { prof: 'HEM300', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 8000] }),
              member('BEAM', { prof: 'IPE450', p1: [0, 0, 7000], p2: [6000, 0, 7000] })],
    conns: [['T144', [0, 1], { rows: 3, stiff: 12 }]],
  },
  {
    name: 't144_sloped',
    description: 'R10 portal geometry: HEA300 column, IPE360 rafter eave +7500 to apex +9000 over 13195, End plate 144.',
    members: [member('COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }),
              member('RAFTER', { prof: 'IPE360', p1: [0, 0, 7500], p2: [13195, 0, 9000] })],
    conns: [['T144', [0, 1], {}]],
  },
  // v70 C09 · Haunch tab (Tekla's haunch plates) and C02 / C09 · Tekla bolt length (docs/steel/PARITY.md "C02 / C09"): R12's own
  // joints, then the same settings on other sizes and slopes, the Automatic creation and the arc chamfers.
  {
    name: 't144_c09_r12',
    description: 'R12 monitor post PC7001 (BI150 post) on the BI220 rafter at 12.8 deg (gap 0: the nominal rafter face; R12 itself has the plate 0.75 mm into it, Tekla flange plate data): Haunch tab top / bottom plate 8 x 60 x 60, chamfers 15 line, Tekla bolt length, bolting direction 2.',
    members: [member('RAFTER', { prof: 'BI220-8-150-10', p1: [39537.187, 27925, 7421.544], p2: [33095.5, 27925, 8888.267], rot: 'Top', plane: 'Middle', depth: 'Middle' }),
              member('COLUMN', { prof: 'BI150-6-120-8', p1: [34039.28, 27925, 8782.496], p2: [34039.28, 27925, 9579.802], rot: 'Below', plane: 'Middle', depth: 'Middle' })],
    conns: [['T144', [0, 1], { tp: 12, bolt: 16, ext: 55.52, extb: 60.641, gap: 0, cols: 2, gauge: 90, side: 35, rows: 4, e: 30.025, pitches: '70 70 70', hpc: 'both', hct: 'line', hpt1: 8, hpb1: 60, hph1: 60, hc1: 15, hc2: 15, hpt2: 8, hpb2: 60, hph2: 60, hc4: 15, hc3: 15, blrule: 'tekla', bdir: 2 }]],
  },
  {
    name: 't144_c09_hea160_ipe300_5deg',
    description: 'HEA160 post on an IPE300 rafter at 5 deg, R12 Haunch tab settings with b and h blank (b = the end plate reach, h = b), ext 50 / 50.',
    members: [member('RAFTER', { prof: 'IPE300', p1: [0, 0, 6000], p2: [8000, 0, 6699.9] }),
              member('COLUMN', { prof: 'HEA160', p1: [4000, 0, 6500], p2: [4000, 0, 8000] })],
    conns: [['T144', [0, 1], { tp: 12, bolt: 16, ext: 50, extb: 50, gap: 0, hpc: 'both', hpt1: 8, hpt2: 8, hpb1: '', hph1: '', hpb2: '', hph2: '', blrule: 'tekla', bdir: 2 }]],
  },
  {
    name: 't144_c09_ipe200_ipe360_10deg_auto_convex',
    description: 'IPE200 post on an IPE360 rafter at 10 deg: Automatic creation (end plate past the top flange only), plates 10 x 50 x 70, convex arc chamfers 20.',
    members: [member('RAFTER', { prof: 'IPE360', p1: [0, 0, 6000], p2: [8000, 0, 7410.6] }),
              member('COLUMN', { prof: 'IPE200', p1: [3000, 0, 6700], p2: [3000, 0, 8500] })],
    conns: [['T144', [0, 1], { tp: 15, bolt: 20, ext: 60, extb: 0, gap: 0, hpc: 'auto', hct: 'convex', hpt1: 10, hpb1: 50, hph1: 70, hpt2: 10, hpb2: 50, hph2: 70, hc1: 20, hc2: 20, hc3: 20, hc4: 20 }]],
  },
  {
    name: 't144_c09_concave_level',
    description: 'IPE240 post standing on a level HEB300 beam, concave arc chamfers 15 x 25, plates 8 x 70 x 80.',
    members: [member('BEAM', { prof: 'HEB300', p1: [0, 0, 5000], p2: [6000, 0, 5000] }),
              member('COLUMN', { prof: 'IPE240', p1: [3000, 0, 5150], p2: [3000, 0, 7000] })],
    conns: [['T144', [0, 1], { tp: 12, bolt: 16, ext: 80, extb: 80, gap: 0, hpc: 'both', hct: 'concave', hpt1: 8, hpb1: 70, hph1: 80, hpt2: 8, hpb2: 70, hph2: 80, hc1: 15, hc2: 25, hc3: 25, hc4: 15 }]],
  },
  {
    name: 't144_c02_r12',
    description: 'R12 eave R3023 / C2017: BI320 column, BI220 rafter at 12.8 deg, end plate 20 with 4 rows, Tekla bolt length (M20 grip 30 -> L60), bolting direction 2.',
    members: [member('COLUMN', { prof: 'BI320-8-150-10', p1: [52838, 22355, 230], p2: [52838, 22355, 7547.745], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('RAFTER', { prof: 'BI220-8-150-10', p1: [52682.189, 22355, 7432.92], p2: [46290.5, 22355, 8888.258], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['T144', [0, 1], { tp: 20, bolt: 20, ext: 11.056, extb: 103.313, gap: 0, cols: 2, gauge: 70, side: 40, rows: 4, e: 50.045, pitches: '70 70 80', blrule: 'tekla', bdir: 2 }]],
  },
];
