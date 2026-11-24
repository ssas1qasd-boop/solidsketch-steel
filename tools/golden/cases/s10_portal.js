// S10: the portal frame generator (v33 stPortal + v42 girts / gable columns + v43 crane runway), every option of its sheet.
// Each case: the sheet's values (PF_DEF plus these); the whole model after Create is dumped (parts, connections, welds).
const P = q => ({ run: { portal: q } });
module.exports = {
  // Create twice (the second frame replaces the first: v69 L13861)
  twice: { pre_portal: { bays: 3 }, run: { portal: { bays: 4, span: 20000 } } },
  // standard: v69's defaults (25 m span, 6 bays of 6 m, eaves 7450, 12°, purlins, ties, roof + wall bracing, girts, gables)
  std: P({}),
  // each option off on its own
  no_purlins: P({ purOn: false }), no_ties: P({ tieOn: false }), no_roofbr: P({ rbOn: false }), no_wallbr: P({ wbOn: false }),
  no_girts: P({ girtOn: false }), no_gables: P({ gableOn: false }), bare: P({ purOn: false, tieOn: false, rbOn: false, wbOn: false, girtOn: false, gableOn: false }),
  crane: P({ craneOn: true }), crane_opts: P({ craneOn: true, crH: 6200, crBeam: 'HEB500', crBr: 'HEB300', crRail: 'PL60*70', crE: 400, crL: 750 }),
  // odd profiles
  odd_profiles: P({ col: 'HEB300', raf: 'IPE500', pur: 'CC200-2-20-60', tie: 'SHS100*3', rb: 'CHS88.9x3.2', wb: 'FLT100*10', girt: 'Z200-2-20-60', gcol: 'IPE300' }),
  bad_girt: P({ girt: 'CZ200-2-20-60' }),   // an unknown girt profile: v69 makes nothing (the sheet checks only col, raf, pur, tie, rb, wb)
  hollow_cols: P({ col: 'SHS300*10', raf: 'HEA300', pur: 'UPN160', rb: 'D20', wb: 'PL60*8' }),
  builtup: P({ col: 'BI400-8-200-12', raf: 'BI500-8-180-12' }),
  // sloped / skewed geometry and edge values
  flat_roof: P({ pitch: 0 }), steep: P({ pitch: 30 }), pitch_44: P({ pitch: 44 }),
  one_bay: P({ bays: 1 }), two_bays: P({ bays: 2 }), many_bays: P({ bays: 10, bay: 5000 }),
  narrow: P({ span: 8000, bays: 3, bay: 4500, eave: 4000, pitch: 15 }), wide: P({ span: 40000, bays: 4, bay: 7500, eave: 9000, pitch: 6 }),
  pur_spacing_big: P({ purS: 20000 }), pur_spacing_small: P({ purS: 900 }),
  girt_spacing: P({ girtS: 2500 }), gable_spacing_wide: P({ gcolS: 30000 }), gable_spacing_tight: P({ gcolS: 3000 }),
  odd_dims: P({ span: 23750, bays: 5, bay: 6125, eave: 7375.5, pitch: 7.5 }),
  // the owner's workflow numbers on one span: eaves +7500, apex +9000 over 13 195 / 2
  owner_span: P({ span: 13195, bays: 5, bay: 5570, eave: 7500, pitch: Math.atan(1500 / 6597.5) * 180 / Math.PI }),
};
