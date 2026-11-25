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
