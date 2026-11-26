#!/usr/bin/env python3
"""Measured geometry checks for connections (owner standing rule B, 2026-11-25; docs/steel/PARITY.md). Images are for the owner;
these numbers decide. A check fails on any of:

  1 CONTACT   designed bearing pairs (C04 Tcl: purlin on rafter, cleat on purlin, cleat on rafter; Tpc: purlin on rafter) have a gap
              > 1 mm (Manifold min_gap: the closest distance between the solids, i.e. perpendicular to the contact face for faces in
              contact) - or they overlap (check 3)
  2 FLOATING  a connection part (plate / angle a component made; in an .ss model every detail plate) neither touches (<= 1 mm) nor is
              bolted to another part of its connection or of its own assembly
  3 OVERLAP   two non-bolt parts intersect with a mean depth (2 x volume / surface area of the intersection) of 0.01 mm or more
              (shallower = contact: float32 storage of touching faces); within a connection, across connections at a joint, and
              (.ss) between any two parts of the model
  4 BOLTS     a bolt does not pass a hole in every part it joins (tools/golden/bolt_hole_check.py rules), or its grip differs from the
              material stack along the bolt by more than 2 mm

    python3 tools/golden/geom_checks.py <model.ss | workflow dump .json> [--out report.json] [--md report.md]

Model forms: an .ss (bodies with shape vp / tv, stConns: TKBOLT / TKCUT and component connections) or a dump from
tools/golden/c04_workflow.js / C04WorkflowTest (bodies with m.v / m.t, conns with type / parts / q).
"""
import gzip, json, os, sys
from collections import Counter, defaultdict
import numpy as np
import manifold3d as mf

sys.path.insert(0, os.path.dirname(__file__))
import bolt_hole_check as H

TOUCH = 1.0; OVER = 1.0; DEPTH = 0.01; GRIP = 2.0   # rule 3 (2026-11-26): an overlap under 0.01 mm mean depth is contact
STATS = Counter()

def load(path):
    if path.endswith('.ss'):
        doc = json.load(gzip.open(path))['doc']; bodies = {}
        for b in doc['bodies']:
            s = b.get('shape') or {}
            if 'vp' not in s or not s.get('tv'): continue
            bodies[b['id']] = dict(id=b['id'], st=b.get('st') or {}, V=np.asarray(s['vp'], float).reshape(-1, 3), T=np.asarray(s['tv'], int).reshape(-1, 3))
        return bodies, doc.get('stConns') or [], 'ss'
    d = json.load(open(path)); bodies = {}
    for b in d['bodies']:
        st = dict(b.get('st') or {}); st.setdefault('name', b.get('name')); st.setdefault('kind', b.get('kind'))
        bodies[str(b['id'])] = dict(id=str(b['id']), st=st, V=np.asarray(b['m']['v'], float).reshape(-1, 3), T=np.asarray(b['m']['t'], int).reshape(-1, 3))
    conns = [dict(c, id=str(c['id']), parts=[str(p) for p in c['parts']]) for c in d['conns']]
    return bodies, conns, 'dump'

_man = {}
def man(b):
    k = b['id']
    if k not in _man:
        try:
            m = mf.Manifold(mf.Mesh64(np.ascontiguousarray(b['V'], np.float64), np.ascontiguousarray(b['T'], np.uint64)))   # 64-bit: float32 at 20 m from the origin is 2 µm, i.e. mm³ on a contact face
            _man[k] = m if m.status() == mf.Error.NoError and not m.is_empty() else None
        except Exception: _man[k] = None
    return _man[k]

def gap(a, b):
    ma, mb = man(a), man(b)
    if ma is None or mb is None: return None
    return float(ma.min_gap(mb, 50.0))      # 50 = search length: larger gaps report as 50

def overlap(a, b):
    """volume of the intersection, or 0 when it is contact (mean depth < DEPTH)"""
    ma, mb = man(a), man(b)
    if ma is None or mb is None: return None
    i = ma ^ mb; v = float(i.volume())
    if v <= 0: return 0.0
    ar = i.surface_area()
    if ar > 0 and 2 * v / ar < DEPTH: STATS['contacts (overlap < 0.01 mm deep)'] += 1; return 0.0
    return v

def depth(a, b):
    """mean thickness of the overlap solid: 2 x volume / its surface area (a thin slab: its thickness), mm"""
    i = man(a) ^ man(b); ar = i.surface_area()
    return round(2 * i.volume() / ar, 4) if ar > 0 else 0.0

def name(b): st = b['st']; return f"{st.get('name')} {st.get('tprof') or st.get('prof') or ''}".strip()

def perp(a):
    x = np.cross(a, [0, 0, 1.0]) if abs(a[2]) < 0.9 else np.cross(a, [1.0, 0, 0]); x /= np.linalg.norm(x); y = np.cross(a, x)
    return [x, -x, y, -y]

def stack_and_holes(bolt_pts, ax, d, tol, parts, L):
    """per bolt: (material length along the bolt line through the parts, [(part, HOLE / NO HOLE / MISSES)])"""
    out = []
    a = np.asarray(ax, float); a = a / (np.linalg.norm(a) or 1)
    for P in bolt_pts:
        P = np.asarray(P, float); s0, s1 = -0.65 * d - 200, L + 200; mat = 0.0; res = []; span = []
        for p in parts:
            hits = H.line_hits(p['V'], p['T'], P, a, s0, s1)
            n, lo, hi = H.ring(p['V'], P, a, -0.65 * d, L, d / 2 - 0.5, d / 2 + tol + 3)
            # material along the line, as the hole wall: the ring's extent along the bolt (the hole passes the plate's thickness)
            beside = [H.line_hits(p['V'], p['T'], P + u * (d / 2 + tol + 3), a, -0.65 * d - 50, L + 50) for u in perp(a)]
            if len(H.line_hits(p['V'], p['T'], P, a, -0.65 * d, L)): res.append((p, 'NO HOLE'))
            elif n >= 6 or sum(len(h) >= 2 for h in beside) >= 3:     # a hole wall, or solid plate all round the empty bolt line
                res.append((p, 'HOLE'))
                # the plate's thickness at the hole: a line parallel to the bolt just outside the hole (through solid plate), entry to exit
                best = None
                for u in perp(a):
                    h2 = H.line_hits(p['V'], p['T'], P + u * (d / 2 + tol + 3), a, -0.65 * d - 50, L + 50)
                    if len(h2) >= 2:
                        th = sum(h2[i + 1] - h2[i] for i in range(0, len(h2) - 1, 2)); best = th if best is None else max(best, th); span += [float(h2[0]), float(h2[-1])]
                mat += best or 0.0
            else: res.append((p, 'MISSES'))
        out.append((mat, res, (max(span) - min(span)) if span else 0.0))
    return out

DESIGNED = {'Tcl': [('PURLIN', 'RAFTER'), ('CLEAT', 'PURLIN'), ('CLEAT', 'RAFTER')], 'Tpc': [('PURLIN', 'RAFTER')],
            'T146': [('SHEAR TAB', 'RAFTER'), ('SHEAR TAB', 'SECONDARY')]}   # C14: tab welded to the main part, bolted to the secondary's web

def role(b, conn, bodies):
    st = b['st']; n = (st.get('name') or '').upper()
    if st.get('kind') == 'bolts': return 'BOLT'
    if 'PURLIN' in n: return 'PURLIN'
    if st.get('kind') == 'member' and conn and conn['parts'] and b['id'] == conn['parts'][0]: return 'RAFTER'
    if st.get('kind') == 'member' and conn and conn['type'] == 'T146': return 'SECONDARY'
    if 'CLEAT' in n and 'STIFF' not in n: return 'CLEAT'
    return n or 'PART'

def check(path):
    bodies, conns, form = load(path); fails = []
    kids = defaultdict(list)
    for b in bodies.values():
        c = b['st'].get('conn')
        if c is not None: kids[str(c)].append(b)
    bolted = defaultdict(set)
    for c in conns:
        if c['type'] == 'TKBOLT':
            for p in c['parts']: bolted[p].update(c['parts'])
        for k in kids.get(str(c['id']), []):
            if k['st'].get('kind') == 'bolts': bolted[k['id']] = set()
    for c in conns:
        cid = str(c['id']); t = c['type']
        if t == 'TKCUT': continue
        mem = [bodies[p] for p in c['parts'] if p in bodies]; plates = [k for k in kids.get(cid, []) if k['st'].get('kind') != 'bolts']
        bolts = [k for k in kids.get(cid, []) if k['st'].get('kind') == 'bolts']
        everything = mem + plates
        # 1 contact (designed pairs)
        for ra, rb in DESIGNED.get(t, []):
            A = [x for x in everything if role(x, c, bodies) == ra]; B = [x for x in everything if role(x, c, bodies) == rb]
            for x in A:
                gs = [g for g in (gap(x, y) for y in B) if g is not None]; STATS['contact pairs'] += 1
                if gs and min(gs) > TOUCH: fails.append(dict(check='CONTACT', conn=cid, type=t, part=name(x), other=rb, value=round(min(gs), 2)))
        # 2 floating (component plates: touch or bolted to a part of the connection)
        for x in plates:
            STATS['plates'] += 1
            ok = any((g is not None and g <= TOUCH) for g in (gap(x, y) for y in everything if y is not x))
            if not ok and not any(x['id'] in [str(p) for p in (bb['st'].get('parts') or [])] for bb in bolts):
                fails.append(dict(check='FLOATING', conn=cid, type=t, part=name(x), value=None))
        # 3 overlap
        for i in range(len(everything)):
            for j in range(i + 1, len(everything)):
                v = overlap(everything[i], everything[j]); STATS['overlap pairs'] += 1
                if v is not None and v > OVER: fails.append(dict(check='OVERLAP', conn=cid, type=t, part=name(everything[i]), other=name(everything[j]), value=round(v, 1), depth=depth(everything[i], everything[j]), ids=[everything[i]['id'], everything[j]['id']]))
        # 4 bolts
        groups = []
        if t == 'TKBOLT':
            q = c['q']; L = float(q.get('grip') or 0) + 2 * float(q.get('d') or 12)
            g = next((k for k in bodies.values() if k['st'].get('kind') == 'bolts' and str(k['st'].get('conn')) == cid), None)
            if g and g['st'].get('L'): L = float(g['st']['L'])
            groups.append((q.get('pts') or [], q.get('ax'), float(q.get('d') or 12), float(q.get('tol') if q.get('tol') is not None else 2), None if q.get('holesOnly') else float(q.get('grip') or 0), L, mem))   # holes only: no bolt, no grip
        for bb in bolts:
            st = bb['st']; d = float(st.get('d') or 12); Lb = float(st.get('L') or 0)
            groups.append((st.get('pts') or [], st.get('ax'), d, 2.0, None, Lb, everything))
        for pts, ax, d, tol, grip, L, parts in groups:
            if not pts or ax is None: continue
            for mat, res, span in stack_and_holes(pts, ax, d, tol, parts, L):
                STATS['bolts'] += 1
                for p, r in res:
                    if t != 'TKBOLT' and r == 'MISSES': continue       # a component bolt group's parts: only the ones it passes
                    if r != 'HOLE': fails.append(dict(check='BOLTS', conn=cid, type=t, part=name(p), value=r))
                if grip is not None and abs(grip - mat) > GRIP: fails.append(dict(check='BOLTS', conn=cid, type=t, part='grip', value=f'grip {grip:.1f} vs stack {mat:.1f}'))
                elif grip is None and t != 'TKBOLT' and span - mat > GRIP: fails.append(dict(check='BOLTS', conn=cid, type=t, part='grip', value=f'air {span - mat:.1f} along the shank (stack {mat:.1f})'))
    # 3 overlap across connections at one joint: plates of two connections that share a member (e.g. two cleats on one purlin end)
    by_member = defaultdict(list)
    for c in conns:
        if c['type'] in ('TKCUT', 'TKBOLT'): continue
        for p in c['parts']: by_member[p].append(str(c['id']))
    seen = set()
    for p, cs in by_member.items():
        for i in range(len(cs)):
            for j in range(i + 1, len(cs)):
                if (cs[i], cs[j]) in seen: continue
                seen.add((cs[i], cs[j]))
                A = [k for k in kids.get(cs[i], []) if k['st'].get('kind') != 'bolts']; B = [k for k in kids.get(cs[j], []) if k['st'].get('kind') != 'bolts']
                for x in A:
                    for y in B:
                        v = overlap(x, y); STATS['overlap pairs'] += 1
                        if v is not None and v > OVER: fails.append(dict(check='OVERLAP', conn=f'{cs[i]}+{cs[j]}', type='joint', part=name(x), other=name(y), value=round(v, 1)))
    # 2 floating, .ss models: every detail plate touches a part of its assembly (its main part included) or is bolted
    if form == 'ss':
        by_asm = defaultdict(list)
        for b in bodies.values():
            if b['st'].get('asm') is not None and b['st'].get('kind') != 'bolts': by_asm[b['st']['asm']].append(b)
        for a in list(by_asm):
            if a in bodies: by_asm[a].append(bodies[a])
        for b in bodies.values():
            st = b['st']
            if st.get('kind') != 'plate' or b['id'] in bolted: continue
            if st.get('asm') is None and not by_asm.get(b['id']): continue     # a single-part assembly: nothing to hold on to inside it
            near = [y for y in by_asm.get(st.get('asm') if st.get('asm') is not None else b['id'], []) if y is not b]
            if not any((g is not None and g <= TOUCH) for g in (gap(b, y) for y in near)):
                fails.append(dict(check='FLOATING', conn=None, type='assembly', part=name(b), value=st.get('amark')))
    # 3 overlap, .ss models: any two parts (bolts excluded) whose solids intersect by more than 1 mm³, wherever they are
    if form == 'ss' and '--no-all-pairs' not in sys.argv:
        ids = [k for k, b in bodies.items() if b['st'].get('kind') in ('plate', 'member')]
        B = np.array([np.r_[bodies[k]['V'].min(0), bodies[k]['V'].max(0)] for k in ids])
        for i in range(len(ids)):
            hit = np.nonzero(~((B[i + 1:, :3] > B[i, 3:]).any(1) | (B[i + 1:, 3:] < B[i, :3]).any(1)))[0] + i + 1
            for j in hit:
                x, y = bodies[ids[i]], bodies[ids[j]]; v = overlap(x, y); STATS['model overlap pairs'] += 1
                if v is not None and v > OVER:
                    same = x['st'].get('amark') == y['st'].get('amark') and (x['st'].get('asm') or x['id']) == (y['st'].get('asm') or y['id'])
                    fails.append(dict(check='OVERLAP', conn=None, type='model', part=name(x), other=name(y), value=round(v, 1), depth=depth(x, y), ids=[x['id'], y['id']], sameAsm=same))
        STATS['parts without a valid solid'] = sum(1 for k in ids if man(bodies[k]) is None)
    return fails, len(conns)

def main():
    path = sys.argv[1]; fails, n = check(path)
    c = Counter((f['check'], f['type'], f['part'], str(f.get('other', ''))) for f in fails)
    print(f'{path}: {n} connections, {len(fails)} failures; checked: {dict(STATS)}')
    for k, v in sorted(c.items(), key=lambda x: (-x[1], x[0])): print(f'  {v:5d}  {k[0]:9s} {k[1]:8s} {k[2]} {("/ " + k[3]) if k[3] else ""}')
    if '--out' in sys.argv: json.dump(fails, open(sys.argv[sys.argv.index('--out') + 1], 'w'), indent=0, default=str)
    if '--md' in sys.argv:
        L = [f'| check | connection type | part | other / value | count |', '|---|---|---|---|---|']
        for k, v in sorted(c.items(), key=lambda x: (-x[1], x[0])): L.append(f'| {k[0]} | {k[1]} | {k[2]} | {k[3]} | {v} |')
        open(sys.argv[sys.argv.index('--md') + 1], 'w').write('\n'.join(L) + '\n')

if __name__ == '__main__': main()
