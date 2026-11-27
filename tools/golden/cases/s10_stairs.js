// S10: stairs (v44 stStairs + v50 stringer-end welds), railing (v44 stRailing + v50 posts to supports) and the GEN
// applications that run them (TS71 Stairs, TS77 Railing); Tpf (Portal frame) opens the sheet (s10_portal.js).
const BEAM = (p1, p2, prof = 'IPE300') => ['BEAM', { prof, p1, p2 }];
const S = o => ({ run: { stairs: o } }), R = o => ({ run: { railing: o } });
module.exports = {
  // stairs: standard, options, odd stringer, skewed in plan, steep / shallow, and onto supports (top part, landing beams)
  st_std: S({ p0: [0, 0, 0], p1: [4000, 0, 3000] }),
  st_wide: S({ p0: [0, 0, 0], p1: [4000, 0, 3000], width: 1500, stringer: 'UPN240', tread: 8, riserMax: 160 }),
  st_odd: S({ p0: [0, 0, 0], p1: [3000, 0, 2400], stringer: 'PL250*12', width: 900 }),
  st_skew: S({ p0: [1000, 500, 0], p1: [3600, 2900, 3300] }),
  st_steep: S({ p0: [0, 0, 0], p1: [1800, 0, 3200] }),
  st_shallow: S({ p0: [0, 0, 0], p1: [6000, 0, 1500], riserMax: 200 }),
  st_short: S({ p0: [0, 0, 0], p1: [600, 0, 350] }),
  st_top: { members: [BEAM([4300, -1500, 3000], [4300, 1500, 3000], 'HEA200')], run: { stairs: { p0: [0, 0, 0], p1: [4000, 0, 3000], topPart: { idx: 0 } } } },
  st_supports: { members: [BEAM([-200, -1500, 0], [-200, 1500, 0], 'HEA200'), BEAM([4200, -1500, 3000], [4200, 1500, 3000], 'HEA200')], run: { stairs: { p0: [0, 0, 100], p1: [4000, 0, 3100] } } },
  // railing: standard, no knee rail, options, a bent path, sloped (on stairs), on a member, onto supports
  rl_std: R({ path: [[0, 0, 0], [6000, 0, 0]] }),
  rl_noknee: R({ path: [[0, 0, 0], [6000, 0, 0]], knee: false }),
  rl_opts: R({ path: [[0, 0, 0], [5000, 0, 0]], h: 1000, postS: 900, post: 'CHS48.3x3.2', rail: 'CHS48.3x2.6' }),
  rl_bent: R({ path: [[0, 0, 0], [4000, 0, 0], [4000, 3000, 0], [1000, 4500, 0]] }),
  rl_sloped: R({ path: [[0, 0, 0], [4000, 0, 3000]] }),
  rl_short: R({ path: [[0, 0, 0], [700, 0, 0]] }),
  rl_on: { members: [BEAM([0, 0, -150], [6000, 0, -150])], run: { railing: { path: [[0, 0, 0], [6000, 0, 0]], on: { idx: 0 } } } },
  rl_supports: { members: [BEAM([0, 0, -150], [6000, 0, -150]), BEAM([0, 3000, -150], [6000, 3000, -150])], run: { railing: { path: [[0, 0, 0], [6000, 0, 0], [6000, 3000, 0]] } } },
  // GEN applications (catalog): TS71 Stairs between a low and a high beam (picked points and none), TS77 Railing on a beam
  gen_ts71: { members: [BEAM([0, -1000, 0], [0, 1000, 0], 'HEA200'), BEAM([4000, -1000, 3000], [4000, 1000, 3000], 'HEA200')], run: { gen: ['TS71', [0, 1], []] } },
  gen_ts71_pts: { members: [BEAM([0, -1000, 0], [0, 1000, 0], 'HEA200'), BEAM([4000, -1000, 3000], [4000, 1000, 3000], 'HEA200')], run: { gen: ['TS71', [0, 1], [[0, 300, 0], [4000, 300, 3000]]] } },
  gen_ts71_rev: { members: [BEAM([4000, -1000, 3000], [4000, 1000, 3000], 'HEA200'), BEAM([0, -1000, 0], [0, 1000, 0], 'HEA200')], run: { gen: ['TS71', [0, 1], []] } },
  gen_ts77: { members: [BEAM([0, 0, 0], [6000, 0, 0])], run: { gen: ['TS77', [0], []] } },
  gen_ts77_two: { members: [BEAM([0, 0, 0], [6000, 0, 0]), BEAM([6000, 0, 0], [6000, 4000, 0])], run: { gen: ['TS77', [0, 1], [[500, 0, 0], [6000, 3500, 0]]] } },
  // v70 C19: Tekla's railing (R13 lower flight, left: stringer 844; posts at 700 plan from y 8335, the last at 11810.441; foot centres on the top face)
  rl_tekla_r13: { members: [['BEAM', { name: 'STRINGER', prof: 'UPN240', p1: [565.5, 12035.972, 1972.906], p2: [565.5, 8289.137, 78.835], rot: 'Top', rAng: 180, plane: 'Middle', depth: 'Middle' }]],
    run: { railing: { tmode: 'tekla', path: [[565.5, 8335, 236.481], [565.5, 11810.441, 1993.359]], on: { idx: 0 } } } },
  rl_tekla_landing: { members: [BEAM([0, -500, 0], [0, 1500, 0], 'HEA200')], run: { railing: { tmode: 'tekla', path: [[0, 0, 95], [0, 565.75, 95]] } } },
};
