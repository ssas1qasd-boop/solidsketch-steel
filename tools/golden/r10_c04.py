#!/usr/bin/env python3
"""C04 golden (purlin cleat Tcl): the 528 R10 parts (264 'Cleat Angle' L140*60*6 + 264 'Stiff.' PL6*44) and their 528 M12 bolt
groups' positions, keyed by the Tcl entry of tests/steel/golden/r10/rebuild_inputs.json (tools/golden/r10_inputs.py).

    python3 tools/golden/r10_c04.py   -> tests/steel/golden/r10/c04_r10.json

Boxes and volumes are R10's own solids as web v69 opens them (r10_parts_v69.json, = the Tekla meshes stored in R10); bolt
points from R10's bolt groups (TKBOLT). Checked by the app (R10C04Test: app vs R10, 0.1 mm) and by r10_compare.py (web vs R10).
Each stiffener and bolt group belongs to the cleat nearest to it (R10 has no link between them).
"""
import gzip, json, math, os
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MODEL = os.environ.get('SS_MODEL', 'R12')
G = os.path.join(REPO, 'tests', 'steel', 'golden', MODEL.lower())
d = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', f'Frame_28x53m_{MODEL}.ss')))['doc']
B = {b['id']: b for b in d['bodies']}
parts = {b['id']: b for b in json.load(open(os.path.join(G, 'r10_parts_v69.json')))['bodies']}
inp = json.load(open(os.path.join(G, 'rebuild_inputs.json')))
lst = next(s for s in inp['steps'] if s['op'] == 'connectMany')['list']
def ctr(box): return [(box[i] + box[i + 3]) / 2 for i in range(3)]
stiffs = [b for b in d['bodies'] if (b.get('st') or {}).get('name') == 'Stiff.' and b['st'].get('prof') == 'PL6']
bolts = [b for b in d['bodies'] if (b.get('st') or {}).get('kind') == 'bolts' and b['st'].get('prof') == 'M12']
tcl = [(i, e) for i, e in enumerate(lst) if e[0] == 'Tcl']
cc = {e[4][0]: ctr(parts[e[4][0]]['box']) for _, e in tcl}
owner = lambda p: min(cc, key=lambda k: math.dist(cc[k], p))
st_of, bo_of = {}, {}
for s in stiffs: st_of.setdefault(owner(ctr(parts[s['id']]['box'])), []).append(s['id'])
for b in bolts:
    c = owner(b['st']['pts'][0])
    if math.dist(cc[c], b['st']['pts'][0]) < 200: bo_of.setdefault(c, []).append(b['id'])
out = []
for i, e in tcl:
    cid = e[4][0]; sid = st_of.get(cid, [])
    assert len(sid) == 1 and len(bo_of.get(cid, [])) in (1, 2), (cid, sid, bo_of.get(cid))
    P = lambda bid: {'id': bid, 'name': B[bid]['st']['name'], 'prof': B[bid]['st'].get('tprof') or B[bid]['st']['prof'], 'box': parts[bid]['box'], 'volume': parts[bid]['volume']}
    out.append({'entry': i, 'members': e[1], 'cleat': P(cid), 'stiff': P(sid[0]),
                'bolts': sorted([[round(v, 3) for v in p] for bid in bo_of[cid] for p in B[bid]['st']['pts']])})
json.dump({'source': 'R10 (tests/steel/models/Frame_28x53m_R10.ss) via tools/golden/r10_c04.py: C04 purlin cleats, keyed by rebuild_inputs.json connectMany entries',
           'count': {'connections': len(out), 'parts': 2 * len(out), 'bolts': sum(len(o['bolts']) for o in out)}, 'cleats': out},
          open(os.path.join(G, f'c04_{MODEL.lower()}.json'), 'w'), indent=None, separators=(',', ':'))
print(len(out), 'connections,', 2 * len(out), 'parts,', sum(len(o['bolts']) for o in out), 'bolts')
