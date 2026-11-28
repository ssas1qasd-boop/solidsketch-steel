// End plate 144 (Tekla dialog component, TKD). Three cases: standard, odd profile, sloped.
'use strict';
const member = (set, extra) => ({ set, extra });

module.exports = [
  {
    name: 't144_standard',
    description: 'HEA300 column, IPE360 beam level at +7500, End plate 144 with default dialog values.',
    members: [member('COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }),
              member('BEAM', { prof: 'IPE360', p1: [0, 0, 7500], p2: [6000, 0, 7500] })],
    conns: [['T144', [0, 1], {}]],
  },
  {
    name: 't144_odd_profile',
    description: 'HEM300 column (rot Top) running past an IPE450 beam framing into its flange at +7000, End plate 144 with 3 bolt rows and 12 mm column stiffeners.',
    members: [member('COLUMN', { prof: 'HEM300', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 8000] }),
              member('BEAM', { prof: 'IPE450', p1: [0, 0, 7000], p2: [6000, 0, 7000] })],
    conns: [['T144', [0, 1], { rows: 3, stiff: 12 }]],
  },
  {
    name: 't144_sloped',
    description: 'R10 portal geometry: HEA300 column, IPE360 rafter eave +7500 to apex +9000 over 13195, End plate 144.',
    members: [member('COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }),
              member('RAFTER', { prof: 'IPE360', p1: [0, 0, 7500], p2: [13195, 0, 9000] })],
    conns: [['T144', [0, 1], {}]],
  },
  // v70 C09 · Haunch tab (Tekla's haunch plates) and C02 / C09 · Tekla bolt length (docs/steel/PARITY.md "C02 / C09"): R12's own
  // joints, then the same settings on other sizes and slopes, the Automatic creation and the arc chamfers.
  {
    name: 't144_c09_r12',
    description: 'R12 monitor post PC7001 (BI150 post) on the BI220 rafter at 12.8 deg: Haunch tab top / bottom plate 8 x 60 x 60, chamfers 15 line, Tekla bolt length, bolting direction 2.',
    members: [member('RAFTER', { prof: 'BI220-8-150-10', p1: [39537.387, 27925, 7422.572], p2: [33095.5, 27925, 8887.194], rot: 'Top', plane: 'Middle', depth: 'Middle' }),
              member('COLUMN', { prof: 'BI150-6-120-8', p1: [34039.28, 27925, 8782.496], p2: [34039.28, 27925, 9579.802], rot: 'Below', plane: 'Middle', depth: 'Middle' })],
    conns: [['T144', [0, 1], { tp: 12, bolt: 16, ext: 55.358, extb: 60.814, gap: 0, cols: 2, gauge: 90, side: 35, rows: 4, e: 30.025, pitches: '70 70 70', hpc: 'both', hct: 'line', hpt1: 8, hpb1: 60, hph1: 60, hc1: 15, hc2: 15, hpt2: 8, hpb2: 60, hph2: 60, hc4: 15, hc3: 15, blrule: 'tekla', bdir: 2 }]],
  },
  {
    name: 't144_c09_hea160_ipe300_5deg',
    description: 'HEA160 post on an IPE300 rafter at 5 deg, R12 Haunch tab settings with b and h blank (b = the end plate reach, h = b), ext 50 / 50.',
    members: [member('RAFTER', { prof: 'IPE300', p1: [0, 0, 6000], p2: [8000, 0, 6699.9] }),
              member('COLUMN', { prof: 'HEA160', p1: [4000, 0, 6500], p2: [4000, 0, 8000] })],
    conns: [['T144', [0, 1], { tp: 12, bolt: 16, ext: 50, extb: 50, gap: 0, hpc: 'both', hpt1: 8, hpt2: 8, hpb1: '', hph1: '', hpb2: '', hph2: '', blrule: 'tekla', bdir: 2 }]],
  },
  {
    name: 't144_c09_ipe200_ipe360_10deg_auto_convex',
    description: 'IPE200 post on an IPE360 rafter at 10 deg: Automatic creation (end plate past the top flange only), plates 10 x 50 x 70, convex arc chamfers 20.',
    members: [member('RAFTER', { prof: 'IPE360', p1: [0, 0, 6000], p2: [8000, 0, 7410.6] }),
              member('COLUMN', { prof: 'IPE200', p1: [3000, 0, 6700], p2: [3000, 0, 8500] })],
    conns: [['T144', [0, 1], { tp: 15, bolt: 20, ext: 60, extb: 0, gap: 0, hpc: 'auto', hct: 'convex', hpt1: 10, hpb1: 50, hph1: 70, hpt2: 10, hpb2: 50, hph2: 70, hc1: 20, hc2: 20, hc3: 20, hc4: 20 }]],
  },
  {
    name: 't144_c09_concave_level',
    description: 'IPE240 post standing on a level HEB300 beam, concave arc chamfers 15 x 25, plates 8 x 70 x 80.',
    members: [member('BEAM', { prof: 'HEB300', p1: [0, 0, 5000], p2: [6000, 0, 5000] }),
              member('COLUMN', { prof: 'IPE240', p1: [3000, 0, 5150], p2: [3000, 0, 7000] })],
    conns: [['T144', [0, 1], { tp: 12, bolt: 16, ext: 80, extb: 80, gap: 0, hpc: 'both', hct: 'concave', hpt1: 8, hpb1: 70, hph1: 80, hpt2: 8, hpb2: 70, hph2: 80, hc1: 15, hc2: 25, hc3: 25, hc4: 15 }]],
  },
  {
    name: 't144_c02_r12',
    description: 'R12 eave R3023 / C2017: BI320 column, BI220 rafter at 12.8 deg, end plate 20 with 4 rows, Tekla bolt length (M20 grip 30 -> L60), bolting direction 2.',
    members: [member('COLUMN', { prof: 'BI320-8-150-10', p1: [52838, 22355, 230], p2: [52838, 22355, 7547.745], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('RAFTER', { prof: 'BI220-8-150-10', p1: [52682.387, 22355, 7433.94], p2: [46290.5, 22355, 8887.194], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['T144', [0, 1], { tp: 20, bolt: 20, ext: 10, extb: 104.385, gap: 0, cols: 2, gauge: 70, side: 40, rows: 4, e: 50.045, pitches: '70 70 80', blrule: 'tekla', bdir: 2 }]],
  },
  // v70 C13 · 144 Stiffeners tab (Tekla's column stiffeners; docs/steel/PARITY.md "C13"): R12's mezzanine joint (stiffeners not created there:
  // they are 186's), then the tab on other column / beam sizes, orientations, shapes and chamfers.
  {
    name: 't144_c13_r12',
    description: 'R12 mezzanine joint C2015 / EB4002: BI320 column, BI320 girder on its flange, End plate 144 t 16, ext 55 / 55, M16 2 x 4 (70 230 70), Haunch tab 10 x 55 x 50, chamfer 12.5 (the girder-side Stiff. plates); Stiffeners tab not created.',
    members: [member('COLUMN', { prof: 'BI320-8-150-10', p1: [13303, 16785, 230], p2: [13303, 16785, 7534.104], rot: 'Below', plane: 'Middle', depth: 'Middle' }),
              member('BEAM', { prof: 'BI320-8-120-10', p1: [334, 16785, 3840], p2: [13127, 16785, 3840], rot: 'Top', plane: 'Middle', depth: 'Middle' })],
    conns: [['T144', [0, 1], { tp: 16, bolt: 16, ext: 55, extb: 55, gap: 0, cols: 2, gauge: 60, side: 30, rows: 4, e: 30, pitches: '70 230 70', hpc: 'both', hct: 'line', hpt1: 10, hpb1: 55, hph1: 50, hc1: 12.5, hc2: 12.5, hpt2: 10, hpb2: 55, hph2: 50, hc3: 12.5, hc4: 12.5, blrule: 'tekla', bdir: 2, scr: 'none' }]],
  },
  {
    name: 't144_c13_hea300_ipe360_stiff',
    description: 'HEA300 column (rot Top: the beam on its flange), IPE360 beam, Stiffeners tab created with the defaults (t = beam flange, full width and depth, chamfer = the root radius 27).',
    members: [member('COLUMN', { prof: 'HEA300', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 7500] }),
              member('BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] })],
    conns: [['T144', [0, 1], { gap: 0, scr: 'both' }]],
  },
  {
    name: 't144_c13_hem300_ipe450_sloped_gaps',
    description: 'HEM300 column, IPE450 beam sloped 8 deg on its flange, bolt rows 40 outside and inside each flange (ext 80), stiffeners parallel to the beam, gap for the web rounding (square with 27 mm root fillets clashes), gap 1 = 5, bottom gaps 3 / 4 = 4, t 15, b 120.',
    members: [member('COLUMN', { prof: 'HEM300', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 8000] }),
              member('BEAM', { prof: 'IPE450', p1: [0, 0, 7000], p2: [6000, 0, 7843.3] })],
    conns: [['T144', [0, 1], { gap: 0, ext: 80, extb: 80, rows: 4, e: 40, pitches: '80 374.4 80', scr: 'both', sor: 'parallel', ssh: 'gap', sg1: 5, sg3: 4, sg4: 4, stt1: 15, stt2: 15, stt3: 15, stt4: 15, stb1: 120, stb2: 120, stb3: 120, stb4: 120 }]],
  },
  {
    name: 't144_c13_heb400_ipe500_perp_concave',
    description: 'HEB400 column, IPE500 beam sloped 5 deg, bolt rows 40 outside and inside each flange (ext 80), stiffeners perpendicular to the column, concave arc chamfers 20 x 15, automatic creation, h 300.',
    members: [member('COLUMN', { prof: 'HEB400', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 8000] }),
              member('BEAM', { prof: 'IPE500', p1: [0, 0, 7000], p2: [6000, 0, 7524.9] })],
    conns: [['T144', [0, 1], { gap: 0, ext: 80, extb: 80, rows: 4, e: 40, pitches: '80 422 80', scr: 'auto', sor: 'perp', sct: 'concave', sc1: 20, sc2: 15, sth1: 300, sth2: 300, sth3: 300, sth4: 300 }]],
  },
  // Group 1 two-video test (docs/steel/PARITY.md "T144 · End plate (144): the two-video test"; tests/steel/videos/T144/VIDEO_1.txt, VIDEO_2.txt).
  {
    name: 't144_v1',
    // Video-faithful (owner 2026-11-28): the video's own values, so rule B is not gated here (c09_cases.js skips it); instead our rule
    // checks must give the warnings Tekla shows in the video (tools/golden/video_status.js).
    videoFaithful: true,
    videoStatus: {
      // Owner decision 2026-11-28 (HANDOFF "T144 video 1 symbol: DECIDED"; VIDEO_1.txt "SYMBOL SETTLED"): the final state is GREEN after the
      // Modify at 24:31 (yellow with the edge-distance message only at 24:21). Our rule check now measures the edge distance only in plates
      // where the bolt has its own hole (web v70/rulecheck.js, app TkCheck), so bolts 1, 2, 9, 10 in the stiffeners (no hole there) give no warning.
      tekla: [{ t: '24:21', where: 'status bar before the last Modify (VIDEO_1.txt SYMBOL SETTLED)', text: 'Bolt edge distance of the bolt with id 82ac12cc-... is too small' },
              { t: '24:31', where: 'after the Modify (3.8, 3-flash-preview; green at 24:38 / 24:40 and in frame f_1490 at 24:50)', text: '(no message; symbol green)' }],
      expect: [],
      level: 1,   // green (owner, 2026-11-28)
      symbol: 'green after the Modify at 24:31 (final state)',
      noWarning: 'Tekla shows no separate warning for the stiffener / bolt clash (the top and bottom rows pass through the stiffeners moved 40 up / down). The only clash check in the video (11:09, an earlier state) lists COLUMN - SPACER PLATE.',
    },
    description: 'Video 1 (VLQiysAuEr4, final state 27:18): HEA360 column, IPE400 beam on its flange. Picture 1 = -80, 2 = -80, gap 0; Plates: End plate t 20, b 180, h 560, Fitting plate 1 t 12; Stiffeners created t 10 (Top / Bottom NS / FS), positions 1 = 20, 2 / 5 = 40 (Tekla: up; saved sg2 / sg5 = -40), 3 / 4 = -40; Haunch top / bottom 12 x 80 x 80, chamfers 80 line, both; Bolts M20, 5 rows, s 120, 6 = -40 (Top), 3 = 40, 4 / 5 blank (spacing = b - 2 x 40 = 100); Tekla bolt length.',
    members: [member('COLUMN', { prof: 'HEA360', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 9000] }),
              member('BEAM', { prof: 'IPE400', p1: [0, 0, 6000], p2: [6000, 0, 6000] })],
    conns: [['T144', [0, 1], { tp: 20, ext: 80, extb: 80, gap: 0, epb: 180, eph: 560, shim: 12, scr: 'both', stt1: 10, stt2: 10, stt3: 10, stt4: 10, sg1: 20, sg2: -40, sg5: -40, sg3: -40, sg4: -40,
      hpc: 'both', hct: 'line', hpt1: 12, hpb1: 80, hph1: 80, hpt2: 12, hpb2: 80, hph2: 80, hc1: 80, hc2: 80, hc3: 80, hc4: 80, bolt: 20, rows: 5, pitch: 120, pitches: '', e: 40, refV: 'Top', side: 40, cols: 2, gauge: '', blrule: 'tekla' }]],
  },
  {
    name: 't144_v2',
    videoFaithful: true,
    videoStatus: {
      tekla: [{ t: '19:38', where: 'status bar (Gemini reads final2_IBVnHuj1F-s.json; no frame saved)', text: 'Bolt edge distance of the bolt with id 02dbe36a-... is too small' }],
      expect: [{ check: 'edge', plate: 'END PLATE', row: 'lowest' }],
      level: 2,   // yellow symbol (the owner, from the video)
    },
    description: 'Video 2 (IBVnHuj1F-s, final state 24:24): HEA400 column, HEA400 beam on its flange. Picture 1 = 0, 2 = 0, gap 0; Plates: End plate t 20, Fitting plate 1 t 20; Stiffeners created t 10, chamfers 30 x 30 line; Haunch top / bottom 10 x 75 x 75, chamfers 1-4 = 55 line, both; Bolts M20, 4 rows, s 120, 6 = 30 (Top), 2 across, 5 = 120, 3 = 30, Tekla length.',
    members: [member('COLUMN', { prof: 'HEA400', rot: 'Top', p1: [0, 0, 0], p2: [0, 0, 7000] }),
              member('BEAM', { prof: 'HEA400', p1: [0, 0, 5000], p2: [6000, 0, 5000] })],
    conns: [['T144', [0, 1], { tp: 20, ext: 0, extb: 0, gap: 0, shim: 20, scr: 'both', stt1: 10, stt2: 10, stt3: 10, stt4: 10, sc1: 30, sc2: 30, sct: 'line',
      hpc: 'both', hct: 'line', hpt1: 10, hpb1: 75, hph1: 75, hpt2: 10, hpb2: 75, hph2: 75, hc1: 55, hc2: 55, hc3: 55, hc4: 55, bolt: 20, rows: 4, pitch: 120, pitches: '', e: 30, refV: 'Top', side: 30, cols: 2, gauge: 120, blrule: 'tekla' }]],
  },
];
