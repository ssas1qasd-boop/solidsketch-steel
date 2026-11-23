// S9 GEN family golden cases (tools/golden/gen_cases.js): members, then v69 __st.genRun(k, all member ids, pts) with q
// saved as the defaults for new components (ss.connDef, read by stCQ). Members in mm.
const col = (x, y, h, o) => ['COLUMN', Object.assign({ p1: [x, y, 0], p2: [x, y, h] }, o || {})];
const raf = (y, z0, z1, o) => ['RAFTER', Object.assign({ p1: [0, y, z0], p2: [10000, y, z1] }, o || {})];
const pur = (x, y0, y1, z, o) => ['PURLIN', Object.assign({ p1: [x, y0, z], p2: [x, y1, z] }, o || {})];
const C = {};
for (const k of ['T13b', 'T13c', 'TS3']) {   // rodcross: T1w on both columns
  C[k + '_std'] = { k, members: [col(0, 0, 6000), col(6000, 0, 6000)] };
  C[k + '_odd'] = { k, members: [col(0, 0, 5000, { prof: 'HEB240', rot: 'Top' }), col(5000, 0, 5000, { prof: 'HEB240', rot: 'Top' })], q: { prof: 'D24' } };
  C[k + '_skew'] = { k, members: [col(0, 0, 6000), col(5500, 1500, 7500)] };
}
C.TS56_std = { k: 'TS56', members: [col(0, 0, 6000), col(6000, 0, 6000)] };
C.TS56_odd = { k: 'TS56', members: [col(0, 0, 6000, { prof: 'HEB300' }), col(6000, 0, 6000, { prof: 'HEB300' })], q: { prof: 'L80x8' } };
C.TS56_skew = { k: 'TS56', members: [col(0, 0, 6000), col(6000, 800, 7000)] };
C.TS56_tube = { k: 'TS56', members: [col(0, 0, 6000), col(6000, 0, 6000)], q: { prof: 'SHS100x5' } };
for (const k of ['TS46', 'TS47', 'TS48', 'TS49']) {   // tube: one brace, T20
  C[k + '_std'] = { k, members: [col(0, 0, 6000), col(6000, 0, 6000)] };
  C[k + '_odd'] = { k, members: [col(0, 0, 6000, { prof: 'HEB260' }), col(5000, 0, 6000, { prof: 'HEB260' })], q: { prof: 'RHS120x80x5' } };
  C[k + '_skew'] = { k, members: [col(0, 0, 6000), col(6000, 1000, 7500)] };
}
C.TS55_std = { k: 'TS55', members: [raf(0, 7500, 7500), raf(6000, 7500, 7500)] };
C.TS55_odd = { k: 'TS55', members: [raf(0, 7500, 7500, { prof: 'HEA300' }), raf(6000, 7500, 7500, { prof: 'HEA300' })], q: { prof: 'D20' } };
C.TS55_slope = { k: 'TS55', members: [raf(0, 7500, 9000), raf(6000, 7500, 9000)] };
C.T50p_std = { k: 'T50p', members: [raf(0, 7500, 7500), raf(6000, 7500, 7500)] };
C.T50p_odd = { k: 'T50p', members: [raf(0, 7500, 7500, { prof: 'HEA300', p2: [7000, 0, 7500] }), raf(5000, 7500, 7500, { prof: 'HEA300', p2: [7000, 5000, 7500] })], q: { prof: 'HEA120', s: 1200 } };
C.T50p_slope = { k: 'T50p', members: [raf(0, 7500, 9000, { p2: [8000, 0, 9000] }), raf(6000, 7500, 9000, { p2: [8000, 6000, 9000] })] };
C.T80b_std = { k: 'T80b', members: [pur(0, 0, 6000, 8000), pur(1500, 0, 6000, 8000)] };
C.T80b_odd = { k: 'T80b', members: [pur(0, 0, 6000, 8000, { prof: 'C200-2-20-70' }), pur(1500, 0, 6000, 8000, { prof: 'C200-2-20-70' })], q: { prof: 'D12' } };
C.T80b_slope = { k: 'T80b', members: [pur(0, 0, 6000, 8000), pur(1500, 0, 6000, 8400)] };
C.TS78_std = { k: 'TS78', members: [col(0, 0, 6000), col(12000, 0, 6000)] };
C.TS78_odd = { k: 'TS78', members: [col(0, 0, 6000, { prof: 'HEB300' }), col(10000, 0, 6000, { prof: 'HEB300' })], q: { prof: 'HEB200', n: 4, depth: 1200 } };
C.TS78_slope = { k: 'TS78', members: [col(0, 0, 6000), col(9000, 0, 7500)] };
C.T1054_std = { k: 'T1054', members: [['BEAM', { p1: [0, 0, 6000], p2: [6000, 0, 6000] }]] };
C.T1054_n3 = { k: 'T1054', members: [['BEAM', { prof: 'HEA200', p1: [0, 0, 6000], p2: [9000, 0, 6000], dx1: 50 }]], q: { n: 3 } };
C.T1054_slope = { k: 'T1054', members: [['RAFTER', { p1: [0, 0, 7500], p2: [8000, 3000, 9000] }]], q: { n: 4 } };
C.Tof_one = { k: 'Tof', members: [col(0, 0, 6000)] };
module.exports = C;
