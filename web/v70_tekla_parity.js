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
