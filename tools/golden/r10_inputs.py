#!/usr/bin/env python3
"""S10 acceptance, v69 side, step 1: the INPUTS a user would give web v69 to rebuild the R10 frame.

    python3 tools/golden/r10_inputs.py            # -> tests/steel/golden/r10/rebuild_inputs.json

Reads tests/steel/models/Frame_28x53m_R10.ss (gzipped JSON, a Tekla import: 6,638 bodies, 3,680 TKBOLT / TKCUT
connections) and writes the steps that rebuild it in v69 (replayed by tools/golden/r10_rebuild.js, compared by
tools/golden/r10_compare.py, report in docs/steel/R10-REPORT.md). Deterministic: no clocks, no randomness, every list is
sorted by R10 body id, numbers are rounded to 0.001 mm.

How the inputs are chosen (the owner's workflow: grid in Tekla syntax, Column / Beam tools, then components):
  1. Grid: R10's own stGrid (x '0 13195 ...', y '0 5570 ...', z '0 7500 1500').
  2. Portal frame application: NOT used. v69's portal frame (v33 + v42 + v43) builds ONE span (two columns, two rafters)
     at x = 0 and deletes the parts of an earlier run (gen = 1), so it cannot make R10's four spans with five column lines,
     its BI220 / BI320 built-up columns, the ridge monitor or the mezzanine. Everything is placed member by member instead.
  3. Members from Tekla built-up sections. R10 stores columns, rafters, beams, monitor posts / rafters, gable columns,
     mezzanine beams and stair beams as three plates (Web + Top flange + Bottom flange, `pts3` outlines). Each web whose
     two flanges are found (flange plane perpendicular to the web, flange mid-plane tf/2 outside the web edge, centred on
     the web, overlapping it along the length) becomes ONE v69 member with the built-up profile
     BI<H>-<tw>-<b top>-<tf top>[-<b bottom>-<tf bottom>] (v41 syntax), H = web height + both flange thicknesses:
       axis   = the section centre line (mid of H, in the web plane) in the length direction,
       ends   = the union of the three plates' extents measured on their own centre lines (square ends),
       rot    = 'Top' + rAng: the angle that turns v69's reference direction (stBuild: Z square to the axis, or -X for a
                column) about the axis onto the web direction towards the Top flange (Front / Below / Back for 90 / 180 /
                270), plane / depth Middle, offsets 0,
       name   = COLUMN (vertical), RAFTER (sloped) or BEAM (level); class and material of the web.
     The webs of the eave and apex haunches have one flange only; they are left to the components (see 5).
     A flange may sit up to 40 mm off the web edge / centre (R10: gable column webs 28.05 mm off, a stair post flange
     12 mm off); the offsets are kept in members[].webGap. Two end snaps, as the user picks the points:
       - a column with an R10 base plate starts at the plate's UNDERSIDE (v69 FAM.BP v54 puts the plate on top of the
         column's low end and cuts the column back), members[].snap = [old z, new z];
       - a rafter / monitor rafter with an apex end plate ends on the face where the two R10 end plates meet
         (members[].snapApex = [old point, new point]); the apex q.top is then R10's plate top above v54apex's zTop(0).
  4. R10's own members, taken as given (prof, p1, p2, rot, rAng, plane, depth, pOff, dOff, dx1, dx2, name, cls, mat, fin):
     purlins, HZ / VL bracing, struts, sag rods, stair stringers, stanchions, hand rails and railings (PD50*5).
     Members that v69 components make themselves are NOT placed: purlin cleat angles (Tcl), anchor rods, nuts and washers
     (base plate 1042 / 1004), stair tread brackets (stair treads).
  5. Components, chosen from tests/steel/models/Frame_28x53m_R04_vs_Tekla.md (and v69's own Document-manager order,
     tkDetails: T1042, T144, apex, T146, T39, Ttg, Ttgx, Tcl, Tsag, T1004, stairtreads). Which parts each joins comes
     from R10 itself: the R10 detail plate that the component would make (base plate, end plate, shear plate, tongue,
     cleat, ...) and the TKBOLT bolt groups that join it to the other assembly. Settings come from the R10 plate (thickness,
     size) and its bolts (diameter, rows / gauge) where v69 has the option; everything else is the v69 default.
       row 1   base plate 1042      [column]                      R10 'Base Plate' + GROUT + CASTPLATE + anchors
       row 16  base plate 1004      [post]                        R10 'Base Plate' PL16*170 (stair landing posts)
       row 2   end plate 144        [column, rafter]              R10 'PLATE' FLT20*150 (F3501) at the eaves
       row 3   apex haunch 'apex'   [rafter, rafter]              R10 'End Plate' FLT20*400 + haunch FLT8*130 (v54 apex)
       row 4   purlin cleat Tcl     [rafter, purlin, purlin]      R10 'Cleat Angle' L140*60*6 and the purlins bolted to it
       row 5-6 tube gusset Ttg      [main part, brace]            R10 'Connection Plate' FLT12*140 tongues (node: 1)
       row 7   tube crossing Ttgx   [continuous, half 1, half 2]  tongues bolted to a gusset owned by another brace
       row 8   sag rod Tsag         [purlin, rod]                 every purlin a ROD12 tie rod passes through
       row 9   end plate 144        [rafter, monitor post]        R10 'End plate Post' PL12*160
       row 10  end plate 144        [post, monitor rafter]        R10 'PLATE' FLT12*120 (F7501)
       row 11  joining plates 14    'apex' with no haunch          R10 'PLATE' PL12*155 (monitor apex)
       row 12  shear plate 146      [column, beam]                R10 'Shear Plate' on a column
       row 13  end plate 144        [column, mezzanine girder]    R10 'PLATE' PL16*120
       row 14  shear plate 146      [girder, joist]               R10 'Shear Plate' FLT10*130
       row 15  cap 39               [mezzanine column, girder]    R10 'End Plate ' PL16*120 on the column top
       row 17  146 hanger           [rafter, gable column]        R10 'PLATE' / 'D' FLT10*120 under the rafter
       row 18  stair treads         [stringer, stringer]          the two sloped stringers of each flight
       row 19  railing welds        'weldonly' [rail, stanchion]  stanchion tops on a hand rail
       (stair) end plate 144        [post, stair beam]            R10 'ENDPLATE' FLT10*150 of the stair beams (not a checklist row)
     144 settings: tp, bolt, ext / extb (plate beyond the beam depth projected on the face, as FAM.EP measures it), cols,
     gauge (gauge2), side, rows, e, pitches, gap. 146: t, bolt, rows / pitch along the secondary's depth, cols / pitchH,
     e, gap, side. 1042 / 1004: t, ext, side, anchors, bolt, gx, gy, anch, pw, gt, gm, cast. apex: tp, ph, pw, rowsAt,
     gauge, bolt, top, len / depth / hw / hf / hb (haunch). 39: tp, Lp, Wp, xsAt, bolt.
Output (tests/steel/golden/r10/rebuild_inputs.json):
  { grid, members: [{ i, st, r10: [ids], kind: 'builtup' | 'given', group }],
    steps: [ { op: 'grid', grid }, { op: 'addMany', from, to }, { op: 'connectMany', list: [[key, [member index...], q, row, r10 ids]] } ],
    notPlaced: [...], notes: [...] }
Member indexes are positions in `members` (the order of __st.addMany); the app replays the same list.
"""
import gzip
import json
import math
import os
import re
import sys
from collections import defaultdict, OrderedDict

WEB_GAP = 40.0   # mm: a flange plane may sit up to this far from the web edge (R10's gable column webs are 28 mm off)
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MODEL = os.environ.get('SS_MODEL', 'R12')   # R11 = the reference since 2026-11-25 (R10 + Tekla-proven fixes, docs/steel/PARITY.md); SS_MODEL=R10 for the old one
R10 = os.path.join(REPO, 'tests', 'steel', 'models', f'Frame_28x53m_{MODEL}.ss')
OUT = os.path.join(REPO, 'tests', 'steel', 'golden', MODEL.lower(), 'rebuild_inputs.json')


# ---------------------------------------------------------------- vectors
def add(a, b): return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
def sub(a, b): return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
def mul(a, s): return [a[0] * s, a[1] * s, a[2] * s]
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b): return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
def norm(a): return math.sqrt(dot(a, a))
def unit(a):
    n = norm(a)
    return [a[0] / n, a[1] / n, a[2] / n] if n > 1e-12 else [0.0, 0.0, 0.0]
def rot_about(v, axis, th):   # Rodrigues, like THREE.Vector3.applyAxisAngle
    c, s = math.cos(th), math.sin(th)
    return add(add(mul(v, c), mul(cross(axis, v), s)), mul(axis, dot(axis, v) * (1 - c)))
def r3(v): return round(v, 3) + 0.0
def R3(a): return [r3(x) for x in a]


def seg_dist(p1, p2, q1, q2):
    """closest distance between segments p1p2 and q1q2, with the parameters (0..1) on each"""
    d1, d2, r = sub(p2, p1), sub(q2, q1), sub(p1, q1)
    a, e, f = dot(d1, d1), dot(d2, d2), dot(d2, r)
    c, b = dot(d1, r), dot(d1, d2)
    den = a * e - b * b
    s = max(0.0, min(1.0, (b * f - c * e) / den)) if den > 1e-9 else 0.0
    t = (b * s + f) / e
    if t < 0: t, s = 0.0, max(0.0, min(1.0, -c / a))
    elif t > 1: t, s = 1.0, max(0.0, min(1.0, (b - c) / a))
    return norm(sub(add(p1, mul(d1, s)), add(q1, mul(d2, t)))), s, t


def point_seg(p, a, b):
    ab = sub(b, a); L2 = dot(ab, ab)
    t = max(0.0, min(1.0, dot(sub(p, a), ab) / L2)) if L2 > 0 else 0.0
    return norm(sub(p, add(a, mul(ab, t)))), t


# ---------------------------------------------------------------- v69 orientation rules (stBuild / stMF, L13605)
ROT = {'Top': 0, 'Front': 1, 'Below': 2, 'Back': 3}
def v69_frame(p1, p2):
    a = unit(sub(p2, p1)); Z = [0, 0, 1]
    if abs(dot(a, Z)) > 0.995: base = [-1, 0, 0]
    else: base = unit(sub(Z, mul(a, dot(a, Z))))
    return a, base
def v69_eh(st):
    a, base = v69_frame(st['p1'], st['p2'])
    th = ((ROT.get(st.get('rot'), 0)) * 90 + float(st.get('rAng') or 0)) * math.pi / 180
    eh = unit(rot_about(base, a, th))
    return a, eh, unit(cross(eh, a))


def thick_width(tprof):
    m = re.match(r'^(?:FLT|PL)(\d+(?:\.\d+)?)\*(\d+(?:\.\d+)?)$', tprof or '')
    return (float(m.group(1)), float(m.group(2))) if m else (None, None)


def fmt(v):
    v = round(v, 1)
    return str(int(v)) if abs(v - round(v)) < 1e-9 else str(v)


# ---------------------------------------------------------------- plates as frames
class Pl:
    """a flat part: R10 plate (pts3 + ncF) or a member with a PL section (the two EB built-ups made of members)"""
    def __init__(self, b):
        s = b['st']; self.id = b['id']; self.st = s; self.name = s['name']
        self.t, self.w = thick_width(s.get('tprof'))
        if s['kind'] == 'plate':
            P = s['pts3']; N = unit(s['ncF']['N']); X = unit(s['ncF']['X'])
            if MODEL == 'R12' and len(P) >= 3:   # C02h (PARITY.md): ncF's directions are rounded to 3 decimals (a 6 m rafter tilts 1 mm);
                # the exact plane from the outline (Newell) and the outline edge along ncF's X
                nw = [0.0, 0.0, 0.0]
                for i in range(len(P)):
                    a, b = P[i], P[(i + 1) % len(P)]; nw = [nw[0] + (a[1] - b[1]) * (a[2] + b[2]), nw[1] + (a[2] - b[2]) * (a[0] + b[0]), nw[2] + (a[0] - b[0]) * (a[1] + b[1])]
                if norm(nw) > 1e-6:
                    nw = unit(nw); N = nw if dot(nw, N) > 0 else mul(nw, -1)
                    best = None
                    for i in range(len(P)):
                        e = sub(P[(i + 1) % len(P)], P[i])
                        if norm(e) < 20: continue
                        e = unit(e); c = dot(e, X)
                        if abs(c) > 0.9999 and (best is None or abs(c) > abs(best[0])): best = (c, e)
                    X = (best[1] if best[0] > 0 else mul(best[1], -1)) if best else X
                    X = unit(sub(X, mul(N, dot(X, N))))
            Y = unit(cross(N, X))
            ex = lambda D: max(dot(p, D) for p in P) - min(dot(p, D) for p in P)
            if ex(Y) > ex(X): X, Y = Y, mul(X, -1)
            self.N, self.X, self.Y = N, X, Y
            self.poly = [(dot(p, X), dot(p, Y)) for p in P]
            self.n0 = sum(dot(p, N) for p in P) / len(P)
        else:   # member PLh*t: section height along eh, thickness along es
            a, eh, es = v69_eh(s)
            self.X, self.Y, self.N = a, eh, es
            c1 = s['p1']; L = norm(sub(s['p2'], s['p1']))
            h = self.w
            x0 = dot(c1, a); y0 = dot(c1, eh)
            self.poly = [(x0, y0 - h / 2), (x0 + L, y0 - h / 2), (x0 + L, y0 + h / 2), (x0, y0 + h / 2)]
            self.n0 = dot(c1, es)
        ys = [p[1] for p in self.poly]; xs = [p[0] for p in self.poly]
        self.y0, self.y1, self.x0, self.x1 = min(ys), max(ys), min(xs), max(xs)

    def span_at(self, y):
        """x extent of the outline on the line at height y (in its own frame)"""
        xs = []; P = self.poly
        for i in range(len(P)):
            (xa, ya), (xb, yb) = P[i], P[(i + 1) % len(P)]
            if (ya - y) * (yb - y) <= 0 and ya != yb: xs.append(xa + (xb - xa) * (y - ya) / (yb - ya))
            elif ya == y == yb: xs += [xa, xb]
        return (min(xs), max(xs)) if xs else (self.x0, self.x1)

    def centre(self):
        xm, ym = (self.x0 + self.x1) / 2, (self.y0 + self.y1) / 2
        return add(add(mul(self.X, xm), mul(self.Y, ym)), mul(self.N, self.n0))

    def point(self, x, y):
        return add(add(mul(self.X, x), mul(self.Y, y)), mul(self.N, self.n0))


def main():
    doc = json.load(gzip.open(R10))['doc']
    doc['bodies'] = [b for b in doc['bodies'] if b.get('st')]   # R12: Tekla's concrete bodies have no st (not steel)
    bodies = sorted(doc['bodies'], key=lambda b: b['id'])
    byid = {b['id']: b for b in bodies}
    asm_of = {b['id']: b['st'].get('asm', b['id']) for b in bodies if b['st']['kind'] != 'bolts'}
    amark = lambda i: byid[i]['st'].get('amark', '')
    pre = lambda i: re.match(r'^[A-Z]*', amark(i)).group(0)

    # TKBOLT partners: part id -> {other part id: [bolt conn]}
    partners = defaultdict(lambda: defaultdict(list))
    for c in doc['stConns']:
        if c['type'] != 'TKBOLT' or len(c['parts']) < 2: continue
        for p in c['parts']:
            for o in c['parts']:
                if o != p: partners[p][o].append(c)

    members = []          # [{i, st, r10, kind, group}]
    member_of = {}        # R10 body id -> member index
    notes = []

    # ------------------------------------------------ 3. built-up sections -> BI members
    flat = [Pl(b) for b in bodies if b['st']['kind'] in ('plate', 'member') and re.search(r'web|flange', b['st']['name'], re.I)
            and (b['st']['kind'] == 'plate' or re.match(r'^PL', b['st']['prof']))]
    webs = [p for p in flat if re.match(r'^web', p.name, re.I)]
    flanges = [p for p in flat if re.search(r'flange', p.name, re.I)]
    # a flange may also be named PLATE (STB9002 in R10): any other FLT plate of the web's assembly may serve
    web_asm = {asm_of[w.id] for w in webs}
    flanges += [Pl(b) for b in bodies if b['st']['kind'] == 'plate' and b['st']['name'] == 'PLATE' and asm_of[b['id']] in web_asm
                and str(b['st'].get('tprof', '')).startswith('FLT')]
    used_fl = set()
    webs.sort(key=lambda w: (-(w.x1 - w.x0), w.id))   # long main webs first: a haunch web never takes a main flange
    for w in webs:
        side_best = {}
        for f in flanges:
            if f.id in used_fl or abs(abs(dot(f.N, w.Y)) - 1) > 0.002: continue
            c = f.centre(); yc = dot(c, w.Y)
            if abs(dot(c, w.N) - w.n0) > WEB_GAP: continue      # the flange is centred on the web plane (R10: up to 12 mm off)
            if (f.x1 - f.x0) < 0.5 * (w.x1 - w.x0): continue  # as long as the web, about
            fx = [dot(f.point(x, (f.y0 + f.y1) / 2), w.X) for x in (f.x0, f.x1)]
            lo, hi = max(min(fx), w.x0), min(max(fx), w.x1)
            if hi - lo < 0.5 * min(w.x1 - w.x0, max(fx) - min(fx)): continue   # overlaps the web along its length
            for sg, gap in ((1, yc - (w.y1 + f.t / 2)), (-1, (w.y0 - f.t / 2) - yc)):
                if abs(gap) < WEB_GAP and (sg not in side_best or abs(gap) < abs(side_best[sg][0])): side_best[sg] = (gap, f, yc)
        if 1 not in side_best or -1 not in side_best: continue
        tops, bots = [side_best[1][1]], [side_best[-1][1]]
        gaps = (side_best[1][0], side_best[-1][0]); ycs = {side_best[1][1].id: side_best[1][2], side_best[-1][1].id: side_best[-1][2]}
        ft, fb = tops[0], bots[0]
        # which flange is "Top": the one named Top flange; if both or neither, the +Y one
        if re.match(r'^bottom', ft.name, re.I) and re.match(r'^top', fb.name, re.I): ft, fb, up = fb, ft, -1
        else: up = 1
        used_fl.update([ft.id, fb.id])
        tw = w.t
        yt, yb = (ycs[ft.id], ycs[fb.id])
        H = abs(yt - yb) + (ft.t + fb.t) / 2
        ymid = (yt + yb) / 2 + (ft.t - fb.t) / 4 * (1 if yt > yb else -1)
        # extents on each part's centre line, in the web's X
        xr = [w.span_at((w.y0 + w.y1) / 2)]
        for f in (ft, fb):
            s0, s1 = f.span_at((f.y0 + f.y1) / 2); ym = (f.y0 + f.y1) / 2
            xr.append(sorted([dot(f.point(s0, ym), w.X), dot(f.point(s1, ym), w.X)]))
        x0, x1 = min(r[0] for r in xr), max(r[1] for r in xr)
        P0 = add(add(mul(w.X, x0), mul(w.Y, ymid)), mul(w.N, w.n0)); P1 = add(add(mul(w.X, x1), mul(w.Y, ymid)), mul(w.N, w.n0))
        if (P0[2] > P1[2] + 1) or (abs(P0[2] - P1[2]) <= 1 and (P0[0], P0[1]) > (P1[0], P1[1])): P0, P1 = P1, P0
        a, base = v69_frame(P0, P1)
        eh = mul(w.Y, up)
        th = math.degrees(math.atan2(dot(a, cross(base, eh)), dot(base, eh)))
        rot, rAng = 'Top', round(th, 3)
        for nm, deg in (('Top', 0), ('Front', 90), ('Below', 180), ('Back', -90), ('Below', -180)):
            if abs(th - deg) < 0.01: rot, rAng = nm, 0
        bt, tft = ft.w, ft.t; bb, tfb = fb.w, fb.t
        prof = f'BI{fmt(H)}-{fmt(tw)}-{fmt(bt)}-{fmt(tft)}' + ('' if (bb, tfb) == (bt, tft) else f'-{fmt(bb)}-{fmt(tfb)}')
        az = abs(a[2]); name = 'COLUMN' if az > 0.995 else 'BEAM' if az < 0.01 else 'RAFTER'
        ws = w.st
        st = OrderedDict(kind='member', name=name, prof=prof, mat=ws.get('mat'), cls=ws.get('cls'), fin=ws.get('fin', 'PAINT'),
                         p1=R3(P0), p2=R3(P1), rot=rot, rAng=rAng, plane='Middle', pOff=0, depth='Middle', dOff=0, dx1=0, dx2=0)
        idx = len(members)
        members.append({'i': idx, 'kind': 'builtup', 'group': pre(w.id) or amark(w.id), 'amark': amark(w.id), 'r10': [w.id, ft.id, fb.id], 'st': st,
                        'webGap': [r3(g) for g in gaps]})
        for k in (w.id, ft.id, fb.id): member_of[k] = idx
    left_webs = [w.id for w in webs if w.id not in member_of]
    left_fl = [f.id for f in flanges if f.id not in member_of]

    # ------------------------------------------------ 4. R10 members taken as given
    MADE_BY_COMPONENTS = re.compile(r'^(Cleat Angle|Anchor Bolt|NUT|WASHER|BRACKET)$')
    KEEP = ['kind', 'name', 'prof', 'mat', 'cls', 'fin', 'p1', 'p2', 'rot', 'rAng', 'plane', 'pOff', 'depth', 'dOff', 'dx1', 'dx2']
    not_placed = defaultdict(list)
    for b in bodies:
        s = b['st']
        if s['kind'] != 'member' or b['id'] in member_of: continue
        if MADE_BY_COMPONENTS.match(s['name']): not_placed[s['name'] + ' ' + s['prof']].append(b['id']); continue
        st = OrderedDict((k, s[k]) for k in KEEP if k in s)
        idx = len(members)
        members.append({'i': idx, 'kind': 'given', 'group': pre(b['id']) or amark(b['id']), 'amark': amark(b['id']), 'r10': [b['id']], 'st': st})
        member_of[b['id']] = idx

    # the member that stands for an R10 assembly (its main part, or any part of it that became a member)
    asm_parts = defaultdict(list)
    for b in bodies:
        if b['st']['kind'] != 'bolts': asm_parts[asm_of[b['id']]].append(b['id'])
    def member_for(part):
        if part in member_of: return member_of[part]
        a = asm_of.get(part)
        if a in member_of: return member_of[a]
        cands = sorted({member_of[p] for p in asm_parts.get(a, []) if p in member_of})
        if len(cands) == 1: return cands[0]
        if cands:   # several members in one assembly: the one nearest to the part
            c = centre_of(part)
            return min(cands, key=lambda i: point_seg(c, members[i]['st']['p1'], members[i]['st']['p2'])[0])
        return nearest_member(centre_of(part), kinds=('builtup',))
    def centre_of(part):
        s = byid[part]['st']
        if s['kind'] == 'plate': P = s['pts3']; return [sum(p[k] for p in P) / len(P) for k in range(3)]
        return mul(add(s['p1'], s['p2']), 0.5)
    def nearest_member(p, kinds=('builtup', 'given'), maxd=1000.0, exclude=()):
        best = None
        for m in members:
            if m['kind'] not in kinds or m['i'] in exclude: continue
            dd, _ = point_seg(p, m['st']['p1'], m['st']['p2'])
            if dd < maxd and (best is None or dd < best[0]): best = (dd, m['i'])
        return best[1] if best else None
    def bolted_to(part):
        """members joined to `part` by TKBOLT bolt groups (other assemblies only), with the bolt groups"""
        out = defaultdict(list)
        for o, cs in sorted(partners[part].items()):
            if asm_of.get(o) == asm_of.get(part): continue
            m = member_for(o)
            if m is not None: out[m] += cs
        return out
    def bolt_d(cs):
        ds = sorted({c['q'].get('d') for c in cs if c['q'].get('d')})
        return int(ds[0]) if ds else None

    conns = []   # [key, [member indexes], q, row, [r10 ids]]
    seen = set()
    def conn(key, parts, q, row, refs):
        if None in parts or len(set(parts)) != len(parts): return False
        sig = (key, tuple(parts) if key not in ('apex',) else tuple(sorted(parts)))
        if sig in seen: return False
        seen.add(sig); conns.append([key, list(parts), q, row, sorted(refs)]); return True

    plates = [b for b in bodies if b['st']['kind'] == 'plate']
    def pl_dims(b):
        p = Pl(b); return p, (p.x1 - p.x0), (p.y1 - p.y0)
    def section_of(i):
        m = re.match(r'^BI(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)', members[i]['st']['prof'])
        return (float(m.group(1)), float(m.group(3))) if m else (None, None)
    def bolt_layout(cs, X, Y):
        """unique coordinates of the bolt positions along X and Y (plate frame)"""
        pts = [p for c in cs for p in c['q']['pts']]
        ux = sorted({round(dot(p, X), 0) for p in pts}); uy = sorted({round(dot(p, Y), 0) for p in pts})
        return pts, ux, uy

    unhandled = defaultdict(list)

    def web_t(st_):
        m = re.match(r'^BI\d+(?:\.\d+)?-(\d+(?:\.\d+)?)', st_['prof'])
        return float(m.group(1)) if m else 8.0

    def eave_haunch_q(ep, col, raf):
        """C02h settings from R12's eave haunch next to end plate `ep`: hlen = the web's far corner from the column axis (horizontal,
        along the rafter), hdep = the web's plumb depth at the end plate, web t, flange t and width."""
        cst = members[col]['st']; rst = members[raf]['st']; cx = cst['p1']
        far = max((rst['p1'], rst['p2']), key=lambda P: math.hypot(P[0] - cx[0], P[1] - cx[1]))
        hd = [far[0] - cx[0], far[1] - cx[1], 0.0]; hd = unit(hd); X = lambda P: (P[0] - cx[0]) * hd[0] + (P[1] - cx[1]) * hd[1]
        epx = X(ep['st']['pts3'][0]); parts = asm_parts[asm_of[ep['id']]]
        webs = [byid[x] for x in parts if byid[x]['st']['name'] == 'Web' and x not in member_of]
        if not webs: return None
        w = min(webs, key=lambda b: min(abs(X(p) - epx) for p in b['st']['pts3']))
        if min(abs(X(p) - epx) for p in w['st']['pts3']) > 60: return None
        P = w['st']['pts3']; xn = min(X(p) for p in P); near = [p for p in P if abs(X(p) - xn) < 0.5]
        fls = [byid[x] for x in parts if byid[x]['st']['name'] == 'Bottom flange' and x not in member_of]
        f = min(fls, key=lambda b: norm(sub(Pl(b).centre(), Pl(w).centre())))
        fp = Pl(f)
        dep = max(p[2] for p in near) - min(p[2] for p in near)   # R12: 82.926 (BI320 eaves) / 82.900 (BI220): one setting, 82.91, for all
        q = OrderedDict(hlen=r3(max(X(p) for p in P)), hdep=82.91 if abs(dep - 82.91) < 0.05 else r3(dep), tw=Pl(w).t, tf=fp.t, bf=fp.w)
        return q, [w['id'], f['id']]

    def hp_q(ep, hps, bst):
        """144 Haunch tab from R12's HAUNCH_PLATEs (C09): b along the end plate, h square to the secondary from the lower bottom
        corner, outer top chamfer (1 / 2 top plate, 4 / 3 bottom plate). The plates lie in the secondary's web plane, the end plate
        square to it; works in that plane: x = along the secondary's depth (eh), z = along its axis."""
        a, eh, es = v69_eh(bst); E = ep['st']['pts3']; c = [sum(v[k] for v in E) / len(E) for k in range(3)]
        far = max((bst['p1'], bst['p2']), key=lambda P: norm(sub(P, c))); ax = unit(sub(far, c)) if dot(sub(far, c), a) else a
        if dot(ax, a) < 0: a = mul(a, -1)
        # end plate back (secondary side) surface as a line in the (eh, a) plane
        e2 = [(dot(v, eh), dot(v, a)) for v in E]; (x0, z0), (x1, z1) = min(e2), max(e2); tn = (z1 - z0) / (x1 - x0)
        t = Pl(ep).t; cs = math.sqrt(1 + tn * tn); zb = lambda x: z0 + (x - x0) * tn + t / 2 * cs
        Pc = dot(bst['p1'], eh); H, Bw = section_hb(bst)
        q = OrderedDict(hpc='both', hct='line')
        for hid in hps:
            P = [(dot(v, eh), dot(v, a)) for v in byid[hid]['st']['pts3']]; s = 1 if sum(x for x, _ in P) / len(P) > Pc else -1; k = 1 if s > 0 else 2
            xf = Pc + s * H / 2; xo = max(P, key=lambda v: s * v[0])[0]; tz = max(z for _, z in P)
            b = abs(xo - xf) * cs; h = tz - min(zb(xf), zb(xo))
            top = [v for v in P if abs(v[1] - tz) < 0.05]; xi = max(top, key=lambda v: s * v[0])[0]; outer = [v for v in P if abs(v[0] - xo) < 0.05]
            cw = abs(xo - xi); ch = tz - max(z for _, z in outer)
            q['hpt%d' % k] = Pl(byid[hid]).t; q['hpb%d' % k] = r3(b); q['hph%d' % k] = r3(h)
            q['hc1' if k == 1 else 'hc4'] = r3(cw); q['hc2' if k == 1 else 'hc3'] = r3(ch)
        return q

    def ep_gap(p, bst, mst):
        """The end plate's distance from the main part's nominal face (negative: into it), measured as in ep_q."""
        a, eh, es = v69_eh(bst); n = p.N; c0 = p.centre(); far = max((bst['p1'], bst['p2']), key=lambda P: norm(sub(P, c0)))
        sgn = 1 if dot(sub(far, c0), n) > 0 else -1; c0 = sub(c0, mul(n, sgn * p.t / 2))
        Hm, Bm = section_hb(mst); am, ehm, esm = v69_eh(mst)
        dm, tm = point_seg(c0, mst['p1'], mst['p2']); Pm = add(mst['p1'], mul(sub(mst['p2'], mst['p1']), tm))
        flange = abs(dot(n, ehm)) >= abs(dot(n, esm)); half = Hm / 2 if flange else web_t(mst) / 2
        return r3(abs(dot(sub(c0, Pm), n)) - half)

    def ep_q(p, bst, pts, d, mst=None, gap=None):
        """End plate 144 settings from the R10 plate and its bolts (FAM.EP, L15547): plate height = the beam depth on the
        face + ext (top) + extb (bottom), width = gauge + 2 side, e = top edge to the first row, pitches top down."""
        a, eh, es = v69_eh(bst)
        X = p.X if dot(p.X, eh) >= 0 else mul(p.X, -1)          # plate length direction, towards the beam's top flange
        Y = unit(cross(p.N, X)); n = p.N
        if abs(dot(X, eh)) < abs(dot(p.Y, eh)): X = p.Y if dot(p.Y, eh) >= 0 else mul(p.Y, -1); Y = unit(cross(n, X))
        Pv = [add(add(mul(p.X, x), mul(p.Y, y)), mul(n, p.n0)) for x, y in p.poly]
        xs = [dot(v, X) for v in Pv]; ys = [dot(v, Y) for v in Pv]; top, bot = max(xs), min(xs)
        H, Bw = section_hb(bst)
        c0 = p.centre(); far = max((bst['p1'], bst['p2']), key=lambda P: norm(sub(P, c0)))
        sgn = 1 if dot(sub(far, c0), n) > 0 else -1
        c0 = sub(c0, mul(n, sgn * p.t / 2))          # the face against the main part (v69 measures the beam depth there)
        if gap: c0 = sub(c0, mul(n, sgn * gap))      # v69 measures it on the main part's face: the gap away (C09: -0.75)
        dd, t = point_seg(c0, bst['p1'], bst['p2']); Pc = add(bst['p1'], mul(sub(bst['p2'], bst['p1']), t))
        an = dot(a, n) or 1e-9; cs = []
        for sy in (-1, 1):
            for sz in (-1, 1):
                cpt = add(add(Pc, mul(eh, sy * H / 2)), mul(es, sz * Bw / 2)); cpt = sub(cpt, mul(a, dot(sub(cpt, c0), n) / an)); cs.append(dot(cpt, X))
        rows = sorted({round(dot(q3, X), 1) for q3 in pts}, reverse=True); cols = sorted({round(dot(q3, Y), 1) for q3 in pts})
        q = OrderedDict(tp=p.t, bolt=d, ext=r3(top - max(cs)), extb=r3(min(cs) - bot))
        if mst is not None:   # gap between the main part's face and the plate (v69 default 2)
            q['gap'] = r3(max(0.0, ep_gap(p, bst, mst)))
        if False:
            Hm, Bm = section_hb(mst); am, ehm, esm = v69_eh(mst)
            dm, tm = point_seg(c0, mst['p1'], mst['p2']); Pm = add(mst['p1'], mul(sub(mst['p2'], mst['p1']), tm))
            flange = abs(dot(n, ehm)) >= abs(dot(n, esm)); half = Hm / 2 if flange else web_t(mst) / 2
            q['gap'] = r3(max(0.0, abs(dot(sub(c0, Pm), n)) - half))
        if len(cols) in (2, 4):
            q['cols'] = len(cols)
            if len(cols) == 2: q['gauge'] = r3(cols[1] - cols[0])
            else: q['gauge'] = r3(cols[2] - cols[1]); q['gauge2'] = r3(cols[1] - cols[0])
            q['side'] = r3(((max(ys) - min(ys)) - (cols[-1] - cols[0])) / 2)
        q['rows'] = len(rows); q['e'] = r3(top - rows[0])
        if len(rows) > 1: q['pitches'] = ' '.join(fmt(rows[i] - rows[i + 1]) for i in range(len(rows) - 1))
        return q

    def snap_apex(own, other, p):
        """Move the rafter's apex end (the end nearer the other rafter) along its axis onto the face of its R10 end plate
        that touches the other rafter's plate (the joint plane; the user picks the apex point there). Returns the z of the
        rafter's top at the joint as v54apex computes it (zTop(0): apex z + half the plumb depth for depth Middle)."""
        st_ = members[own]['st']; ost = members[other]['st']
        ends = sorted([(min(norm(sub(st_[k], ost['p1'])), norm(sub(st_[k], ost['p2']))), k) for k in ('p1', 'p2')])
        k = ends[0][1]; far = st_['p2' if k == 'p1' else 'p1']
        n = p.N; c0 = p.centre(); sgn = 1 if dot(sub(c0, far), n) > 0 else -1
        f0 = add(c0, mul(n, sgn * p.t / 2))                       # plate face away from this rafter
        a = sub(st_[k], far); den = dot(a, n)
        if abs(den) < 1e-9: return None
        tt = dot(sub(f0, far), n) / den; new = add(far, mul(a, tt))
        members[own].setdefault('snapApex', [st_[k], R3(new)]); st_[k] = R3(new)
        H, _ = section_hb(st_); run = math.hypot(new[0] - far[0], new[1] - far[1]) or 1; th = math.atan((new[2] - far[2]) / run)
        return new[2] + H / math.cos(th) / 2

    def sp_qh(p, pts, bst):
        """C14 (R12): the horizontal edge distance (last bolt column to the tab's far end) and the gap that follows from it"""
        a, eh, es = v69_eh(bst)
        V, Hd = (p.X, p.Y) if abs(dot(p.X, eh)) >= abs(dot(p.Y, eh)) else (p.Y, p.X)
        Pv = [add(add(mul(p.X, x), mul(p.Y, y)), mul(p.N, p.n0)) for x, y in p.poly]
        hs = [dot(v, Hd) for v in Pv]; cols = sorted({round(dot(q3, Hd), 1) for q3 in pts})
        c0 = p.centre(); near = min((bst['p1'], bst['p2']), key=lambda P: norm(sub(P, c0))); far = bst['p2'] if near is bst['p1'] else bst['p1']
        sg = 1 if dot(sub(far, near), Hd) > 0 else -1             # along the beam, away from the main part
        h0 = min(hs) if sg > 0 else -max(hs); h1 = max(hs) if sg > 0 else -min(hs); cs = sorted(c * sg for c in cols)
        eH = h1 - cs[-1]; return OrderedDict(eh=r3(eH), gap=r3(cs[0] - h0 - eH))

    def sp_q(p, pts, d, bst):
        """Shear plate 146 settings (FAM.ST, L14939): bolt rows run along the secondary beam's depth (eh), columns along
        its axis; plate height = (rows - 1) pitch + 2 e, plate length = gap + 2 e + (cols - 1) pitchH; side = the side of
        the beam web the tab is on (+1 / -1 about X x eh, X from the joint end towards the far end)."""
        a, eh, es = v69_eh(bst)
        V, Hd = (p.X, p.Y) if abs(dot(p.X, eh)) >= abs(dot(p.Y, eh)) else (p.Y, p.X)
        Pv = [add(add(mul(p.X, x), mul(p.Y, y)), mul(p.N, p.n0)) for x, y in p.poly]
        extV = max(dot(v, V) for v in Pv) - min(dot(v, V) for v in Pv); extH = max(dot(v, Hd) for v in Pv) - min(dot(v, Hd) for v in Pv)
        rows = sorted({round(dot(q3, V), 1) for q3 in pts}); cols = sorted({round(dot(q3, Hd), 1) for q3 in pts})
        q = OrderedDict(t=p.t, bolt=d, rows=len(rows))
        e = (extV - (rows[-1] - rows[0])) / 2; q['e'] = r3(e)
        if len(rows) > 1: q['pitch'] = r3(rows[1] - rows[0])
        q['cols'] = len(cols)
        if len(cols) > 1: q['pitchH'] = r3(cols[1] - cols[0])
        q['gap'] = r3(max(0.0, extH - 2 * e - (cols[-1] - cols[0])))
        c0 = p.centre(); near = min((bst['p1'], bst['p2']), key=lambda P: norm(sub(P, c0))); far = bst['p2'] if near is bst['p1'] else bst['p1']
        X = unit(sub(far, near)); Zb = unit(cross(X, eh))
        q['side'] = 1 if dot(sub(c0, near), Zb) >= 0 else -1
        return q

    c20_taken = set()
    def c20_q(sp, col, beam):
        """C20: a shear plate on a column web that has Tekla's Top / Bottom NS / FS stiffeners next to it = Column with stiffeners (186).
        Tab: Tekla's beam end gap 20 is the tab start to the first bolt column less the horizontal edge (sp_qh), the tab length (Plates b),
        the bolt group centred (Bolts tab Middle 0: Tekla's grip and bolt length). Stiffeners (created by the first 186 at a column, the
        next has them 'not created' but the same sizes: they set its flange strip): t, b, chamfer at the web-side corners (on the root plane), the gap to the flanges, K preparation."""
        pc = Pl(sp).centre(); names = ('Top FS', 'Top NS', 'Bot FS', 'Bot Ns')
        stf = [x for x in asm_parts[asm_of[sp['id']]] if byid[x]['st']['name'] in names and norm(sub(Pl(byid[x]).centre(), pc)) < 600]
        if len(stf) != 4: return None
        p = Pl(sp); pts = [q3 for c in bolted_to(sp['id']).get(beam, []) for q3 in c['q']['pts']]
        if not pts: return None
        q = OrderedDict(sp_qh(p, pts, members[beam]['st']))
        V, Hd = (p.X, p.Y) if abs(dot(p.X, v69_eh(members[beam]['st'])[1])) >= abs(dot(p.Y, v69_eh(members[beam]['st'])[1])) else (p.Y, p.X)
        Pv = [add(add(mul(p.X, x), mul(p.Y, y)), mul(p.N, p.n0)) for x, y in p.poly]; q['tb'] = r3(max(dot(v, Hd) for v in Pv) - min(dot(v, Hd) for v in Pv))
        q['refV'] = 'Middle'; q['posV'] = 0
        # Bolts tab 'Bolting direction': v70 puts the heads on the beam's Y x X side (C14); R12 joint 4893 (C2016 / EB4009) has them on the other side
        bst = members[beam]['st']; cp = members[col]['st']['p1']; a1, a2 = bst['p1'], bst['p2']
        near, far = (a1, a2) if math.hypot(a1[0] - cp[0], a1[1] - cp[1]) < math.hypot(a2[0] - cp[0], a2[1] - cp[1]) else (a2, a1)
        X = [far[0] - near[0], far[1] - near[1]]; gax = bolted_to(sp['id']).get(beam)[0]['q']['ax']
        if gax[0] * -X[1] + gax[1] * X[0] < 0: q['bdir'] = 2
        key = (col, round(pc[2]))
        taken = key in c20_taken; c20_taken.add(key)
        import numpy as _np
        V0 = _np.asarray(byid[stf[0]]['shape']['vp'], float).reshape(-1, 3); ext = V0.max(0) - V0.min(0); t, L = sorted(ext)[0], sorted(ext)[2]; bw = sorted(ext)[1]
        cst = members[col]['st']; H, Bf = section_hb(cst); m = re.match(r'^BI(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)', cst['prof'])
        tw, tf = (float(m.group(2)), float(m.group(4))) if m else (8.0, 10.0)
        # the chamfer at the web-side corners, on the root face (the 3.4 mm between the K bevels): how far the web edge stops short of the ends
        ax = int(_np.argmin(ext)); lax = int(_np.argmax(ext)); wax = 3 - ax - lax; zc = (V0[:, ax].min() + V0[:, ax].max()) / 2
        root = V0[_np.abs(V0[:, ax] - zc) < t / 6 + 0.05]; cpt = _np.asarray(cst['p1'], float)
        web_w = V0[:, wax].min() if abs(V0[:, wax].min() - cpt[wax]) < abs(V0[:, wax].max() - cpt[wax]) else V0[:, wax].max()
        on_web = root[_np.abs(root[:, wax] - web_w) < 0.05][:, lax]
        ch = round(((on_web.min() - V0[:, lax].min()) + (V0[:, lax].max() - on_web.max())) / 2, 1) if len(on_web) else 0.0
        q.update(scr='both', ssh='line', sct='line', sc1=ch, sc2=ch, sgap2=r3(((H - 2 * tf) - L) / 2), sprep='K')
        for k in (1, 2, 3, 4): q['stt%d' % k] = r3(t); q['stb%d' % k] = r3(bw)
        if taken: q['scr'] = 'none'; return q, []   # the stiffeners are the first 186's; the sizes still set the flange strip (Picture 2)
        return q, stf

    def section_hb(bst):
        m = re.match(r'^BI(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)', bst['prof'])
        if m: return float(m.group(1)), float(m.group(3))
        m = re.match(r'^(?:SHS|RHS)(\d+(?:\.\d+)?)', bst['prof'])
        if m: return float(m.group(1)), float(m.group(1))
        return 200.0, 100.0

    # ---- rows 1 / 16: base plates
    rods = [b for b in bodies if b['st']['kind'] == 'member' and b['st']['name'] == 'Anchor Bolt']
    nuts = [b for b in bodies if b['st']['kind'] == 'member' and b['st']['name'] in ('NUT', 'WASHER')]
    grouts = [b for b in plates if b['st']['name'] in ('GROUT', 'CASTPLATE')]
    for b in plates:
        if b['st']['name'] != 'Base Plate': continue
        col = member_for(b['id']); p, L, W = pl_dims(b)
        if col is None: unhandled['Base Plate no column'].append(b['id']); continue
        cst = members[col]['st']; H, Bf = section_of(col) if members[col]['kind'] == 'builtup' else (None, None)
        # the column starts at the underside of its base plate (v69 FAM.BP, v54: the plate is the box 0..t above the
        # column's low end and the column is cut back to its top), so the built-up column's low end is snapped onto the
        # R10 plate's underside (R10's flange plates run past the plate and are cut by TKCUT)
        zbot = min(q3[2] for q3 in b['st']['pts3']) - p.t / 2
        if members[col]['kind'] == 'builtup' and abs(cst['p1'][2] - zbot) < 200 and abs(cst['p1'][2] - cst['p2'][2]) > 1000:
            members[col]['snap'] = [cst['p1'][2], r3(zbot)]; cst['p1'] = [cst['p1'][0], cst['p1'][1], r3(zbot)]
        a, eh, es = v69_eh(cst)
        Lh = max(abs(dot(p.X, eh)) * L, abs(dot(p.Y, eh)) * W); Wb = max(abs(dot(p.X, es)) * L, abs(dot(p.Y, es)) * W)
        c = p.centre()
        an = [r for r in rods if abs(r['st']['p1'][0] - c[0]) < L and abs(r['st']['p1'][1] - c[1]) < L]
        q = OrderedDict(t=p.t, ext=r3((Lh - H) / 2), side=r3((Wb - Bf) / 2))
        if an:
            d = float(re.sub(r'\D', '', an[0]['st']['prof']))
            gx = max(dot(r['st']['p1'], eh) for r in an) - min(dot(r['st']['p1'], eh) for r in an)
            gy = max(dot(r['st']['p1'], es) for r in an) - min(dot(r['st']['p1'], es) for r in an)
            Lr = norm(sub(an[0]['st']['p2'], an[0]['st']['p1']))
            q.update(anchors=len(an), bolt=d, gx=r3(gx), gy=r3(gy), anch=r3(Lr - p.t - 2.6 * d))
            if any(abs(n['st']['p1'][0] - an[0]['st']['p1'][0]) < 1 and abs(n['st']['p1'][1] - an[0]['st']['p1'][1]) < 1 for n in nuts): q['pw'] = 1
        # grout and cast plate under it: the nearest of each kind within 250 mm in plan (R10's grout is up to 130 mm off centre)
        gr = []
        for kind in ('GROUT', 'CASTPLATE'):
            cand = sorted((norm(sub(Pl(g).centre()[:2] + [0], c[:2] + [0])), g['id']) for g in grouts
                          if g['st']['name'] == kind and Pl(g).centre()[2] < c[2] and c[2] - Pl(g).centre()[2] < 100)
            if cand and cand[0][0] < 250: gr.append(byid[cand[0][1]])
        refs = [b['id']] + [r['id'] for r in an] + [g['id'] for g in gr]
        key = 'T1004' if pre(b['id']) == 'STR' else 'T1042'   # checklist: 1042 for the frame, gable and mezzanine columns, 1004 for the stair landing posts
        for g in gr:
            gp, gL, gW = pl_dims(g)
            if g['st']['name'] == 'GROUT':
                q['gt'] = gp.t; q['gm'] = r3(((max(gL, gW) - max(L, W)) + (min(gL, gW) - min(L, W))) / 4)
            else: q['cast'] = gp.t
        conn(key, [col], q, '1' if key == 'T1042' else '16', refs)
        if key == 'T1042' and 'gt' not in q: notes.append(f'base plate {b["id"]}: no grout found under it')

    # ---- end plates (144), apex (106 / 14), cap (39), shear plates (146), hangers
    EP = {('R', 'PLATE', 'FLT20*150'): '2', ('PC', 'End plate Post', 'PL12*160'): '9', ('PR', 'PLATE', 'FLT12*120'): '10',
          ('EB', 'End Plate ', 'PL16*120'): '13',   # C13 (PARITY.md): Tekla's name has a trailing space; before C13 this was read as Cap 39
          ('EB', 'PLATE', 'PL16*120'): '13', ('STB', 'ENDPLATE', 'FLT10*150'): 'stair'}
    for b in plates:
        s = b['st']; g = pre(b['id']); key3 = (g, s['name'], s['tprof'])
        if b['id'] in member_of: continue
        if s['name'] in ('PLATE', 'End plate Post', 'ENDPLATE', 'End Plate', 'End Plate ', 'Shear Plate', 'D'):
            p, L, W = pl_dims(b); bt = bolted_to(b['id']); own = member_for(b['id'])
            if not bt:
                unhandled[f'{s["name"]} {s["tprof"]} ({g}) not bolted to another assembly'].append(b['id']); continue
            other = sorted(bt, key=lambda m: -len(bt[m]))[0]; cs = bt[other]; d = bolt_d(cs)
            pts, ux, uy = bolt_layout(cs, p.X, p.Y)
            if key3 in EP:
                refs = [b['id']]; hps = [x for x in asm_parts[asm_of[b['id']]] if byid[x]['st']['name'] == 'HAUNCH_PLATE'] if MODEL == 'R12' else []
                if MODEL == 'R12' and EP[key3] == '13':   # C13: the girder-side 'Top Stiff.' / 'Bot Stiff.' next to this end plate are 144's haunch plates
                    pc = Pl(b).centre(); hps = [x for x in asm_parts[asm_of[b['id']]] if byid[x]['st']['name'] in ('Top Stiff.', 'Bot Stiff.') and norm(sub(Pl(byid[x]).centre(), pc)) < 300]
                if hps:   # C09 (PARITY.md): Tekla's 144 Haunch tab, read off R12's haunch plates; the plate on Tekla's flange plate, 0.75 mm under the axis' top face
                    g = ep_gap(p, members[own]['st'], members[other]['st']); q = ep_q(p, members[own]['st'], pts, d, members[other]['st'], gap=g); q['gap'] = g
                    q.update(hp_q(b, hps, members[own]['st'])); refs += hps
                else: q = ep_q(p, members[own]['st'], pts, d, members[other]['st'])
                if MODEL == 'R12' and EP[key3] in ('2', '9', '13'):   # C02 / C09 / C13 (PARITY.md): Tekla's bolt length and bolting direction
                    c0 = p.centre(); far = max((members[own]['st']['p1'], members[own]['st']['p2']), key=lambda P: norm(sub(P, c0)))
                    tosec = p.N if dot(sub(far, c0), p.N) > 0 else mul(p.N, -1)
                    q['blrule'] = 'tekla'; q['bdir'] = 2 if dot(cs[0]['q']['ax'], tosec) > 0 else 1
                conn('T144', [other, own], q, EP[key3], refs)
                if MODEL == 'R12' and EP[key3] == '2':   # C02h (PARITY.md): Eave haunch (Teh), read off R12's web + flange
                    hq = eave_haunch_q(b, other, own)
                    if hq: conn('Teh', [other, own], hq[0], '2', hq[1])
            elif s['name'] == 'End Plate' and s['tprof'] == 'FLT20*400':   # apex 106 with haunch (v54 apex)
                hw = [x for x in asm_parts[asm_of[b['id']]] if byid[x]['st']['name'] == 'Web' and byid[x]['st']['tprof'] == 'FLT8*130']
                hf = [x for x in asm_parts[asm_of[b['id']]] if byid[x]['st']['name'] == 'Bottom flange' and x not in member_of]
                rows = sorted({round(abs(dot(pp, [0, 0, 1])), 0) for pp in pts}, reverse=True)
                zt = max(q3[2] for q3 in s['pts3']); ra = ' '.join(fmt(zt - z) for z in rows)
                gauge = max(dot(pp, [0, 1, 0]) for pp in pts) - min(dot(pp, [0, 1, 0]) for pp in pts)
                q = OrderedDict(tp=p.t, ph=max(L, W), pw=min(L, W), rowsAt=ra, gauge=r3(gauge), bolt=d, grade='8.8')
                ap = snap_apex(own, other, p)
                if ap is not None: q['top'] = r3(zt - ap)
                if hw:
                    hp = Pl(byid[hw[0]]); q.update(len=round(hp.x1 - hp.x0), depth=round(hp.y1 - hp.y0), hw=hp.t)
                    fl = sorted(hf, key=lambda x: norm(sub(Pl(byid[x]).centre(), hp.centre())))
                    if fl: fp = Pl(byid[fl[0]]); q.update(hf=fp.t, hb=fp.w)
                conn('apex', [own, other], q, '3', [b['id']] + hw)
            elif s['name'] == 'PLATE' and s['tprof'] == 'PL12*155':   # monitor apex joining plates (14) = v54 apex, no haunch
                zt = max(q3[2] for q3 in s['pts3']); rows = sorted({round(pp[2], 0) for pp in pts}, reverse=True)
                gauge = max(pp[1] for pp in pts) - min(pp[1] for pp in pts)
                q = OrderedDict(tp=p.t, ph=max(L, W), pw=min(L, W), rowsAt=' '.join(fmt(zt - z) for z in rows), gauge=r3(gauge), bolt=d, grade='8.8', len=0)
                ap = snap_apex(own, other, p)
                if ap is not None: q['top'] = r3(zt - ap)
                conn('apex', [own, other], q, '11', [b['id']])
            elif s['name'] == 'End Plate ':   # cap plate on the mezzanine column top (39)
                xs = sorted({round(dot(pp, p.X), 0) for pp in pts}); xm = (p.x0 + p.x1) / 2
                q = OrderedDict(tp=p.t, Lp=max(L, W), Wp=min(L, W), xsAt=' '.join(fmt(x - xm) for x in xs), bolt=d)
                conn('T39', [own, other], q, '15', [b['id']])
            elif s['name'] == 'Shear Plate':
                q = sp_q(p, pts, d, members[other]['st'])
                row = '14' if s['tprof'] == 'FLT10*130' and g == 'EB' else '12'
                if row == '14' and MODEL == 'R12':   # C14 (PARITY.md): Tekla's Bolts tab 'Top' + the dimension from the joist's top to the top bolt row
                    jt = max(max(byid[i]['shape']['vp'][2::3]) for i in members[other]['r10'])
                    q['refV'] = 'Top'; q['posV'] = r3(jt - max(pp[2] for pp in pts))
                    q.update(sp_qh(p, pts, members[other]['st']))
                c20 = c20_q(b, own, other) if MODEL == 'R12' and row == '12' else None
                if c20: q.update(c20[0]); conn('T186', [own, other], q, '20', [b['id']] + c20[1])   # C20 (PARITY.md): Column with stiffeners (186)
                else: conn('T146', [own, other], q, row, [b['id']])
            elif s['name'] in ('PLATE', 'D') and s['tprof'] == 'FLT10*120':   # hanger under the rafter for the gable column top
                rf, gc = (own, other) if members[own]['st']['name'] == 'RAFTER' else (other, own)
                q = sp_q(p, pts, d, members[gc]['st'])
                conn('T146', [rf, gc], q, '17', [b['id']])
            else:
                unhandled[f'{s["name"]} {s["tprof"]} ({g})'].append(b['id'])

    # ---- row 4: purlin cleats
    for b in bodies:
        s = b['st']
        if s['kind'] != 'member' or s['name'] != 'Cleat Angle': continue
        own = member_for(b['id'])
        purl = sorted(m for m in bolted_to(b['id']) if members[m]['st']['name'] == 'Purlin')
        if own is None or not purl: unhandled['Cleat Angle without rafter / purlin'].append(b['id']); continue
        d = bolt_d([c for o in partners[b['id']] for c in partners[b['id']][o]])
        conn('Tcl', [own] + purl[:2], OrderedDict(bolt=d) if d else OrderedDict(), '4', [b['id']])

    # ---- rows 5-7: brace ends (tongue plates)
    for b in plates:
        s = b['st']
        if s['name'] != 'Connection Plate': continue
        br = member_for(b['id']); bt = bolted_to(b['id'])
        if not bt: unhandled['Connection Plate not bolted'].append(b['id']); continue
        other = sorted(bt, key=lambda m: -len(bt[m]))[0]; d = bolt_d(bt[other])
        if members[other]['kind'] == 'given' and re.search(r'Bracing|Strut', members[other]['st']['name']):
            continue   # crossing: handled below
        # gusset owned by a loose gusset assembly: nearest frame member to the gusset
        conn('Ttg', [other, br], OrderedDict(bolt=d), '5', [b['id']])
    # crossings: halves whose tongues bolt to a gusset of a continuous brace
    crossing = defaultdict(set)
    for b in plates:
        s = b['st']
        if s['name'] != 'Connection Plate': continue
        br = member_for(b['id'])
        for other in bolted_to(b['id']):
            if members[other]["kind"] == "given" and re.search(r"Bracing|Strut", members[other]["st"]["name"]): crossing[other].add(br)
    for cont, halves in sorted(crossing.items()):
        hv = sorted(halves)
        if len(hv) == 2: conn('Ttgx', [cont] + hv, OrderedDict(), '7', [])
        else: unhandled[f'crossing with {len(hv)} halves'].append(members[cont]['r10'][0])

    # ---- row 8: sag rods through purlins
    purlins = [m for m in members if m['st']['name'] == 'Purlin']
    for m in members:
        if not m['st']['name'].startswith('Tie Rod'): continue
        hit = []
        for pm in purlins:
            dd, s1, t1 = seg_dist(m['st']['p1'], m['st']['p2'], pm['st']['p1'], pm['st']['p2'])
            if dd < 110 and 0 < t1 < 1: hit.append(pm['i'])
        if not hit: unhandled['Tie Rod crossing no purlin'].append(m['r10'][0])
        for h in sorted(hit): conn('Tsag', [h, m['i']], OrderedDict(), '8', [])

    # ---- row 18: stair treads between the two sloped stringers of a flight
    str_ = [m for m in members if m['st']['name'] == 'STRINGER' and abs(m['st']['p1'][2] - m['st']['p2'][2]) > 500]
    for i, a in enumerate(str_):
        for b2 in str_[i + 1:]:
            pa = sorted([a['st']['p1'], a['st']['p2']], key=lambda p: p[2]); pb = sorted([b2['st']['p1'], b2['st']['p2']], key=lambda p: p[2])
            if abs(pa[0][2] - pb[0][2]) < 5 and abs(pa[0][1] - pb[0][1]) < 5:
                conn('stairtreads', [a['i'], b2['i']], OrderedDict(), '18', [])
    # ---- row 19: railing welds (stanchion top on a hand rail)
    rails = [m for m in members if m['st']['name'] == 'Hand Rail']
    for m in members:
        if m['st']['name'] != 'Stanchion': continue
        top = max([m['st']['p1'], m['st']['p2']], key=lambda p: p[2])
        for r in rails:
            dd, _ = point_seg(top, r['st']['p1'], r['st']['p2'])
            if dd < 30: conn('weldonly', [r['i'], m['i']], OrderedDict(), '19', []); break

    # ---- parts no component makes
    handled_names = {'Base Plate', 'GROUT', 'CASTPLATE', 'PLATE', 'End plate Post', 'ENDPLATE', 'End Plate', 'End Plate ', 'Shear Plate', 'D', 'Connection Plate'}
    out = OrderedDict()
    out['source'] = 'tools/golden/r10_inputs.py from tests/steel/models/Frame_28x53m_R10.ss'
    out['grid'] = OrderedDict((k, doc['stGrid'][k]) for k in ('x', 'y', 'z'))
    out['members'] = members
    out['steps'] = [OrderedDict(op='grid', grid=out['grid']), OrderedDict(op='addMany', **{'from': 0, 'to': len(members)}),
                    OrderedDict(op='connectMany', list=conns)]
    out['notPlaced'] = OrderedDict(sorted((k, sorted(v)) for k, v in not_placed.items()))
    out['notes'] = notes
    out['unhandled'] = OrderedDict(sorted((k, sorted(v)) for k, v in unhandled.items()))
    out['leftover'] = OrderedDict(webs=sorted(left_webs), flanges=sorted(left_fl))
    out['counts'] = OrderedDict(members=len(members), builtup=sum(1 for m in members if m['kind'] == 'builtup'),
                                given=sum(1 for m in members if m['kind'] == 'given'), connections=len(conns),
                                byKey=OrderedDict(sorted(_count(c[0] for c in conns).items())),
                                byRow=OrderedDict(sorted(_count(c[3] for c in conns).items(), key=lambda kv: (len(kv[0]), kv[0]))))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f: json.dump(out, f, indent=1); f.write('\n')
    print(json.dumps(out['counts'], indent=1))
    print('unhandled:', {k: len(v) for k, v in out['unhandled'].items()})
    print('leftover webs', len(left_webs), 'flanges', len(left_fl))


def _count(it):
    c = defaultdict(int)
    for x in it: c[x] += 1
    return c


if __name__ == '__main__':
    main()
