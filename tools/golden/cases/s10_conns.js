// S10: v69's own connection types that the applications make (v34 base / knee / apex, v35 cleat / wgusset / tgusset /
// tieend, v42 gabletop, v43 craneseat / railclip, v44 stairtreads / weldonly; v54 apex options, the later defaults).
// Per type: standard, odd profile, skewed, sloped, and every option of its def varied (one at a time).
const M = (set, prof, p1, p2, o) => [set, Object.assign({ prof, p1, p2 }, o || {})];
const C = (k, members, q, extra) => Object.assign({ members, run: { connect: [k, members.map((_, i) => i), q || {}] } }, extra || {});
const out = {};
const vary = (pre, k, members, opts, extra) => { for (const [o, v] of Object.entries(opts)) out[`${pre}_${o}_${String(v).replace(/[^a-z0-9]/gi, '')}`] = C(k, members, { [o]: v }, extra); };
// ---- base plate (column foot)
const col = M('COLUMN', 'HEA260', [0, 0, 0], [0, 0, 7450]);
out.base_std = C('base', [col]);
out.base_odd = C('base', [M('COLUMN', 'SHS200*10', [0, 0, 0], [0, 0, 6000])]);
out.base_bi = C('base', [M('COLUMN', 'BI400-8-200-12', [0, 0, 0], [0, 0, 6000])]);
out.base_rot = C('base', [M('COLUMN', 'HEB300', [1000, 2000, 300], [1000, 2000, 7000], { rot: 'Front' })]);
out.base_sloped = C('base', [M('COLUMN', 'HEA200', [0, 0, 0], [400, 300, 5000])]);
out.base_reversed = C('base', [M('COLUMN', 'HEA260', [0, 0, 7450], [0, 0, 0])]);
vary('base', 'base', [col], { t: 30, ext: 120, side: 60, bolt: 30, grade: '10.9', anch: 800, a: 8 });
// ---- eaves haunch (column + rafter at 12°); length 0 = span / 20 (S.stGen.span, else 20 000)
const H = 7450, raf = (x1, z1, o) => M('RAFTER', 'IPE400', [0, 0, H], [x1, 0, z1], o);
const knee = [col, raf(12500, H + 12500 * Math.tan(12 * Math.PI / 180))];
out.knee_std = C('knee', knee);
out.knee_stgen = C('knee', knee, {}, { stGen: { span: 30000 } });
out.knee_len = C('knee', knee, { len: 1800 });
out.knee_odd = C('knee', [M('COLUMN', 'HEB360', [0, 0, 0], [0, 0, H]), M('RAFTER', 'IPE500', [0, 0, H], [12000, 0, H + 2000])]);
out.knee_colfront = C('knee', [M('COLUMN', 'HEA260', [0, 0, 0], [0, 0, H], { rot: 'Front' }), raf(12500, H + 2600)]);
out.knee_flat = C('knee', [col, raf(10000, H)]);
out.knee_skew = C('knee', [col, M('RAFTER', 'IPE400', [0, 0, H], [9000, 6000, H + 1800])]);
out.knee_rafdepth_front = C('knee', [col, raf(12500, H + 2600, { depth: 'Front' })]);
out.knee_rafdepth_behind = C('knee', [col, raf(12500, H + 2600, { depth: 'Behind' })]);
out.knee_reversed = C('knee', [col, M('RAFTER', 'IPE400', [12500, 0, H + 2600], [0, 0, H])]);
vary('knee', 'knee', knee, { tp: 30, len: 2500, depth: 500, rows: 6, bolt: 24, grade: '10.9', gauge: 140, cap: 25, stiff: 20, a: 8 });
// ---- apex haunch (two rafters); v54 options (ph > 0: Tekla 106 plates)
const Z = 7450 + 12500 * Math.tan(12 * Math.PI / 180), apex = [M('RAFTER', 'IPE400', [0, 0, 7450], [12500, 0, Z]), M('RAFTER', 'IPE400', [25000, 0, 7450], [12500, 0, Z])];
out.apex_std = C('apex', apex);
out.apex_odd = C('apex', [M('RAFTER', 'IPE600', [0, 0, 7450], [12500, 0, Z]), M('RAFTER', 'IPE600', [25000, 0, 7450], [12500, 0, Z])]);
out.apex_flat = C('apex', [M('RAFTER', 'IPE400', [0, 0, 7000], [8000, 0, 7000]), M('RAFTER', 'IPE400', [16000, 0, 7000], [8000, 0, 7000])]);
out.apex_skew = C('apex', [M('RAFTER', 'IPE400', [0, 0, 7450], [9000, 7000, Z]), M('RAFTER', 'IPE400', [18000, 14000, 7450], [9000, 7000, Z])]);
out.apex_steep = C('apex', [M('RAFTER', 'IPE400', [0, 0, 7000], [6000, 0, 10500]), M('RAFTER', 'IPE400', [12000, 0, 7000], [6000, 0, 10500])]);
out.apex_depth_front = C('apex', apex.map(m => [m[0], Object.assign({}, m[1], { depth: 'Front' })]));
vary('apex', 'apex', apex, { tp: 30, len: 1400, depth: 400, rows: 5, bolt: 24, grade: '10.9', gauge: 140, a: 8 });
out.apex_v54 = C('apex', apex, { ph: 400, pw: 160 });
out.apex_v54_rows = C('apex', apex, { ph: 400, pw: 160, rowsAt: '50 120 260 330', top: '30', bot: '30', hf: 12, hb: 150, hw: 10 });
out.apex_v54_hb = C('apex', apex, { ph: 450, pw: 200, hb: 0, hf: 8 });
// ---- purlin cleat (rafter + purlin square to the slope)
const pit = 12 * Math.PI / 180, rf = M('RAFTER', 'IPE400', [0, 0, 7450], [12500, 0, Z]), sx = 3000, sz = 7450 + sx * Math.tan(pit) + 200;
const pur = M('PURLIN', 'IPE160', [sx, -3000, sz], [sx, 3000, sz], { rAng: -12 });
out.cleat_std = C('cleat', [rf, pur]);
out.cleat_odd = C('cleat', [rf, M('PURLIN', 'CC200-2-20-60', [sx, -3000, sz], [sx, 3000, sz], { rAng: -12 })]);
out.cleat_flat = C('cleat', [M('RAFTER', 'IPE400', [0, 0, 7000], [10000, 0, 7000]), M('PURLIN', 'IPE160', [3000, -3000, 7280], [3000, 3000, 7280])]);
out.cleat_zed = C('cleat', [rf, M('PURLIN', 'Z200-2-20-60', [sx, -3000, sz], [sx, 3000, sz], { rAng: -12 })]);
vary('cleat', 'cleat', [rf, pur], { t: 12, h: 250, w: 120, bolt: 16, grade: '10.9', a: 6 });
// ---- bolted gusset (column + wall bracing flat bar); tube gusset (rafter + roof brace); tie end plate (column + tie)
const wb = M('BRACING', 'PL80*10', [-145, 0, 300], [-145, 6000, 6650], { name: 'WALL BRACING', depth: 'Middle' });
out.wg_std = C('wgusset', [col, wb]);
out.wg_odd = C('wgusset', [col, M('BRACING', 'FLT100*10', [-145, 0, 300], [-145, 6000, 6650], { depth: 'Middle' })]);
out.wg_steep = C('wgusset', [col, M('BRACING', 'PL80*10', [-145, 0, 300], [-145, 3000, 6650], { depth: 'Middle' })]);
out.wg_top = C('wgusset', [col, M('BRACING', 'PL80*10', [-145, 6000, 300], [-145, 0, 6650], { depth: 'Middle' })]);
vary('wg', 'wgusset', [col, wb], { t: 15, len: 400, w: 200, bolt: 20, grade: '10.9', a: 6 });
const rb = M('BRACING', 'CHS114.3x3.6', [700, 0, 7450 + 700 * Math.tan(pit) - 200], [6250, 6000, 7450 + 6250 * Math.tan(pit) - 200], { depth: 'Middle' });
out.tg_std = C('tgusset', [rf, rb]);
out.tg_odd = C('tgusset', [rf, M('BRACING', 'CHS88.9x3.2', rb[1].p1, rb[1].p2, { depth: 'Middle' })]);
out.tg_rod = C('tgusset', [rf, M('BRACING', 'D20', rb[1].p1, rb[1].p2, { depth: 'Middle' })]);
out.tg_flat = C('tgusset', [M('RAFTER', 'IPE400', [0, 0, 7000], [12000, 0, 7000]), M('BRACING', 'CHS114.3x3.6', [700, 0, 6800], [6000, 6000, 6800], { depth: 'Middle' })]);
vary('tg', 'tgusset', [rf, rb], { t: 14, gap: 150, lap: 180, w: 200, a: 7 });
const tie = M('BEAM', 'CHS114.3x3.6', [0, 0, 6900], [0, 6000, 6900], { depth: 'Middle', dx1: -15.75, dx2: -15.75, name: 'EAVES TIE' });
out.tie_std = C('tieend', [col, tie]);
out.tie_odd = C('tieend', [M('COLUMN', 'HEB300', [0, 0, 0], [0, 0, 7450]), M('BEAM', 'SHS100*3', [0, 0, 6900], [0, 6000, 6900], { depth: 'Middle' })]);
out.tie_far = C('tieend', [M('COLUMN', 'HEA260', [0, 6000, 0], [0, 6000, 7450]), tie]);
vary('tie', 'tieend', [col, tie], { t: 16, size: 200, bolt: 20, grade: '10.9', a: 6 });
// ---- gable column top (rafter + gable column under it)
const gc = M('COLUMN', 'HEA200', [6000, 0, 0], [6000, 0, 7450 + 6000 * Math.tan(pit)]);
out.gt_std = C('gabletop', [rf, gc]);
out.gt_odd = C('gabletop', [rf, M('COLUMN', 'IPE300', [6000, 0, 0], [6000, 0, 7450 + 6000 * Math.tan(pit)])]);
out.gt_flat = C('gabletop', [M('RAFTER', 'IPE400', [0, 0, 7000], [12000, 0, 7000]), M('COLUMN', 'HEA200', [6000, 0, 0], [6000, 0, 6600])]);
vary('gt', 'gabletop', [rf, gc], { tp: 20, ext: 90, side: 20, bolt: 20, grade: '10.9', a: 8 });
// ---- crane beam seat (bracket + crane beam on it) and rail clips (crane beam + rail)
const br = M('BEAM', 'HEA300', [0, 0, 5000], [600, 0, 5000], { name: 'CRANE BRACKET' }), cb = M('BEAM', 'HEB400', [350, -3000, 5150], [350, 9000, 5150], { name: 'CRANE BEAM', depth: 'Behind' });
out.cs_std = C('craneseat', [br, cb]);
out.cs_odd = C('craneseat', [M('BEAM', 'HEB300', [0, 0, 5000], [600, 0, 5000]), M('BEAM', 'HEB500', [350, -3000, 5150], [350, 9000, 5150], { depth: 'Behind' })]);
vary('cs', 'craneseat', [br, cb], { tp: 25, bolt: 24, grade: '10.9', ts: 15, gx: 80, a: 8 });
const rl = M('BEAM', 'PL50*60', [350, -3000, 5550], [350, 9000, 5550], { name: 'CRANE RAIL', depth: 'Behind' });
out.rc_std = C('railclip', [cb, rl]);
out.rc_odd = C('railclip', [M('BEAM', 'HEB500', [350, -3000, 5150], [350, 9000, 5150], { depth: 'Behind' }), M('BEAM', 'PL60*70', [350, -3000, 5650], [350, 9000, 5650], { depth: 'Behind' })]);
vary('rc', 'railclip', [cb, rl], { s: 500, t: 15, w: 100, l: 80, bolt: 20, grade: '10.9', stop: 0 });
// ---- stair treads (two stringers) and the plain weld
const S1 = M('BEAM', 'UPN200', [0, -537.5, 0], [4000, -537.5, 3000], { name: 'STRINGER', depth: 'Middle' }), S2 = M('BEAM', 'UPN200', [0, 537.5, 0], [4000, 537.5, 3000], { name: 'STRINGER', depth: 'Middle' });
out.tr_std = C('stairtreads', [S1, S2]);
out.tr_odd = C('stairtreads', [M('BEAM', 'PL250*12', [0, -500, 0], [3000, -500, 2400], { depth: 'Middle' }), M('BEAM', 'PL250*12', [0, 500, 0], [3000, 500, 2400], { depth: 'Middle' })]);
out.tr_skew = C('stairtreads', [M('BEAM', 'UPN200', [0, -537.5, 0], [3000, 2000, 3000], { depth: 'Middle' }), M('BEAM', 'UPN200', [0, 537.5, 0], [3000, 3075, 3000], { depth: 'Middle' })]);
vary('tr', 'stairtreads', [S1, S2], { sl: 200, tread: 8, riserMax: 150, nosing: 40, leg: 60, ta: 6, bolt: 16, grade: '10.9', foot: 0, tf: 15 });
out.weld_std = C('weldonly', [M('BEAM', 'IPE300', [0, 0, 3000], [6000, 0, 3000]), M('COLUMN', 'CHS48.3x3.2', [3000, 0, 3150], [3000, 0, 4250])]);
out.weld_a = C('weldonly', [M('BEAM', 'IPE300', [0, 0, 3000], [6000, 0, 3000]), M('COLUMN', 'CHS48.3x3.2', [3000, 0, 3150], [3000, 0, 4250])], { a: 6 });
module.exports = out;
