  // == T142 == (agent g1-t142, docs/steel/PARITY.md "T142 · Two sided end plate (142): the two-video test")
  // Tekla's Two sided end plate (142) (conn_142_help): "connects two beams to a beam or to a column using bolted end plates. One bolt group
  // goes through all the three parts." v69 made it as two separate End plate 144s (two bolt groups at the same holes, the second one's grip
  // padded). Video 1 (_Au1wpEDNzw: HEB300 column, two IPE300 beams on its flanges) and video 2 (J2OINltOa4I: two beams on a column web):
  // one bolt group from end plate 1 through the main part to end plate 2 (video 1: long bolts across both column flanges, 12:10-12:17).
  //   Picture 1 / 2: end plate 1's upper / lower edge from the top / bottom of secondary 1 (Tekla sign, blank = from the bolt group);
  //   3 / 4: end plate 2's lower / upper edge from secondary 2 (blank = end plate 2 mirrors end plate 1 about the main part); 5: the gap of
  //   each side. Plates 1 / Plates 2: each end plate's t / b / h (blank b / h = the bolt group plus its edge distances; h given and 1 blank:
  //   centred on the bolt group, as video 1: h 450 on the IPE300 with a centred bolt group, 75 above and below, the 75 x 75 haunch plates
  //   fill it). Haunch: Top plate / Bottom plate (secondary 1), Top plate 2 / Bottom plate 2 (secondary 2); a plate with no thickness is not
  //   made (video 2 makes none). Bolts: one group in secondary 1's frame; 1 / 2 the vertical position from the secondary (Top / Middle /
  //   Below, Tekla's help), 3 the edge distance, 4 the number, 5 the spacing; 6 / 7 horizontal. Sort secondaries by profile height (Yes:
  //   the taller beam is secondary 1). Models saved before v70 have no 'bgrp' in their stored settings and keep v69's two groups.
  Object.assign(CONN.T142.def, { bgrp: 'one', srt: 1, gap: 0, gap2: 0, ext2: '', extb2: '', tp2: 10, shim2: 0, epb: '', eph: '', epb2: '', eph2: '', posV: '', posH: 0, refV: 'Top', refH: 'Middle',
    hpc: 'auto', hpc2: 'auto', hpt1: '', hpb1: '', hph1: '', hpt2: '', hpb2: '', hph2: '', hpt3: '', hpb3: '', hph3: '', hpt4: '', hpb4: '', hph4: '', hc1: '', hc2: '', hc3: '', hc4: '', hct: 'line', blrule: 'tekla' });
  Object.assign(ST_LBL, { bgrp: 'Bolt group', srt: 'Sort secondaries by profile height', gap2: 'Gap (second side)', ext2: 'End plate 2 upper edge', extb2: 'End plate 2 lower edge', tp2: 'End plate 2 thickness',
    shim2: 'Fitting plate 2 (second side)', epb2: 'End plate 2 width (b)', eph2: 'End plate 2 height (h)', hpc2: 'Haunch plate creation (secondary 2)' });
  {
    const T4 = TKD.T144.tabs, tab = n => T4.find(t => t.name === n);
    const offG = (h, l) => [h, l.map(x => TKF('_' + x, x, { off: 'Not used by this component in SolidSketch' }))];
    const bt = tab('Bolts'), grp = n => (bt.groups.find(g => g[0] === n) || [n, []])[1];
    const creation = k => TKF(k, k === 'hpc' ? 'Haunch plate creation (secondary 1)' : 'Haunch plate creation (secondary 2)', { type: 'sel', dv: 'auto', opts: [tkOpt('auto', 'Automatic', 'auto'), tkOpt('both', 'Top and bottom haunch plates are always created', 'on'), tkOpt('none', 'Haunch plates are not created', 'off')],
      note: 'Automatic: a plate where the end plate reaches past the flange and its thickness is given. Thickness 0 or blank = no plate.' });
    const hch = (tab('Haunch').groups || []).find(g => g[0] === 'Haunch plate chamfers');
    TKD.T142 = { help: 'conn_142_help', tabs: [
      { name: 'Picture', pic: 'ep', dims: [
        TKF('ext', '1', Object.assign({ at: [53, 17], tip: 'End plate upper edge distance from the top of the first secondary beam (blank = from the bolt group)' }, neg)),
        TKF('extb', '2', Object.assign({ at: [53, 79], tip: 'End plate lower edge distance from the bottom of the first secondary beam (blank = from the bolt group)' }, neg)),
        TKF('extb2', '3', Object.assign({ at: [80, 79], tip: 'End plate lower edge distance from the bottom of the second secondary beam (blank = as end plate 1)' }, neg)),
        TKF('ext2', '4', Object.assign({ at: [80, 17], tip: 'End plate upper edge distance from the top of the second secondary beam (blank = as end plate 1)' }, neg)),
        TKF('gap', '5', { at: [18, 95], tip: 'Gap between the shim plates (or the end plate) and the main part, first side' }),
        TKF('gap2', '5', { at: [34, 95], tip: 'Gap between the shim plates (or the end plate) and the main part, second side' })],
        groups: [['Secondaries', [TKF('srt', 'Sort secondaries by profile height', { type: 'sel', dv: 1, opts: [tkOpt(1, 'Yes'), tkOpt(0, 'No')], note: 'Yes: the larger profile is the first secondary beam' })]],
          offG('Compensation flange plates', ['Compensation flange plate arrangements', '6 · Upper compensation flange plate edge', '7 · Lower compensation flange plate edge', '8 · Upper compensation flange plate edge 2', '9 · Lower compensation flange plate edge 2'])] },
      { name: 'Plates 1', parts: [['End plate', 'END PLATE', { t: 'tp', b: 'epb', h: 'eph' }], ['Fitting Plate 1', 'SHIM', { t: 'shim' }]],
        groups: [offG('Plates', ['Fitting Plate 2', 'Fitting Plate 3', 'Number of fitting pl. 1 (DEF=1)', 'Comp. flange pl']), offG('Shim plates', ['Bolt edge distances in shim plate', 'Shim plate shape', 'Tolerance', 'Shim plate position', 'Gap size']),
          offG('Safety connections', ['Safety connection', 'Safety connection location', 'Safety connection cut type', 'Vertical cut/offset', 'Horizontal cut', 'Radius'])] },
      { name: 'Plates 2', parts: [['End plate', 'END PLATE', { t: 'tp2', b: 'epb2', h: 'eph2' }], ['Fitting Plate 1', 'SHIM', { t: 'shim2' }]],
        groups: [offG('Plates', ['Fitting Plate 2', 'Fitting Plate 3', 'Number of fitting pl. 1 (DEF=1)', 'Comp. flange pl']), offG('Shim plates', ['Bolt edge distances in shim plate', 'Shim plate shape', 'Tolerance', 'Shim plate position', 'Gap size']),
          offG('Shim plate and end plate positions', ['1 · Vertical move', '2 · Horizontal move'])] },
      { name: 'Haunch', parts: [['Top plate', 'HAUNCH_PLATE', { t: 'hpt1', b: 'hpb1', h: 'hph1' }], ['Top plate 2', 'HAUNCH_PLATE', { t: 'hpt3', b: 'hpb3', h: 'hph3' }], ['Bottom plate', 'HAUNCH_PLATE', { t: 'hpt2', b: 'hpb2', h: 'hph2' }], ['Bottom plate 2', 'HAUNCH_PLATE', { t: 'hpt4', b: 'hpb4', h: 'hph4' }]],
        groups: [hch ? [hch[0], hch[1].slice()] : ['Haunch plate chamfers', []], ['Haunch plate creation', [creation('hpc'), creation('hpc2')]]] },
      tab('Notch'),
      { name: 'Bolts', pic: 'bolts', dims: [
        TKF('posV', '1', { at: [8, 18], tip: 'Dimension for vertical bolt group position, from the reference 2 (Top: the upper edge of the secondary part to the uppermost bolt; blank = the edge distance below the plate top)', ph: 'auto' }),
        TKF('pitches', '5', Object.assign({ type: 'list', at: [8, 34], tip: 'Bolt spacing, vertical (one value per space, e.g. 90 90 90 90)' }, pitchList)),
        TKF('rows', '4', { at: [8, 50], tip: 'Number of bolts, vertical (0 = auto)' }),
        TKF('e', '3', { at: [8, 82], tip: 'Bolt edge distance, vertical (bolt centre to the plate\'s upper and lower edges)' }),
        TKF('cols', '4', { at: [30, 95], tip: 'Number of bolts, horizontal', type: 'sel', opts: [tkOpt(2, '2'), tkOpt(4, '4')] }),
        TKF('posH', '6', { at: [50, 95], dv: 0, tip: 'Dimension for horizontal bolt group position, from the reference 7' }),
        TKF('gauge', '5', { type: 'list', at: [64, 95], tip: 'Bolt spacing, horizontal (blank = the plate width b less 2 x the edge distance 3)',
          get: q => q.gauge === '' || q.gauge === undefined ? '' : (q.cols | 0) === 4 ? `${q.gauge2 || q.gauge} ${q.gauge} ${q.gauge2 || q.gauge}` : `${q.gauge}`,
        set: (q, k, v) => { const a = String(v).trim().split(/\s+/).map(Number).filter(x => isFinite(x) && x > 0); if (!String(v).trim()) { q.cols = 2; q.gauge = ''; } else if (a.length >= 3) { q.cols = 4; q.gauge = a[1]; q.gauge2 = a[0]; } else if (a.length) { q.cols = 2; q.gauge = a[0]; } } }),
        TKF('side', '3', { at: [83, 95], tip: 'Bolt edge distance, horizontal (bolt centre to the plate\'s side edges)' })],
        groups: [['Bolt group', [TKF('refV', '2 · Vertical measurement', { type: 'sel', dv: 'Top', opts: [tkOpt('Top', 'Top', 'top'), tkOpt('Middle', 'Middle', 'mid'), tkOpt('Below', 'Below', 'bot')] }),
          TKF('refH', '7 · Horizontal measurement', { type: 'sel', dv: 'Middle', opts: [tkOpt('Left', 'Left', 'left'), tkOpt('Middle', 'Middle', 'hmid'), tkOpt('Right', 'Right', 'right')] }),
          TKF('bdel', '8 · Bolts to delete', { type: 'list', ph: 'e.g. 2 5' })]],
          ['Bolt basic properties', grp('Bolt basic properties')], ['Slotted holes', grp('Slotted holes')], ['Bolt assembly', grp('Bolt assembly')]] },
      tab('Holes'), tab('General'), tab('Design'), tab('Welds')].filter(Boolean) };
  }
  {
    const ep142 = FAM.EP;
    const num = (v, d) => v === '' || v === undefined || v === null || !isFinite(+v) ? d : +v;
    const side142 = (A, Bm, U) => {   // FAM_EP70's frame of one secondary: the main part's face plane, up along the secondary's web
      // the face normal of the main part (not the beam's direction: a skewed beam's end plate lies on the web / flange, Tekla)
      const J = stJoint(A, Bm), Bp = J.B, M = J.M; const fl = Math.abs(J.u.dot(M.eh)) >= Math.abs(J.u.dot(M.es)); const N0 = fl ? M.eh : M.es;
      const u = N0.clone().multiplyScalar(J.u.dot(N0) >= 0 ? 1 : -1).normalize(); const xu = J.X.dot(u) || 1;
      const Pc = M.c1.clone().addScaledVector(M.a, J.E.clone().sub(M.c1).dot(M.a)); const O = Pc.addScaledVector(u, (fl ? M.h / 2 : M.tw / 2) * U);
      let FY = Bp.eh.clone().addScaledVector(u, -Bp.eh.dot(u)); if (FY.length() < 1e-6) FY = J.Y.clone().addScaledVector(u, -J.Y.dot(u)); FY.normalize(); const FZ = new THREE.Vector3().crossVectors(u, FY).normalize();
      const onFace = p => { const s = p.clone().sub(O).dot(u) / xu; return p.clone().addScaledVector(J.X, -s); };
      const cs = []; for (const sy of [-1, 1]) for (const sz of [-1, 1]) { const p = onFace(O.clone().addScaledVector(Bp.eh, sy * Bp.h / 2 * U).addScaledVector(Bp.es, sz * Bp.b / 2 * U)); cs.push([p.clone().sub(O).dot(FY), p.clone().sub(O).dot(FZ), p]); }
      const yT = Math.max(...cs.map(c => c[0])), yB = Math.min(...cs.map(c => c[0])), zC = (Math.max(...cs.map(c => c[1])) + Math.min(...cs.map(c => c[1]))) / 2;
      const kc = Math.max(0.2, Math.abs(J.Y.dot(FY))), cg = Math.max(10, (J.M.r || 0) + 1) * U;   // coped (beam into a beam web): the plate stays clear of the main part's flanges and web rounding
      return { J, Bp, u, O, FY, FZ, Ff: stFrame3(O, u, FY), cs, yT, yB, zC, cl: y => J.cope ? Math.min(Math.max(y, (J.yBot + cg) / kc), (J.yTop - cg) / kc) : y };
    };
    const gen142 = X => {
      const { U, q, A, cut, bolts, weld } = X; const BIG = v49Big();
      // famGeom's own plate(): web/v70/t144.js (loaded after this file) wraps FAM.EP and resizes END PLATE / SHIM by its b / h in End plate
      // 144's frames; the 142 plates are already made to size here
      const plate = (name, man, t, prof) => { if (man) X.out.parts.push({ name, man, prof: prof || 'PL' + Math.round(t * 10) / 10 }); };
      let B1 = X.B, B2 = X.B2; if (+q.srt !== 0 && stMF(B2.st).h > stMF(B1.st).h + 1e-9) { const t = B1; B1 = B2; B2 = t; }
      const S1 = side142(A, B1, U), S2 = side142(A, B2, U); const cosA = -S1.u.dot(S2.u); if (cosA < 0.2) return ep142(X);   // not on opposite faces: v69
      const tp = q.tp * U, gp = num(q.gap, 0) * U, sh = num(q.shim, 0) * U, n0 = gp + sh, n1 = n0 + tp;
      const tp2 = num(q.tp2, q.tp) * U, gp2 = num(q.gap2, 0) * U, sh2 = num(q.shim2, 0) * U, m0 = gp2 + sh2, m1 = m0 + tp2;
      // horizontal: FAM_EP70 (Bolts 5 blank = b - 2 x the edge distance 3, as T144)
      const nc = (q.cols | 0) === 4 ? 4 : 2; const pb = num(q.epb, NaN), sd = num(q.side, 0);
      const gv = q.gauge === '' || q.gauge === undefined ? (pb > 0 && nc !== 4 && pb - 2 * sd > 0 ? pb - 2 * sd : 90) : +q.gauge;
      const g = gv * U, g2 = (+q.gauge2 || gv) * U; const zs = nc === 4 ? [-g / 2 - g2, -g / 2, g / 2, g / 2 + g2] : [-g / 2, g / 2]; const zw = (nc === 4 ? g / 2 + g2 : g / 2) + sd * U;
      let zC = S1.zC; { const ph = num(q.posH, 0), gw = zw - sd * U; if (q.refH === 'Left') zC = zC - S1.Bp.b / 2 * U + ph * U + gw; else if (q.refH === 'Right') zC = zC + S1.Bp.b / 2 * U - ph * U - gw; else zC += ph * U; }
      // vertical: plate edges given by Picture 1 / 2 (internal sign: + = past the beam), else from the bolt group
      const e = num(q.e, 35) * U, x1 = num(q.ext, NaN), x2 = num(q.extb, NaN);
      const yh0 = isFinite(x1) ? S1.cl(S1.yT + x1 * U) : NaN, yl0 = isFinite(x2) ? S1.cl(S1.yB - x2 * U) : NaN;
      const H = (isFinite(yh0) ? yh0 : S1.yT) - (isFinite(yl0) ? yl0 : S1.yB); let nr = q.rows ? q.rows | 0 : tkRows(H / U); const ps = v49Pitches(q, nr); if (String(q.pitches || '').trim()) nr = ps.length + 1;
      const sp = v58Span(ps, nr, q) * U, pv = num(q.posV, NaN);
      let top; if (q.refV === 'Middle') top = (S1.yT + S1.yB) / 2 + sp / 2 + (isFinite(pv) ? pv : 0) * U;
      else if (q.refV === 'Below') top = (isFinite(pv) ? S1.yB + pv * U : (isFinite(yl0) ? yl0 : S1.yB) + e) + sp;
      else top = isFinite(pv) ? S1.yT - pv * U : (isFinite(yh0) ? yh0 : S1.yT) - e;
      const ys = []; if (!String(q.pitches || '').trim() && !(+q.pitch)) { const lo = (isFinite(yl0) ? yl0 : S1.yB) + e; for (let i = 0; i < nr; i++) ys.push(top - (top - lo) * i / Math.max(1, nr - 1)); }
      else { let y = top; for (let i = 0; i < nr; i++) { ys.push(y); y -= (ps[i] || ps[ps.length - 1] || 70) * U; } }
      const yLo = ys[ys.length - 1], hP = num(q.eph, NaN) * U; let yh, yl;
      if (hP > 0) { yh = isFinite(yh0) ? yh0 : isFinite(yl0) ? yl0 + hP : (top + yLo) / 2 + hP / 2; yl = yh - hP; }
      else { yh = isFinite(yh0) ? yh0 : top + e; yl = isFinite(yl0) ? yl0 : yLo - e; }
      const z0 = pb > 0 ? zC - pb / 2 * U : zC - zw, z1 = pb > 0 ? zC + pb / 2 * U : zC + zw;
      // the bolt group: from end plate 1's outer face through the main part to end plate 2's outer face
      const tkL = q.blrule === 'tekla'; const bl = []; for (const y of ys) for (const z of zs) bl.push(S1.Ff.P(n1 + (tkL ? 0 : 0.1) * U, y, zC + z));
      const F1 = S1.Ff.P(0, ys[0], zC + zs[0]); const Dm = F1.clone().sub(S2.O).dot(S2.u) / S1.u.dot(S2.u);
      const grip = n1 + Dm + m1 / cosA + (tkL ? 0 : 2 * U);
      // end plate 2 on the far face: the bolts' points there, edges by Picture 3 / 4 or as end plate 1
      const ax = S1.u.clone().negate(); let y2a = Infinity, y2b = -Infinity, z2a = Infinity, z2b = -Infinity;
      for (const y of ys) for (const z of zs) { const Q = S1.Ff.P(0, y, zC + z).addScaledVector(ax, Dm).sub(S2.O); const yy = Q.dot(S2.FY), zz = Q.dot(S2.FZ); y2a = Math.min(y2a, yy); y2b = Math.max(y2b, yy); z2a = Math.min(z2a, zz); z2b = Math.max(z2b, zz); }
      const x3 = num(q.extb2, NaN), x4 = num(q.ext2, NaN), hP2 = num(q.eph2, NaN) * U, pb2 = num(q.epb2, NaN);
      let yh2 = isFinite(x4) ? S2.cl(S2.yT + x4 * U) : y2b + (yh - top), yl2 = isFinite(x3) ? S2.cl(S2.yB - x3 * U) : y2a - (yLo - yl);
      if (hP2 > 0) { if (!isFinite(x4)) yh2 = isFinite(x3) ? yl2 + hP2 : (y2a + y2b) / 2 + (yh - (top + yLo) / 2); yl2 = yh2 - hP2; }
      const zc2 = (z2a + z2b) / 2, hw2 = pb2 > 0 ? pb2 / 2 * U : (z1 - z0) / 2;
      const BB = stBolts(bl, ax.toArray(), q.bolt, grip, U);
      let ep1 = S1.Ff.box(n0, n1, yl, yh, z0, z1), ep2 = S2.Ff.box(m0, m1, yl2, yh2, zc2 - hw2, zc2 + hw2);
      for (const hc of BB.holes) { ep1 = ep1.subtract(hc); ep2 = ep2.subtract(hc); cut(A.id, hc); }
      const ends = (S, Bm, m) => { cut(Bm.id, S.Ff.box(-BIG, m, -BIG, BIG, -BIG, BIG)); const J = S.J; if (J.cope) { const hb = S.Bp.h / 2 * U;
        if (hb > J.yTop - 10 * U) cut(Bm.id, J.box(-BIG, J.xFace + J.copeL, J.yTop - 10 * U, BIG / 10, -BIG / 10, BIG / 10)); if (-hb < J.yBot + 10 * U) cut(Bm.id, J.box(-BIG, J.xFace + J.copeL, -BIG / 10, J.yBot + 10 * U, -BIG / 10, BIG / 10)); } };
      ends(S1, B1, n1); ends(S2, B2, m1);
      plate('END PLATE', ep1, q.tp); if (sh) { let s = S1.Ff.box(n0 - sh, n0, yl, yh, z0, z1); for (const hc of BB.holes) s = s.subtract(hc); plate('SHIM', s, q.shim); }
      plate('END PLATE', ep2, tp2 / U); if (sh2) { let s = S2.Ff.box(m0 - sh2, m0, yl2, yh2, zc2 - hw2, zc2 + hw2); for (const hc of BB.holes) s = s.subtract(hc); plate('SHIM', s, sh2 / U); }
      bolts(BB, bl.length, tkL ? tkBoltLen70(q.bolt, grip / U) : grip / U + 2 * q.bolt);
      for (const [S, m] of [[S1, n1], [S2, m1]]) { const lp = [S.cs[0], S.cs[1], S.cs[3], S.cs[2]].map(c => c[2].clone().addScaledVector(S.u, m).toArray()); weld('Beam to end plate', q.a, 2 * (S.yT - S.yB) / U + 4 * S.Bp.b, lp); }
      // haunch plates (hp70, End plate 144's C09 plates), one call per secondary with that side's plate edges and settings
      const hq = (S, k1, k2, c, t, gpp, shh, yy1, yy0) => Object.assign({}, q, { tp: t / U, gap: gpp / U, shim: shh / U, front: 0, double: 0, stub: 0, stiffT: 0, ext: (yy1 - S.yT) / U, extb: (S.yB - yy0) / U, hpc: c,
        hpt1: num(q['hpt' + k1], 0), hpb1: q['hpb' + k1], hph1: q['hph' + k1], hpt2: num(q['hpt' + k2], 0), hpb2: q['hpb' + k2], hph2: q['hph' + k2], hc1: num(q.hc1, 0), hc2: num(q.hc2, 0), hc3: num(q.hc3, 0), hc4: num(q.hc4, 0) });
      if (q.hpc === 'auto' || q.hpc === 'both') hp70(Object.assign({}, X, { q: hq(S1, 1, 2, q.hpc, tp, gp, sh, yh, yl) }), B1);
      if (q.hpc2 === 'auto' || q.hpc2 === 'both') hp70(Object.assign({}, X, { q: hq(S2, 3, 4, q.hpc2, tp2, gp2, sh2, yh2, yl2) }), B2);
      return X.out;
    };
    FAM.EP = X => X.c && X.c.type === 'T142' && X.c.q && X.c.q.bgrp === 'one' && X.B && X.B2 ? gen142(X) : ep142(X);
  }
