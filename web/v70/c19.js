  // == C19 == (agent C19, docs/steel/PARITY.md "C19 · Railing welds as Tekla")
  // R13 (Tekla's IFC) for every stair / landing railing: each stanchion CHS48*3 is welded (a = 2, workshop) to the part it STANDS on (the
  // stringer UPN240, or the landing beam's top flange) and belongs to that part's assembly; its foot is cut on that part's top face (a plane:
  // the lowest point of the cut is the member's start point p1, r x tan(slope) below the foot centre); its top is square, at the hand rail's
  // centre line, h = 1000 above the foot centre; the hand rail CHS48*3 and the railing PD50*5 (500 above the foot centre) run from the
  // first post's centre line to the last one's, square, no weld and no cut (loose parts: R13's own model has them running into the posts).
  // Posts stand at a fixed plan spacing from the first one (700 on the lower flight, 800 on the upper one), the last one at the end.
  // 1. Weld (no parts) `weldonly`: q.fit = 'face' (Tekla: the secondary's end fitted on the main part's face it stands on, a plane cut) or
  //    'none' (no cut). Anything else = v69 (the main part's envelope: for a channel stringer it leaves the foot's lower wedge inside the
  //    channel, below the flange: R13 rebuild 22 stanchion / stringer overlaps before C19).
  // 2. Railing (stRailing, and Railing S77 with Railing type = Tekla): tmode 'tekla' builds the railing as above.
  {
    const g19 = stConnGeom;
    function fitFace19(Ast, Bst) {   // the half-space beyond the main part's face, at the secondary's end nearest the main part (a plane fit)
      const U = stU(), MA = stMF(Ast), MB = stMF(Bst);
      const dl = P => { const v = P.clone().sub(MA.c1); return v.addScaledVector(MA.a, -v.dot(MA.a)).length(); };
      const E = dl(MB.c1) <= dl(MB.c2) ? MB.c1 : MB.c2, Far = E === MB.c1 ? MB.c2 : MB.c1;
      const F = stFaceAt(Ast, Far.clone().sub(E).normalize().multiplyScalar(1e-3).add(E)); const n = F.n.clone().normalize();
      if (Far.clone().sub(E).dot(n) <= 0) return null;   // the secondary does not leave the face outward: no fit
      const Q = F.Pc.clone().addScaledVector(n, F.off * U); const O = E.clone().addScaledVector(n, -E.clone().sub(Q).dot(n));
      const X = new THREE.Vector3().crossVectors(n, Math.abs(n.z) < 0.9 ? new THREE.Vector3(0, 0, 1) : new THREE.Vector3(1, 0, 0)).normalize(), Y = new THREE.Vector3().crossVectors(X, n).normalize();   // X x Y = -n: into the main part
      const w = (2 * Math.max(MB.h, MB.b) + 100) * U, d = (MA.h + MA.b + 100) * U;
      return stBox3(O, X, Y, -w, w, -w, w, 0, d);
    }
    stConnGeom = function (c) {
      const r = g19(c); const fit = c && c.type === 'weldonly' && c.q ? c.q.fit : null;
      if (!r || (fit !== 'face' && fit !== 'none')) return r;
      const A0 = stBody(c.parts[0]), B0 = stBody(c.parts[1]); if (!B0) return r;
      if (!r.cuts) r.cuts = new Map(); r.cuts.delete(B0.id);
      if (fit === 'face' && A0) { try { const m = fitFace19(A0.st, B0.st); if (m) r.cuts.set(B0.id, [m]); } catch (e) { /* no cut */ } }
      return r;
    };
    // 2. the railing as Tekla
    const RAIL19 = { tmode: 'tekla', post: 'CHS48*3', rail: 'CHS48*3', knee: 'PD50*5', rh: 1000, kh: 500, ps: 700, wa: 2 };
    const rail19 = stRailing;
    stRailing = function (o) {
      if (!o || o.tmode !== 'tekla') return rail19(o);
      const U = stU(), q = Object.assign({}, RAIL19, o), P = q.path.map(V3), posts = [], rails = [], Z = new THREE.Vector3(0, 0, 1);
      const pr = ((stProf(q.post) || {}).d || 48) / 2;
      for (let k = 0; k + 1 < P.length; k++) {
        const a = P[k], b = P[k + 1], hv = b.clone().sub(a).setZ(0), Lh = hv.length() / U; if (!(Lh > 0)) continue;
        const sl = Math.abs(b.z - a.z) / U / Lh, drop = pr * sl * U;   // the foot cut on the slope: its lowest point r x tan below the centre
        const ss = []; for (let s = 0; s < Lh - 1; s += +q.ps > 0 ? +q.ps : Lh) ss.push(s); ss.push(Lh);
        for (const s of ss) { if (k > 0 && s === 0) continue; const F = a.clone().lerp(b, s / Lh);
          posts.push(Object.assign({ kind: 'member' }, ST_SETS.COLUMN, { name: 'STANCHION', prof: q.post, ap: 'HR', pp: 'hr', cls: 11, rot: 'Front', p1: F.clone().addScaledVector(Z, -drop).toArray(), p2: F.clone().addScaledVector(Z, +q.rh * U).toArray() })); }
        for (const [z, nm, pf] of [[+q.rh, 'HANDRAIL', q.rail], ...(q.knee ? [[+q.kh, 'KNEE RAIL', q.knee === true ? RAIL19.knee : q.knee]] : [])])
          rails.push(Object.assign({ kind: 'member' }, ST_SETS.BEAM, { name: nm, prof: pf, ap: 'HR', pp: 'hr', cls: 11, depth: 'Middle', p1: a.clone().addScaledVector(Z, z * U).toArray(), p2: b.clone().addScaledVector(Z, z * U).toArray() }));
      }
      const r = stAddMany([...posts, ...rails], 'Railing'); const pid = r.ids.slice(0, posts.length), own = new Set(r.ids.filter(x => x != null)), list = [];
      for (const id of pid) { if (id == null) continue; const pb = stBody(id); const sup = o.on != null ? o.on : stSupportAt(V3(pb.st.p1), own, 40);
        if (sup != null && !stLinked(sup, id)) list.push(['weldonly', [sup, id], { a: +q.wa || 2, fit: 'face' }]); }
      const res = list.length ? stConnectMany(list, 'Railing: posts welded to the support') : [];
      toast(`Railing: ${posts.length} posts, ${rails.length} rails`); return { ids: r.ids, conns: res };
    };
    Object.assign(window.__st, { railing: stRailing });
    // Railing S77: Railing type (v69 / Tekla) and Tekla's values; Tekla: along the picked part's top face, between the picked points
    const D77 = CONN.TS77 && CONN.TS77.def;
    if (D77) Object.assign(D77, { tmode: '', post: RAIL19.post, rail: RAIL19.rail, knee: RAIL19.knee, rh: RAIL19.rh, kh: RAIL19.kh, ps: RAIL19.ps, wa: RAIL19.wa });
    Object.assign(ST_LBL, { tmode: 'Railing type', post: 'Post profile', rail: 'Hand rail profile', knee: 'Knee rail profile (empty = none)', rh: 'Hand rail height (post foot centre to rail axis)', kh: 'Knee rail height', ps: 'Post spacing (plan)', wa: 'Post weld a' });
    if (D77 && typeof tkGeneric === 'function') { const T = tkGeneric('TS77'); const pt = T.tabs.find(t => t.name === 'Parameters');
      if (pt) { const fl = pt.groups[0][1]; const i = fl.findIndex(f => f.k === 'tmode'); const sel = TKF('tmode', 'Railing type', { type: 'sel', dv: '', opts: [tkOpt('', 'v69 (posts by spacing, rails welded to the posts)'), tkOpt('tekla', 'Tekla (R13): posts fitted on the part, top square at the hand rail')] });
        if (i >= 0) fl[i] = sel; else fl.unshift(sel); }
      TKD.TS77 = T; }
    const gen19 = stGenRun;
    stGenRun = function (k, parts, pts) {
      const CN = CONN[k], q = CN ? Object.assign({}, CN.def, stCQ(k)) : {};
      if (!CN || q.kind !== 'rail' || q.tmode !== 'tekla') return gen19(k, parts, pts);
      const U = stU(), A = stBody(parts[0]); if (!A || !A.st || A.st.kind !== 'member') { toast('Pick the part the railing stands on'); return; }
      const M = stMF(A.st), up = M.eh.z >= 0 ? M.eh.clone() : M.eh.clone().negate(), top = P => { const v = P.clone().sub(M.c1); return M.c1.clone().addScaledVector(M.a, v.dot(M.a)).addScaledVector(up, M.h / 2 * U); };
      const pa = top(pts && pts[0] ? V3(pts[0]) : M.c1), pb = top(pts && pts[1] ? V3(pts[1]) : M.c2);
      stRailing(Object.assign({}, q, { path: [pa.toArray(), pb.toArray()], on: A.id }));
    };
    window.__st.genRun = (k, p, q) => stGenRun(k, p, q);
  }
