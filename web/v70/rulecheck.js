// == rule checks: bolt's own hole (main session, 2026-11-28) ==
// v58 tkStatus (v69 L16034) with one change, in the bolt edge distance check: a cut segment is skipped as the bolt's own hole
// outline only when BOTH its ends lie on that hole (within hr + slot / 2 + 1.5 of the bolt line), not when its midpoint does.
// The midpoint test depended on how the mesh happens to be triangulated: in t144_v1 (video 1) a stiffener face passing through
// the bolt centre was taken for the hole, and web reported 25 mm where the app (another triangulation) reported 2 mm; the bolt
// in fact cuts half into the stiffener (0 mm). A hole outline still has both ends on the hole, so a holed plate is measured as before.
tkStatus = function tkStatus70(c, deep) {
    if (TK_ST.has(c.id)) return TK_ST.get(c.id);
    const U = stU(); const msgs = [], errs = []; const lbl = CONN[c.type] ? CONN[c.type].tekla : c.type;
    const kids = S.bodies.filter(b => b.st && b.st.conn === c.id); const ok = stConnOK(c);
    if (!ok) { const r = { s: 3, msgs: [`${lbl} #${c.id}: the component could not be created on these parts`] }; TK_ST.set(c.id, r); return r; }
    const plates = kids.filter(b => b.st.kind === 'conn' && !tkSkipPlate(b)); const bolts = kids.filter(b => b.st.kind === 'bolts' && b.st.pts && b.st.pts.length);
    const q = Object.assign({}, CONN[c.type] ? CONN[c.type].def : {}, c.q);
    for (const B of bolts) { const ax = V3(B.st.ax).normalize(); const d = +B.st.d || 20; const d0 = +B.st.d0 || d + (d <= 24 ? 2 : 3); const hr = d0 / 2 * U; const P = B.st.pts.map(V3); let nOut = 0;
      const edge = new Map(); P.forEach((P0, i) => { let pierced = 0; let minE = Infinity, minPl = null;
        for (const pl of plates) { const sr = tkPierce(pl, P0, ax, hr + Math.max(+B.st.sv || 0, +B.st.sh || 0) / 2 * U, U); if (!sr) continue; pierced++;
          const O = P0.clone().addScaledVector(ax, (sr[0] + sr[1]) / 2); const segs = tkSlice(pl, O, ax);
          for (const [a, b] of segs) { const m = a.clone().add(b).multiplyScalar(0.5); const own = P.some(Q => { const QO = Q.clone().addScaledVector(ax, O.clone().sub(Q).dot(ax)); const R = hr + Math.max(+B.st.sv || 0, +B.st.sh || 0) / 2 * U + 1.5 * U; return QO.distanceTo(a) < R && QO.distanceTo(b) < R; }); if (own) continue; const dd = tkSegDist(O, a, b); if (dd < minE) { minE = dd; minPl = pl; } } }
        if (!pierced) nOut++; else if (minE < TK_RULE.edge * d * U - 0.5 * U) { const kk = minPl.st.name; if (!edge.has(kk)) edge.set(kk, { ids: [], min: Infinity }); const o = edge.get(kk); o.ids.push(i + 1); o.min = Math.min(o.min, minE); } });
      for (const [nm, o] of edge) msgs.push(`Bolt edge distance of the bolt${o.ids.length > 1 ? 's' : ''} ${o.ids.join(', ')} (M${d}) in ${nm} is too small: ${Math.round(o.min / U)} mm < ${Math.round(TK_RULE.edge * d)} mm (${TK_RULE.edge}·d)`);
      if (nOut === P.length && plates.length) { const mems = c.parts.map(stBody).filter(b => b && b.man && b.st && b.st.kind === 'member'); const hrr = hr + Math.max(+B.st.sv || 0, +B.st.sh || 0) / 2 * U; if (P.some(P0 => mems.some(m => tkPierce(m, P0, ax, hrr, U)))) nOut = 0; }
      if (nOut && plates.length) { errs.push(`${nOut} bolt${nOut > 1 ? 's are' : ' is'} outside the plates (${B.st.name} M${d})`); }
      let minP = Infinity; for (let i = 0; i < P.length; i++) for (let j = i + 1; j < P.length; j++) { const v = P[j].clone().sub(P[i]); v.addScaledVector(ax, -v.dot(ax)); minP = Math.min(minP, v.length()); }
      if (P.length > 1 && minP < TK_RULE.pitch * d0 * U - 0.5 * U) msgs.push(`Bolt spacing is too small: ${Math.round(minP / U)} mm < ${Math.round(TK_RULE.pitch * d0)} mm (${TK_RULE.pitch}·d0, M${d})`); }
    const tmin = (plates.map(tkThick).filter(t => t > 0))[0]; if (tmin) for (const w of c.welds || []) if (+w.a > TK_RULE.weld * tmin + 0.05) { msgs.push(`Weld “${w.name}” a=${w.a} mm is too big for a ${tmin} mm plate (max ${(TK_RULE.weld * tmin).toFixed(1)})`); break; }
    if (deep !== false) { const mems = c.parts.map(stBody).filter(b => b && b.man && b.st && b.st.kind === 'member');
      for (const pl of plates) { let bb; try { bb = pl.man.boundingBox(); } catch (e) { continue; } let vol = 0; try { vol = Math.abs(pl.man.volume()); } catch (e) { /* skip */ }
        for (const m of mems) { let mb; try { mb = m.man.boundingBox(); } catch (e) { continue; } let sep = false; for (let k = 0; k < 3; k++) if (bb.min[k] > mb.max[k] || mb.min[k] > bb.max[k]) sep = true; if (sep) continue;
          let iv = 0; try { const im = pl.man.intersect(m.man); iv = Math.abs(im.volume()); if (im.delete) im.delete(); } catch (e) { iv = 0; } if (iv > Math.max(TK_RULE.overlap * vol, 125 * U * U * U)) { msgs.push(`${pl.st.name} overlaps ${m.st.name} ${m.st.prof} (${Math.round(iv / (U * U * U) / 1000)} cm³)`); break; } } } }
    const r = { s: errs.length ? 3 : msgs.length ? 2 : 1, msgs: [...errs, ...msgs].map(t => `${lbl} #${c.id}: ${t}`) }; TK_ST.set(c.id, r); return r;
  };
