  // == C20 == (agent R13, docs/steel/PARITY.md "C12 / C20 / C14 on R13")
  // A 186 (Stiffener creation set) whose secondary runs past the main part's web face (Picture 1 < 0: R13's EB4007 webs, Tekla's -10 at
  // C2021 / C2024, through the column web: R13's own rule B) cuts the secondary at the web face (gap 0), never into the main part. The bolts
  // stay where Picture 1 + the horizontal edge distance put them (edge = edge + gap), and the tab keeps its length (Plates b, or the length
  // the unclamped settings give).
  { const st186 = FAM.ST; FAM.ST = X => {
      const q = X.q, g = +q.gap;
      if (!(X.c && X.c.type === 'T186' && q.scr && isFinite(g) && g < 0)) return st186(X);
      const eh0 = q.eh === '' || q.eh === undefined || !isFinite(+q.eh) ? +q.e : +q.eh;
      const tb = +q.tb > 0 ? +q.tb : g + 2 * eh0 + (Math.max(1, q.cols | 0) - 1) * (+q.pitchH || 0);
      return st186(Object.assign({}, X, { q: Object.assign({}, q, { gap: 0, eh: eh0 + g, tb }) })); }; }
