  // == C18 ==
  // C18 on R13 (docs/steel/PARITY.md "C18"): Tekla's assemblies of the stairs, and the counter plates on EB4002. After the chapter's C18 section.
  // 1. Stair end plate: "Plate welded to the member" (pweld). R13's two top plates FLT12*150 (884 / 886, Tekla assembly PLSTR9001) have no
  //    weld and no owner: loose plates, bolted outward (2 x 4 M16 L50, grip 24) through a counter plate FLT12*150 welded to the girder
  //    EB4002's flange edges (885 / 887, assembly EB4002) = Counter plate on the main part = Yes (main part EB4002), welded = No.
  //    No: the plate has no weld and is its own assembly. Yes (default, old models): as before (the member's assembly).
  // 2. Stair treads, Treads = Tekla: each tread is its own assembly (Tekla's SGR9001 x 23: THREAD the main part, its two L60*6 brackets in
  //    it), not the first stringer's (v69 stOwner: parts[0]). The tread has no owner; a bracket belongs to the tread made just before it
  //    (v69 stRegen: makeBodyRecord gives the tread its id just before tkKidSt).
  CONN.Tsep.def.pweld = 1;
  ST_LBL.pweld = 'Plate welded to the member';
  { const pos = ((TKD.Tsep.tabs.find(t => t.name === 'Plates') || {}).groups || []).find(g => g[0] === 'Position');
    if (pos && !pos[1].some(f => f.k === 'pweld' || f.key === 'pweld')) pos[1].push(TKF('pweld', 'Plate welded to the member', { type: 'sel', dv: 1, opts: [tkOpt(1, 'Yes (the member\'s assembly)'), tkOpt(0, 'No (a loose plate, its own assembly)')] })); }
  { const sep70 = FAM.SEP; FAM.SEP = X => sep70(String(X.q.pweld) === '0' ? Object.assign({}, X, { weld: () => {} }) : X); }
  let c18Tread = null;
  { const own = stOwner; stOwner = function (c, name, ix) {
      if (c.type === 'Tsep' && name !== 'COUNTER PLATE' && String((c.q || {}).pweld) === '0') return undefined;
      if (c.type === 'stairtreads' && String((c.q || {}).tmode) === 'tekla' && (name === 'TREAD' || name === 'BRACKET')) return name === 'TREAD' ? undefined : 'c18tread';
      return own(c, name, ix); }; }
  { const kid = tkKidSt; tkKidSt = function (c, p, asm) {
      if (asm === 'c18tread') asm = c18Tread; else if (c && c.type === 'stairtreads' && p && p.name === 'TREAD' && asm === undefined) c18Tread = S.nextId - 1;
      return kid(c, p, asm); }; }
