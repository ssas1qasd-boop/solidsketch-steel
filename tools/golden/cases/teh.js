// v70 C02h · Eave haunch (SolidSketch's own component, key Teh; docs/steel/PARITY.md "C02h"): R12's eave (with its End plate 144), then the same
// component on other sizes and slopes, with and without an End plate (144) at the joint.
'use strict';
const member = (set, extra) => ({ set, extra });
const R12EP = { tp: 20, bolt: 20, ext: 10, extb: 104.385, gap: 0, cols: 2, gauge: 70, side: 40, rows: 4, e: 50.045, pitches: '70 70 80', blrule: 'tekla', bdir: 2 };
module.exports = [
  {
    name: 'teh_r12',
    description: 'R12 eave R3023 / C2017: BI320 column, BI220 rafter at 12.8 deg, End plate 144 (R12 settings) + Eave haunch: length 2000 from the column axis, depth at the column 82.91, web 8, flange 10 x 150.',
    members: [member('COLUMN', { prof: 'BI320-8-150-10', p1: [52838, 22355, 230], p2: [52838, 22355, 7547.745], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('RAFTER', { prof: 'BI220-8-150-10', p1: [52682.387, 22355, 7433.94], p2: [46290.5, 22355, 8887.194], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['T144', [0, 1], R12EP], ['Teh', [0, 1], { hlen: 2000, hdep: 82.91, tw: 8, tf: 10, bf: 150 }]],
  },
  {
    name: 'teh_hea300_ipe360_10deg',
    description: 'HEA300 column, IPE360 rafter at 10 deg, End plate 144 (tp 20, extb 280: the lowest bolt row below the haunch flange) + haunch length 1500, depth 200, flange 12 x 170.',
    members: [member('COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }),
              member('RAFTER', { prof: 'IPE360', p1: [0, 0, 7500], p2: [10000, 0, 9263.3] })],
    conns: [['T144', [0, 1], { tp: 20, gap: 0, ext: 20, extb: 280, blrule: 'tekla' }], ['Teh', [0, 1], { hlen: 1500, hdep: 200, tw: 8, tf: 12, bf: 170 }]],
  },
  {
    name: 'teh_hea240_ipe300_15deg_tp25',
    description: 'HEA240 column, IPE300 rafter at 15 deg, End plate 144 tp 25 gap 0 (extb 160, top bolt row 60 below the plate top: X01 fits the rafter to the plate over its whole depth, so the default 35 put the top bolts through its top flange) + haunch length 1800, depth 120.',
    members: [member('COLUMN', { prof: 'HEA240', p1: [0, 0, 0], p2: [0, 0, 6000] }),
              member('RAFTER', { prof: 'IPE300', p1: [0, 0, 6000], p2: [8000, 0, 8143.6] })],
    conns: [['T144', [0, 1], { tp: 25, gap: 0, ext: 20, extb: 160, e: 60 }], ['Teh', [0, 1], { hlen: 1800, hdep: 120, tw: 8, tf: 10, bf: 150 }]],
  },
  {
    name: 'teh_ipe400_5deg_no144',
    description: 'IPE400 column, IPE330 rafter at 5 deg, no end plate (the rafter starts at the column face): the haunch starts at the column face; length 1200, depth 150.',
    members: [member('COLUMN', { prof: 'IPE400', p1: [0, 0, 0], p2: [0, 0, 6000] }),
              member('RAFTER', { prof: 'IPE330', p1: [200, 0, 6017.5], p2: [8000, 0, 6699.9] })],
    conns: [['Teh', [0, 1], { hlen: 1200, hdep: 150, tw: 8, tf: 10, bf: 160 }]],
  },
];
