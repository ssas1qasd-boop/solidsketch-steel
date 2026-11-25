#!/usr/bin/env python3
"""Bolt check: every bolt must pass through a hole in each part it joins.

    python3 tools/golden/bolt_hole_check.py                    R10 (tests/steel/models/Frame_28x53m_R10.ss): every TKBOLT
    python3 tools/golden/bolt_hole_check.py --report           ... and write docs/steel/BOLT-HOLES.md + tests/steel/golden/r10/bolt_holes.json
    python3 tools/golden/bolt_hole_check.py --model <file.ss>   another model (e.g. tests/steel/models/Frame_28x53m_R11.ss)

For each bolt (point P on the head side, axis a, diameter d, from the connection; length L from its bolt group body, else
grip + 2·d) and each part the connection joins (TKBOLT q.parts; the parts in q.skip are not drilled by the component because
R10's Tekla meshes already carry their holes), along the bolt's shank s in [-0.65·d, L]:
  HOLE      the line passes the part without touching material, and the part has a ring of vertices around the line
            (distance d/2 - 0.5 … d/2 + tol + 3: the hole's wall) -> pass
  NO HOLE   the line hits the part's material (a triangle of its mesh) -> fail: the bolt goes through solid steel
  MISSES    the line neither hits the part nor passes a hole in it -> fail: the bolt does not reach this part
Also reported (not a failure): the air along the shank between the first and the last part it passes (gap), which shows
bolts that cross an open section (the C04 cleat on the toe side: 58 mm of air inside the C).
Deterministic; numpy.
"""
import gzip, json, math, os, sys
from collections import Counter, defaultdict
import numpy as np

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
R10 = os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R10.ss')

def mesh(b):
    s = b.get('shape') or {}
    if 'vp' not in s or 'tv' not in s: return None
    V = np.asarray(s['vp'], float).reshape(-1, 3); T = np.asarray(s['tv'], int).reshape(-1, 3)
    return V, T

def line_hits(V, T, P, a, s0, s1):
    """parameters s in [s0, s1] where the line P + s·a crosses a triangle (Möller–Trumbore, vectorised)"""
    v0, v1, v2 = V[T[:, 0]], V[T[:, 1]], V[T[:, 2]]; e1, e2 = v1 - v0, v2 - v0
    h = np.cross(a, e2); det = (e1 * h).sum(1); ok = np.abs(det) > 1e-12; inv = np.where(ok, 1.0 / np.where(ok, det, 1), 0)
    sv = P - v0; u = (sv * h).sum(1) * inv; q = np.cross(sv, e1); v = (q * a).sum(1) * inv; t = (e2 * q).sum(1) * inv
    m = ok & (u >= -1e-9) & (v >= -1e-9) & (u + v <= 1 + 1e-9) & (t >= s0) & (t <= s1)
    return np.sort(t[m])

def ring(V, P, a, s0, s1, r0, r1):
    w = V - P; s = w @ a; perp = np.linalg.norm(w - np.outer(s, a), axis=1)
    m = (s >= s0 - 1) & (s <= s1 + 1) & (perp >= r0) & (perp <= r1)
    return int(m.sum()), (s[m].min() if m.any() else None), (s[m].max() if m.any() else None)

def check(doc, conns=None):
    B = {b['id']: b for b in doc['bodies']}
    groups = defaultdict(list)
    for b in doc['bodies']:
        st = b.get('st') or {}
        if st.get('kind') == 'bolts' and st.get('conn') is not None: groups[st['conn']].append(st)
    cache = {}
    def M(i):
        if i not in cache: cache[i] = mesh(B[i]) if i in B else None
        return cache[i]
    res = []
    for c in doc.get('stConns') or []:
        if c.get('type') != 'TKBOLT' or (conns and c['id'] not in conns): continue
        q = c.get('q') or {}; a = np.asarray(q.get('ax') or [0, 0, 1], float); a = a / (np.linalg.norm(a) or 1)
        d = float(q.get('d') or 12); tol = float(q.get('tol') if q.get('tol') is not None else 2)
        g = groups.get(c['id']); L = float(g[0].get('L')) if g and g[0].get('L') else float(q.get('grip') or 0) + 2 * d
        for P in q.get('pts') or []:
            P = np.asarray(P, float); s0, s1 = -0.65 * d, L; spans = []
            for pid in c.get('parts') or []:
                m = M(pid); name = ((B.get(pid) or {}).get('st') or {}).get('name', '?')
                if m is None: res.append(dict(conn=c['id'], part=pid, name=name, P=P.round(1).tolist(), result='NO MESH')); continue
                V, T = m; hits = line_hits(V, T, P, a, s0, s1)
                n, r_lo, r_hi = ring(V, P, a, s0, s1, d / 2 - 0.5, d / 2 + tol + 3)
                if len(hits): r = 'NO HOLE'; spans.append((hits.min(), hits.max()))
                elif n >= 6: r = 'HOLE'; spans.append((r_lo, r_hi))
                else: r = 'MISSES'
                res.append(dict(conn=c['id'], part=pid, name=name, prof=((B.get(pid) or {}).get('st') or {}).get('tprof') or ((B.get(pid) or {}).get('st') or {}).get('prof'),
                                d=d, P=P.round(1).tolist(), result=r, ring=n))
            if len(spans) >= 2:
                spans.sort(); air = sum(max(0, spans[i + 1][0] - spans[i][1]) for i in range(len(spans) - 1))
                for r in res[-len(c.get('parts') or []):]: r['air'] = round(float(air), 1)
    return res

def main():
    model = sys.argv[sys.argv.index('--model') + 1] if '--model' in sys.argv else R10
    doc = json.load(gzip.open(model))['doc']; res = check(doc)
    cnt = Counter(r['result'] for r in res); bad = [r for r in res if r['result'] != 'HOLE']
    print(f"R10 TKBOLT: {len({r['conn'] for r in res})} connections, {len(res)} bolt × part checks: {dict(cnt)}")
    by = Counter((r['result'], r['name'], r.get('prof')) for r in bad)
    for k, n in by.most_common(40): print(f'  {n:5d}  {k[0]:8s} {k[1]} {k[2]}')
    air = Counter(round(r.get('air', 0)) for r in res if r.get('air', 0) > 5)
    print('bolts with air along the shank > 5 mm (mm: bolt×part checks):', dict(sorted(air.items())))
    if '--report' in sys.argv:
        out = os.path.join(REPO, 'tests', 'steel', 'golden', 'r10', 'bolt_holes.json')
        json.dump({'source': 'tools/golden/bolt_hole_check.py', 'counts': dict(cnt), 'failures': bad}, open(out, 'w'), indent=0)
        L = ['# Bolt check: every bolt through a hole in each part it joins (R10)', '',
             'Generated by `python3 tools/golden/bolt_hole_check.py --report` (rules in the script header). Data: `tests/steel/golden/r10/bolt_holes.json`.', '',
             f"R10: {len({r['conn'] for r in res})} TKBOLT connections, {len(res)} bolt × part checks: " + ', '.join(f'{k} {v}' for k, v in sorted(cnt.items())) + '.', '',
             '| result | part | profile | checks | connections |', '|---|---|---|---|---|']
        cs = defaultdict(set)
        for r in bad: cs[(r['result'], r['name'], r.get('prof'))].add(r['conn'])
        for k, n in by.most_common(): L.append(f'| {k[0]} | {k[1]} | {k[2]} | {n} | {len(cs[k])} (e.g. {", ".join(str(x) for x in sorted(cs[k])[:5])}) |')
        L += ['', 'Bolts with air along the shank between the parts (> 5 mm), bolt × part checks by mm: ' + ', '.join(f'{k} mm: {v}' for k, v in sorted(air.items())) + '.', '']
        open(os.path.join(REPO, 'docs', 'steel', 'BOLT-HOLES.md'), 'w').write('\n'.join(L))

if __name__ == '__main__': main()
