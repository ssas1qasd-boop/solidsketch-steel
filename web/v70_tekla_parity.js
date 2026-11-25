  // ===================== v70 · Tekla parity =====================
  // Inserted into the web build just before the "v28 · BIM layers" chapter (inside the steel scope: CONN, FAM, TKD, TKF, tkOpt,
  // purlinWeb, stMF, stFaceAt, stFrame3, stBolts are in reach). tools/web/build_v70.py writes reference/solidsketch_v70.html;
  // the golden harness (tools/golden/lib/v69.js) adds the same text in memory. One parity fix per section; each section names
  // its proof in docs/steel/PARITY.md.
  //
  // ---------- C04 · purlin cleat (Tcl): Tekla 'Cleat location' ----------
  // Tekla, Cold rolled overlap (1), Cleat tab, Cleat location: Front (default: "Tekla Structures positions the cleat according to
  // the purlin profile that is used") / Back. R10 (264 cleats, docs/steel/PARITY.md C04): on a C / U purlin, Front = the toe (open)
  // side whatever the slope; the long leg stands on the rafter on the toe line, the short leg at its top points away from the
  // purlin; the angle sits where Tekla puts an L member at Middle / Middle: centred on the bounding box of the profile as turned
  // (part 'Top' frame), which moves it a few mm on a slope. The stiffener PL6*44 lies on the long leg's outer face.
  // Back = v69 (behind the web). Old models have no 'loc' in their stored settings and keep the v69 result; new cleats get Front.
  // Z and I purlins: unchanged (R10 has none; their side stays q.side).
  CONN.Tcl.def.loc = 'front';
  ST_LBL.loc = 'Cleat location';
  { const tab = TKD.Tcl.tabs.find(t => t.name === 'Parts'); const g = tab && (tab.groups || []).find(x => x[0] === 'Cleat');
    if (g && !g[1].some(f => f.k === 'loc')) g[1].push(TKF('loc', 'Cleat location', { type: 'sel', opts: [tkOpt('front', 'Front'), tkOpt('back', 'Back')], note: 'Front = the purlin toe side (C / U purlins); Back = behind the web' })); }
  const CL70_STIFF = [[135, 22], [21, 22], [11, 12], [11, -22], [21, -22], [135, 12]];   // R10 'Stiff.' PL6*44 outline: (height above the rafter top, along the purlin)
  const FAM_CL70 = FAM.CL;
  FAM.CL = X => {
    const { U, q, A, c, cut, plate, bolts, weld } = X; const purs = [X.B, X.B2].filter(Boolean); if (!purs.length) return null;
    const MP = stMF(purs[0].st); const loc = c && c.q ? c.q.loc : undefined;
    if (loc !== 'front' || !(MP.p.kind === 'C' || MP.p.kind === 'U')) return FAM_CL70(X);
    const MA = stMF(A.st);
    const w0 = MA.c1.clone().sub(MP.c1); const b = MA.a.dot(MP.a), dd = MA.a.dot(w0), ee = MP.a.dot(w0), den = (1 - b * b) || 1e-9; const PP = MP.c1.clone().addScaledVector(MP.a, (ee - b * dd) / den);
    const FA = stFaceAt(A.st, PP); const n = FA.n.clone(); const base = FA.Pc.clone().addScaledVector(n, FA.off * U); const y = MP.a.clone().addScaledVector(n, -MP.a.dot(n)).normalize();
    const W = purlinWeb(MP, q); const out = W.dir.clone().addScaledVector(n, -W.dir.dot(n)).addScaledVector(y, -W.dir.dot(y)).normalize();
    const webPt = PP.clone().addScaledVector(W.dir, W.off * U); const sw = webPt.clone().sub(base).dot(out); const Ow = base.clone().addScaledVector(out, sw);   // rafter top, web back, frame line (v69 O)
    const toe = out.clone().negate(); const O = Ow.clone().addScaledVector(toe, MP.b * U);   // rafter top, purlin toe line
    const t = q.t * U, L1 = q.L1 * U, L2 = q.L2 * U, w = q.w / 2 * U; const F0 = stFrame3(O, toe, n);   // x = away from the purlin (toe side), y = up from the rafter top, z = along the purlin
    const pts = [[0, 0], [t, 0], [t, L1 - t], [L2, L1 - t], [L2, L1], [0, L1]];
    // Tekla L member at Middle / Middle: the bounding box of the turned profile in the part's Top frame (y up, z = x × y) is centred on the axis
    const xa = F0.Z.clone(); let yT = new THREE.Vector3(0, 0, 1).addScaledVector(xa, -xa.z); if (yT.length() < 1e-6) yT = new THREE.Vector3(1, 0, 0).addScaledVector(xa, -xa.x); yT.normalize(); const zT = new THREE.Vector3().crossVectors(xa, yT);
    let a0 = Infinity, a1 = -Infinity, b0 = Infinity, b1 = -Infinity; for (const [px, py] of pts) { const P = F0.P(px, py, 0); const a = P.dot(yT), bb = P.dot(zT); a0 = Math.min(a0, a); a1 = Math.max(a1, a); b0 = Math.min(b0, bb); b1 = Math.max(b1, bb); }
    const C0 = F0.P(L2 / 2, L1 / 2, 0); const sh = yT.clone().multiplyScalar(C0.dot(yT) - (a0 + a1) / 2).addScaledVector(zT, C0.dot(zT) - (b0 + b1) / 2);
    const F = stFrame3(O.clone().add(sh), toe, n); let cl = F.poly(pts, -w, w);
    if (q.stiff) { const G = stFrame3(F0.P(t - q.ts * U / 2, 0, 0), n, F0.Z); plate('CLEAT STIFFENER', G.poly(CL70_STIFF.map(p => [p[0] * U, p[1] * U]), 0, q.ts * U), q.ts); }
    const zAxis = F0.Z; const all = [];
    for (const P of purs) { const M2 = stMF(P.st); const mid = M2.c1.clone().lerp(M2.c2, 0.5); const sgn = Math.sign(mid.clone().sub(O).dot(zAxis)) || 1;
      const zz = (purs.length === 1 && Math.abs(M2.c1.clone().sub(O).dot(zAxis)) > w && Math.abs(M2.c2.clone().sub(O).dot(zAxis)) > w && Math.sign(M2.c1.clone().sub(O).dot(zAxis)) !== Math.sign(M2.c2.clone().sub(O).dot(zAxis))) ? [-q.e * U, q.e * U] : [sgn * q.e * U];
      const bl = []; for (const z of zz) for (const hh of [q.h1, q.h2]) bl.push(F0.P(-M2.b * U, hh * U, z));   // from the web back face toward the toes (R10 TKBOLT)
      const grip = (M2.b + q.L2) * U; /* web back to the short leg's end: R10 M12 L144 = b + L2 + 2·d */ const BB = stBolts(bl, toe.toArray(), q.bolt, grip, U);
      for (const hc of BB.holes) { cl = cl.subtract(hc); cut(P.id, hc); } all.push([BB, bl.length, grip]); }
    plate('CLEAT', cl, q.t, `L${q.L1}*${q.L2}*${q.t}`); for (const [BB, k, grip] of all) bolts(BB, k, grip / U + 2 * q.bolt); weld('Cleat to rafter top flange', q.a, 2 * q.w);
  };
