// Page-side dumper: runs inside v69 and returns plain JSON for one model.
// Positions are rounded to 0.01 mm; native tests compare with 0.1 mm tolerance,
// counts and marks exactly, NC text line by line (CLAUDE.md, "How we verify").
'use strict';

/* eslint-disable no-undef */
function dumpModel(opts) {
  opts = opts || {};
  const S = __ss.S, st = __st;
  const R = v => Math.round(v * 100) / 100;
  const RV = a => a.map(R);
  const bbox = m => { try { const q = m.boundingBox(); return { min: RV(Array.from(q.min)), max: RV(Array.from(q.max)) }; } catch (e) { return null; } };
  const vol = m => { try { return R(Math.abs(m.volume())); } catch (e) { return null; } };
  const KEEP = ['kind', 'name', 'prof', 'mat', 'cls', 'fin', 'pp', 'ps', 'ap', 'as', 'pos', 'amark', 'asm', 'conn',
    'p1', 'p2', 'plane', 'pOff', 'rot', 'rAng', 'depth', 'dOff', 'dx1', 'dx2', 'd', 'n', 'grade', 'L', 'ax'];
  const roundDeep = v => typeof v === 'number' ? R(v) : Array.isArray(v) ? v.map(roundDeep)
    : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, roundDeep(x)])) : v;

  const parts = [], bolts = [];
  for (const b of S.bodies) {
    if (!b.st) continue;
    const s = {}; for (const k of KEEP) if (b.st[k] !== undefined) s[k] = roundDeep(b.st[k]);
    if (b.st.kind === 'bolts') {
      bolts.push(Object.assign({ id: b.id, pts: roundDeep(b.st.pts || []) }, s));
      continue;
    }
    let w = null; try { const x = st.partWeight(b); w = { weight: R(x.weight), net: R(x.net), gross: R(x.gross), catalog: !!x.catalog, kgm: R(x.kgm || 0), len: R(x.len || 0) }; } catch (e) { w = { error: String(e.message || e) }; }
    let holes = []; try { holes = st.tkd.holes([b.id]).map(h => ({ d0: h.d0, a: h.a, b: h.b, ax: RV(h.ax) })); } catch (e) { holes = [{ error: String(e.message || e) }]; }
    parts.push(Object.assign({ id: b.id, type: b.type, volume: vol(b.man), bbox: bbox(b.man), weight: w, holes,
      outline: b.st.pts3 ? roundDeep(b.st.pts3) : (b.st.pts ? roundDeep(b.st.pts) : null) }, s));
  }

  // Connections: inputs (q), welds, and the cut solids each one applies to each part.
  const conns = [];
  for (const c of S.stConns || []) {
    let cuts = {}; let ok = null;
    try { ok = st.connOK(c); } catch (e) { ok = null; }
    try { const g = st.live.connGeom(c); /* live chain; st.connGeom is the stale v34 export */ if (g && g.cuts) for (const [id, list] of g.cuts) cuts[id] = list.map(m => ({ volume: vol(m), bbox: bbox(m) })); } catch (e) { cuts = { error: String(e.message || e) }; }
    conns.push({ id: c.id, type: c.type, parts: c.parts, ok, q: roundDeep(c.q || {}),
      welds: (c.welds || []).map(w => ({ name: w.name, a: w.a, len: R(w.len || 0) })), cuts });
  }

  const W = st.weights();
  const out = {
    counts: { parts: parts.length, boltGroups: bolts.length, bolts: bolts.reduce((n, b) => n + (+b.n || 0), 0), connections: conns.length },
    parts, bolts, conns,
    weights: { weight_t: W.weight_t, net_t: W.net_t, gross_t: W.gross_t, perAssembly: W.asm, perPartMark: W.part },
  };

  // Drawings: the list, and each drawing's data (dimensions, tables, lines) as v69 builds it.
  const list = st.tkd.all();
  out.drawings = list.map(x => ({ key: x.key, type: x.type, mark: x.mark, title: x.title, n: x.n == null ? null : x.n, file: x.file }));
  if (opts.drawingData) {
    out.drawingData = {};
    for (const x of list) { if (x.type === 'G') continue; try { const dw = st.tkd.build(x.key); out.drawingData[x.key] = dw ? JSON.parse(st.tkd.json(dw)) : null; } catch (e) { out.drawingData[x.key] = { error: String(e.message || e) }; } }
  }

  // NC / DSTV files (text kept whole; S13 compares line by line).
  try { const r = st.ncExport({}); out.nc = { files: (r.files || []).map(f => ({ path: f.path || f.name, text: f.text })), warnings: r.warnings || [] }; }
  catch (e) { out.nc = { error: String(e.message || e) }; }
  return out;
}

module.exports = { dumpModel };
