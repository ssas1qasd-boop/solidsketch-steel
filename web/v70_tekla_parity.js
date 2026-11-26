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
      try { const r = stRecord(st); made.push(r); conns.push(['Tpc', [A.id, r.id]], ['Tpc', [B.id, r.id]]); } catch (e) { toast(e.message || String(e)); }
    }
    if (!made.length) return; step(() => { S.bodies = [...S.bodies, ...made]; ensureFolder28('stBrace'); const ids = []; for (const [ck, ps] of conns) { if (!CONN[ck]) continue; stConnAdd(ck, ps, stCQ(ck)); ids.push(...ps); } if (ids.length) stRegen([...new Set(ids)]); }, CN.label); commit(); stWeldsDraw(); toast(`${CN.label}: ${made.length} parts${conns.length ? ` · ${conns.length} connections` : ''}`);
  };
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
      const xs0 = q.through ? J.xFace - (J.M.h - J.M.tw * 0) * U : J.xFace; const xe = (q.colstiff && !J.flange && !mainBeam && J.M.p.kind === 'I') ? (J.M.b / 2 - J.M.tw / 2) * U : 0; const xF2 = J.xFace + xe; const eH = q.eh === '' || q.eh === undefined || !isFinite(+q.eh) ? e : +q.eh * U;   /* v70: horizontal edge distance (Tekla 146 Bolts tab) */ const x1 = xF2 + q.gap * U + 2 * eH + (nC - 1) * q.pitchH * U; const z0 = Bp.tw / 2 * U; const sides = q.side === 2 || q.two ? [1, -1] : q.side === -1 ? [-1] : [1];
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
