#!/usr/bin/env python3
"""C04 proof (docs/steel/PARITY.md): which side of the purlin R10's purlin cleats are on, and where exactly, from R10's own
Tekla meshes. Prints one row per group: support, purlin rAng, toe direction (uphill / downhill), count, cleat rAng, the cleat's
long-leg range from the web back toward the toes, its height range above the support face, the shift the rule predicts
(an L 60 x 140 x 6 turned by its rAng, its bounding box in the part's Top frame centred on the axis), the stiffener outlines
and the bolt groups.

    python3 tools/golden/r10_c04_proof.py
"""
import gzip, json, math, os, collections
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
d = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R10.ss')))['doc']
B = {b['id']: b for b in d['bodies']}
inp = json.load(open(os.path.join(REPO, 'tests', 'steel', 'golden', 'r10', 'rebuild_inputs.json')))
M = inp['members']; T = [e for e in next(s for s in inp['steps'] if s['op'] == 'connectMany')['list'] if e[0] == 'Tcl']
dot = lambda a, b: sum(x * y for x, y in zip(a, b)); sub = lambda a, b: [x - y for x, y in zip(a, b)]
V = lambda b: [b['shape']['vp'][i:i + 3] for i in range(0, len(b['shape']['vp']), 3)]

def web(p):   # the web side of a C purlin: the side of the section without the lip-end vertices (|u| between 70 and 90)
    s = p['st']; P = V(p); vs = [dot(sub(x, s['p1']), s['v']) for x in P]; us = [dot(sub(x, s['p1']), s['u']) for x in P]
    lo, hi = min(vs), max(vs); lips = [vs[i] for i in range(len(P)) if 70 < abs(us[i]) < 90]
    lip_hi = all(x > (lo + hi) / 2 for x in lips)
    return (lo, 1.0) if lip_hi else (hi, -1.0)   # web back position along v, direction toward the toes

def shift(rang, A=60, H=140, t=6):
    P = [(0, 0), (A, 0), (A, t), (t, t), (t, H), (0, H)]; c, s = math.cos(math.radians(rang)), math.sin(math.radians(rang))
    Q = [(x * c - y * s, x * s + y * c) for x, y in P]; bx = ((min(q[0] for q in Q) + max(q[0] for q in Q)) / 2, (min(q[1] for q in Q) + max(q[1] for q in Q)) / 2)
    oc = (A / 2 * c - H / 2 * s, A / 2 * s + H / 2 * c); dd = (oc[0] - bx[0], oc[1] - bx[1]); return (dd[0] * c + dd[1] * s, -dd[0] * s + dd[1] * c)

stiffs = [b for b in d['bodies'] if (b.get('st') or {}).get('name') == 'Stiff.' and b['st'].get('prof') == 'PL6']
bolts = [b for b in d['bodies'] if (b.get('st') or {}).get('kind') == 'bolts' and b['st'].get('prof') == 'M12']
G = collections.OrderedDict(); side = collections.Counter()
for key, ix, q, row, rid in T:
    p = B[M[ix[1]]['r10'][0]]; s = p['st']; wv, toe = web(p); o = s['p1']
    ax = sub(s['p2'], o); L = math.sqrt(dot(ax, ax)); ax = [x / L for x in ax]
    F = lambda x: ((dot(sub(x, o), s['v']) - wv) * toe, 100 - dot(sub(x, o), s['u']), dot(sub(x, o), ax))   # toward toes, above the support, along the purlin
    c = B[rid[0]]; P = [F(x) for x in V(c)]; cz = (min(x[2] for x in P) + max(x[2] for x in P)) / 2
    side['toe side' if min(x[0] for x in P) >= 59.9 else 'behind the web'] += 1
    up = 'uphill' if s['v'][2] * toe > 1e-9 else 'downhill' if s['v'][2] * toe < -1e-9 else 'level'
    k = (M[ix[0]]['group'], M[ix[0]]['st']['name'], round(s['rAng'], 2), up)
    g = G.setdefault(k, {'n': 0, 'two': 0, 'rAng': c['st']['rAng'], 'leg': collections.Counter(), 'stiff': collections.Counter(), 'bolts': collections.Counter()})
    g['n'] += 1; g['two'] += len(ix) == 3
    g['leg'][(round(min(x[0] for x in P), 1), round(max(x[0] for x in P), 1), round(min(x[1] for x in P), 1), round(max(x[1] for x in P), 1))] += 1
    cc = [sum(x[i] for x in V(c)) / len(V(c)) for i in range(3)]
    st = min(stiffs, key=lambda b: math.dist(b['st']['pts3'][0], cc))['st']
    g['stiff'][tuple((round(F(x)[0] - 60, 1), round(F(x)[1], 1), round(F(x)[2] - cz, 1)) for x in st['pts3'])] += 1
    for b in bolts:
        if math.dist(b['st']['pts'][0], cc) < 200:
            g['bolts'][(b['st']['prof'], b['st']['grade'], b['st']['L'], tuple(sorted((round(F(x)[0], 1), round(F(x)[1], 1)) for x in b['st']['pts'])), round(dot(b['st']['ax'], s['v']) * toe, 3))] += 1
print('side of the web:', dict(side))
for k, g in G.items():
    print(f"\n{k}: {g['n']} cleats ({g['two']} with two purlins), cleat rAng {g['rAng']}, rule shift (toe, down) {tuple(round(v, 1) for v in shift(g['rAng']))}")
    print('  long leg from the web back / height above the support:', dict(g['leg']))
    print('  stiffener (from the toe line, height, along from the cleat centre):', dict(g['stiff']))
    print('  bolt groups (size, grade, L, (from web back, height), axis toward the toes):', dict(g['bolts']))
