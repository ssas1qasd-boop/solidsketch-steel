#!/usr/bin/env python3
"""R11 = R10 (tests/steel/models/Frame_28x53m_R10.ss, unchanged) with the R10 data errors proven from Tekla's own package
(tests/steel/tekla/Frame_28mx53m_Package_1.zip: drawings, NC, bolt list, db1) corrected. Each fix is one function below, with its
proof; docs/steel/PARITY.md ("R11") lists them. Deterministic (same input -> same bytes).

    python3 tools/golden/r11_build.py [fix ...]      default: every fix in FIXES -> tests/steel/models/Frame_28x53m_R11.ss

Fix c04 (purlin cleats 8503 L140*60*6, stiffeners 8504 PL6*44, their M12 bolts):
  Proof: Tekla bolt list "4.6 BOLT 12X30 x 1056" (R10: L144, the bolts crossing 58-72 mm of air inside the C); assembly drawing
  R3022 (front view and sections B-B ... F-F): the cleat's short leg (60) lies on the rafter flange (weld 5), the tall leg (140)
  stands against the purlin web (bolts through the web), the stiffener 8504 sits in the cleat's corner across the purlin on the
  rafter web line, holes 40 / 100 above the rafter top and +-35 from the rafter web; single parts 8503 / 8504 and their NC files
  (8503: holes 4 x 14 at x 25 / 95, y 40 / 100 in the 140 leg; 8504: outline (0,0) (114,0) (124,10) (124,44) (114,44) (0,10)).
  R10 (importer) had the angle on the purlin's toe line upside down (short leg at the top) and the stiffener turned 90 deg.
  New geometry, in each purlin's frame (s = from the purlin web's back face toward its toes, h = above the purlin bottom = the
  rafter top, z = along the purlin from the cleat's centre; the frame, the purlins and every hole in them are R10's):
    cleat      L: tall leg s -6..0, h 0..140; short leg s -60..0, h 0..6; z -60..60; 4 holes d 14 through the tall leg at
               h 40 / 100, z -35 / +35 (8503 NC)
    stiffener  6 thick, z -3..3, outline (a along the short leg from the tall leg's inner face, b up the tall leg from the short
               leg's inner face): (0,124) (0,10) (10,0) (44,0) (44,10) (10,124) -> s = -6 - a, h = 6 + b (8504 NC)
    bolts      R10's bolt lines (h, z kept), head at the cleat's outer face s = -6, axis toward the web; M12 4.6, L 30, grip 8
               (cleat 6 + web 2), the connection's q.pts / ax / grip likewise
"""
import gzip, json, math, os, sys, copy
import numpy as np
import manifold3d as mf

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
R10 = os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R10.ss')
R11 = os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R11.ss')

def r3(v): return round(float(v) + 0.0, 3)
def unit(v): v = np.asarray(v, float); return v / np.linalg.norm(v)

def shape_of(man):
    m = man.to_mesh(); vp = np.asarray(m.vert_properties)[:, :3]; tv = np.asarray(m.tri_verts, int)
    return {'vp': [round(float(x), 3) for x in vp.reshape(-1)], 'tv': [int(x) for x in tv.reshape(-1)]}

def frame_map(man, O, S, H, Z):
    """local (s, h, z) -> world O + s*S + h*H + z*Z (right-handed or not: mirror handled by Manifold's transform)"""
    M = np.column_stack([S, H, Z, O])   # 3x4
    return man.transform(M.tolist())

def prism(poly2d, z0, z1):
    cs = mf.CrossSection([[(float(x), float(y)) for x, y in poly2d]], mf.FillRule.Positive)
    if cs.area() < 0: cs = mf.CrossSection([[(float(x), float(y)) for x, y in reversed(poly2d)]], mf.FillRule.Positive)
    return mf.Manifold.extrude(cs, z1 - z0).translate((0, 0, z0))

def cyl_along_s(h, z, r, s0, s1, seg=16):
    c = mf.Manifold.cylinder(s1 - s0, r, r, seg)          # along +z from 0
    # rotate so its axis is local s (x): z -> x
    c = c.transform([[0, 0, 1, 0], [0, 1, 0, 0], [-1, 0, 0, 0]])  # (x,y,z) -> (z, y, -x)  maps axis z to x
    return c.translate((s0, h, z))

def web_side(b):
    st = b['st']; vp = np.asarray(b['shape']['vp'], float).reshape(-1, 3) - np.asarray(st['p1'], float)
    u, v = np.asarray(st['u'], float), np.asarray(st['v'], float); dv, du = vp @ v, vp @ u
    lo, hi = dv.min(), dv.max(); H = du.max() - du.min()
    lips = dv[(np.abs(du) > H / 2 - 30) & (np.abs(du) < H / 2 - 5)]
    if len(lips) and (lips > (lo + hi) / 2).all(): return lo, 1.0     # web at lo, toes toward +v
    if len(lips) and (lips < (lo + hi) / 2).all(): return hi, -1.0
    raise ValueError('purlin %s: web side not found' % b['id'])

def bolt_man(P, ax, d, grip):
    """the same solid as v69 stBolts: head, shank, nut"""
    P, ax = np.asarray(P, float), unit(ax)
    def cyl(p0, r, L, seg):
        c = mf.Manifold.cylinder(L, r, r, seg)
        z = ax; x = np.cross(z, [0, 0, 1.0]) if abs(z[2]) < 0.9 else np.cross(z, [1.0, 0, 0]); x = unit(x); y = np.cross(z, x)
        return c.transform(np.column_stack([x, y, z, p0]).tolist())
    return cyl(P - 0.65 * d * ax, 0.95 * d, 0.65 * d, 6) + cyl(P - 0.2 * d * ax, d / 2, grip + 1.2 * d, 10) + cyl(P + grip * ax, 0.95 * d, 0.8 * d, 6)

CLEAT_POLY = [(0, 0), (0, 140), (-6, 140), (-6, 6), (-60, 6), (-60, 0)]
STIFF_AB = [(0, 124), (0, 10), (10, 0), (44, 0), (44, 10), (10, 124)]

def fix_c04(doc, log):
    B = {b['id']: b for b in doc['bodies']}; C = {c['id']: c for c in doc['stConns']}
    inp = json.load(open(os.path.join(REPO, 'tests', 'steel', 'golden', 'r10', 'rebuild_inputs.json')))
    M = inp['members']; T = [e for e in next(s for s in inp['steps'] if s['op'] == 'connectMany')['list'] if e[0] == 'Tcl']
    stiffs = [b for b in doc['bodies'] if (b.get('st') or {}).get('name') == 'Stiff.' and b['st'].get('prof') == 'PL6']
    n_cl = n_st = n_bg = 0
    for key, ix, q, row, rid in T:
        cl = B[rid[0]]; pur = B[M[ix[1]]['r10'][0]]; st = pur['st']
        webv, toe = web_side(pur)
        u, v = np.asarray(st['u'], float), np.asarray(st['v'], float); p1 = np.asarray(st['p1'], float)
        ax = unit(np.asarray(st['p2'], float) - p1)
        S = v * toe; Hh = -u                               # toward the toes; up from the rafter (u points into the rafter)
        Z = np.cross(S, Hh)                                # right-handed local frame
        cvp = np.asarray(cl['shape']['vp'], float).reshape(-1, 3); cz = float(((cvp - p1) @ ax).min() + ((cvp - p1) @ ax).max()) / 2
        # origin: web back face, purlin bottom (= rafter top), cleat centre along the purlin
        O = p1 + v * webv + u * 100.0 + ax * cz
        zsign = 1.0 if Z @ ax > 0 else -1.0
        # cleat
        c = prism(CLEAT_POLY, -60, 60)
        for hh in (40, 100):
            for zz in (-35, 35): c = c - cyl_along_s(hh, zz, 7.0, -20, 10)
        cw = frame_map(c, O, S, Hh, Z)
        cl['shape'] = shape_of(cw)
        cs = cl['st']; ctr = O + S * -30 + Hh * 70
        half = 60 * ax
        a = ctr - half if np.dot(np.asarray(cs['p2']) - np.asarray(cs['p1']), ax) > 0 else ctr + half
        b = ctr + half if np.dot(np.asarray(cs['p2']) - np.asarray(cs['p1']), ax) > 0 else ctr - half
        cs['p1'] = [r3(x) for x in a]; cs['p2'] = [r3(x) for x in b]
        cs['rAng'] = r3(((cs['rAng'] + 180 + 180) % 360) - 180)
        cs['u'] = [r3(-x) for x in cs['u']]; cs['v'] = [r3(-x) for x in cs['v']]
        cs['tk_fix'] = 'R11 c04: Tekla R3022 / 8503 (was R10 toe line, upside down)'
        n_cl += 1
        # stiffener: the R10 'Stiff.' nearest this cleat
        cc = cvp.mean(0); sb = min(stiffs, key=lambda b: float(np.linalg.norm(np.asarray(b['st']['pts3'], float).mean(0) - cc)))
        sp = [(-6 - a_, 6 + b_) for a_, b_ in STIFF_AB]
        sw = frame_map(prism(sp, -3, 3), O, S, Hh, Z)
        sb['shape'] = shape_of(sw)
        ss = sb['st']; ss['pts3'] = [[r3(x) for x in (O + S * s_ + Hh * h_)] for s_, h_ in sp]
        Xn = -Hh; Yn = -S; ss['ncF'] = {'X': [r3(x) for x in Xn], 'Y': [r3(x) for x in Yn], 'N': [r3(x) for x in np.cross(Xn, Yn)]}
        ss['tk_fix'] = 'R11 c04: Tekla R3022 / 8504 NC (was turned 90 deg in the web plane)'
        n_st += 1
        # bolts: every TKBOLT joining this cleat
        for c2 in doc['stConns']:
            if c2['type'] != 'TKBOLT' or cl['id'] not in c2['parts']: continue
            q2 = c2['q']; new = []
            for P in q2['pts']:
                w = np.asarray(P, float) - O; hh, zz = float(w @ Hh), float(w @ Z)
                new.append([r3(x) for x in O + S * -6 + Hh * hh + Z * zz])
            q2['pts'] = new; q2['ax'] = [r3(x) for x in S]; q2['grip'] = 8
            for bb in doc['bodies']:
                bs = bb.get('st') or {}
                if bs.get('kind') == 'bolts' and bs.get('conn') == c2['id']:
                    bs['pts'] = new; bs['ax'] = q2['ax']; bs['L'] = 30; bs['grade'] = '4.6'
                    man = None
                    for P in new: m = bolt_man(P, S, 12, 8); man = m if man is None else man + m
                    bb['shape'] = shape_of(man); n_bg += 1
    log.append(f'c04: {n_cl} cleats, {n_st} stiffeners, {n_bg} bolt groups moved to the purlin web (Tekla R3022, 8503, 8504, bolt list 12X30 4.6)')

def reflect_shape(b, n, c):
    """reflect a body's mesh in the plane through c with normal n (winding reversed so the solid stays outward-facing)"""
    V = np.asarray(b['shape']['vp'], float).reshape(-1, 3); T = np.asarray(b['shape']['tv'], int).reshape(-1, 3)
    V = V - 2 * np.outer((V - c) @ n, n); T = T[:, [0, 2, 1]]
    b['shape'] = {'vp': [round(float(x), 3) for x in V.reshape(-1)], 'tv': [int(x) for x in T.reshape(-1)]}

def fix_stairs(doc, log):
    """Stair stringers (UPN240, 12 pieces 9507-9518) and tread brackets (L60*6 9501 / 9502, 46).
    Proof: Tekla bolt list "8.8XOX BOLT 12X35 x 92" (grip about 15: bracket leg 6 + UPN240 web 9.5; R10 had grip 145, L169, 130 mm of
    air); SGR9001 assembly drawing: tread 9503 PL10*336 L990 with the brackets' bolted legs at its ends, 5 + 995 = 1000 between their
    outer faces, i.e. on the stringer webs; R10 has its stringers' flange tips 1000 apart (x 608 / 1608) and their webs on the far side,
    and the brackets' bolted legs inboard (54 mm from the stringer). NC 9501 / 9502 (holes in one leg, y 30) and 9507 (holes in the web)
    fix the hole positions, which R10 already has. So R10 mirrored both: R11 reflects each stringer about its own centre plane across
    the bolt axis (web toward the treads, flanges out) and each bracket about its own centre (bolted leg against the web, the other leg
    under the tread); holes move with the meshes. Bolts: head at the bracket leg's inner face, axis toward the web, M12 8.8 L35,
    grip 15.5 (bracket 6 + web 9.5)."""
    B = {b['id']: b for b in doc['bodies']}
    def V(b): return np.asarray(b['shape']['vp'], float).reshape(-1, 3)
    stringers = {b['id'] for b in doc['bodies'] if (b.get('st') or {}).get('name') == 'STRINGER'}
    done_str, n_br, n_bg = set(), 0, 0
    for c in doc['stConns']:
        if c['type'] != 'TKBOLT': continue
        names = {(B.get(p) or {}).get('st', {}).get('name') for p in c['parts']}
        if not ({'BRACKET', 'STRINGER'} <= names): continue
        q = c['q']; a = unit(q['ax']); br = next(B[p] for p in c['parts'] if B[p]['st']['name'] == 'BRACKET'); sr = next(B[p] for p in c['parts'] if B[p]['st']['name'] == 'STRINGER')
        P0 = np.asarray(q['pts'][0], float)
        # orient a from the bracket toward the stringer
        sb, ss = (V(br) - P0) @ a, (V(sr) - P0) @ a
        if ss.mean() < sb.mean(): a = -a
        if sr['id'] not in done_str:
            vs = V(sr); t = vs @ a; reflect_shape(sr, a, a * (t.min() + t.max()) / 2)
            st = sr['st']; st['rAng'] = r3(((st.get('rAng', 0) + 180) % 360)); st['tk_fix'] = 'R11 stairs: web toward the treads (Tekla SGR9001, bolt list 12X35)'
            if 'u' in st: st['u'] = [r3(-x) for x in st['u']]
            if 'v' in st: st['v'] = [r3(-x) for x in st['v']]
            done_str.add(sr['id'])
        if not br['st'].get('tk_fix'):
            vb = V(br); t = vb @ a; reflect_shape(br, a, a * (t.min() + t.max()) / 2)
            br['st']['rAng'] = r3(((br['st'].get('rAng', 0) + 90 + 180) % 360) - 180); br['st']['tk_fix'] = 'R11 stairs: bolted leg against the stringer web (Tekla SGR9001)'
            n_br += 1
        bmax = float((V(br) @ a).max())                   # the bracket's face on the stringer web
        new = []
        for P in q['pts']:
            P = np.asarray(P, float); new.append([r3(x) for x in P + a * (bmax - 6 - P @ a)])
        q['pts'] = new; q['ax'] = [r3(x) for x in a]; q['grip'] = 15.5
        for bb in doc['bodies']:
            bs = bb.get('st') or {}
            if bs.get('kind') == 'bolts' and bs.get('conn') == c['id']:
                bs['pts'] = new; bs['ax'] = q['ax']; bs['L'] = 35; bs['grade'] = '8.8'
                man = None
                for P in new: m = bolt_man(P, a, 12, 15.5); man = m if man is None else man + m
                bb['shape'] = shape_of(man); n_bg += 1
    # the short UPN240 end pieces of the same stringer assemblies (same section line, welded on): flipped with their stringer
    amk = {B[i]['st'].get('amark') for i in done_str}; ax_ = a
    for b in doc['bodies']:
        st = b.get('st') or {}
        if st.get('name') == 'STRINGER' and st.get('amark') in amk and b['id'] not in done_str:
            vs = V(b); t = vs @ ax_; reflect_shape(b, ax_, ax_ * (t.min() + t.max()) / 2)
            st['rAng'] = r3(((st.get('rAng', 0) + 180) % 360)); st['tk_fix'] = 'R11 stairs: end piece flipped with its stringer'
            if 'u' in st: st['u'] = [r3(-x) for x in st['u']]
            if 'v' in st: st['v'] = [r3(-x) for x in st['v']]
            done_str.add(b['id'])
    log.append(f'stairs: {len(done_str)} stringers (web toward the treads), {n_br} brackets (bolted leg on the web), {n_bg} bolt groups M12 8.8 L35 (Tekla SGR9001, bolt list 12X35 x 92)')

FIXES = {'c04': fix_c04, 'stairs': fix_stairs}

def main():
    want = sys.argv[1:] or list(FIXES)
    raw = json.load(gzip.open(R10)); doc = raw['doc']; log = []
    for k in want: FIXES[k](doc, log)
    raw['name'] = 'Frame_28x53m_R11'; raw['r11'] = {'from': 'Frame_28x53m_R10.ss', 'fixes': log, 'tool': 'tools/golden/r11_build.py'}
    data = json.dumps(raw, separators=(',', ':')).encode()
    with open(R11, 'wb') as f:
        with gzip.GzipFile(fileobj=f, mode='wb', mtime=0, filename='') as g: g.write(data)
    print('\n'.join(log)); print(R11, os.path.getsize(R11))

if __name__ == '__main__': main()
