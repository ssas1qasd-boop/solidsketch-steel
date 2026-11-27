// v70 C01 / C16 · Base plate 1042 / 1004 as Tekla (docs/steel/PARITY.md "C01 / C16"): anchor rods as parts (hooked rod, washer, nut),
// grout under the plate with slopes, cast plate under the grout. R12's frame, gable and stair-post bases, then other sizes and a skewed column.
'use strict';
const member = (set, extra) => ({ set, extra });
const col = (prof, p, top, rot, rAng) => member('COLUMN', { prof, p1: p, p2: [p[0], p[1], top], rot, rAng: rAng || 0, plane: 'Middle', depth: 'Middle' });
const R12 = { t: 20, ext: 20, anchors: 4, bolt: 20, anch: 528, awith: 'rods', aL: 600, aup: 50, ahr: 40, ahw: 120, ahdir: 'x', anut: 1, awash: 1, gt: 30, gm: 30, gslope: 'slope', cast: 4, castd: 30, gorder: 'tekla' };
module.exports = [
  { name: 't1042_r12_c2020', description: 'R12 C2020: BI320-8-150-10 frame column, plate 20 x 360 x 200, 4 ROD20 L600 (a 40, b 120) hooked along the column depth, washer + nut PD40*10, grout PL30 with slopes, cast plate PL4 under it.',
    members: [col('BI320-8-150-10', [52838, 16785, 230], 7547.745, 'Below')], conns: [['T1042', [0], Object.assign({}, R12, { side: 25, gx: 100, gy: 100 })]] },
  { name: 't1042_r12_egc2001', description: 'R12 EGC2001: BI220-6-200-10 gable column (rot Front), plate 20 x 240 x 260, gauges 80 / 100.',
    members: [col('BI220-6-200-10', [49290.5, 27890, 230], 8043.935, 'Front')], conns: [['T1042', [0], Object.assign({}, R12, { side: 20, gx: 80, gy: 100 })]] },
  { name: 't1004_r12_str9001', description: 'R12 STR9001: BI162-6-150-6 stair landing post on base plate 1004, plate 16 x 200 x 170, 4 ROD16 L250 (a 32, b 68), no nut / washer, grout PL30 with slopes, no cast plate.',
    members: [col('BI162-6-150-6', [598, 12235.01, 30], 2080, 'Back')], conns: [['T1004', [0], { t: 16, ext: 19, side: 10, anchors: 4, bolt: 16, gx: 70, gy: 100, anch: 192, awith: 'rods', aL: 250, aup: 50, ahr: 32, ahw: 68, ahdir: 'x', anut: 0, awash: 0, gt: 30, gm: 30, gslope: 'slope', gorder: 'tekla' }]] },
  { name: 't1042_hea300_defaults_y_2nuts', description: 'HEA300 column, rods with Tekla defaults (L 500, a 2d, b L/5), hooks along Y, two nuts, grout box without slopes, cast plate 60 below the base plate (Tekla 6 default).',
    members: [col('HEA300', [0, 0, 0], 4000, 'Top')], conns: [['T1042', [0], { t: 25, ext: 75, side: 50, anchors: 4, bolt: 24, awith: 'rods', ahdir: 'y', anut: 2, awash: 1, gt: 40, gm: 40, cast: 6, castd: 60, gorder: 'tekla' }]] },
  { name: 't1042_ipe360_skew30_inward_6', description: 'IPE360 column turned 30 deg, 6 ROD20, hooks inward along X, M20 L400 (b 80), grout with slopes, no cast plate.',
    members: [col('IPE360', [1000, 2000, 0], 5000, 'Top', 30)], conns: [['T1042', [0], { t: 20, ext: 60, side: 40, anchors: 6, bolt: 20, awith: 'rods', aL: 400, ahdir: '-x', anut: 1, awash: 1, gt: 30, gm: 25, gslope: 'slope', gorder: 'tekla' }]] },
];
