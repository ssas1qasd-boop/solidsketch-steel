// S8: components that use the point picked on the first part (v69 c.pt): the bearing plate at the picked beam end.
module.exports = {
  pt_t1044_far_end: { k: 'T1044', members: [['BEAM', { prof: 'IPE360', p1: [0, 0, 4000], p2: [6000, 0, 4000] }]], q: {}, pt: [5800, 0, 4100] },
  pt_t1044_near_end: { k: 'T1044', members: [['RAFTER', { prof: 'IPE300', p1: [0, 0, 4000], p2: [6000, 2000, 4600] }]], q: {}, pt: [300, 100, 4000] },
};
