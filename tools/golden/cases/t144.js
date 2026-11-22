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
];
