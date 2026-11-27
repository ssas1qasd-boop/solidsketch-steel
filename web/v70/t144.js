  // == T144 == (agent g1-t144, docs/steel/PARITY.md "T144 · End plate (144): the two-video test")
  // Plates tab, End plate b / h (Tekla: "End plate thickness, width, and height. The width and height are defined by the bolt group edge
  // distances", conn_144_help). Both reference videos type the width (VLQiysAuEr4 05:22 b 180 / h 560, Bds3oxGdc2k 04:42 b 180); blank =
  // as before (width = gauge + 2 x edge distance, height from Picture 1 / 2). Tekla keeps the bolts where the Bolts tab puts them (Bds3oxGdc2k
  // 04:15-04:25: h 200 / 400 moves only the plate's lower edge, the bolts stay): b is centred where the plate was, h runs down from the plate's
  // top edge (Picture 1). The shim plates follow the end plate (Tekla: "shim plates are of the same size as the end plate").
  // Bolts tab 5 (horizontal bolt spacing) left blank: Tekla takes the spacing from the plate width and the edge distance 3 (VLQiysAuEr4 24:28:
  // b 180, edge 40 / 40, spacing blank -> the bolts 100 apart); with b blank too, the default spacing (90).
  { const ep144 = FAM.EP; FAM.EP = X0 => {
      let X = X0; if (X0.q.gauge === '' || X0.q.gauge === undefined) { const b0 = +X0.q.epb, sd = +X0.q.side || 0;
        X = Object.assign({}, X0, { q: Object.assign({}, X0.q, { gauge: isFinite(b0) && b0 > 0 && (X0.q.cols | 0) !== 4 && b0 - 2 * sd > 0 ? b0 - 2 * sd : 90 }) }); }
      const q = X.q, pb = +q.epb, ph = +q.eph;
      if (!((isFinite(pb) && pb > 0) || (isFinite(ph) && ph > 0)) || !X.B) return ep144(X);
      const U = X.U, holes = [];
      const frameOf = Bm => { const J = stJoint(X.A, Bm), Bp = J.B, u = J.u.clone(); const O = J.E.clone().addScaledVector(J.X, J.xFace);
        let FY = Bp.eh.clone().addScaledVector(u, -Bp.eh.dot(u)); if (FY.length() < 1e-6) FY = J.Y.clone().addScaledVector(u, -J.Y.dot(u)); FY.normalize();
        return stFrame3(O, u, FY); };
      const frames = [X.B, X.B2].filter(Boolean).map(frameOf); let k = 0;   // the end plate being made (FAM_EP70 makes the end plate, then its shim, per secondary)
      const ext = (man, F) => { const m = man.getMesh(), v = m.vertProperties, np = m.numProp, lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
        for (let i = 0; i < v.length; i += np) { const p = new THREE.Vector3(v[i], v[i + 1], v[i + 2]).sub(F.O); const c = [p.dot(F.X), p.dot(F.Y), p.dot(F.Z)];
          for (let a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], c[a]); hi[a] = Math.max(hi[a], c[a]); } }
        return [lo, hi]; };
      const resize = (man, F) => { const [lo, hi] = ext(man, F); const zc = (lo[2] + hi[2]) / 2;
        const z0 = pb > 0 ? zc - pb / 2 * U : lo[2], z1 = pb > 0 ? zc + pb / 2 * U : hi[2], y1 = hi[1], y0 = ph > 0 ? y1 - ph * U : lo[1];
        let s = F.box(lo[0], hi[0], y0, y1, z0, z1); for (const hc of holes) s = s.subtract(hc); return s; };
      const X2 = Object.assign({}, X, {
        cut: (id, m) => { if (id === X.A.id && m) holes.push(m); return X.cut(id, m); },
        plate: (name, man, t, prof) => {
          if (man && (name === 'END PLATE' || name === 'SHIM')) { if (name === 'END PLATE') k++; man = resize(man, frames[Math.min(k - 1, frames.length - 1)]); }
          return X.plate(name, man, t, prof); } });
      return ep144(X2); }; }
  ST_LBL.epb = 'End plate width (b)'; ST_LBL.eph = 'End plate height (h)';
  Object.assign(CONN.T144.def, { epb: '', eph: '' });
  { const pt = TKD.T144.tabs.find(t => t.name === 'Plates'); if (pt && pt.parts) pt.parts = pt.parts.map(p => p[1] === 'END PLATE' ? [p[0], p[1], Object.assign({}, p[2], { b: 'epb', h: 'eph' })] : p); }
  // Bolts tab 6 (vertical bolt group position, reference Top): Tekla measures it from the upper edge of the secondary part to the uppermost bolt
  // (conn_144_help; VLQiysAuEr4 23:13: -40 = the top row 40 above the IPE400's top, plate 80 above it, measured 40 from the plate's top edge
  // at 23:50). SolidSketch keeps e from the plate's top edge (old models unchanged); the dialog shows and takes Tekla's value: 6 = e - 1, where
  // 1 is the plate's reach above the secondary (Picture 1, Tekla sign: -ext). Bolts tab 5 may be left blank (see FAM.EP above).
  { const bt = TKD.T144.tabs.find(t => t.name === 'Bolts');
    if (bt && bt.dims) bt.dims = bt.dims.map(f => f.k === 'e' ? Object.assign({}, f, { tip: 'Vertical bolt group position: from the top of the secondary part to the uppermost bolt (reference Top; negative = above it)',
        get: q => q.e === '' || q.e === undefined ? '' : Math.round((+q.e - (+q.ext || 0)) * 1000) / 1000, set: (q, k, v) => { if (v !== '' && isFinite(+v)) q.e = +v + (+q.ext || 0); } })
      : f.k === 'gauge' ? Object.assign({}, f, { tip: 'Bolt spacing, horizontal (blank = the plate width b less 2 x the edge distance 3)',
        get: q => q.gauge === '' || q.gauge === undefined ? '' : (q.cols | 0) === 4 ? `${q.gauge2 || q.gauge} ${q.gauge} ${q.gauge2 || q.gauge}` : `${q.gauge}`,
        set: (q, k, v) => { const a = String(v).trim().split(/\s+/).map(Number).filter(x => isFinite(x) && x > 0); if (!String(v).trim()) { q.cols = 2; q.gauge = ''; } else if (a.length >= 3) { q.cols = 4; q.gauge = a[1]; q.gauge2 = a[0]; } else if (a.length) { q.cols = 2; q.gauge = a[0]; } } }) : f); }
  // Stiffeners tab, Stiffener positions 2 / 5 (top near / far side): in Tekla a positive value moves the top stiffener up, past the beam's top
  // flange (VLQiysAuEr4 15:02-15:30, the presenter: "if we make it 40 it goes up ... below, if we make it 40 it will go up, we want it down, so
  // negative 40 ... this one is higher than the flange and this one is lower"), as 3 / 4 move the bottom ones (positive = up). SolidSketch's
  // sg2 / sg5 run the other way (positive = toward the beam's middle, C13); the dialog shows and takes Tekla's sign, the saved value is unchanged.
  { const sp = TKD.T144.tabs.find(t => t.name === 'Stiffeners'); const g = sp && sp.groups && sp.groups.find(x => x[0] === 'Stiffener positions');
    if (g) g[1] = g[1].map(f => f.k === 'sg2' || f.k === 'sg5' ? Object.assign({}, f, neg, { tip: 'Positive = up, past the top flange (Tekla)' }) : f.k === 'sg3' || f.k === 'sg4' ? Object.assign({}, f, { tip: 'Positive = up, toward the beam\'s middle (Tekla)' }) : f); }
