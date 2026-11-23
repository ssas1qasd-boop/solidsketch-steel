// S8: Shear plate simple (146), family ST. Standard (IPE360 beam into an HEA300 column flange), odd profile (IPE240 into
// a turned HEM300 web, 1 row more, two sided), sloped / skewed (beam into a main beam, skewed 20°), plus options.
const COL = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }];
module.exports = {
  t146_standard: { k: 'T146', members: [COL, ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]], q: {} },
  t146_odd_profile: { k: 'T146', members: [['COLUMN', { prof: 'HEM300', p1: [0, 0, 0], p2: [0, 0, 7500], rot: 'Top' }], ['BEAM', { prof: 'IPE240', p1: [0, 0, 6500], p2: [6000, 0, 6500] }]], q: { rows: 3, side: 2 } },
  t146_skewed: { k: 'T146', members: [['BEAM', { prof: 'IPE500', p1: [0, -3000, 7000], p2: [0, 3000, 7000] }], ['BEAM', { prof: 'IPE300', p1: [0, 0, 7000], p2: [5638, 2052, 7000] }]], q: {} },
  t146_full_colstiff: { k: 'T146', members: [COL, ['BEAM', { prof: 'IPE400', p1: [0, 0, 6800], p2: [6000, 0, 6800] }]], q: { full: 1, cols: 2, colstiff: 1, a: 5 } },
  t146_shaped_tee: { k: 'T146', members: [COL, ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]], q: { shaped: 1, tee: 1, gap: 20 } },
};
