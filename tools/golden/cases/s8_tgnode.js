// S8: v54 TG node gusset (several Ttg on one node of a main part share one GUSSET PLATE). Members: main part first, then the
// braces; the case's component joins the first n members, `more` / `pre` add the other braces' Ttg after / before it.
const COL = ['COLUMN', { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7000] }];
const BR = (p1, p2, prof) => ['BRACING', { prof: prof || 'CHS88.9x4', p1, p2 }];
module.exports = {
  tgnode_two: { k: 'Ttg', n: 2, members: [COL, BR([150, 0, 3000], [4150, 0, 6000]), BR([150, 0, 3000], [4150, 0, 0])], q: {}, more: [['Ttg', [0, 2], {}]] },
  tgnode_three_odd: { k: 'Ttg', n: 2, members: [['BEAM', { prof: 'IPE400', p1: [0, 0, 6000], p2: [8000, 0, 6000] }], BR([4000, 0, 5600], [1000, 0, 2600], 'RHS120x80x5'), BR([4000, 0, 5600], [4000, 0, 2600], 'SHS100x5'), BR([4000, 0, 5600], [7000, 0, 2600], 'RHS120x80x5')], q: {}, more: [['Ttg', [0, 2], {}], ['Ttg', [0, 3], {}]] },
  tgnode_skewed: { k: 'Ttg', n: 2, members: [COL, BR([150, 0, 3000], [3150, 2000, 5500]), BR([150, 0, 3000], [3150, -1500, 5000])], q: {}, more: [['Ttg', [0, 2], {}]] },
  tgnode_sloped: { k: 'Ttg', n: 2, members: [['RAFTER', { p1: [0, 0, 6000], p2: [8000, 0, 6700] }], BR([4000, 0, 5900], [1000, 0, 2600]), BR([4000, 0, 5900], [7000, 0, 2600])], q: {}, more: [['Ttg', [0, 2], { side: -1 }]] },
  tgnode_not_lead: { k: 'Ttg', n: 2, members: [COL, BR([150, 0, 3000], [4150, 0, 6000]), BR([150, 0, 3000], [4150, 0, 0])], q: {}, pre: [['Ttg', [0, 2], {}]] },
  tgnode_far: { k: 'Ttg', n: 2, members: [COL, BR([150, 0, 3000], [4150, 0, 6000]), BR([150, 0, 1000], [4150, 0, -2000])], q: {}, more: [['Ttg', [0, 2], {}]] },
  tgnode_nodeR: { k: 'Ttg', n: 2, members: [COL, BR([150, 0, 3000], [4150, 0, 6000]), BR([150, 0, 2400], [4150, 0, -600])], q: { nodeR: 800 }, more: [['Ttg', [0, 2], { nodeR: 800 }]] },
  tgnode_off: { k: 'Ttg', n: 2, members: [COL, BR([150, 0, 3000], [4150, 0, 6000]), BR([150, 0, 3000], [4150, 0, 0])], q: { node: 0 }, more: [['Ttg', [0, 2], {}]] },
  tgnode_options: { k: 'Ttg', n: 2, members: [COL, BR([150, 0, 3000], [4150, 0, 6000], 'CHS114.3x5'), BR([150, 0, 3000], [4150, 0, 0])], q: { t: 15, tf: 15, bolt: 20, clear: 30, cap: 0 }, more: [['Ttg', [0, 2], { fw: 200, e: 50 }]] },
};
