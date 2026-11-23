// S8: the v58 Tekla bolt options checked through End plate 144 (its settings reach stBolts through the v58 wrapper).
const M = [['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7500] }], ['BEAM', { prof: 'IPE360', p1: [0, 0, 7000], p2: [6000, 0, 7000] }]];
const c = q => ({ k: 'T144', members: M, q });
module.exports = {
  bo_stag1: c({ stag: 1 }), bo_stag2: c({ stag: 2 }), bo_stag3: c({ stag: 3 }), bo_stag4: c({ stag: 4, cols: 4, gauge2: 50 }),
  bo_bdel: c({ bdel: '2 5' }), bo_slot: c({ htype: 'Slotted', sl1: 10 }), bo_slot_rot: c({ htype: 'Slotted', sl1: 12, sl2: 4, srot: 1 }),
  bo_over: c({ htype: 'Oversized', sl2: 6 }), bo_tapped: c({ htype: 'Tapped' }), bo_nohole: c({ htype: 'No hole' }),
  bo_nowasher: c({ ba_w: 0 }), bo_nonut: c({ ba_n: 0 }), bo_nobolt: c({ ba_b: 0 }), bo_dir2: c({ bdir: 2 }), bo_blen: c({ blen: 15 }), bo_tol3: c({ tol: 3, bolt: 24 }),
};
