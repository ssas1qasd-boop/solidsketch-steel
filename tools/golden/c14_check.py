#!/usr/bin/env python3
"""C14 member bodies vs R12: at each of the 68 joints, the joist's and the girder's solid (web v70: build/c14/web_bodies.json from
tools/golden/c14_bodies.js; app: build/c14/app_bodies.json from C14BodiesTest) inside a box 300 mm round the R12 tab, against R12's
own plates of that joist / girder (their union). Same = symmetric difference <= 0.1 mm x the piece's surface area (a face 0.1 mm off).
    python3 tools/golden/c14_check.py [build/c14/web_bodies.json]"""
import gzip, json, os, sys, collections
import numpy as np, manifold3d as mf
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
def man(V, T): return mf.Manifold(mf.Mesh64(np.ascontiguousarray(np.asarray(V, float).reshape(-1, 3)), np.ascontiguousarray(np.asarray(T, np.uint64).reshape(-1, 3))))
def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, 'build', 'c14', 'web_bodies.json')
    W = json.load(open(path)); wb = {str(b['id']): b for b in W['bodies']}
    D = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; B = {b['id']: b for b in D['bodies']}
    inp = json.load(open(os.path.join(REPO, 'tests', 'steel', 'golden', 'r12', 'rebuild_inputs.json')))
    lst = [e for e in next(s for s in inp['steps'] if s['op'] == 'connectMany')['list'] if e[0] == 'T146' and e[3] == '14']
    R = lambda i: man(B[i]['shape']['vp'], B[i]['shape']['tv'])
    res = collections.Counter(); worst = {}
    for e in lst:
        tab = B[e[4][0]]; tv = np.asarray(tab['shape']['vp']).reshape(-1, 3); lo, hi = tv.min(0) - 300, tv.max(0) + 300
        box = mf.Manifold.cube(tuple(float(x) for x in hi - lo)).translate(tuple(float(x) for x in lo))
        for role, mi in (('girder', e[1][0]), ('joist', e[1][1])):
            r12 = None
            for pid in inp['members'][mi]['r10']: m = R(pid); r12 = m if r12 is None else r12 + m
            r12 = r12 ^ box; b = wb[str(W['members'][str(mi)])]; ours = man(b['m']['v'], b['m']['t']) ^ box
            x = (r12 - ours).volume() + (ours - r12).volume(); tol = 0.1 * max(r12.surface_area(), 1)
            ok = x <= tol; res[(role, ok)] += 1; worst[role] = max(worst.get(role, 0), x / max(r12.surface_area(), 1))
    print('C14 member bodies vs R12 (box 300 mm round each tab):', dict(res), 'worst mean deviation (mm):', {k: round(v, 4) for k, v in worst.items()})
    return res
if __name__ == '__main__': main()
