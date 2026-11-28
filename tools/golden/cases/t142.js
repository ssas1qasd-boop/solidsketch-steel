// Two sided end plate (142) (Group 1, docs/steel/PARITY.md "T142 · Two sided end plate (142): the two-video test"): one bolt group from end
// plate 1 through the main part to end plate 2. Standard / odd profile / sloped / beam web / skewed cases with the defaults (rule B gated),
// then the two video examples (tests/steel/videos/T142/VIDEO_1.txt, VIDEO_2.txt), built with the videos' own values.
'use strict';
const member = (set, extra) => ({ set, extra });
const COL = member('COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] });
const beam = (prof, s, o) => member('BEAM', Object.assign({ prof, p1: [0, 0, 7000], p2: [s * 6000, 0, 7000] }, o || {}));
// the other EP families' first component (T101 ... T50) copies this file's pattern: std / odd / sloped / beam web / skewed + v1 / v2
module.exports = [
  { name: 't142_std_hea300_ipe360', description: 'HEA300 column, two IPE360 beams on its web (both sides, level +7000), Two sided end plate (142) with the defaults: one bolt group through both end plates and the web.',
    members: [COL, beam('IPE360', 1), beam('IPE360', -1)], conns: [['T142', [0, 1, 2], {}]] },
  { name: 't142_odd_heb300_ipe270_ipe300', description: 'HEB300 column, IPE270 and IPE300 on its web, picked smaller first (Sort secondaries by profile height: the IPE300 becomes secondary 1; end plate 2 mirrors end plate 1 on the IPE270), 3 rows.',
    members: [member('COLUMN', { prof: 'HEB300', p1: [0, 0, 0], p2: [0, 0, 7500] }), beam('IPE270', 1), beam('IPE300', -1)], conns: [['T142', [0, 1, 2], { rows: 3 }]] },
  { name: 't142_sloped_hea300_ipe360_10deg', description: 'HEA300 column, two IPE360 rafters at 10 deg on its web (both sides up), Two sided end plate (142), Middle bolt group 4 x 2 at 80.',
    members: [COL, member('RAFTER', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 8058] }), member('RAFTER', { prof: 'IPE360', p1: [0, 0, 7000], p2: [-6000, 0, 8058] })], conns: [['T142', [0, 1, 2], { refV: 'Middle', rows: 4, pitches: '80 80 80', ext: '', extb: '' }]] },
  { name: 't142_beam_web_ipe500_ipe300', description: 'IPE500 main beam, two IPE300 floor beams into its web from both sides (coped), Two sided end plate (142) with the defaults.',
    members: [member('BEAM', { prof: 'IPE500', p1: [0, -3000, 7000], p2: [0, 3000, 7000] }), beam('IPE300', 1), beam('IPE300', -1)], conns: [['T142', [0, 1, 2], {}]] },
  { name: 't142_skew_hea300_ipe300_10deg', description: 'HEA300 column, two IPE300 beams skewed 10 deg in plan into its web (opposite sides), Two sided end plate (142), plates b 160 / h 260, Middle 3 rows at 80.',
    members: [COL, member('BEAM', { prof: 'IPE300', p1: [0, 0, 7000], p2: [5908.8, 1041.9, 7000] }), member('BEAM', { prof: 'IPE300', p1: [0, 0, 7000], p2: [-5908.8, -1041.9, 7000] })],
    conns: [['T142', [0, 1, 2], { epb: 160, eph: 260, ext: '', refV: 'Middle', rows: 3, pitch: 80 }]] },
  {
    name: 't142_v1',
    description: 'Video 1 (_Au1wpEDNzw, Bangladesh Civil Engineering, Tekla 2017): HEB300 column, two IPE300 beams on its flanges; Picture 1-4 blank, 5 = 2 / 2; Plates 1 and 2: end plate 16 x 150 x 450; Bolts M16, Middle, 5 x 2, spacing 90, edge 45 / 35, horizontal spacing 80; Haunch: 4 plates 6 x 75 x 75, chamfers 75.',
    videoFaithful: true,
    videoStatus: {
      tekla: [{ t: '12:05', where: 'status bar after Modify (Gemini lighter model, status__Au1wpEDNzw.json; no frame)', text: 'Information: polygon depth value put as zero' }],
      expect: [],   // Tekla shows only that information line (it is on the status bar from 09:1x on, before the 142 is made): no warning to match
      symbol: 'not read (no frame; the lighter model did not report the cone colour for this span)',
      noWarning: 'Tekla shows no warning for its 360 mm bolts across the whole HEB300 (both flanges, 2 x 2 mm gaps and the 262 mm between the flanges along the shank); rule B reports that as air along the shank (10 bolts)',
    },
    members: [member('COLUMN', { prof: 'HEB300', p1: [0, 0, 0], p2: [0, 0, 7500], rot: 'Top' }), beam('IPE300', 1), beam('IPE300', -1)],
    conns: [['T142', [0, 1, 2], { ext: '', extb: '', ext2: '', extb2: '', gap: 2, gap2: 2, tp: 16, epb: 150, eph: 450, tp2: 16, epb2: 150, eph2: 450,
      bolt: 16, tol: 2, refV: 'Middle', posV: '', e: 45, rows: 5, pitches: '', pitch: 90, refH: 'Middle', posH: 0, side: 35, cols: 2, gauge: 80,
      hpc: 'auto', hpc2: 'auto', hpt1: 6, hpb1: 75, hph1: 75, hpt2: 6, hpb2: 75, hph2: 75, hpt3: 6, hpb3: 75, hph3: 75, hpt4: 6, hpb4: 75, hph4: 75, hc1: 75, hc2: 75, hc3: 75, hc4: 75 }]],
  },
  {
    name: 't142_v2',
    description: 'Video 2 (J2OINltOa4I, Gani sk, imperial): two beams on a column web; Plates 1 and 2: end plate t 1/2" (12.7), b / h blank; Bolts 5/8" (M16), tolerance 1/16" (1.5875), Middle, 3 x 2, vertical spacing 5" (127), edge 3 1/2" (88.9), horizontal spacing 3 1/2" (88.9), edge 2" (50.8). The video never names its profiles (Tekla imperial defaults, W shapes not in the catalog): HEB300 / IPE300 stand in (NOT from the video).',
        members: [member('COLUMN', { prof: 'HEB300', p1: [0, 0, 0], p2: [0, 0, 7500] }), beam('IPE300', 1), beam('IPE300', -1)],
    conns: [['T142', [0, 1, 2], { ext: '', extb: '', ext2: '', extb2: '', tp: 12.7, epb: '', eph: '', tp2: 12.7, epb2: '', eph2: '',
      bolt: 16, tol: 1.5875, refV: 'Middle', posV: '', e: 88.9, rows: 3, pitches: '', pitch: 127, refH: 'Middle', posH: 0, side: 50.8, cols: 2, gauge: 88.9 }]],
  },
];
