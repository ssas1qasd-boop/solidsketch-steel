// S5 golden: grid parsing (Tekla syntax) and steel part solids from the live v69 chain.
//   node tools/golden/s5_geometry.js      -> tests/steel/golden/s5/grid.json, members.json
// Member solids come from __st.live.build (the live stBuild chain: v32 + v41), as Manifold meshes:
// every vertex (rounded to 0.01 mm), the volume and the bounding box. Native tests compare at 0.1 mm.
// Each case is stored as its property set (ST_SETS) and the overrides; the part is ST_SETS[set] + overrides.
'use strict';
const fs = require('fs');
const path = require('path');
const { GOLDEN, openV69 } = require('./lib/v69');

const GRID = ['0 4*13195', '0 5*5570', '0 7500 1500', '0 6*6000', '0 20000', '0 7000', '3*6000', '-500 2*3000.5 1000', '1000',
  '0  6000   6000 ', '', '0 abc 500', '0 2*-3000 4500', '2*0', '0 0*5000 100', '12.5 1e3'];

// Each case: a property set (ST_SETS) plus overrides. Points in mm (doc unit mm, so model units = mm).
const P = {
  col: [[0, 0, 0], [0, 0, 7500]], colB: [[13195, 5570, 0], [13195, 5570, 7500]],
  bx: [[0, 0, 7500], [13195, 0, 7500]], by: [[0, 0, 7500], [0, 5570, 7500]], bxNeg: [[13195, 5570, 7500], [0, 5570, 7500]],
  raf: [[0, 0, 7500], [13195, 0, 9000]], skew: [[0, 0, 7500], [13195, 5570, 9000]], diag: [[0, 0, 0], [6000, 5570, 7500]],
};
const M = (set, prof, pts, extra) => ({ set, st: Object.assign({ kind: 'member', prof, p1: pts[0], p2: pts[1] }, extra || {}) });
const CASES = {
  col_hea300: M('COLUMN', 'HEA300', P.col),
  col_hea300_gridB: M('COLUMN', 'HEA300', P.colB),
  col_rot_top: M('COLUMN', 'HEA300', P.col, { rot: 'Top' }),
  col_left_front: M('COLUMN', 'HEB240', P.col, { plane: 'Left', depth: 'Front' }),
  col_right_behind_off: M('COLUMN', 'HEB240', P.col, { plane: 'Right', depth: 'Behind', pOff: 25, dOff: -40 }),
  col_ends: M('COLUMN', 'HEA300', P.col, { dx1: -15, dx2: 120 }),
  col_rang: M('COLUMN', 'IPE360', P.col, { rAng: 15 }),
  beam_ipe300_x: M('BEAM', 'IPE300', P.bx),
  beam_ipe300_y: M('BEAM', 'IPE300', P.by),
  beam_ipe300_xneg: M('BEAM', 'IPE300', P.bxNeg),
  beam_front: M('BEAM', 'IPE300', P.bx, { rot: 'Front' }),
  beam_below: M('BEAM', 'IPE300', P.bx, { rot: 'Below' }),
  beam_back: M('BEAM', 'IPE300', P.bx, { rot: 'Back', depth: 'Middle' }),
  beam_depth_front: M('BEAM', 'IPE300', P.bx, { depth: 'Front', plane: 'Left' }),
  beam_offsets: M('BEAM', 'IPE300', P.bx, { pOff: 40, dOff: 60, dx1: 100, dx2: -50 }),
  rafter_ipe360: M('RAFTER', 'IPE360', P.raf),
  rafter_rang: M('RAFTER', 'IPE360', P.raf, { rAng: 30, plane: 'Right' }),
  skew_ipe360: M('RAFTER', 'IPE360', P.skew),
  diag_chs: M('BRACING', 'CHS60.3x3.2', P.diag),
  shs_cat: M('BEAM', 'SHS100x100x5', P.bx),
  shs_param: M('BEAM', 'SHS100*5', P.skew, { rot: 'Front' }),
  rhs_cat: M('BEAM', 'RHS200x100x6', P.raf, { plane: 'Left' }),
  chs_param: M('BRACING', 'CHS168.3*6', P.bx),
  rod: M('BRACING', 'D20', P.diag),
  angle: M('BEAM', 'L100*100*10', P.bx),
  angle_uneq: M('BEAM', 'L140*60*6', P.skew, { rot: 'Below' }),
  flat: M('BEAM', 'FLT20*150', P.by),
  plate_member: M('BEAM', 'PL12*200', P.raf),
  upn: M('PURLIN', 'UPN200', P.by),
  upe_col: M('COLUMN', 'UPE300', P.col, { plane: 'Left' }),
  cc: M('GIRT', 'C200-2-20-60', P.bx),
  zz: M('PURLIN', 'Z200-2-20-60', P.raf),
  bi: M('BEAM', 'BI400-10-250-15-200-12', P.bx),
  phi: M('RAFTER', 'PHI300-600-8-200-12', P.raf),
  phi_col: M('COLUMN', 'PHI300-600-8-200-12-180-10', P.col, { plane: 'Left', depth: 'Behind', dx2: 50 }),
  plate_flat: { set: 'PLATE', st: { kind: 'plate', prof: 'PL20', pts: [[0, 0], [400, 0], [400, 300], [0, 300]], z: 7500 } },
  plate_behind: { set: 'PLATE', st: { kind: 'plate', prof: 'PL15', pts: [[0, 0], [500, 0], [600, 250], [100, 400]], z: 0, depth: 'Behind', dOff: 5 } },
  plate_middle: { set: 'PLATE', st: { kind: 'plate', prof: 'PL10', pts: [[13195, 0], [13495, 0], [13495, 200], [13195, 200]], z: 7500, depth: 'Middle' } },
  plate_3d: { set: 'PLATE', st: { kind: 'plate', prof: 'PL20', pts3: [[0, 0, 7500], [400, 0, 7500], [400, 0, 7900], [0, 0, 7900]] } },
  plate_3d_sloped: { set: 'PLATE', st: { kind: 'plate', prof: 'PL12', pts3: [[0, 0, 0], [300, 0, 100], [300, 250, 100], [0, 250, 0]], depth: 'Front', dOff: 3 } },
};

(async () => {
  const { browser, page } = await openV69({ log: false });
  let out;
  try {
    out = await page.evaluate(({ grid, cases }) => {
      const st = __st, L = st.live;
      const R = v => Math.round(v * 100) / 100;
      const g = grid.map(t => [t, L.parse(t)]);
      const letters = [0, 1, 25, 26, 27, 51, 52, 701, 702].map(i => [i, L.letters(i)]);
      const members = {};
      for (const [name, c] of Object.entries(cases)) {
        const s = Object.assign({}, st.ST_SETS[c.set], c.st);
        if (s.kind === 'plate') { delete s.p1; delete s.p2; }
        try {
          const m = L.build(s); const mesh = m.getMesh(); const n = mesh.numProp, vp = mesh.vertProperties;
          const verts = []; for (let i = 0; i < vp.length; i += n) verts.push([R(vp[i]), R(vp[i + 1]), R(vp[i + 2])]);
          verts.sort((a, b) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2]);
          const bb = m.boundingBox();
          members[name] = { set: c.set, st: c.st, volume: R(Math.abs(m.volume())), bbox: [...Array.from(bb.min), ...Array.from(bb.max)].map(R), verts };
        } catch (e) { members[name] = { set: c.set, st: c.st, error: String(e.message || e) }; }
      }
      return { grid: g, letters, members };
    }, { grid: GRID, cases: CASES });
  } finally { await browser.close(); }
  const dir = path.join(GOLDEN, 's5'); fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'grid.json'), JSON.stringify({ source: 'web v69 stParse / stLetters (live)', parse: out.grid, letters: out.letters }, null, 1) + '\n');
  // Compact: one case per line, so the file stays small and diffs stay readable.
  const lines = Object.entries(out.members).map(([k, v]) => JSON.stringify(k) + ':' + JSON.stringify(v));
  fs.writeFileSync(path.join(dir, 'members.json'), '{"source":"web v69 __st.live.build (stBuild v32+v41) of ST_SETS[set] + st, Manifold mesh vertices rounded to 0.01 mm",\n"cases":{\n' + lines.join(',\n') + '\n}}\n');
  const errs = Object.entries(out.members).filter(([, v]) => v.error);
  console.log(`grid ${out.grid.length} · letters ${out.letters.length} · members ${lines.length} (${errs.length} errors${errs.length ? ': ' + errs.map(([k, v]) => k + ' ' + v.error).join('; ') : ''})`);
})().catch(e => { console.error(e); process.exit(1); });
