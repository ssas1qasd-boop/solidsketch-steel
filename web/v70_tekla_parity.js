  // ===================== v70 · Tekla parity =====================
  // Inserted into the web build just before the "v28 · BIM layers" chapter (inside the steel scope: CONN, FAM, TKD, TKF, tkOpt,
  // purlinWeb, stMF, stFaceAt, stFrame3, stBolts are in reach). tools/web/build_v70.py writes reference/solidsketch_v70.html;
  // the golden harness (tools/golden/lib/v69.js) adds the same text in memory. One parity fix per section; each section names
  // its proof in docs/steel/PARITY.md.
  //
  // ---------- C04 · purlin cleat (Tcl): Tekla's own cleat (R11) ----------
  // Proof (docs/steel/PARITY.md "R11 fix 1", Tekla package tests/steel/tekla/): bolt list 4.6 BOLT 12X30 x 1056; assembly drawings
  // R3022 / R3023 / R3001 (short leg on the rafter flange, tall leg bolted through the purlin web, stiffener in the corner across the
  // purlin on the rafter web line, holes 40 / 100 above the rafter top and +-35 from the rafter web); NC 8503 (L140*60*6 L120, holes
  // 25 / 95 x 40 / 100) and NC 8504 (PL6, outline (0,0) (114,0) (124,10) (124,44) (114,44) (0,10)). That is v69's side (behind the web,
  // the short leg pointing away from the purlin) with Tekla's stiffener and bolts. 'Cleat location': Back (Tekla, the default for new
  // cleats) / v69 (the result before v70). Old models have no 'loc' in their stored settings and keep the v69 result.
  // Z and I purlins: unchanged (the package has none).
  CONN.Tcl.def.loc = 'back'; CONN.Tcl.def.grade = '4.6';
  ST_LBL.loc = 'Cleat location';
  { const tab = TKD.Tcl.tabs.find(t => t.name === 'Parts'); const g = tab && (tab.groups || []).find(x => x[0] === 'Cleat');
    if (g && !g[1].some(f => f.k === 'loc')) g[1].push(TKF('loc', 'Cleat location', { type: 'sel', opts: [tkOpt('back', 'Back'), tkOpt('v69', 'v69 (before v70)')], note: 'Back = against the purlin web, as Tekla (stiffener and bolts as Tekla NC 8504 / bolt list)' })); }
  const FAM_CL70 = FAM.CL;
  FAM.CL = X => {
    const { U, q, A, c, cut, plate, bolts, weld } = X; const purs = [X.B, X.B2].filter(Boolean); if (!purs.length) return null;
    const MP = stMF(purs[0].st); const loc = c && c.q ? c.q.loc : undefined;
    if (loc !== 'back' || !(MP.p.kind === 'C' || MP.p.kind === 'U')) return FAM_CL70(X);
    const MA = stMF(A.st);
    const w0 = MA.c1.clone().sub(MP.c1); const b = MA.a.dot(MP.a), dd = MA.a.dot(w0), ee = MP.a.dot(w0), den = (1 - b * b) || 1e-9; const PP = MP.c1.clone().addScaledVector(MP.a, (ee - b * dd) / den);
    const FA = stFaceAt(A.st, PP); const n = FA.n.clone(); const base = FA.Pc.clone().addScaledVector(n, FA.off * U); const y = MP.a.clone().addScaledVector(n, -MP.a.dot(n)).normalize();
    // R12 (Tekla's IFC): the cleat's underside is the purlin's underside (264 / 264 within 0.02 mm), not the rafter axis' top face (Tekla's
    // flange plates are not quite parallel to the axis: up to 2 mm along a rafter). The level is the purlin section's lowest point along n.
    { const sec = stSection(MP.p); if (sec) { let lo = Infinity; for (const q of sec.outer) lo = Math.min(lo, MP.es.dot(n) * q[0] + MP.eh.dot(n) * q[1]); base.addScaledVector(n, PP.dot(n) + lo * U - base.dot(n)); } }
    const W = purlinWeb(MP, q); const out = W.dir.clone().addScaledVector(n, -W.dir.dot(n)).addScaledVector(y, -W.dir.dot(y)).normalize();
    const webPt = PP.clone().addScaledVector(W.dir, W.off * U); const sw = webPt.clone().sub(base).dot(out); const O = base.clone().addScaledVector(out, sw);   // rafter top, web back, frame line (as v69)
    const t = q.t * U, L1 = q.L1 * U, L2 = q.L2 * U, w = q.w / 2 * U; const F = stFrame3(O, out, n);   // x = away from the web, y = up from the rafter top, z = along the purlin (as v69)
    let cl = F.poly([[0, 0], [L2, 0], [L2, t], [t, t], [t, L1], [0, L1]], -w, w);
    if (q.stiff) {   // NC 8504: 124 up the tall leg, 44 along the short leg (L1 - 16, L2 - 16), 10 x 10 snip at the heel, 10 wide at both ends
      const hS = L1 - 16 * U, wS = L2 - 16 * U, k = 10 * U;
      plate('CLEAT STIFFENER', F.poly([[t, t + hS], [t, t + k], [t + k, t], [t + wS, t], [t + wS, t + k], [t + k, t + hS]], -q.ts * U / 2, q.ts * U / 2), q.ts); }
    const zAxis = F.Z; const all = [];
    for (const P of purs) { const M2 = stMF(P.st); const mid = M2.c1.clone().lerp(M2.c2, 0.5); const sgn = Math.sign(mid.clone().sub(O).dot(zAxis)) || 1; const W2 = purlinWeb(M2, q); const tw2 = (W2.t || 2) * U;
      const zz = (purs.length === 1 && Math.abs(M2.c1.clone().sub(O).dot(zAxis)) > w && Math.abs(M2.c2.clone().sub(O).dot(zAxis)) > w && Math.sign(M2.c1.clone().sub(O).dot(zAxis)) !== Math.sign(M2.c2.clone().sub(O).dot(zAxis))) ? [-q.e * U, q.e * U] : [sgn * q.e * U];
      const bl = []; for (const z of zz) for (const hh of [q.h1, q.h2]) bl.push(F.P(t, hh * U, z));   // head on the cleat's outer face, toward the web (Tekla)
      const grip = t + tw2; const BB = stBolts(bl, out.clone().negate().toArray(), q.bolt, grip, U);
      for (const hc of BB.holes) { cl = cl.subtract(hc); cut(P.id, hc); } all.push([BB, bl.length, grip]); }
    // Tekla bolt length: the catalogue length (5 mm steps) for grip + 1.5·d + 2 (M12, grip 8: 30, as the bolt list)
    plate('CLEAT', cl, q.t, `L${q.L1}*${q.L2}*${q.t}`); for (const [BB, k, grip] of all) bolts(BB, k, Math.ceil((grip / U + 1.5 * q.bolt + 2) / 5) * 5); weld('Cleat to rafter top flange', q.a, 2 * q.w);
  };

  // ---------- Generation of purlins (50), T50p: purlins on the real rafter top (C04 follow-up, owner 2026-11-25) ----------
  // v69 put each purlin's line at the rafter's centre + h/2 straight UP (global Z), kept it upright (rot Top, no tilt) and let the
  // PURLIN property set override the profile chosen in the dialog (q.prof). On a sloped rafter the purlin then cut into the rafter
  // (IPE160 on 12.8°: 13.6 mm at the lower flange corner, 1.0 mm at the other; docs/steel/PARITY.md "C04 workflow"). Tekla (R11):
  // the purlin's web stands perpendicular to the rafter and its underside lies on the rafter top. v70: the profile is q.prof (the PURLIN
  // set's when blank); the purlin's section centre is on rafter top + h/2 along the rafter's up direction (perpendicular to the rafter),
  // and the section is turned so its web is perpendicular to the rafters (rAng). Supports that are vertical keep v69's purlins.
  // The purlins it made before v70 stay as saved (generation is a one-time action).
  const stGenRun70 = stGenRun;
  stGenRun = function (k, parts, pts) {
    const CN = CONN[k]; const q = CN ? Object.assign({}, CN.def, stCQ(k)) : {};
    if (!CN || q.kind !== 'purlins') return stGenRun70(k, parts, pts);
    const U = stU(); const A = stBody(parts[0]), B = parts[1] != null ? stBody(parts[1]) : null;
    if (!A || !B || !A.st || !B.st || A.st.kind !== 'member' || B.st.kind !== 'member') return stGenRun70(k, parts, pts);
    const MA = stMF(A.st), MB = stMF(B.st); const Z = new THREE.Vector3(0, 0, 1);
    const up = M => { const n = Z.clone().addScaledVector(M.a, -M.a.dot(Z)); return n.lengthSq() < 1e-6 ? null : n.normalize(); };
    const nA = up(MA), nB = up(MB); if (!nA || !nB) return stGenRun70(k, parts, pts);
    const prof = q.prof || ST_SETS.PURLIN.prof; const pp = stProf(prof); if (!pp) { toast(`Unknown profile ${prof}`); return; }
    const hp = pp.kind === 'O' ? pp.d : (pp.h || 160); const s = (q.s || 1500) * U; const L = Math.min(MA.c2.distanceTo(MA.c1), MB.c2.distanceTo(MB.c1));
    const made = [], conns = [];
    for (let x = 150 * U; x <= L - 100 * U; x += s) {
      const pa = MA.c1.clone().addScaledVector(MA.a, x).addScaledVector(nA, (MA.h / 2 + hp / 2) * U), pb = MB.c1.clone().addScaledVector(MB.a, x).addScaledVector(nB, (MB.h / 2 + hp / 2) * U);
      const ap = pb.clone().sub(pa).normalize(); const base = Z.clone().addScaledVector(ap, -ap.dot(Z)).normalize(); const n = nA.clone().add(nB).normalize();
      const th = Math.atan2(new THREE.Vector3().crossVectors(base, n).dot(ap), base.dot(n)) * 180 / Math.PI;
      const st = Object.assign({ kind: 'member' }, ST_SETS.PURLIN, { name: 'PURLIN', prof, p1: pa.toArray(), p2: pb.toArray(), rot: 'Top', rAng: Math.round(th * 1e4) / 1e4, plane: 'Middle', depth: 'Middle', pOff: 0, dOff: 0 });
      try { const r = stRecord(st); made.push(r); if (!taken70(A.id, pa)) conns.push(['Tpc', [A.id, r.id]]); if (!taken70(B.id, pb)) conns.push(['Tpc', [B.id, r.id]]); } catch (e) { toast(e.message || String(e)); }
    }
    if (!made.length) return; step(() => { S.bodies = [...S.bodies, ...made]; ensureFolder28('stBrace'); const ids = []; for (const [ck, ps] of conns) { if (!CONN[ck]) continue; stConnAdd(ck, ps, stCQ(ck)); ids.push(...ps); } if (ids.length) stRegen([...new Set(ids)]); }, CN.label); commit(); stWeldsDraw(); toast(`${CN.label}: ${made.length} parts${conns.length ? ` · ${conns.length} connections` : ''}`);
  };
  // one cleat per joint (owner 2026-11-26, Tekla): a purlin end on a rafter that already has a cleat (Tpc / Tcl) for a purlin ending at the same
  // point (a purlin run on each side of a middle rafter) gets no second plate cleat
  function taken70(rafId, P) { return (S.stConns || []).some(c => (c.type === 'Tpc' || c.type === 'Tcl') && c.parts[0] === rafId && c.parts.slice(1).some(id => { const b = stBody(id); return b && b.st && b.st.p1 && [b.st.p1, b.st.p2].some(q => V3(q).distanceTo(P) < 2 * stU()); })); }
  window.__st.genRun = (k, p, q) => stGenRun(k, p, q);

  // ---------- Weights · Tekla's own rule (R12, owner 2026-11-26) ----------
  // Proof (docs/steel/PARITY.md "Weights", tools/golden/r12_weights.py): in Tekla's IFC every part's weight is its own faceted
  // solid x density, bolt holes not taken off (sum over 4,945 parts 63,705.8 kg; Part List 63,711.7). Tekla builds a circle as
  // an inscribed polygon: solid rods (ROD / D) 8 sides, tubes (PD / CHS) 12 sides below D50 and 16 from D50 (the model's sizes:
  // ROD12/16/20, CHS48*3, PD40*10, PD50*5); SHS / RHS with square corners; grating 1,333 kg/m3, grout 0. v66 scaled every round
  // section by a fixed table (taken from R10's meshes, e.g. ROD16 188.4 instead of 181.0, PD40*10 942.5 instead of 900) and SHS by
  // the ratio to v69's own section, so a part that already has Tekla's polygon (an imported model, R12) was scaled twice. v70: the
  // factor is Tekla's section area / the part's own section area, measured on the solid (10 mm slabs at 20 / 35 / 50 / 65 / 80 %
  // of the length, the median). Catalog
  // profiles keep catalog kg/m x length (v66).
  {
    const R66 = window.__st.weightRules, P66 = window.__st.partWeight;
    R66.TK_DENS.GRATING = 1333;
    R66.TK_KGM['CC200-2-20-60'] = 5.5264;                           // Tekla weighs the C by its section (704 mm² x 7850), not the rounded 5.53
    const r2 = x => Math.round(x * 100) / 100, ngon = (n, r) => n / 2 * Math.sin(2 * Math.PI / n) * r * r;
    const profOf = st => String(st.tprof || st.prof || '').trim().toUpperCase();
    function tkArea70(p) {                                          // Tekla's section area, mm²; null = no rule (v66 stays)
      let m = /^(?:ROD|RD|D)(\d+(?:\.\d+)?)$/.exec(p); if (m) return ngon(8, +m[1] / 2);
      m = /^(?:PD|CHS)(\d+(?:\.\d+)?)\*(\d+(?:\.\d+)?)$/.exec(p); if (m) { const D = +m[1], t = +m[2], n = D < 50 ? 12 : 16; return ngon(n, D / 2) - ngon(n, Math.max(0, D / 2 - t)); }
      m = /^SHS(\d+(?:\.\d+)?)\*(\d+(?:\.\d+)?)$/.exec(p); if (m) { const b = +m[1], t = +m[2]; return b * b - (b - 2 * t) ** 2; }
      m = /^RHS(\d+(?:\.\d+)?)\*(\d+(?:\.\d+)?)\*(\d+(?:\.\d+)?)$/.exec(p); if (m) { const h = +m[1], b = +m[2], t = +m[3]; return h * b - (h - 2 * t) * (b - 2 * t); }
      return null;
    }
    function ownArea70(b) {                                         // the solid's section area (10 mm slabs, median of 5), mm²
      const U = stU(), st = b.st, P1 = V3(st.p1), P2 = V3(st.p2), a = P2.clone().sub(P1).normalize(), c = P1.clone().add(P2).multiplyScalar(0.5);
      const X = new THREE.Vector3().crossVectors(a, Math.abs(a.z) < 0.9 ? new THREE.Vector3(0, 0, 1) : new THREE.Vector3(1, 0, 0)).normalize(), Y = new THREE.Vector3().crossVectors(a, X).normalize();
      const big = 5000 * U, L = P2.distanceTo(P1), A = [];          // 10 mm slabs at 20 / 35 / 50 / 65 / 80 % of the length: the median (a slot or notch, e.g. a tube crossing, is not the section)
      for (const f of [0.2, 0.35, 0.5, 0.65, 0.8]) { const o = P1.clone().addScaledVector(a, L * f); const slab = stBox3(o, X, Y, -big, big, -big, big, -5 * U, 5 * U); A.push(Math.abs(b.man.intersect(slab).volume()) / (U * U * U) / 10); }
      A.sort((x, y) => x - y); return A[2];
    }
    function partWeight70(b) {
      const r = P66(b); if (!b || !b.st || b.st.kind !== 'member' || !b.man || !b.st.p1 || !b.st.p2) return r;
      const At = tkArea70(profOf(b.st)); if (!At) return r;
      const U = stU(); let Am = 0; try { Am = ownArea70(b); } catch (e) { return r; } if (!(Am > 0)) return r;
      const rho = (R66.TK_DENS[String(b.st.mat || '').toUpperCase()] ?? 7850);
      let vol0 = Math.abs(b.man.volume()) / (U * U * U);              // the solid without bolt holes (v66 solidNoHoles, exact here)
      if (r.holesKg > 0 || (S.stConns || []).some(c => c.type === 'TKBOLT' && c.parts.includes(b.id))) {
        try { const cuts = []; for (const c of S.stConns || []) { if (c.type === 'TKBOLT' || !c.parts.includes(b.id)) continue; const g = stConnGeom(c); const cs = g && g.cuts.get(b.id); if (cs) cuts.push(...cs); }
          let m0 = stBuild(b.st); if (cuts.length) m0 = m0.subtract(stUnion(cuts)); const v0 = Math.abs(m0.volume()) / (U * U * U); if (v0 >= vol0 - 1e-6 && v0 < vol0 * 1.25 + 1e5) vol0 = v0; } catch (e) { } }
      const net = vol0 * 1e-9 * rho * At / Am;
      return Object.assign({}, r, { weight: r2(net), net: r2(net), gross: r2(Math.max(r.gross, net)), tkArea: r2(At), ownArea: r2(Am) });
    }
    function weights70() {                                          // v66 tkWeights with the v70 part weight
      const asm = new Map(), part = new Map(); let W = 0, N = 0, G = 0, bolts = 0, n = 0;
      for (const b of S.bodies) { if (!b.st) continue; if (b.st.kind === 'bolts' || b.st.kind === 'bolt') { bolts++; continue; } const w = partWeight70(b); n++; W += w.weight; N += w.net; G += w.gross;
        const ak = b.st.amark || b.st.asm || ''; let a = asm.get(ak); if (!a) asm.set(ak, a = { weight: 0, net: 0, gross: 0, parts: 0 }); a.weight += w.weight; a.net += w.net; a.gross += w.gross; a.parts++;
        const pk = b.st.pos || b.st.pmark || ''; let p = part.get(pk); if (!p) part.set(pk, p = { prof: String(b.st.tprof || b.st.prof || ''), mat: b.st.mat || '', n: 0, weight: 0, net: 0, gross: 0 }); p.n++; p.weight += w.weight; p.net += w.net; p.gross += w.gross; }
      const fx = o => Object.fromEntries([...o].map(([k, v]) => [k, Object.assign({}, v, { weight: r2(v.weight), net: r2(v.net), gross: r2(v.gross) })]));
      return { weight_t: Math.round(W) / 1000, net_t: Math.round(N) / 1000, gross_t: Math.round(G) / 1000, parts: n, boltBodiesExcluded: bolts, asm: fx(asm), part: fx(part),
        note: 'Tekla rules (v70): WEIGHT = catalog kg/m x L for standard profiles, otherwise the solid without bolt holes; round sections as Tekla polygons (rods 8, tubes 12 < D50 <= 16 sides), SHS/RHS square corners.' };
    }
    Object.assign(window.__st, { partWeight: id => partWeight70(typeof id === 'object' ? id : stBody(id)), weights: weights70, weightRules: Object.assign({}, R66, { tkArea70 }) });
  }

  // ---------- Generation of purlins (50): Profile is a text field (owner workflow 2026-11-26) ----------
  // v69's generic dialog makes a field a number box unless its default is non-numeric text; T50p's default profile is '' (= the PURLIN
  // property set), so 'Profile' was a number box and a typed name (CC200-2-20-60) was read as NaN and dropped. The default is now the
  // PURLIN set's profile itself (same result) and the field is text.
  if (CONN.T50p && !CONN.T50p.def.prof) CONN.T50p.def.prof = ST_SETS.PURLIN.prof;

  // ---------- C04 · one cleat per joint (owner 2026-11-26) ----------
  // Tekla has one cleat at each purlin joint (R12: 264 joints, 264 L140*60*6, no plate cleat). The purlin application (T50p) puts its
  // own plate cleat (Tpc) on every purlin end; adding a purlin cleat L (Tcl) on a rafter and purlin(s) now replaces the Tpc on that
  // rafter and those purlins (its plate and bolts go; the parts are rebuilt by the caller's stRegen, as for any new connection).
  {
    const stConnAdd70 = stConnAdd;
    stConnAdd = function (type, parts, q) {
      if (type === 'Tcl' && parts && parts.length >= 2) {
        const raf = parts[0], purs = new Set(parts.slice(1));
        const gone = new Set((S.stConns || []).filter(c => c.type === 'Tpc' && c.parts.length >= 2 && c.parts[0] === raf && purs.has(c.parts[1])).map(c => c.id));
        if (gone.size) { S.stConns = S.stConns.filter(c => !gone.has(c.id)); S.bodies = S.bodies.filter(b => !(b.st && gone.has(b.st.conn))); }
      }
      return stConnAdd70.apply(this, arguments);
    };
  }


  // ---------- C14 · Shear plate (146): vertical position of the bolt group (owner 2026-11-26) ----------
  // Tekla's 146 Bolts tab sets the bolt group's vertical position by a reference (Top / Middle / Below) and a dimension (video
  // MrhM4X9opLo 14:50: "Top", frame-checked; docs/steel/PARITY.md "C14"). v69 always centres the group on the secondary's depth. v70
  // (only when 'Vertical measurement' is set; old models and new ones with the default keep v69's centred group):
  //   Top    = the top bolt row this far below the secondary's top
  //   Below  = the bottom bolt row this far above the secondary's bottom
  //   Middle = the group's centre this far below the secondary's middle (0 = centred)
  // measured on the secondary's own section, not its coped zone (R12: the 68 joist tabs are 20 mm off if measured from the cope).
  // The tab follows the rows (edge distance e above and below), as in v69. Also from Tekla's 146 Bolts tab: the horizontal edge distance
  // (eh: bolts to the tab's far end and from the beam end; R12 35 with the vertical one 30) and, when a vertical measurement is set,
  // Tekla's bolt length: stack + 1.5 d + 2, rounded up to 5 mm (R12: M16, 18 mm -> L45), the grip = the plate stack, and the bolt heads
  // on the left looking along the secondary from the main part (J.Y x J.X; R12: 34 tabs on that side with heads on the tab, 34 on the
  // other side with heads on the beam web).
  ST_LBL.refV = ST_LBL.refV || 'Vertical measurement'; ST_LBL.posV = ST_LBL.posV || 'Vertical position';
  { const bt = TKD.T146.tabs.find(t => t.name === 'Bolts'); if (bt && !bt.dims.some(f => f.k === 'posV')) {
      bt.dims.push(TKF('posV', '1', { at: [8, 8], tip: 'Vertical position of the bolt group (from the reference)', dv: '' }));
      bt.dims.push(TKF('eh', '5', { at: [64, 8], tip: 'Horizontal edge distance (bolts to the tab end; empty = the vertical one)', dv: '' }));
      bt.groups[0][1].push(TKF('refV', 'Vertical measurement', { type: 'sel', dv: '', opts: [tkOpt('', 'Centred (v69)'), tkOpt('Top', 'Top', 'top'), tkOpt('Middle', 'Middle', 'mid'), tkOpt('Below', 'Below', 'bot')] })); } }
    FAM.ST = X => {   // v69 L14939 with the v70 vertical position (marked)
    const { U, q, A, cut, plate, bolts, weld } = X; const one = Bm => {
      const J = stJoint(A, Bm), Bp = J.B, t = q.t * U, e = q.e * U; const nC = Math.max(1, q.cols | 0); const n0 = q.rows ? q.rows | 0 : Math.max(2, tkRows(Bp.h)); const yc = (J.yHi + J.yLo) / 2;
      const room = J.yHi - J.yLo - 2 * (Bp.tf + (Bp.r || 10) + 5) * U; let n = n0; while (n > 1 && ((n - 1) * q.pitch + 2 * q.e) * U > room + 1e-9 && !q.full) n--;
      let y1 = yc + ((n - 1) * q.pitch / 2 + q.e) * U, y0 = yc - ((n - 1) * q.pitch / 2 + q.e) * U;
      { const pv = q.posV === '' || q.posV === undefined ? NaN : +q.posV, sp = (n - 1) * q.pitch * U;    // v70: Tekla's vertical position of the bolt group (Bolts tab)
        const hb = Bp.h / 2 * U;   // from the secondary's own top / middle / bottom (not the coped zone J.yHi / J.yLo: R12, 68 tabs)
        let top = NaN; if (q.refV === 'Top' && isFinite(pv)) top = hb - pv * U; else if (q.refV === 'Below' && isFinite(pv)) top = -hb + pv * U + sp; else if (q.refV === 'Middle') top = sp / 2 - (isFinite(pv) ? pv : 0) * U;
        if (isFinite(top)) { y1 = top + q.e * U; y0 = top - sp - q.e * U; } }
      const mainBeam = Math.abs(J.M.a.z) < 0.9; const mr = ((J.M.r || 0) + 1) * U; if (q.full || q.top) { const yT = mainBeam && isFinite(J.yTop) ? J.yTop - mr : (Bp.h / 2 - Bp.tf - 5) * U, yB = mainBeam && isFinite(J.yBot) ? J.yBot + mr : -(Bp.h / 2 - Bp.tf - 5) * U; if (q.top || q.full) y1 = Math.max(y1, yT); if (q.full) y0 = Math.min(y0, yB); }
      const xs0 = q.through ? J.xFace - (J.M.h - J.M.tw * 0) * U : J.xFace; const xe = (q.colstiff && !J.flange && !mainBeam && J.M.p.kind === 'I') ? (J.M.b / 2 - J.M.tw / 2) * U : 0; const xF2 = J.xFace + xe; const eH = q.eh === '' || q.eh === undefined || !isFinite(+q.eh) ? e : +q.eh * U;   /* v70: horizontal edge distance (Tekla 146 Bolts tab) */ const x1 = +q.tb > 0 ? xs0 + q.tb * U : xF2 + q.gap * U + 2 * eH + (nC - 1) * q.pitchH * U;   /* v70 C20: Plates tab 'Tab plate b' sets the tab's length from the main part */ const z0 = Bp.tw / 2 * U; const sides = q.side === 2 || q.two ? [1, -1] : q.side === -1 ? [-1] : [1];
      const tabs = sides.map(sg => { const F = stFrame3(J.E, J.X, J.Y); return q.shaped ? F.poly([[xs0, y0], [x1 - 15 * U, y0], [x1, y0 + 15 * U], [x1, y1 - 15 * U], [x1 - 15 * U, y1], [xs0, y1]], sg * z0, sg * (z0 + t)) : F.box(xs0, x1, y0, y1, sg * z0, sg * (z0 + t)); });
      const bl = []; const yb0 = y1 - (q.full || q.top ? Math.max(q.e, (y1 - y0) / U / 2 - (n - 1) * q.pitch / 2) : q.e) * U;
      for (let j = 0; j < nC; j++) for (let i = 0; i < n; i++) bl.push(J.P(xF2 + q.gap * U + eH + j * q.pitchH * U, yb0 - i * q.pitch * U, sides[0] * (z0 + t + (q.refV ? 0 : 0.1 * U))));
      const grip = sides.length * t + Bp.tw * U + (q.refV ? 0 : 2 * U); let bax = J.Zb.clone().multiplyScalar(-sides[0]);
      if (q.refV && new THREE.Vector3().crossVectors(J.Y, J.X).dot(bax) < 0) { for (let k = 0; k < bl.length; k++) bl[k] = bl[k].clone().addScaledVector(bax, grip); bax.negate(); }   /* v70: Tekla's heads on the Y x X side (R12: 68 / 68), grip = the plate stack */
      const BB = stBolts(bl, bax.toArray(), q.bolt, grip, U); let tb = tabs; for (const hc of BB.holes) { tb = tb.map(x => x.subtract(hc)); cut(Bm.id, hc); }
      J.cutEnd(xF2 + q.gap * U, cut, Bm.id); tb.forEach(x => plate('SHEAR TAB', x, q.t)); for (const s of [1, -1]) { const fy0 = s * (Bp.h / 2 - Bp.tf - (Bp.r || 0)) * U, fy1 = s * Bp.h / 2 * U; if ((s > 0 ? y1 > fy0 : y0 < fy0)) for (const sg of sides) cut(Bm.id, J.box(J.xFace - 1 * U, x1 + 20 * U, Math.min(fy0, fy1) - 1 * U, Math.max(fy0, fy1) + 1 * U, sg * (z0 - 0.5 * U), sg * (Bp.b / 2 + 5) * U)); } bolts(BB, bl.length, q.refV ? Math.ceil((sides.length * q.t + Bp.tw + 1.5 * q.bolt + 2) / 5) * 5 : grip / U + 2 * q.bolt);   /* v70: Tekla's bolt length (stack + 1.5 d + 2, up to 5 mm; R12: M16 grip 18 -> L45, C04 M12 grip 8 -> L30) */
      if (q.through) cut(A.id, J.box(xs0 - 1 * U, J.xFace + 1 * U, y0 - 1 * U, y1 + 1 * U, sides.length > 1 ? -z0 - t - 1 * U : z0 - 1 * U, z0 + t + 1 * U));
      weld('Shear tab to main part', q.a, 2 * (y1 - y0) / U * sides.length, [[J.xFace, y0, z0], [J.xFace, y1, z0], [J.xFace, y1, z0 + t], [J.xFace, y0, z0 + t]].map(v => J.P(v[0], v[1], v[2]).toArray()));
      if (q.tee) { const F = stFrame3(J.E, J.X, J.Y); plate('TEE FLANGE', F.box(J.xFace, J.xFace + 12 * U, y0, y1, z0 + t / 2 - 60 * U, z0 + t / 2 + 60 * U), 12); }
      if (q.colstiff || q.stiff) { if (!mainBeam && J.flange) { const N = new THREE.Vector3().crossVectors(J.M.eh, J.M.es).normalize(); for (const yy of [Bp.h / 2 - Bp.tf / 2, -(Bp.h / 2 - Bp.tf / 2)]) stStiffPair(J.M, J.P(0, yy * U, 0).sub(J.M.c1).dot(N), Math.max(10, Bp.tf), 15 * U, [1, -1], plate); }
        else if (!mainBeam && !J.flange) { const N = new THREE.Vector3().crossVectors(J.M.eh, J.M.es).normalize(); const sg = (Math.sign(J.X.dot(J.M.es)) || 1) * (q.colstiff ? 1 : -1); for (const yy of [Bp.h / 2 - Bp.tf / 2, -(Bp.h / 2 - Bp.tf / 2)]) stStiffPair(J.M, J.P(0, yy * U, 0).sub(J.M.c1).dot(N), Math.max(10, Bp.tf), 15 * U, [sg], plate); }
        else if (mainBeam && J.M.p.kind === 'I') { const F = stFrame3(J.E, J.X, J.Y); const mh = (J.M.h / 2 - J.M.tf) * U; plate('STIFFENER', F.box(J.xFace - J.M.tw * U, J.xFace - J.M.tw * U - (J.M.b / 2 - J.M.tw / 2 - 5) * U, (isFinite(J.yBot) ? J.yBot : -mh) + mr, (isFinite(J.yTop) ? J.yTop : mh) - mr, z0, z0 + t), q.t); } }
    };
    one(X.B); if (X.B2) one(X.B2);
  };
  //
  // ---------- C09 · End plate (144) Haunch tab: Tekla's haunch plates (R12 PC7001 x 24) ----------
  // Proof (docs/steel/PARITY.md "C09"): Tekla's 144 help (Haunch tab: Top plate / Bottom plate t, b, h; Haunch plate chamfers 1-4 and
  // chamfer type; Haunch plate creation Automatic / Top and bottom / Not created) and the video rf0oBDvnNjE 10:53-12:22 (t set, b and h
  // left blank, then b = 5" and 4"). R12: the 24 monitor-post joints have two HAUNCH_PLATE t 8 in the post's web plane, standing on the
  // end plate against each post flange: the bottom edge on the end plate is b = 60 long (measured along the end plate), the outer edge runs
  // along the post, the top edge is square to the post at h = 60 above the lower bottom corner, the outer top corner is chamfered 15 x 15
  // (line) and the inner corner (flange / end plate weld) is cut 15 x 15. v69's 'Haunch plate creation' made a 250 mm FLANGE PLATE
  // ('comp'); old models keep it. v70 adds the Tekla plates under new keys (hpc = '' : none, as v69, so other 144 joints are unchanged).
  ST_LBL.hpc = 'Haunch plate creation';
  Object.assign(CONN.T144.def, { hpc: '', hpt1: 8, hpb1: '', hph1: '', hpt2: 8, hpb2: '', hph2: '', hc1: 15, hc2: 15, hc3: 15, hc4: 15, hct: 'line' });
  { const i = TKD.T144.tabs.findIndex(t => t.name === 'Haunch'); if (i >= 0) TKD.T144.tabs[i] = { name: 'Haunch',
      parts: [['Top plate', 'HAUNCH_PLATE', { t: 'hpt1', b: 'hpb1', h: 'hph1' }], ['Bottom plate', 'HAUNCH_PLATE', { t: 'hpt2', b: 'hpb2', h: 'hph2' }]],
      groups: [['Haunch plate chamfers', [TKF('hc1', '1 · Width of the top haunch plate chamfer'), TKF('hc2', '2 · Height of the top haunch plate chamfer'), TKF('hc3', '3 · Height of the bottom haunch plate chamfer'), TKF('hc4', '4 · Width of the bottom haunch plate chamfer'), TKF('hct', 'Chamfer type', { type: 'sel', dv: 'line', opts: [tkOpt('none', 'No chamfer'), tkOpt('line', 'Line chamfer'), tkOpt('convex', 'Convex arc chamfer'), tkOpt('concave', 'Concave arc chamfer')] })]],
        ['Haunch plate creation', [TKF('hpc', 'Haunch plate creation', { type: 'sel', dv: '', opts: [tkOpt('', 'Not created (v69)', 'off'), tkOpt('auto', 'Automatic', 'auto'), tkOpt('both', 'Top and bottom haunch plates are created', 'on'), tkOpt('none', 'Haunch plates are not created', 'off')], note: 'Automatic: a plate on each side where the end plate reaches past the flange. Thickness 0 = no plate on that side.' })]]] }; }
  const hp70 = (X, Bm) => {   // the haunch plates of one secondary (same frame as FAM.EP)
    const { U, q, A, plate, weld } = X; const J = stJoint(A, Bm), Bp = J.B; const u = J.u.clone(); const xu = J.X.dot(u) || 1; const O = J.E.clone().addScaledVector(J.X, J.xFace);
    let FY = Bp.eh.clone().addScaledVector(u, -Bp.eh.dot(u)); if (FY.length() < 1e-6) FY = J.Y.clone().addScaledVector(u, -J.Y.dot(u)); FY.normalize();
    const onFace = p => { const s = p.clone().sub(O).dot(u) / xu; return p.clone().addScaledVector(J.X, -s); };
    const ys = []; for (const sy of [-1, 1]) for (const sz of [-1, 1]) ys.push(onFace(O.clone().addScaledVector(Bp.eh, sy * Bp.h / 2 * U).addScaledVector(Bp.es, sz * Bp.b / 2 * U)).sub(O).dot(FY));
    const fr = (q.front || q.double || q.stub || q.stiffT ? Math.max(q.tp, 10) : 0) * U; const n1 = ((q.gap || 0) + (q.shim || 0) + q.tp) * U + fr;
    const yh = Math.max(...ys) + q.ext * U, yl = Math.min(...ys) - q.extb * U; const num = (v, d) => v === '' || v === undefined || v === null || !isFinite(+v) ? d : +v;
    for (const s of [1, -1]) {
      const k = s > 0 ? 1 : 2, t = num(q['hpt' + k], 8); if (!(t > 0)) continue;
      const Pf = O.clone().addScaledVector(Bp.eh, s * Bp.h / 2 * U); const lam = (n1 - Pf.clone().sub(O).dot(u)) / (J.X.dot(u) || 1); const C = Pf.clone().addScaledVector(J.X, lam);
      const reach = s > 0 ? yh - C.clone().sub(O).dot(FY) : C.clone().sub(O).dot(FY) - yl;   // end plate past this flange, along the plate
      if (q.hpc === 'auto' && reach < 10 * U) continue;
      const e1 = Bp.eh.clone().multiplyScalar(s), e2 = J.X.clone(); const w = e1.clone().addScaledVector(u, -e1.dot(u)).normalize(); const we = w.dot(e1), wa = w.dot(e2);
      const b = num(q['hpb' + k], Math.max(reach / U, 10)) * U, h = num(q['hph' + k], b / U) * U, cw = (s > 0 ? num(q.hc1, 0) : num(q.hc4, 0)) * U, ch = (s > 0 ? num(q.hc2, 0) : num(q.hc3, 0)) * U, cl = 15 * U;
      const nP = new THREE.Vector3().crossVectors(e1, e2).normalize(), dl = t / 2 * U * Math.abs(u.dot(nP)) / (Math.abs(u.dot(e2)) || 1) + 0.01 * U;   // across a slope: down to the end plate, trimmed below
      const tz = Math.min(0, b * wa) + h, xo = b * we; const pts = [[0, cl], [cl * we, cl * wa - dl], [xo, b * wa - dl]];
      if (q.hct !== 'none' && cw > 0 && ch > 0) { if (q.hct === 'convex' || q.hct === 'concave') { for (let i = 0; i <= 8; i++) { const f = i / 8 * Math.PI / 2; pts.push(q.hct === 'concave' ? [xo - cw * Math.sin(f), tz - ch * Math.cos(f)] : [xo - cw + cw * Math.cos(f), tz - ch + ch * Math.sin(f)]); } } else pts.push([xo, tz - ch], [xo - cw, tz]); }
      else pts.push([xo, tz]);
      pts.push([0, tz]);
      const F = stFrame3(C, e1, e2), BIG = 60000 * U; plate('HAUNCH_PLATE', F.poly(pts, -t / 2 * U, t / 2 * U).subtract(stFrame3(O, u, FY).box(-BIG, n1, -BIG, BIG, -BIG, BIG)), t);
      weld('Haunch plate to end plate and flange', Math.min(5, q.a || 5), 2 * ((b - cl) + (tz - cl)) / U);
    }
  };
  // Bolts (R12, all 1,288 Tekla bolt groups: M12 grip 8 / 15.5 -> L30 / 35, M16 16 / 18-22 / 24-26.7 -> 40 / 45 / 50, M20 30 / 40 -> 60 / 70):
  // Tekla's length = grip + nut (ISO 4032 m) + washer (ISO 7089) + 5, rounded up to 5 mm, with the grip = the plate stack. 'Bolt length'
  // in the Bolts tab (Bolt assembly): v69 (grip + 2 mm + 2 d; old models and the default) / Tekla. R12 C09: M16 grip 22 -> L45, bolting
  // direction 2 (heads under the rafter flange).
  const tkBoltLen70 = (d, grip) => { const m = { 10: 8.4, 12: 10.8, 14: 12.8, 16: 14.8, 20: 18, 22: 19.4, 24: 21.5, 27: 23.8, 30: 25.6, 36: 31 }[d] || 0.9 * d, w = { 10: 2, 12: 2.5, 14: 2.5, 16: 3, 20: 3, 22: 3, 24: 4, 27: 4, 30: 4, 36: 5 }[d] || 0.16 * d; return Math.ceil((grip + m + w + 5) / 5 - 1e-6) * 5; };
  ST_LBL.blrule = 'Bolt length';
  { const bt = TKD.T144.tabs.find(t => t.name === 'Bolts'); const g = bt && bt.groups.find(x => x[0] === 'Bolt assembly'); if (g && !g[1].some(f => f.k === 'blrule')) { g[1] = g[1].slice(); g[1].push(TKF('blrule', 'Bolt length', { type: 'sel', dv: '', opts: [tkOpt('', 'v69 (grip + 2 d)'), tkOpt('tekla', 'Tekla (grip + nut + washer + 5, up to 5 mm)')] })); } }
  CONN.T144.def.blrule = '';
  const FAM_EP70 = X => {   // v69 live FAM.EP (L15931658 of the v69 file) with the v70 bolt length (marked)
    const { U, q, A, cut, plate, bolts, weld } = X; const BIG = v49Big();
    const one = (Bm, sharedGrip) => {
      const J = stJoint(A, Bm), Bp = J.B; const u = J.u.clone(); const xu = J.X.dot(u) || 1; const O = J.E.clone().addScaledVector(J.X, J.xFace);
      let FY = Bp.eh.clone().addScaledVector(u, -Bp.eh.dot(u)); if (FY.length() < 1e-6) FY = J.Y.clone().addScaledVector(u, -J.Y.dot(u)); FY.normalize(); const FZ = new THREE.Vector3().crossVectors(u, FY).normalize(); const Ff = stFrame3(O, u, FY);
      const onFace = p => { const s = p.clone().sub(O).dot(u) / xu; return p.clone().addScaledVector(J.X, -s); };   // carry a point along the beam onto the face plane
      const cs = []; for (const sy of [-1, 1]) for (const sz of [-1, 1]) { const p = onFace(O.clone().addScaledVector(Bp.eh, sy * Bp.h / 2 * U).addScaledVector(Bp.es, sz * Bp.b / 2 * U)); cs.push([p.clone().sub(O).dot(FY), p.clone().sub(O).dot(FZ), p]); }
      const yT = Math.max(...cs.map(c => c[0])), yB = Math.min(...cs.map(c => c[0])); let zC = (Math.max(...cs.map(c => c[1])) + Math.min(...cs.map(c => c[1]))) / 2;
      const tp = q.tp * U, gp = (q.gap || 0) * U, sh = (q.shim || 0) * U, fr = (q.front || q.double || q.stub || q.stiffT ? Math.max(q.tp, 10) : 0) * U; const n0 = gp + fr + sh, n1 = n0 + tp;
      let yh = yT + q.ext * U, yl = yB - q.extb * U; if (J.cope) { const yTf = (J.yTop - 10 * U) / Math.max(0.2, Math.abs(J.Y.dot(FY))), yBf = (J.yBot + 10 * U) / Math.max(0.2, Math.abs(J.Y.dot(FY))); yh = Math.min(yh, yTf); yl = Math.max(yl, yBf); }
      const nc = (q.cols | 0) === 4 ? 4 : 2; const g = q.gauge * U, g2 = (q.gauge2 || q.gauge) * U; const zs = nc === 4 ? [-g / 2 - g2, -g / 2, g / 2, g / 2 + g2] : [-g / 2, g / 2]; const zw = (nc === 4 ? g / 2 + g2 : g / 2) + q.side * U; { const ph = +q.posH || 0, gw = zw - q.side * U; if (q.refH === 'Left') zC = zC - Bp.b / 2 * U + ph * U + gw; else if (q.refH === 'Right') zC = zC + Bp.b / 2 * U - ph * U - gw; else zC += ph * U; }
      let ep = Ff.box(n0, n1, yl, yh, zC - zw, zC + zw); const H = (yh - yl) / U; let nr = q.rows ? q.rows | 0 : tkRows(H); let ps = v49Pitches(q, nr); if (String(q.pitches || '').trim()) nr = ps.length + 1;
      let top = yh - q.e * U; { const sp = v58Span(ps, nr, q), pv = q.posV === '' || q.posV === undefined ? NaN : +q.posV; if (q.refV === 'Middle') top = (yh + yl) / 2 + sp / 2 * U + (isFinite(pv) ? pv : 0) * U; else if (q.refV === 'Below') top = yl + (isFinite(pv) ? pv : q.e) * U + sp * U; } const ys = []; if (!String(q.pitches || '').trim() && !(+q.pitch)) { for (let i = 0; i < nr; i++) ys.push(top - (top - (yl + q.e * U)) * i / Math.max(1, nr - 1)); } else { let y = top; for (let i = 0; i < nr; i++) { ys.push(y); y -= (ps[i] || ps[ps.length - 1] || 70) * U; } }
      const tkL = q.blrule === 'tekla'; const bl = []; for (const y of ys) for (const z of zs) bl.push(Ff.P(n1 + (tkL ? 0 : 0.1) * U, y, zC + z)); const grip = tkL ? tp + Math.max(0, gp) + sh + fr + J.tPrim * U + (sharedGrip || 0) : n1 + J.tPrim * U + (sharedGrip || 0) + 2 * U;   /* v70: Tekla's grip = the plate stack */
      const BB = stBolts(bl, u.clone().negate().toArray(), q.bolt, grip, U); for (const hc of BB.holes) { ep = ep.subtract(hc); cut(A.id, hc); }
      cut(Bm.id, Ff.box(-BIG, n1, -BIG, BIG, -BIG, BIG));   // the secondary ends on the plate's back face, parallel to the main face
      if (J.cope) { const hb = Bp.h / 2 * U; if (hb > J.yTop - 10 * U) cut(Bm.id, J.box(-BIG, J.xFace + J.copeL, J.yTop - 10 * U, BIG / 10, -BIG / 10, BIG / 10)); if (-hb < J.yBot + 10 * U) cut(Bm.id, J.box(-BIG, J.xFace + J.copeL, -BIG / 10, J.yBot + 10 * U, -BIG / 10, BIG / 10)); }
      plate('END PLATE', ep, q.tp); bolts(BB, bl.length, tkL ? tkBoltLen70(q.bolt, grip / U) : grip / U + 2 * q.bolt);   /* v70: Tekla's bolt length */
      if (sh) { let s = Ff.box(n0 - sh, n0, yl, yh, zC - zw, zC + zw); for (const hc of BB.holes) s = s.subtract(hc); plate('SHIM', s, q.shim); }
      if (fr) { const mr2 = ((J.M.r || 0) + 1) * U; let f = Ff.box(gp, gp + fr, Math.max(yl - 20 * U, isFinite(J.yBot) ? J.yBot + mr2 : -BIG), Math.min(yh + 20 * U, isFinite(J.yTop) ? J.yTop - mr2 : BIG), zC - zw - 10 * U, zC + zw + 10 * U); for (const hc of BB.holes) f = f.subtract(hc); plate(q.stub ? 'STUB PLATE' : 'FRONT PLATE', f, fr / U); weld('Front plate to main part', q.a, 2 * (yh - yl + 2 * zw) / U); }
      const lp = [cs[0], cs[1], cs[3], cs[2]].map(c => c[2].clone().addScaledVector(u, n1).toArray()); weld('Beam to end plate', q.a, 2 * (yT - yB) / U + 4 * Bp.b, lp);
      const x1J = J.xFace + n1 / xu;
      if (q.comp) for (const s of [1, -1]) { const y0 = s * Bp.h / 2 * U; plate('FLANGE PLATE', J.box(x1J, x1J + 250 * U, y0, y0 + s * Math.max(8, Bp.tf) * U, -Bp.b / 2 * U, Bp.b / 2 * U), Math.max(8, Bp.tf)); }
      if (q.seat && !(Math.abs(J.M.a.z) < 0.9 && !J.flange)) { const F = stFrame3(J.E, J.X, J.Y); plate('SEAT ANGLE', stLAngle(F, x1J, -Bp.h / 2 * U, 90 * U, 90 * U, 10 * U, 1, -1, -Bp.b / 2 * U, Bp.b / 2 * U), 10, 'L90*90*10'); }
      if ((q.stiff || q.stiffT) && J.flange && Math.abs(J.M.a.z) > 0.9) { const N = new THREE.Vector3().crossVectors(J.M.eh, J.M.es).normalize(); for (const c of [cs.reduce((a, b) => a[0] > b[0] ? a : b), cs.reduce((a, b) => a[0] < b[0] ? a : b)]) stStiffPair(J.M, c[2].clone().addScaledVector(FY, -Math.sign(c[0]) * Bp.tf / 2 * U).sub(J.M.c1).dot(N), Math.max(10, Bp.tf), 15 * U, [1, -1], plate); }
      return { n1 };
    };
    one(X.B, 0); if (X.B2) one(X.B2, q.shared ? (q.tp + (q.gap || 0)) * U : 0);
  };
  FAM.EP = X => { const r = FAM_EP70(X); const hpc = X.q.hpc; if ((hpc === 'auto' || hpc === 'both') && r !== null) for (const Bm of [X.B, X.B2].filter(Boolean)) hp70(X, Bm); return r; };
  //
  // ---------- C02h · Eave haunch (SolidSketch's own component; R12 eave x 48; docs/steel/PARITY.md "C02h") ----------
  // In the owner's Tekla model the eave haunch is exploded (plain plates, no component), so no dialog can be proven by Tekla itself.
  // Tekla's Eaves haunch (102) was tried first (help conn_102_help_eaves_haunch): its *Haunch length* (horizontal, from the column's
  // centre line to where the haunch flange meets the rafter's bottom flange) fits R12 (2,000 at both column sizes), but none of its depth
  // settings is one value over R12 (Picture 5, the haunch's whole height: 496.72 / 508.06; Parameters 2: 154.81 / 166.15), and 102 also
  // makes its own end plate and stiffeners, which R12's eaves do not have. So (owner's step 2) our own component with the fewest fields
  // that reproduce all 48: Haunch length (102's definition), Depth at the column (plumb, at the end plate: 82.926 / 82.900 -> 82.91),
  // web t, flange t and width. It sits with End plate (144) at each eave (C02) and starts at the back face of the End plate (144) joining
  // the same column and rafter (column face + gap + shim / front + plate), or at the column face when there is none. Parts: the web, a
  // triangle under the rafter's underside; the flange under its lower edge, ends cut plumb; both in the rafter's assembly. Welds as R12's
  // (a 6): web to rafter flange and to end plate both sides, flange to web all round, flange to end plate (2 x top outstand +
  // underside), flange end to rafter flange both sides.
  CONN.Teh = { label: 'Eave haunch', tekla: 'Eave haunch (SolidSketch, C02h)', code: 'C02h', parts: ['main part (column)', 'secondary part (rafter)'], fam: 'EH', grp: 'Haunch and moment', gen: false, ro: [],
    def: { hlen: 2000, hdep: 82.91, tw: 8, tf: 10, bf: 150, a: 6, nweb: 'Web', nfl: 'Bottom flange', cls: 1 } };
  ST_LBL.hlen = 'Haunch length (from the column axis)'; ST_LBL.hdep = 'Depth at the column';
  { const g = ST_CATALOG.find(x => x.g === 'Haunch and moment'); if (g && !g.items.includes('Teh')) g.items.splice(Math.max(0, g.items.indexOf('T102') + 1), 0, 'Teh'); }
  TKD.Teh = { help: 'conn_102_help_eaves_haunch', tabs: [
    { name: 'Picture', pic: 'apex', dims: [TKF('hdep', 'D', { at: [77, 64], tip: 'Depth at the column: the haunch web, plumb, at the end plate, below the rafter\'s bottom flange' }), TKF('hlen', 'L', { at: [72, 92], tip: 'Haunch length: horizontal, from the centre line of the column to where the haunch flange meets the rafter\'s bottom flange (as Tekla 102)' })] },
    { name: 'Parts', parts: [['Haunch web', 'Web', { t: 'tw' }], ['Haunch flange', 'Bottom flange', { t: 'tf', b: 'bf' }]] },
    tkGeneralTab(), tkWeldsTab()] };
  FAM.EH = X => {
    const { U, q, A, B, plate, weld } = X; if (!B) return null;
    const MC = stMF(A.st), M = stMF(B.st); const Z = new THREE.Vector3(0, 0, 1);
    let hd = M.a.clone().addScaledVector(Z, -M.a.dot(Z)); if (hd.length() < 1e-6) return null; hd.normalize();
    const col = MC.c1.clone(); const far = M.c1.clone().sub(col).dot(hd) > M.c2.clone().sub(col).dot(hd) ? M.c1 : M.c2; if (far.clone().sub(col).dot(hd) < 0) hd.negate();
    // the rafter's underside on its web plane: the section's lowest point along the plumb-ish direction square to the axis
    const dz = Z.clone().addScaledVector(M.a, -M.a.dot(Z)).normalize(); const sec = stSection(M.p || stProf(B.st.prof)); let lo = Infinity;
    for (const p of (sec ? sec.outer : [[0, -M.h / 2]])) lo = Math.min(lo, M.es.dot(dz) * p[0] + M.eh.dot(dz) * p[1]);
    const U0 = M.c1.clone().addScaledVector(dz, lo * U); const ad = M.a.dot(hd) || 1e-9;
    const under = x => U0.clone().addScaledVector(M.a, (x - U0.clone().sub(col).dot(hd)) / ad);   // underside point at horizontal distance x from the column axis
    // start: the back face of the 144 end plate at this joint, else the column face
    const half = Math.abs(MC.eh.dot(hd)) * MC.h / 2 + Math.abs(MC.es.dot(hd)) * MC.b / 2; let xs = half * U;
    const ep = (S.stConns || []).find(k => k !== X.c && k.type === 'T144' && k.parts && k.parts[0] === A.id && k.parts[1] === B.id);
    if (ep) { const e = Object.assign({}, CONN.T144.def, ep.q); xs += ((+e.gap || 0) + (+e.shim || 0) + (e.front || e.double || e.stub || e.stiffT ? Math.max(+e.tp, 10) : 0) + (+e.tp || 0)) * U; }
    const L = q.hlen * U, D = q.hdep * U; if (!(L > xs + 1 * U) || !(D > 0)) return null;
    const N0 = under(xs), Fp = under(L), N1 = N0.clone().addScaledVector(Z, -D);
    const O = col.clone().addScaledVector(Z, -col.dot(Z)); const Y = new THREE.Vector3().crossVectors(hd, Z).normalize();
    const zc = M.c1.clone().sub(O).dot(Y);   // the web plane
    const F2 = stFrame3(O.clone().addScaledVector(Y, zc), hd, Z); const p2 = P => [P.clone().sub(O).dot(hd), P.dot(Z)];
    const a0 = p2(N0), a1 = p2(Fp), a2 = p2(N1); const tw = q.tw * U, tf = q.tf * U, bf = q.bf * U;
    plate(q.nweb || 'Web', F2.poly([a0, a1, a2], -tw / 2, tw / 2), q.tw, 'PL' + q.tw);
    const m = (a1[1] - a2[1]) / (a1[0] - a2[0]), tv = tf * Math.sqrt(1 + m * m);
    plate(q.nfl || 'Bottom flange', F2.poly([a2, a1, [a1[0], a1[1] - tv], [a2[0], a2[1] - tv]], -bf / 2, bf / 2), q.tf, 'PL' + q.tf);
    const lw = Math.hypot(a1[0] - a0[0], a1[1] - a0[1]) / U, lf = Math.hypot(a1[0] - a2[0], a1[1] - a2[1]) / U;
    weld('Haunch web to rafter flange', q.a, 2 * lw); weld('Haunch web to end plate', q.a, 2 * D / U); weld('Haunch flange to web (around)', q.a, 2 * lf + 2 * q.tw);
    weld('Haunch flange to end plate', q.a, q.bf + (q.bf - q.tw)); weld('Haunch flange to rafter flange', q.a, 2 * q.bf);
  };
  { const own70 = stOwner; stOwner = function (c, name, ix) { const CN = CONN[c.type]; if (CN && CN.fam === 'EH') return c.parts[1]; return own70(c, name, ix); }; }
  //
  // ---------- C13 · End plate (144) Stiffeners tab: Tekla's column stiffeners (docs/steel/PARITY.md "C13") ----------
  // Tekla's 144 help (2025 / 2024), Stiffeners tab: Top NS / Top FS / Bottom NS / Bottom FS (t, b, h) in the main part; Stiffener
  // orientation (parallel to the secondary part / perpendicular to the main part); Stiffener creation (Automatic / created / not created);
  // Stiffener shape (line chamfered / square / gap for the main part web rounding); Stiffener positions 1 (gap to the main part's web) and
  // 2-5 (gaps between the top NS / bottom NS / bottom FS / top FS stiffener and the secondary's flange edge); chamfer 1 (vertical) / 2
  // (horizontal), default the profile's radius, and chamfer type. R12: at the 6 mezzanine 144 joints the column stiffeners are not 144's
  // (they are Column with stiffeners (186)'s, at all 15 columns), so C13's 144 has them 'not created'; this tab is proven on other sizes
  // (tools/golden/cases/t144.js t144_c13_*). Here: a stiffener's outer face is flush with the secondary's flange outer face (gap 0), it spans
  // between the main part's flanges (length h, blank = the clear depth, centred), from the web face + gap 1 (+ the root radius for the
  // 'gap' shape) out to the flange edge (width b, blank = to the edge), thickness t (blank = the secondary's flange thickness);
  // near side = the main part's +es side. v69's own 'stiff' (thickness list) stays for old models; the default 'scr' = '' keeps every
  // existing 144 unchanged.
  ST_LBL.scr = 'Stiffener creation';
  Object.assign(CONN.T144.def, { scr: '', sor: 'parallel', ssh: 'line', sg1: 0, sg2: 0, sg3: 0, sg4: 0, sg5: 0, sc1: '', sc2: '', sct: 'line', stt1: '', stb1: '', sth1: '', stt2: '', stb2: '', sth2: '', stt3: '', stb3: '', sth3: '', stt4: '', stb4: '', sth4: '' });
  { const i = TKD.T144.tabs.findIndex(t => t.name === 'Stiffeners'); if (i >= 0) TKD.T144.tabs[i] = { name: 'Stiffeners',
      parts: [['Top NS', 'STIFFENER', { t: 'stt1', b: 'stb1', h: 'sth1' }], ['Top FS', 'STIFFENER', { t: 'stt4', b: 'stb4', h: 'sth4' }], ['Bottom NS', 'STIFFENER', { t: 'stt2', b: 'stb2', h: 'sth2' }], ['Bottom FS', 'STIFFENER', { t: 'stt3', b: 'stb3', h: 'sth3' }]],
      groups: [['Stiffener orientation', [TKF('sor', 'Stiffener orientation', { type: 'sel', dv: 'parallel', opts: [tkOpt('parallel', 'Stiffeners are parallel to the secondary part'), tkOpt('perp', 'Stiffeners are perpendicular to the main part')] })]],
        ['Stiffener creation', [TKF('scr', 'Stiffener creation', { type: 'sel', dv: '', opts: [tkOpt('', 'Not created (v69)', 'off'), tkOpt('auto', 'Automatic: stiffeners are created when necessary', 'auto'), tkOpt('both', 'Stiffeners are created', 'on'), tkOpt('none', 'No stiffeners are created', 'off')] })]],
        ['Stiffener shape', [TKF('ssh', 'Stiffener shape', { type: 'sel', dv: 'line', opts: [tkOpt('line', 'Line chamfered stiffener plates'), tkOpt('square', 'Square stiffener plates'), tkOpt('gap', 'Stiffener plates with a gap for the main part web rounding')] })]],
        ['Stiffener positions', [TKF('sg1', '1 · Gap between the stiffener and the beam web edge'), TKF('sg2', '2 · Gap between the top near side stiffener and the beam flange edge'), TKF('sg3', '3 · Gap between the bottom near side stiffener and the beam flange edge'), TKF('sg4', '4 · Gap between the bottom far side stiffener and the beam flange edge'), TKF('sg5', '5 · Gap between the top far side stiffener and the beam flange edge')]],
        ['Chamfer dimensions', [TKF('sc1', '1 · Vertical dimension of the chamfer', { ph: 'radius' }), TKF('sc2', '2 · Horizontal dimension of the chamfer', { ph: 'radius' }), TKF('sct', 'Chamfer type', { type: 'sel', dv: 'line', opts: [tkOpt('none', 'No chamfer'), tkOpt('line', 'Line chamfer'), tkOpt('convex', 'Convex arc chamfer'), tkOpt('concave', 'Concave arc chamfer')] })]]] }; }
  const stiff70 = (X, Bm) => {
    const { U, q, A, plate, weld } = X; const J = stJoint(A, Bm), Bp = J.B, M = J.M; if (!J.flange) return;
    if (q.scr === 'auto' && !(Math.abs(M.a.z) > 0.9)) return;   // Automatic: a column with the secondary on its flange
    const num = (v, d) => v === '' || v === undefined || v === null || !isFinite(+v) ? d : +v;
    const O = J.E.clone().addScaledVector(J.X, J.xFace); const hb = Bp.h / 2 * U;
    const levels = [['top', O.clone().addScaledVector(Bp.eh, hb)], ['bot', O.clone().addScaledVector(Bp.eh, -hb)]];
    let dE = M.eh.clone(); if (q.sor !== 'perp') { const a = J.X.clone().addScaledVector(M.es, -J.X.dot(M.es)); if (a.length() > 1e-6) { a.normalize(); dE = a.dot(M.eh) < 0 ? a.negate() : a; } }
    let N = new THREE.Vector3().crossVectors(dE, M.es); if (N.dot(M.a) < 0) { dE.negate(); N.negate(); }
    const uin = (M.h / 2 - M.tf) * U / Math.max(0.2, Math.abs(dE.dot(M.eh))); const r = (M.r || 0) * U;
    const w0 = M.tw / 2 * U + num(q.sg1, 0) * U + (q.ssh === 'gap' ? r : 0); const ch1 = q.ssh === 'square' || q.ssh === 'gap' || q.sct === 'none' ? 0 : num(q.sc1, M.r || 0) * U, ch2 = q.ssh === 'square' || q.ssh === 'gap' || q.sct === 'none' ? 0 : num(q.sc2, M.r || 0) * U;
    for (const [lv, P] of levels) for (const sg of [1, -1]) {   // NS = +es side
      const k = lv === 'top' ? (sg > 0 ? 1 : 4) : (sg > 0 ? 2 : 3); const gap = num(q['sg' + (k === 1 ? 2 : k === 2 ? 3 : k === 3 ? 4 : 5)], 0) * U;
      const t = num(q['stt' + k], Math.max(8, Bp.tf)) * U; const b = num(q['stb' + k], (M.b / 2 * U - w0) / U) * U; const L = num(q['sth' + k], 2 * uin / U) * U / 2; const w1 = w0 + b;
      const s = P.clone().sub(M.c1).dot(N) / (M.a.dot(N) || 1); const Oz = M.c1.clone().addScaledVector(M.a, s); const z0 = lv === 'top' ? -gap - t : gap, z1 = z0 + t;
      const pts = []; const corner = (x0, xs) => {   // the chamfer at the web / flange corner (x0 = -L or +L side, xs = +1 toward the centre)
        if (!(ch1 > 0 && ch2 > 0) || Math.abs(Math.abs(x0) - uin) > 1e-6 * U) return [[x0, w0]];
        if (q.sct === 'convex' || q.sct === 'concave') { const o = []; for (let i = 0; i <= 8; i++) { const f = i / 8 * Math.PI / 2; o.push(q.sct === 'concave' ? [x0 + xs * ch1 * Math.sin(f), w0 + ch2 * Math.cos(f)] : [x0 + xs * (ch1 - ch1 * Math.cos(f)), w0 + ch2 - ch2 * Math.sin(f)]); } return o; }
        return [[x0, w0 + ch2], [x0 + xs * ch1, w0]]; };
      pts.push(...corner(-L, 1)); const right = corner(L, -1).reverse(); pts.push(...right); pts.push([L, w1], [-L, w1]);
      const F = stFrame3(Oz, dE, M.es), MF = stFrame3(M.c1, M.eh, M.es), BIG = 60000 * U, hi = (M.h / 2 - M.tf) * U;   // trimmed at the flanges' inner faces (a tilted stiffener's corners)
      const sm = F.poly(pts.map(p => [p[0], sg * p[1]]), z0, z1).subtract(MF.box(hi, BIG, -BIG, BIG, -BIG, BIG)).subtract(MF.box(-BIG, -hi, -BIG, BIG, -BIG, BIG));
      plate('STIFFENER', sm, t / U, 'PL' + Math.round(t / U * 10) / 10);
      weld('Stiffener to main part', Math.min(5, q.a || 5), 2 * (2 * L + b) / U);
    }
  };
  { const ep70 = FAM.EP; FAM.EP = X => { const r = ep70(X); if ((X.q.scr === 'auto' || X.q.scr === 'both') && r !== null) for (const Bm of [X.B, X.B2].filter(Boolean)) stiff70(X, Bm); return r; }; }
  //
  // ---------- C20 · Column with stiffeners (186): Tekla's shear tab + column stiffeners (docs/steel/PARITY.md "C20") ----------
  // Tekla's 186 help (conn_186_help 2025): a square shear tab welded to the main part web and stiffeners, bolted to the secondary web;
  // Stiffeners tab Top / Bottom NS / FS (t, b, h), orientation, creation, shape, Stiffener gap 1 (distance from the flange edge to the
  // stiffener edge) and 2 (gap between the flanges and the stiffener), positions 1-5, chamfer 1 / 2 (default 10) and chamfer type.
  // R12 (14 C20 joints + 4 EGC2002 joints): stiffeners t 10, b 70 / 90, 2 mm from each flange, flush with the secondary's flange outer
  // faces, near side = the secondary's side, web-side corners chamfered 0.7 x the main web (5.6 / 4.2), and the three welded edges (web,
  // both flanges) prepared for a K weld (45 deg double bevel, t / 3 deep from each face, root t / 3), as Tekla's IFC solid and NC show;
  // tab from the main web face, Tab plate b 150 / 100, the secondary 20 from the main part (Picture 1). v69's T186 (colstiff: v69's
  // stiffeners and the tab moved out past them) stays for old models: v70 applies when 'scr' is set.
  ST_LBL.sgap1 = 'Stiffener gap 1'; ST_LBL.sgap2 = 'Stiffener gap 2'; ST_LBL.sprep = 'Stiffener weld preparation';
  const colStiff70 = (X, Bm, o) => {   // Tekla's Top / Bottom NS / FS between the main part's flanges at the secondary's flange levels
    const { U, q, A, plate, weld } = X; const J = stJoint(A, Bm), Bp = J.B, M = J.M; const num = (v, d) => v === '' || v === undefined || v === null || !isFinite(+v) ? d : +v;
    const O = J.E.clone().addScaledVector(J.X, J.xFace); const hb = Bp.h / 2 * U; const levels = [['top', O.clone().addScaledVector(Bp.eh, hb)], ['bot', O.clone().addScaledVector(Bp.eh, -hb)]];
    let dE = M.eh.clone(); if (q.sor !== 'perp') { const a = J.X.clone().addScaledVector(M.es, -J.X.dot(M.es)); if (a.length() > 1e-6) { a.normalize(); dE = a.dot(M.eh) < 0 ? a.negate() : a; } }
    let N = new THREE.Vector3().crossVectors(dE, M.es); if (N.dot(M.a) < 0) { dE.negate(); N.negate(); }
    const g2 = num(q.sgap2, 0) * U, uin = ((M.h / 2 - M.tf) * U - g2) / Math.max(0.2, Math.abs(dE.dot(M.eh))); const r = (M.r || 0) * U;
    const w0 = M.tw / 2 * U + num(q.sg1, 0) * U + (q.ssh === 'gap' ? r : 0); const out = (M.b / 2 - M.tw / 2) * U;
    const chD = Math.max(o.chDef, M.r || 0);   // Tekla's default 10; on a rolled main part never less than the root radius (rule B: a 10 chamfer cuts into an HEA300's r 27)
    const ch1 = q.ssh === 'square' || q.ssh === 'gap' || q.sct === 'none' ? 0 : num(q.sc1, chD) * U, ch2 = q.ssh === 'square' || q.ssh === 'gap' || q.sct === 'none' ? 0 : num(q.sc2, chD) * U;
    const ns = o.nsSign(J);   // the near side
    const edges = {};   // per level: how far along the secondary (from J.E) the near side stiffener's outer edge reaches
    for (const [lv, P] of levels) for (const sg of [ns, -ns]) {
      const near = sg === ns, k = lv === 'top' ? (near ? 1 : 4) : (near ? 2 : 3); const gap = num(q['sg' + (k === 1 ? 2 : k === 2 ? 3 : k === 3 ? 4 : 5)], 0) * U;
      const t = num(q['stt' + k], Math.max(8, Bp.tf)) * U; const b = num(q['stb' + k], (out - num(q.sgap1, 0) * U - (w0 - M.tw / 2 * U)) / U) * U; const L = num(q['sth' + k], 2 * uin / U) * U / 2; const w1 = w0 + b;
      const s = P.clone().sub(M.c1).dot(N) / (M.a.dot(N) || 1); const Oz = M.c1.clone().addScaledVector(M.a, s); const z0 = lv === 'top' ? -gap - t : gap, z1 = z0 + t;
      if (near) edges[lv] = Oz.clone().sub(J.E).dot(J.X) + sg * w1 * M.es.dot(J.X) + L * Math.abs(dE.dot(J.X));
      if (o.dry) continue;
      const corner = (x0, xs) => { if (!(ch1 > 0 && ch2 > 0)) return [[x0, w0]];
        if (q.sct === 'convex' || q.sct === 'concave') { const o2 = []; for (let i = 0; i <= 8; i++) { const f = i / 8 * Math.PI / 2; o2.push(q.sct === 'concave' ? [x0 + xs * ch1 * Math.sin(f), w0 + ch2 * Math.cos(f)] : [x0 + xs * (ch1 - ch1 * Math.cos(f)), w0 + ch2 - ch2 * Math.sin(f)]); } return o2; }
        return [[x0, w0 + ch2], [x0 + xs * ch1, w0]]; };
      const pts = [...corner(-L, 1), ...corner(L, -1).reverse(), [L, w1], [-L, w1]];
      const F = stFrame3(Oz, dE, M.es), MF = stFrame3(M.c1, M.eh, M.es), BIG = 60000 * U, hi = (M.h / 2 - M.tf) * U;
      let sm = F.poly(pts.map(p => [p[0], sg * p[1]]), z0, z1).subtract(MF.box(hi, BIG, -BIG, BIG, -BIG, BIG)).subtract(MF.box(-BIG, -hi, -BIG, BIG, -BIG, BIG));
      if (q.sprep === 'K') {   // the welded edges (the web edge and both flange edges): 45 deg double bevel, t / 3 from each face
        const d = t / 3, e = 1 * U, zt = z1, zb = z0, wedge = (Fe, a0, a1) => [Fe.poly([[-e, zt + e], [-e, zt - d - e], [d + e, zt + e]], a0, a1), Fe.poly([[-e, zb - e], [-e, zb + d + e], [d + e, zb - e]], a0, a1)];   // 45 deg faces through (0, zt - d) / (0, zb + d)
        const cuts = [];
        { const Fe = stFrame3(Oz.clone().addScaledVector(M.es, sg * w0), M.es.clone().multiplyScalar(sg), N); cuts.push(...wedge(Fe, -L - e, L + e).map(m => m)); }   // web edge: n along +sg·es, extruded along es x N
        for (const sx of [1, -1]) { const Fe = stFrame3(Oz.clone().addScaledVector(dE, sx * L), dE.clone().multiplyScalar(-sx), N); cuts.push(...wedge(Fe, -BIG, BIG)); }
        for (const c of cuts) sm = sm.subtract(c);
      }
      plate(o.name || 'STIFFENER', sm, t / U, 'PL' + Math.round(t / U * 10) / 10);
      weld('Stiffener to main part', Math.min(5, q.a || 5), 2 * (2 * L + b) / U);
    }
    return { J, edges };
  };
  // Picture tab 2 (Tekla 186 help: 'Size of the strip made to the secondary part flange', default 20): R12 cuts both flanges of every
  // 186 beam back to 20 past the near side stiffener's outer edge (C20 70 + 20 = 90, EGC2002 90 + 20 = 110 from the web face), also
  // where the second beam at the column shares the first one's stiffeners ('No stiffeners are created'), so the strip is made from the
  // stiffener the settings would make. Square to the beam, the full flange width (and the root radius, as v69's strip).
  const strip70 = (X, Bm) => {
    const { U, q, cut } = X; const r0 = colStiff70(X, Bm, { chDef: 10, nsSign: J => (J.X.dot(J.M.es) >= 0 ? 1 : -1), dry: true }); const J = r0.J, Bp = J.B;
    if (Math.abs(J.M.a.z) < 0.9 || J.flange || J.M.p.kind !== 'I') return;
    const sp = q.strip === '' || q.strip === undefined || !isFinite(+q.strip) ? 20 : +q.strip;
    for (const [lv, s] of [['top', 1], ['bot', -1]]) { const xe = r0.edges[lv]; if (!isFinite(xe)) continue;
      const fy0 = s * (Bp.h / 2 - Bp.tf - (Bp.r || 0)) * U, fy1 = s * (Bp.h / 2 + 1) * U;
      cut(Bm.id, J.box(J.xFace - 1 * U, xe + sp * U, Math.min(fy0, fy1), Math.max(fy0, fy1), -(Bp.b / 2 + 5) * U, (Bp.b / 2 + 5) * U)); }
  };
  { const st70 = FAM.ST; FAM.ST = X => {
      if (!(X.c && X.c.type === 'T186' && X.q.scr)) return st70(X);
      const n0 = X.out.parts.length; const r = st70(Object.assign({}, X, { q: Object.assign({}, X.q, { colstiff: 0, stiff: 0 }) }));
      if (r !== null) for (const Bm of [X.B, X.B2].filter(Boolean)) {   // a skewed secondary: the tab ends on the main part's web face (Tekla welds it there), not square to the beam
        const J = stJoint(X.A, Bm), M = J.M; if (Math.abs(J.X.dot(M.es)) > 0.9999 || J.flange) continue; const U = X.U, BIG = 60000 * U;
        const web = stFrame3(M.c1, M.es, M.a).box(-M.tw / 2 * U, M.tw / 2 * U, -BIG, BIG, -BIG, BIG);
        for (let i = n0; i < X.out.parts.length; i++) if (X.out.parts[i].name === 'SHEAR TAB') X.out.parts[i].man = X.out.parts[i].man.subtract(web); }
      if (r !== null) for (const Bm of [X.B, X.B2].filter(Boolean)) { if (X.q.scr === 'both' || X.q.scr === 'auto') colStiff70(X, Bm, { chDef: 10, nsSign: J => (J.X.dot(J.M.es) >= 0 ? 1 : -1) }); strip70(X, Bm); }
      return r; }; }
  Object.assign(CONN.T186.def, { scr: '', sor: 'parallel', ssh: 'line', sgap1: '', sgap2: '', sg1: 0, sg2: 0, sg3: 0, sg4: 0, sg5: 0, sc1: '', sc2: '', sct: 'line', sprep: '', tb: '', strip: 20, stt1: '', stb1: '', sth1: '', stt2: '', stb2: '', sth2: '', stt3: '', stb3: '', sth3: '', stt4: '', stb4: '', sth4: '' });
  TKD.T186 = JSON.parse(JSON.stringify({ help: 'conn_186_help' })); TKD.T186.tabs = TKD.T146.tabs.map(t => Object.assign({}, t));
  { const tabs = TKD.T186.tabs; tabs[0] = Object.assign({}, tabs[0], { dims: [...tabs[0].dims, TKF('strip', '2', { at: [60, 70], tip: 'Size of the strip made to the secondary part flange (from the stiffener edge)' })] }); const pi = tabs.findIndex(t => t.name === 'Plates'); if (pi >= 0) tabs[pi] = Object.assign({}, tabs[pi], { parts: [['Tab plate', 'SHEAR TAB', { t: 't', b: 'tb' }]] });
    const si = tabs.findIndex(t => t.name === 'Stiffeners'); const stiffTab = { name: 'Stiffeners',
      parts: [['Top NS', 'STIFFENER', { t: 'stt1', b: 'stb1', h: 'sth1' }], ['Top FS', 'STIFFENER', { t: 'stt4', b: 'stb4', h: 'sth4' }], ['Bottom NS', 'STIFFENER', { t: 'stt2', b: 'stb2', h: 'sth2' }], ['Bottom FS', 'STIFFENER', { t: 'stt3', b: 'stb3', h: 'sth3' }]],
      groups: [['Stiffener orientation', [TKF('sor', 'Stiffener orientation', { type: 'sel', dv: 'parallel', opts: [tkOpt('parallel', 'Stiffeners are parallel to the secondary part'), tkOpt('perp', 'Stiffeners are perpendicular to the main part')] })]],
        ['Stiffener creation', [TKF('scr', 'Stiffener creation', { type: 'sel', dv: '', opts: [tkOpt('', 'v69 (before v70)', 'off'), tkOpt('auto', 'Automatic: stiffeners are created when necessary', 'auto'), tkOpt('both', 'Stiffeners are created', 'on'), tkOpt('none', 'No stiffeners are created', 'off')] })]],
        ['Stiffener shape', [TKF('ssh', 'Stiffener shape', { type: 'sel', dv: 'line', opts: [tkOpt('line', 'Line chamfered stiffener plates'), tkOpt('square', 'Square stiffener plates'), tkOpt('gap', 'Stiffener plates with a gap for the main part web rounding')] })]],
        ['Stiffener gap', [TKF('sgap1', '1 · Distance from the edge of the flange to the edge of the stiffener'), TKF('sgap2', '2 · Size of the gap between the flanges and the stiffener')]],
        ['Stiffener positions', [TKF('sg1', '1 · Gap between the stiffener and the beam web edge'), TKF('sg2', '2 · Gap between the top near side stiffener and the beam flange edge'), TKF('sg3', '3 · Gap between the bottom near side stiffener and the beam flange edge'), TKF('sg4', '4 · Gap between the bottom far side stiffener and the beam flange edge'), TKF('sg5', '5 · Gap between the top far side stiffener and the beam flange edge')]],
        ['Chamfer dimensions', [TKF('sc1', '1 · Vertical dimension of the chamfer', { ph: '10' }), TKF('sc2', '2 · Horizontal dimension of the chamfer', { ph: '10' }), TKF('sct', 'Chamfer type', { type: 'sel', dv: 'line', opts: [tkOpt('none', 'No chamfer'), tkOpt('line', 'Line chamfer'), tkOpt('convex', 'Convex arc chamfer'), tkOpt('concave', 'Concave arc chamfer')] })]],
        ['Welds (stiffeners)', [TKF('sprep', 'Stiffener weld preparation', { type: 'sel', dv: '', opts: [tkOpt('', 'None'), tkOpt('K', 'K: 45 deg double bevel, t / 3 from each face')], note: 'Tekla sets this with the stiffener welds (Welds tab); R12 has K' })]]] };
    if (si >= 0) tabs[si] = stiffTab; else tabs.splice(3, 0, stiffTab);
    const hi = tabs.findIndex(t => t.name === 'Beam cut'); if (hi >= 0) tabs.splice(hi, 1, tkOff('Doubler plate', ['Doubler plates'])); }
