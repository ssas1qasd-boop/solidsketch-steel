#!/usr/bin/env python3
"""R12 = Frame_28x53m_R12.ss built from Tekla's own IFC export (tests/steel/tekla/Frame_28x53m_tekla_v2.ifc: Tekla Structures
2025 SP6, IFC2X3 CV2.0, surface geometry, assemblies, bolts and welds on). R10 and R11 stay unchanged.

    python3 tools/golden/r12_build.py          -> tests/steel/models/Frame_28x53m_R12.ss + build/r12/parts.json (for r12_compare.py)
    python3 tools/golden/r12_build.py R13      -> tests/steel/models/Frame_28x53m_R13.ss + build/r13/parts.json (docs/steel/R13.md)
    python3 tools/golden/r12_build.py R12 OUT.ss   (any model to another path, e.g. to prove R12 is rebuilt byte-identical)

The same code builds R13 from Tekla's IFC of the fixed model (tests/steel/tekla/Frame_28x53m_tekla_v3.ifc = Package_2's out.ifc).
Only the inputs differ (MODELS below): the IFC, the model the part marks start from (R11 for R12, R12 for R13) and Tekla's lists
the marks are checked against (Package_1 Reports/ for R12; Package_2 SingleList.xsr + the material list of each assembly drawing
for R13, whose part marks Tekla renumbered: the R12 mark is translated through the two part lists first, see tekla_pos).

What comes from where (every value below is Tekla's, nothing is taken from R10/R11 except where stated):
  parts (IfcBeam / IfcColumn / IfcMember / IfcPlate / IfcFooting)
    mesh      Tekla's faceted brep, placed by its IfcLocalPlacement chain, with Tekla's bolt holes cut in (the hole solids of each
              bolt assembly's mapped representation, i.e. Tekla's hole diameter, one cylinder per bolt through the whole stack)
    name, profile (tprof), material, class, weight      IfcProduct Name / ObjectType, IfcMaterial, 'Tekla Common' Class,
              'Tekla Quantity' Weight (kept as st.tkW)
    kind      PL / FLT profiles -> plate: pts3 = the outline of the largest face perpendicular to the thickness direction, moved to
              the mid-plane, depth Middle, prof PL<t>; any other profile -> member: p1 / p2 on the section's bounding-box centre
              line at the two ends of the solid, rot Top + rAng and plane / depth Middle such that web v69's stBuild makes Tekla's
              section (solved per member, then proven by rebuilding every member in v69: tools/golden/v69_build.js)
    assembly  IfcElementAssembly Tag = assembly mark (st.amark); main part = the heaviest part of the assembly, on equal weight the one
              named like the assembly (Tekla's IFC does not flag the main part; Tekla names an assembly after it); every other part
              gets st.asm = main part id
    part mark (st.pos)   NOT in the IFC. From R11 and Tekla's own 'Part List' report (package Reports/):
              R11's mark where it agrees with Tekla's Part List (profile, grade, weight, length); otherwise the Part List row that
              fits and still has parts to place (tekla_pos). r12_compare.py checks counts, profiles, lengths, weights against
              the Part List.
    concrete  (C30 footings / pedestals) are kept as plain bodies (no st) in folder 'Concrete (Tekla)'
  bolt groups (IfcMechanicalFastener + 'Tekla Bolt')
    every bolt: the mapped item's placement (axis = its local y); the bolt solid (head, shank, washer, nut) and the hole solid are
    Tekla's. pts = the head's underside on the axis, ax = toward the nut, grip = the hole stack length, d, L, grade (Bolt standard),
    the parts joined = the parts the hole solid cuts. One TKBOLT connection + one 'bolts' body per group; groups without a bolt
    solid (holes only) get q.holesOnly and no body.
  welds (IfcFastener 'Weld' + IfcRelConnectsWithRealizingElements)
    st.tkWelds of the secondary part: one record per Tekla weld: the main part's id, shop / site, around, length, above / below line
    size and type, and the weld solid's box (v69 keeps a part's st as it is on open / save; it drops unknown document fields).
"""
import gzip, json, math, os, subprocess, sys, collections
import numpy as np
import manifold3d as mf
sys.path.insert(0, os.path.dirname(__file__))
from ifc_read import Ifc, brep_loops

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
_T, _M = os.path.join(REPO, 'tests', 'steel', 'tekla'), os.path.join(REPO, 'tests', 'steel', 'models')
# per model: the IFC, the document template (grid, folders, units), the model the part marks start from, the output, Tekla's lists
MODELS = {
    'R12': dict(ifc=os.path.join(_T, 'Frame_28x53m_tekla_v2.ifc'), ifcName='tests/steel/tekla/Frame_28x53m_tekla_v2.ifc', template=os.path.join(_M, 'Frame_28x53m_R11.ss'),
                prior=os.path.join(_M, 'Frame_28x53m_R11.ss'), out=os.path.join(_M, 'Frame_28x53m_R12.ss'), work=os.path.join(REPO, 'build', 'r12'),
                pkg=os.path.join(_T, 'Frame_28mx53m_Package_1.zip'), lists='package1'),
    'R13': dict(ifc=os.path.join(_T, 'Frame_28x53m_tekla_v3.ifc'), ifcName='tests/steel/tekla/Frame_28x53m_tekla_v3.ifc', template=os.path.join(_M, 'Frame_28x53m_R11.ss'),
                prior=os.path.join(_M, 'Frame_28x53m_R12.ss'), out=os.path.join(_M, 'Frame_28x53m_R13.ss'), work=os.path.join(REPO, 'build', 'r13'),
                pkg=os.path.join(_T, 'Frame_28mx53m_Package_2.zip'), lists='package2', oldPkg=os.path.join(_T, 'Frame_28mx53m_Package_1.zip')),
}
MODEL = 'R12'
IFC, R11, R12, OUT = MODELS['R12']['ifc'], MODELS['R12']['prior'], MODELS['R12']['out'], MODELS['R12']['work']
TEMPLATE = MODELS['R12']['template']
PART_TYPES = ['IFCBEAM', 'IFCCOLUMN', 'IFCMEMBER', 'IFCPLATE', 'IFCFOOTING']

def r3(v): return round(float(v) + 0.0, 3)
def unit(v): v = np.asarray(v, float); return v / np.linalg.norm(v)
def log(*a): print(*a, file=sys.stderr, flush=True)

# ---------------------------------------------------------------- meshes
def to_manifold(V, T):
    """Tekla brep triangles -> Manifold (shared vertices merged); None if the surface is not a closed 2-manifold"""
    V = np.asarray(V, np.float64); key = np.round(V, 4); u, inv = np.unique(key, axis=0, return_inverse=True)
    T2 = inv.reshape(-1)[np.asarray(T, int)]; T2 = T2[(T2[:, 0] != T2[:, 1]) & (T2[:, 1] != T2[:, 2]) & (T2[:, 0] != T2[:, 2])]
    Vu = np.zeros((len(u), 3)); Vu[inv.reshape(-1)] = V
    m = mf.Manifold(mf.Mesh64(np.ascontiguousarray(Vu, np.float64), np.ascontiguousarray(T2, np.uint64)))
    if m.status() != mf.Error.NoError or m.is_empty(): return None
    if m.volume() < 0: return None
    return m

def mesh_of(man):
    m = man.to_mesh64(); return np.asarray(m.vert_properties)[:, :3].astype(float), np.asarray(m.tri_verts, int)

def components(T):
    """vertex index sets of the connected pieces of a triangle mesh"""
    T = np.asarray(T, int); par = list(range(int(T.max()) + 1 if len(T) else 0))
    def find(x):
        while par[x] != x: par[x] = par[par[x]]; x = par[x]
        return x
    for a, b, c in T:
        ra, rb, rc = find(a), find(b), find(c); par[rb] = ra; par[find(rc)] = ra
    out = collections.defaultdict(list)
    for v in set(T.reshape(-1).tolist()): out[find(v)].append(v)
    return [np.array(v) for v in out.values()]

def jsnum(x):
    """numbers as web v69 (JSON.stringify) writes them: 12 not 12.0, 0 not -0.0"""
    if isinstance(x, float):
        if x == 0: return 0
        return int(x) if x.is_integer() and abs(x) < 1e15 else x
    if isinstance(x, dict): return {k: jsnum(v) for k, v in x.items()}
    if isinstance(x, (list, tuple)): return [jsnum(v) for v in x]
    return x

def shape(V, T):
    """mesh as the web build stores it: vertex positions in float32 (three.js / Manifold MeshGL), so v69 and the app save the same bytes"""
    v = np.asarray(V, np.float64).reshape(-1); v[np.abs(v) < 1e-4] = 0.0      # no 1.8e-9 noise (and no exponent notation in the file)
    return {'vp': [float(x) for x in v.astype(np.float32)], 'tv': [int(x) for x in np.asarray(T).reshape(-1)]}

def cylinder(P0, ax, r, L, seg=24):
    ax = unit(ax); x = unit(np.cross(ax, [0, 0, 1.0]) if abs(ax[2]) < 0.9 else np.cross(ax, [1.0, 0, 0])); y = np.cross(ax, x)
    c = mf.Manifold.cylinder(L, r, r, seg)
    return c.transform(np.column_stack([x, y, ax, P0]).tolist())

# ---------------------------------------------------------------- profiles
def thick_of(prof):
    """plate thickness from a Tekla plate profile: PL<t>*<w> / FLT<t>*<w> (Tekla writes thickness first for both here)"""
    s = prof.upper().lstrip('FLTPL'); return float(s.split('*')[0])

def is_plate(prof): p = prof.upper(); return p.startswith('PL') or p.startswith('FLT')

def section_v69(profs):
    """each member profile's section in v69 (stBuild with a = +Y, rot Top, rAng 0: es = -X, eh = +Z): outline points (q0, q1)
    centred on the section box, as v69 places it with plane / depth Middle"""
    lst = [{'id': p, 'st': {'kind': 'member', 'prof': p, 'p1': [0, 0, 0], 'p2': [0, 1000, 0], 'rot': 'Top', 'rAng': 0, 'plane': 'Middle', 'depth': 'Middle'}} for p in profs]
    res = v69_build(lst, 'sections'); out = {}
    for p in profs:
        r = res[p]
        if 'error' in r: raise SystemExit('v69 cannot build %s: %s' % (p, r['error']))
        V = np.array(r['verts']); V = V[np.abs(V[:, 1]) < 0.01]
        out[p] = np.unique(np.c_[-V[:, 0], V[:, 2]].round(3), axis=0)       # (q0 along es, q1 along eh)
    return out

def v69_build(lst, tag):
    os.makedirs(OUT, exist_ok=True); fi, fo = os.path.join(OUT, tag + '_in.json'), os.path.join(OUT, tag + '_out.json')
    json.dump(lst, open(fi, 'w'))
    subprocess.run(['node', os.path.join(REPO, 'tools', 'golden', 'v69_build.js'), fi, fo], check=True, cwd=REPO)
    return json.load(open(fo))

def outline_dist(A, B):
    """max over points of A of the distance to the nearest point of B (both small point sets of a section)"""
    if len(A) == 0 or len(B) == 0: return 1e9
    D = np.linalg.norm(A[:, None, :] - B[None, :, :], axis=2); return float(D.min(1).max())

ROUND = ('CHS', 'PD', 'ROD', 'D')

def solve_member(W, Vl, sec, prof):
    """IFC placement W (local x = axis, y / z = section), local vertices Vl, v69 section -> st fields (rot Top, rAng, plane / depth
    Middle, p1 / p2 on the section box centre line at the solid's two ends) and the fit error of the section (mm).
    Candidates: the axis either way (p1 -> p2 along +x or -x: reversing it mirrors the section, which an unequal angle needs) and
    the four quarter turns; score = how many of v69's section corners lie on a Tekla vertex (0.2 mm), then the worst corner."""
    X, Y, Z, O = W[:3, 0], W[:3, 1], W[:3, 2], W[:3, 3]
    ys, zs = Vl[:, 1], Vl[:, 2]; cy, cz = (ys.min() + ys.max()) / 2, (zs.min() + zs.max()) / 2
    S = np.unique(np.c_[ys - cy, zs - cz].round(3), axis=0)            # Tekla section, centred on its box
    q = sec[prof]; best = None; rnd = prof.upper().startswith(ROUND)
    for sg in (1, -1):
        for k in range(4):
            ey, ez = [(0, 1), (-1, 0), (0, -1), (1, 0)][k]                 # eh in (Y, Z)
            esy, esz = (ez, -ey) if sg > 0 else (-ez, ey)                    # es = eh x a, a = sg * X
            P = np.c_[q[:, 0] * esy + q[:, 1] * ey, q[:, 0] * esz + q[:, 1] * ez]
            dmin = np.linalg.norm(P[:, None, :] - S[None, :, :], axis=2).min(1)
            box = float(np.abs(np.r_[P.min(0), P.max(0)] - np.r_[S.min(0), S.max(0)]).max())
            score = (-box, float((dmin <= 0.2).mean()), -float(dmin.max())) if not rnd else (-box, 1.0, 0.0)
            if best is None or score > best[0]: best = (score, sg, ey * Y + ez * Z, max(box, float(dmin.max())) if not rnd else box, P)
            if rnd: break
        if rnd: break
    _, sg, eh, e, P = best
    a = X * sg; col = abs(a[2]) > 0.995
    if col: dz = np.array([0, 1.0, 0]); base = np.array([-1.0, 0, 0])
    else: dz = np.array([0, 0, 1.0]) - a * a[2]; dz = dz / np.linalg.norm(dz); base = dz
    ds = np.cross(dz, a); ds /= np.linalg.norm(ds)
    th = math.degrees(math.atan2(float(np.cross(base, eh) @ a), float(base @ eh)))
    # v69 centres the section on the box of its projection on (ds, dz) (stBuild), not on the section's own box: shift the axis
    w = np.outer(P[:, 0], Y) + np.outer(P[:, 1], Z); s_, z_ = w @ ds, w @ dz
    off = ds * (s_.min() + s_.max()) / 2 + dz * (z_.min() + z_.max()) / 2
    x0, x1 = Vl[:, 0].min(), Vl[:, 0].max()
    C = O + Y * cy + Z * cz + off; A, B = C + X * x0, C + X * x1
    if sg < 0: A, B = B, A
    return {'p1': [r3(v) for v in A], 'p2': [r3(v) for v in B], 'rot': 'Top', 'rAng': r3(th % 360), 'plane': 'Middle', 'depth': 'Middle',
            'pOff': 0, 'dOff': 0, 'dx1': 0, 'dx2': 0}, e

def plate_fields(f, items, W, t):
    """outline of the plate's largest face perpendicular to its thickness direction, moved to the mid-plane (world)"""
    best = None
    for kind, ii, M in items:
        if kind != 'brep': continue
        G = W @ M
        for loops in brep_loops(f, ii):
            P = loops[0] @ G[:3, :3].T + G[:3, 3]
            n = np.zeros(3)
            for k in range(len(P)): a, b = P[k], P[(k + 1) % len(P)]; n += np.cross(a, b)
            ar = np.linalg.norm(n) / 2
            if ar < 1e-6: continue
            if best is None or ar > best[0] + 1e-6: best = (ar, P, n / np.linalg.norm(n))
    ar, P, n = best
    return P, n

# ---------------------------------------------------------------- part marks from Tekla's Assembly Part List
PKG = os.path.join(REPO, 'tests', 'steel', 'tekla', 'Frame_28mx53m_Package_1.zip')

def report_rows(name, pkg=None):
    import zipfile, re, html
    with zipfile.ZipFile(pkg or PKG) as z:
        n = next(x for x in z.namelist() if x.endswith('Reports/' + name)); t = z.read(n).decode('latin-1')
    R = []
    for r in re.findall(r'<tr[^>]*>(.*?)</tr>', t, re.S | re.I):
        c = [html.unescape(re.sub(r'<[^>]+>', '', x)).replace('\xa0', ' ').strip() for x in re.findall(r'<td[^>]*>(.*?)</td>', r, re.S | re.I)]
        if any(c): R.append(c[1:] if c and c[0] == '' else c)
    return R

def norm_prof(p):
    """Tekla's reports round a contour plate's width to 1 mm: PL12*222.6 -> PL12*223"""
    if p.upper().startswith('PL') and '*' in p:
        a, b = p.split('*', 1)
        try: return '%s*%d' % (a, int(math.floor(float(b) + 0.5)))
        except ValueError: return p
    return p

def tekla_pos(steel):
    """st.pos: Tekla's IFC has no part marks. R11's mark (from R10, i.e. Tekla's own database through the importer) is kept where it
    agrees with Tekla's 'Part List' report (package Reports/: per mark profile (rounded as Tekla rounds it), grade, length and weight
    of one part, count); where it does not (an R11 part matched to the wrong R12 part, e.g. a moved gusset), the mark is the Part
    List row that fits (profile, grade, weight 0.06 kg, length 1 mm) and still has parts to place, preferring the rows that the
    'Assembly Part List' lists under the part's assembly mark. (The Assembly Part List alone cannot give marks: it lists marks with
    identical geometry, e.g. 3504 / 3508, under one row.) Each part's reason is kept (r['posWhy'])."""
    if MODELS[MODEL]['lists'] == 'package2': return tekla_pos2(steel)
    plist = part_list1()
    R = report_rows('Assembly Part List Excel.xls'); h = next(i for i, r in enumerate(R) if r[:3] == ['Assembly Mark', 'Sub-Assembly Mark', 'Part Mark'])
    in_asm = collections.defaultdict(set); cur = None
    for r in R[h + 1:]:
        if r[0] and not r[0].startswith('NOTE'): cur = r[0]; continue
        if cur and len(r) >= 12 and r[2]: in_asm[cur].add(r[2])
    place_pos(steel, plist, in_asm, 'R11')

def part_list1(pkg=None):
    """Package_1 Reports/Part List Excel.xls: mark -> profile, count, grade, length, weight of one part"""
    pl = report_rows('Part List Excel.xls', pkg); h = next(i for i, r in enumerate(pl) if r[:2] == ['PartPos', 'Profile'])
    return {r[0]: dict(prof=r[1], n=int(r[2]), mat=r[3], len=float(r[4]), w=float(r[6])) for r in pl[h + 1:] if r and r[0] != 'Total for' and len(r) >= 7}

def prof_eq(lp, tprof):
    """a report's profile vs Tekla's: rounded as Tekla rounds a contour plate's width; a long name cut to 'CC200-2-2***' (SingleList.xsr)"""
    if lp.endswith('***'): return tprof.startswith(lp[:-3])
    return lp == norm_prof(tprof)

def place_pos(steel, plist, in_asm, src, prior=None, tol=0.06):
    """the marks: the prior model's mark (prior(r): R11's for R12, R12's translated through the renumbering for R13) where it fits
    the part list, else the fitting row that still has parts, preferring rows listed under the part's assembly"""
    if prior is None: prior = lambda r: r.get('pos11')
    def fits(x, r, tol=0.06):
        # a contour plate may be listed as a flat of the other orientation (PL5*44.1, length 100 = FLT5*100, length 44): for plates
        # the thickness, grade and weight decide; for profiles the profile, grade, weight and length
        if x['mat'] != r['mat'] or abs(x['w'] - r['w']) > tol: return False
        if is_plate(r['tprof']) and is_plate(x['prof']): return abs(thick_of(x['prof']) - thick_of(r['tprof'])) < 0.01
        return prof_eq(x['prof'], r['tprof']) and (r['len'] is None or abs(x['len'] - float(r['len'])) <= 1.0)
    left = collections.Counter({k: v['n'] for k, v in plist.items()}); todo = []
    for r in sorted(steel, key=lambda r: r['bid']):
        pk = prior(r); x = plist.get(pk)
        if x is not None and fits(x, r, 0.1) and left[pk] > 0: r['pos'] = pk; r['posWhy'] = src + ' mark, fits the Part List'; left[r['pos']] -= 1
        else: todo.append(r)
    for r in todo:
        c = [k for k, x in plist.items() if fits(x, r, tol)]
        c.sort(key=lambda k: (left[k] <= 0, k not in in_asm.get(r['amark'], ()), k != prior(r), k))
        if not c: r['pos'] = r.get('pos11', ''); r['posWhy'] = 'no Part List row fits: ' + src + ' mark'; continue
        r['pos'] = c[0]; left[c[0]] -= 1
        r['posWhy'] = 'Part List row' + (' (in the assembly)' if c[0] in in_asm.get(r['amark'], ()) else '') + ('' if len(c) == 1 else ' (of %s)' % '/'.join(c[:6]))

# ---------------------------------------------------------------- main
def main():
    f = Ifc(IFC); mats = f.materials(); log('IFC: %d entities' % len(f.ents))
    parts = [i for t in PART_TYPES for i in f.by_type.get(t, [])]
    # assemblies
    asm_of, asm_kids = {}, collections.defaultdict(list)
    for r in f.by_type['IFCRELAGGREGATES']:
        a = f.a(r); par = f.ref(a[4])
        if f.t(par) != 'IFCELEMENTASSEMBLY': continue
        for c in a[5]: c = f.ref(c); asm_of[c] = par; asm_kids[par].append(c)
    pset = lambda p, s, k: f.psets(p).get(s, {}).get(k)
    weight = lambda p: float(pset(p, 'Tekla Quantity', 'Weight') or 0)
    main_of = {}
    for A, kids in asm_kids.items():
        pk = [k for k in kids if k in set(parts)]
        # the heaviest part; equal weights (a built-up member's two flanges) -> the one named like the assembly (Tekla names an
        # assembly after its main part), then the lower IFC id
        if pk: main_of[A] = max(pk, key=lambda k: (round(weight(k), 2), f.a(k)[2].strip() == (f.a(A)[2] or '').strip(), -k))
    # R11, only for the part marks (st.pos) and the folder list
    r11 = json.load(gzip.open(TEMPLATE)); prior = r11 if R11 == TEMPLATE else json.load(gzip.open(R11))
    rb = [b for b in prior['doc']['bodies'] if b.get('st') and b['st']['kind'] != 'bolts']   # (R12's concrete bodies have no st)

    # ---- bolts first: the hole solids, the bolt solids
    bolts = []
    for g in f.by_type['IFCMECHANICALFASTENER']:
        a = f.a(g); d, L = float(a[8]), float(a[9]); W, items = f.product_items(g); ps = f.psets(g).get('Tekla Bolt', {})
        per = collections.OrderedDict()
        for kind, ii, M in items:
            G = W @ M; o = tuple(np.round(G[:3, 3], 4)); per.setdefault(o, []).append((ii, G))
        grp = {'guid': a[0], 'id': g, 'd': d, 'L': L, 'std': ps.get('Bolt standard') or '', 'hole': float(ps.get('Bolt hole diameter') or 0), 'bolts': []}
        for o, lst in per.items():
            G0 = lst[0][1]; y = unit(G0[:3, 1]); O = G0[:3, 3]
            holes = []; solid = None
            for ii, G in lst:
                V, T = f.brep(ii); r = np.hypot(V[:, 0], V[:, 2]).max(); lv = np.unique(V[:, 1].round(2))
                if len(lv) > 4 and r > 0.7 * d: solid = (ii, G, V, T)
                else: holes.append((ii, G, V, T, r))
            # hole stack along y (local), Tekla leaves 0.1 mm between the hole solids of two plates
            hy = [(V[:, 1].min(), V[:, 1].max(), r) for _, _, V, _, r in holes]
            segs = []                                   # Tekla's hole solid per part: (y0, y1, r), one closed piece per part
            for _, _, V, T, r in holes:
                for comp in components(T): segs.append((float(V[comp, 1].min()), float(V[comp, 1].max()), float(r)))
            segs.sort()
            if solid is not None:
                V = solid[2]; lv = np.unique(V[:, 1].round(2)); rr = [np.hypot(V[np.abs(V[:, 1] - l) < 0.02, 0], V[np.abs(V[:, 1] - l) < 0.02, 2]).max() for l in lv]
                tail_hi = rr[-1] < 0.7 * d             # the shank's free end has the bolt radius only
                sgn = 1.0 if tail_hi else -1.0
                head_under = lv[1] if tail_hi else lv[-2]
            else: sgn, head_under = 1.0, None
            if hy: h0, h1 = min(h[0] for h in hy), max(h[1] for h in hy); hr = max(h[2] for h in hy)
            else: h0 = h1 = 0.0; hr = (grp['hole'] or d + 2) / 2
            if head_under is None: head_under = h0 if sgn > 0 else h1
            s0 = round(head_under * 10) / 10; far = (h1 if sgn > 0 else h0)
            grip = round(abs(far - s0) * 10) / 10
            # Tekla's hole solids stop 0.1 mm short of the outer faces: round the stack ends to 0.1 mm
            if solid is not None:                       # Tekla's grip: the head's underside to the washer (the bolt solid's levels)
                lv = np.unique(solid[2][:, 1].round(2)); grip = round(abs((lv[2] - lv[1]) if tail_hi else (lv[-2] - lv[-3])) * 10) / 10
            grp['bolts'].append({'O': O, 'y': y, 'sgn': sgn, 'P': O + y * s0, 'ax': y * sgn, 'grip': grip, 'h0': h0, 'h1': h1, 'hr': hr, 'solid': solid, 'segs': segs})
        bolts.append(grp)
    log('bolt groups %d, bolts %d' % (len(bolts), sum(len(g['bolts']) for g in bolts)))

    # ---- parts
    recs = []
    for p in parts:
        a = f.a(p); W, items = f.product_items(p); V, T = f.mesh(p)
        m = (mats.get(p) or '').split('/'); mat = m[-1] if m else ''
        A = asm_of.get(p)
        recs.append({'ifc': p, 'guid': a[0], 'name': a[2], 'tprof': a[3] or '', 'mat': mat, 'matcat': m[0] if m else '', 'W': W, 'items': items, 'V': V, 'T': T,
                     'cls': pset(p, 'Tekla Common', 'Class'), 'w': weight(p), 'len': pset(p, 'Tekla Quantity', 'Length'), 'asm': A,
                     'amark': f.a(A)[7] if A else '', 'aname': f.a(A)[2] if A else ''})
    recs.sort(key=lambda r: r['ifc'])
    for k, r in enumerate(recs): r['bid'] = k + 1
    bid_of = {r['ifc']: r['bid'] for r in recs}
    steel = [r for r in recs if r['matcat'] != 'CONCRETE']
    log('parts %d (steel / grout / grating %d, concrete %d)' % (len(recs), len(steel), len(recs) - len(steel)))

    # ---- part marks from R11 (name + material, nearest box)
    RBb = np.array([np.r_[np.array(b['shape']['vp']).reshape(-1, 3).min(0), np.array(b['shape']['vp']).reshape(-1, 3).max(0)] for b in rb])
    gi, gr = collections.defaultdict(list), collections.defaultdict(list)
    for k, r in enumerate(steel): gi[(r['name'].strip(), r['mat'])].append(k)
    for j, b in enumerate(rb): gr[(b['st']['name'].strip(), b['st']['mat'])].append(j)
    IBb = np.array([np.r_[r['V'].min(0), r['V'].max(0)] for r in steel]); match = {}
    for key, I in gi.items():
        J = gr.get(key, [])
        if not J: continue
        ci = (IBb[I, :3] + IBb[I, 3:]) / 2; cj = (RBb[J, :3] + RBb[J, 3:]) / 2
        si = np.sort(IBb[I, 3:] - IBb[I, :3], 1); sj = np.sort(RBb[J, 3:] - RBb[J, :3], 1)
        D = np.linalg.norm(ci[:, None] - cj[None], axis=2) + np.abs(si[:, None] - sj[None]).sum(2)
        ui, uj = set(), set()
        for x in np.argsort(D, axis=None):
            u, v = divmod(int(x), len(J))
            if u in ui or v in uj: continue
            ui.add(u); uj.add(v); match[I[u]] = J[v]
            if len(ui) == len(I) or len(uj) == len(J): break
    # second pass (Tekla renamed a few parts against R10: 'PLATE' / 'End Plate ', two flanges): same profile + material, nearest box
    used = set(match.values()); left = [k for k in range(len(steel)) if k not in match]; free = [j for j in range(len(rb)) if j not in used]
    for k in sorted(left, key=lambda k: steel[k]['bid']):
        r = steel[k]; c = [j for j in free if rb[j]['st'].get('tprof') == r['tprof'] and rb[j]['st']['mat'] == r['mat']]
        if not c: continue
        ci = (IBb[k, :3] + IBb[k, 3:]) / 2; j = min(c, key=lambda j: float(np.linalg.norm((RBb[j, :3] + RBb[j, 3:]) / 2 - ci)))
        match[k] = j; free.remove(j)
    for k, r in enumerate(steel): r['r11'] = rb[match[k]]['id'] if k in match else None; r['pos11'] = rb[match[k]]['st'].get('pos', '') if k in match else ''
    tekla_pos(steel)

    # ---- member sections (v69) and member fields
    mprofs = sorted({r['tprof'] for r in steel if not is_plate(r['tprof'])})
    sec = section_v69(mprofs); log('member profiles', mprofs)
    for r in steel:
        Inv = np.linalg.inv(r['W']); Vl = r['V'] @ Inv[:3, :3].T + Inv[:3, 3]
        if is_plate(r['tprof']):
            t = thick_of(r['tprof']); P, n = plate_fields(f, r['items'], r['W'], t)
            ext = r['V'] @ n; mid = (ext.min() + ext.max()) / 2; P = P + n * (mid - P @ n)[:, None]
            r['kind'] = 'plate'; r['t'] = t; r['pts3'] = [[r3(v) for v in q] for q in P]; r['thick_meas'] = float(ext.max() - ext.min())
            # NC frame (as R10's importer wrote it): N = the plate normal, X = Tekla's part x axis in the plate's plane (its length
            # direction), else the outline's longest edge; Y = N x X
            Xa = r['W'][:3, 0] - n * float(r['W'][:3, 0] @ n)
            if np.linalg.norm(Xa) < 0.5:
                e = max(((P[(k + 1) % len(P)] - P[k]) for k in range(len(P))), key=lambda v: float(np.linalg.norm(v))); Xa = e - n * float(e @ n)
            Xa = unit(Xa); r['ncF'] = {'X': [r3(v) for v in Xa], 'Y': [r3(v) for v in np.cross(n, Xa)], 'N': [r3(v) for v in n]}
        else:
            r['kind'] = 'member'; r['mf'], r['fit'] = solve_member(r['W'], Vl, sec, r['tprof'])

    # ---- prove the member fields: rebuild every member in v69 and compare its section box (Tekla local frame) with Tekla's
    lst = [{'id': str(r['bid']), 'st': dict(r['mf'], kind='member', prof=r['tprof'])} for r in steel if r['kind'] == 'member']
    lst += [{'id': str(r['bid']), 'st': {'kind': 'plate', 'prof': 'PL%g' % r['t'], 'pts3': r['pts3'], 'depth': 'Middle'}} for r in steel if r['kind'] == 'plate']
    built = v69_build(lst, 'parts')
    for r in steel:
        b = built[str(r['bid'])]
        if 'error' in b: r['v69'] = {'error': b['error']}; continue
        Vb = np.array(b['verts']); Inv = np.linalg.inv(r['W']); Lb = Vb @ Inv[:3, :3].T + Inv[:3, 3]; Lt = r['V'] @ Inv[:3, :3].T + Inv[:3, 3]
        r['v69'] = {'box': float(np.abs(np.r_[Lb.min(0), Lb.max(0)] - np.r_[Lt.min(0), Lt.max(0)]).max()), 'sec': float(np.abs(np.r_[Lb.min(0), Lb.max(0)][[1, 2, 4, 5]] - np.r_[Lt.min(0), Lt.max(0)][[1, 2, 4, 5]]).max())}

    # ---- holes: Tekla's hole solids. Each is one closed piece per part it goes through (Tekla stops it 0.1 mm inside both faces of
    # that part); the part = the solid that surrounds the piece (material just outside the hole radius at its mid-length, on two
    # opposite sides; so a rod or anchor standing inside a hole is not "joined", and is not drilled). That part gets the hole
    # (cylinder of Tekla's radius over the piece's length + one thickness each side, cut from that part only) and is a part the bolt
    # joins.
    man = {}
    for r in recs:
        m = to_manifold(r['V'], r['T']); man[r['bid']] = m
        if m is None: log('  part %d %s %s: brep is not a closed manifold, kept without holes' % (r['bid'], r['name'], r['tprof']))
    box = {r['bid']: np.r_[r['V'].min(0) - 1, r['V'].max(0) + 1] for r in recs}
    SB = np.array([box[r['bid']] for r in steel]); cuts = collections.defaultdict(list)
    cube = mf.Manifold.cube((0.2, 0.2, 0.2), True)
    def inside(m, P): return (m ^ cube.translate(tuple(float(x) for x in P))).volume() > 1e-6
    for g in bolts:
        g['parts'] = []
        for bt in g['bolts']:
            G0 = bt['solid'][1] if bt['solid'] is not None else None
            ex = unit(np.cross(bt['y'], [0, 0, 1.0]) if abs(bt['y'][2]) < 0.9 else np.cross(bt['y'], [1.0, 0, 0])); ez = np.cross(bt['y'], ex)
            hit = []
            for y0, y1, r in bt['segs']:
                ym = (y0 + y1) / 2; Pm = bt['O'] + bt['y'] * ym
                near = np.nonzero(~((SB[:, 3:] < Pm - r - 2).any(1) | (SB[:, :3] > Pm + r + 2).any(1)))[0]
                for k in near:
                    pr = steel[k]; m = man[pr['bid']]
                    if m is None: continue
                    if sum(inside(m, Pm + e * (r + 0.6)) for e in (ex, -ex, ez, -ez)) >= 2:
                        if pr['bid'] not in hit: hit.append(pr['bid'])
                        # through the part only (only this part is cut), so past a sloped face too: + one thickness (<= 15 mm) each side
                        ext = min(15.0, max(0.5, y1 - y0))
                        cuts[pr['bid']].append(cylinder(bt['O'] + bt['y'] * (y0 - ext), bt['y'], r, (y1 - y0) + 2 * ext))
            bt['hit'] = hit
            for h in hit:
                if h not in g['parts']: g['parts'].append(h)
        if g['bolts']:
            bt = g['bolts'][0]
            g['parts'].sort(key=lambda b: float(((box[b][:3] + box[b][3:]) / 2 - bt['P']) @ bt['ax']))
    for r in recs:
        m = man[r['bid']]
        if m is not None and cuts.get(r['bid']):
            h = cuts[r['bid']][0]
            for c in cuts[r['bid']][1:]: h = h + c
            m2 = m - h; r['V'], r['T'] = mesh_of(m2); r['holes'] = len(cuts[r['bid']])
        elif m is not None: r['V'], r['T'] = mesh_of(m)

    if MODELS[MODEL]['lists'] == 'package2': nc_refine(steel, bolts)

    # ---- welds
    welds = []
    rel = {}
    for x in f.by_type.get('IFCRELCONNECTSWITHREALIZINGELEMENTS', []):
        a = f.a(x)
        for e in a[7]: rel[f.ref(e)] = (f.ref(a[5]), f.ref(a[6]))
    for w in f.by_type.get('IFCFASTENER', []):
        a = f.a(w); ps = f.psets(w); c, ab, be = ps.get('Tekla Weld Common', {}), ps.get('Tekla Weld Above Line', {}), ps.get('Tekla Weld Below Line', {})
        V, _ = f.mesh(w); pr = rel.get(w, (None, None))
        welds.append({'guid': a[0], 'parts': [bid_of.get(pr[0]), bid_of.get(pr[1])], 'shop': c.get('Assembly type'), 'around': c.get('Edge/Around'),
                      'len': c.get('Length'), 'above': {'a': ab.get('Size1'), 'type': ab.get('Type1')}, 'below': {'a': be.get('Size2'), 'type': be.get('Type2')},
                      'box': [r3(v) for v in np.r_[V.min(0), V.max(0)]] if len(V) else None})

    # ---- the document
    doc = json.loads(json.dumps(r11['doc']))      # R11's document settings (grid, folders, units); bodies / connections replaced
    bodies, conns = [], []
    ST_TYPE = {}
    main_bid = {A: bid_of[m] for A, m in main_of.items()}
    for r in recs:
        if r['matcat'] == 'CONCRETE':
            bodies.append({'id': r['bid'], 'name': '%s %d' % (r['name'], r['bid']), 'color': 0xa0a0a0, 'hidden': False, 'folder28': 'Concrete (Tekla)',
                           'tk': {'guid': r['guid'], 'prof': r['tprof'], 'mat': r['mat'], 'amark': r['amark'], 'w': r['w']}, 'shape': shape(r['V'], r['T'])})
            continue
        st = {'kind': r['kind'], 'name': r['name'], 'prof': ('PL%g' % r['t']) if r['kind'] == 'plate' else r['tprof'], 'mat': r['mat'],
              'cls': int(float(r['cls'])) if r['cls'] not in (None, '') else 0, 'fin': 'PAINT', 'pp': '', 'ps': 1, 'ap': '', 'as': 1,
              'tk': r['guid'], 'tprof': r['tprof']}
        if r['kind'] == 'plate': st.update({'depth': 'Middle', 'pts3': r['pts3'], 'ncF': r['ncF']})
        else: st.update(r['mf'])
        st.update({'pos': r['pos'], 'amark': r['amark'], 'tkW': r['w']})
        if r['len'] is not None: st['tkL'] = r3(r['len'])
        mb = main_bid.get(r['asm'])
        if mb is not None and mb != r['bid']: st['asm'] = mb
        typ = 'stPlate' if r['kind'] == 'plate' else 'stBeam'
        bodies.append({'id': r['bid'], 'name': '%s %d' % (r['name'], r['bid']), 'color': 0x8e8e93, 'hidden': False, 'type': typ, 'st': st, 'shape': shape(r['V'], r['T'])})
    nid = len(recs) + 1; cid = 1
    for g in bolts:
        if not g['bolts']: continue
        b0 = g['bolts'][0]; holes_only = all(bt['solid'] is None for bt in g['bolts'])
        grade = g['std'].replace('XOX', '').strip() or '8.8'
        q = {'pts': [[r3(v) for v in bt['P']] for bt in g['bolts']], 'ax': [r3(v) for v in b0['ax']], 'd': g['d'], 'grip': b0['grip'], 'tol': 2, 'tk': g['guid'],
             'L': g['L'], 'grade': grade, 'hole': g['hole']}
        if holes_only: q['holesOnly'] = True
        conns.append({'id': cid, 'type': 'TKBOLT', 'parts': g['parts'], 'q': q, 'welds': []})
        if not holes_only:
            Vs, Ts, n = [], [], 0
            for bt in g['bolts']:
                if bt['solid'] is None: continue
                ii, G, V, T = bt['solid']; Vw = V @ G[:3, :3].T + G[:3, 3]; Vs.append(Vw); Ts.append(T + n); n += len(V)
            bodies.append({'id': nid, 'name': 'BOLTS %d' % nid, 'color': 0x5a5f66, 'hidden': False, 'type': 'stBolt',
                           'st': {'kind': 'bolts', 'conn': cid, 'name': 'BOLTS', 'prof': 'M%g' % g['d'], 'pts': q['pts'], 'ax': q['ax'], 'd': g['d'], 'n': len(g['bolts']), 'grade': grade, 'L': g['L']},
                           'shape': shape(np.vstack(Vs), np.vstack(Ts))})
            nid += 1
        cid += 1
    doc['bodies'] = bodies; doc['stConns'] = conns; doc['stNextConn'] = cid; doc['nextId'] = nid; doc['colorIdx'] = nid
    wsec = collections.defaultdict(list)
    for w in welds:
        if w['parts'][1] is not None: wsec[w['parts'][1]].append({k: v for k, v in w.items() if k != 'parts'} | {'main': w['parts'][0]})
    for b in bodies:
        if b.get('st') and b['id'] in wsec: b['st']['tkWelds'] = wsec[b['id']]
    if not any(fd.get('name') == 'Concrete (Tekla)' for fd in doc.get('folders', [])): doc.setdefault('folders', []).append({'id': 1795630776001, 'name': 'Concrete (Tekla)', 'type': 'concrete', 'open': False})
    out = {k: v for k, v in r11.items() if k not in ('doc', 'r11')}
    out['name'] = 'Frame_28x53m_' + MODEL
    out[MODEL.lower()] = {'from': MODELS[MODEL]['ifcName'] + ' (Tekla Structures 2025 SP6, IFC2X3 CV2.0)', 'tool': 'tools/golden/r12_build.py',
                  'parts': len(steel), 'concrete': len(recs) - len(steel), 'boltGroups': len(conns), 'welds': len(welds)}
    out['doc'] = doc
    data = json.dumps(jsnum(out), separators=(',', ':'), ensure_ascii=False).encode()
    with open(R12, 'wb') as fh:
        with gzip.GzipFile(fileobj=fh, mode='wb', mtime=0, filename='') as gz: gz.write(data)
    log('wrote', R12, os.path.getsize(R12))
    # per-part data for r12_compare.py
    os.makedirs(OUT, exist_ok=True)
    json.dump({'parts': [{k: (v if not isinstance(v, np.ndarray) else v.tolist()) for k, v in r.items() if k in ('bid', 'ifc', 'guid', 'name', 'tprof', 'mat', 'kind', 'cls', 'w', 'len', 'amark', 'aname', 'r11', 'pos', 'fit', 'v69', 'holes', 't', 'thick_meas', 'pos11', 'posWhy', 'pos12', 'pos12to13')} for r in recs],
               'main': {f.a(A)[7] + '#' + str(A): bid_of[m] for A, m in main_of.items()},
               'bolts': [{'guid': g['guid'], 'd': g['d'], 'L': g['L'], 'std': g['std'], 'n': len(g['bolts']), 'parts': g['parts'], 'grip': [bt['grip'] for bt in g['bolts']],
                          'hits': [bt['hit'] for bt in g['bolts']], 'holesOnly': all(bt['solid'] is None for bt in g['bolts'])} for g in bolts]},
              open(os.path.join(OUT, 'parts.json'), 'w'))

def tekla_pos2(steel):
    """R13 (Package_2): Tekla renumbered every part after the fix, so the R12 mark is first translated to the new numbering: the old
    Part List (Package_1) and the new one (SingleList.xsr), each in mark order, are aligned on (grade, length, weight of one part);
    an R12 mark in an aligned pair becomes the new mark of that pair, any other R12 mark is tried as it is. The assembly -> part marks come from the material list of each
    assembly drawing (Assembley.PDF, read with pdftotext), as Package_2 has no Assembly Part List report."""
    import difflib
    plist = single_list()
    old = part_list1(MODELS[MODEL]['oldPkg'])
    ko = sorted(old, key=mark_key); kn = sorted(plist, key=mark_key)
    key = lambda x: (x['mat'], int(round(x['len'])), round(x['w'], 1))
    sm = difflib.SequenceMatcher(None, [key(old[k]) for k in ko], [key(plist[k]) for k in kn], autojunk=False)
    tr = {}
    for a, b, n in sm.get_matching_blocks():
        for i in range(n): tr[ko[a + i]] = kn[b + i]
    for r in steel: r['pos12'] = r.get('pos11', ''); r['pos12to13'] = tr.get(r['pos12'], r['pos12'])
    in_asm = asm_drawing_lists()
    # SingleList.xsr gives one part's weight to 0.1 kg (6524: 12.4 for Tekla's 12.46 kg): 0.1 kg here
    place_pos(steel, plist, in_asm, 'R12', prior=lambda r: r['pos12to13'], tol=0.1)

def nc_holes(pkg=None):
    """Package_2 NC/<mark>.nc1 (DSTV) of plates ('B'): mark -> (length, width, holes [(x, y, d)])"""
    import zipfile, re
    out = {}
    with zipfile.ZipFile(pkg or MODELS['R13']['pkg']) as z:
        for n in z.namelist():
            if '/NC/' not in n or not n.lower().endswith('.nc1'): continue
            L = [x.strip() for x in z.read(n).decode('latin-1').splitlines()]
            if len(L) < 12 or L[9] != 'B': continue
            hs, sec = [], None
            for x in L[12:]:
                if x in ('AK', 'BO', 'IK', 'SI', 'KO', 'PU', 'KA', 'EN'): sec = x; continue
                if sec == 'BO' and x.startswith('v'):
                    t = re.findall(r'-?\d+(?:\.\d*)?', x[1:]); hs.append((float(t[0]), float(t[1]), float(t[2])))   # x (+ a reference letter), y, d
            out[os.path.basename(n)[:-4]] = (float(L[10]), float(L[11]), hs)
    return out

def hole_sig(L, W, hs):
    """a plate's holes, independent of which face is up and which end is first: per hole its distance to the nearer end and the nearer
    long edge (0.5 mm)"""
    if W > L: L, W, hs = W, L, [(y, x, d) for x, y, d in hs]
    return tuple(sorted((round(min(x, L - x) * 2) / 2, round(min(y, W - y) * 2) / 2) for x, y, d in hs))

def nc_refine(steel, bolts):
    """R13: where the part list leaves a choice (identical rows of several marks, e.g. the column shear plates FLT10*150 L210 =
    4506 / 4514 / 4515), the mark is the one whose NC file (Package_2 NC/) has the part's holes. The part's holes = the axes of the bolts
    that go through it, met with its mid-plane, in its plate frame (ncF). Each mark keeps its count; parts with no NC file or no unique
    hole pattern keep their mark."""
    nc = nc_holes()
    holes = collections.defaultdict(list)
    for g in bolts:
        for bt in g['bolts']:
            for b in bt.get('hit', []): holes[b].append((bt['O'], bt['y']))
    def sig(r):
        X, Y, N = (np.array(r['ncF'][k]) for k in 'XYN'); P = np.array(r['pts3']); P0 = P[0]
        u, v = (P - P0) @ X, (P - P0) @ Y; hs = []
        for O, y in holes.get(r['bid'], []):
            den = float(y @ N)
            if abs(den) < 1e-6: continue
            H = O + y * float((P0 - O) @ N) / den; hs.append((float((H - P0) @ X - u.min()), float((H - P0) @ Y - v.min()), 0))
        return hole_sig(float(u.max() - u.min()), float(v.max() - v.min()), hs)
    # the choice: the marks of the rows an ambiguous part fits (posWhy '(of a/b/c)'); the group = every plate holding one of those marks
    groups = {}
    for r in steel:
        if r.get('kind') == 'plate' and ' (of ' in r.get('posWhy', ''): groups[tuple(sorted(r['posWhy'].split(' (of ')[1].split(')')[0].split('/')))] = None
    groups = {K: [r for r in steel if r.get('kind') == 'plate' and r.get('pos') in K] for K in groups}
    for K, rs in groups.items():
        ks = [k for k in K if k in nc]
        if len(ks) < 2: continue
        ksig = {k: hole_sig(*nc[k]) for k in ks}
        if len(set(ksig.values())) < len(ks): continue
        pool = collections.Counter(r['pos'] for r in rs)
        want = {id(r): [k for k in ks if ksig[k] == sig(r)] for r in rs}
        if any(len(w) != 1 for w in want.values()): log('  nc_refine %s: a part matches no NC hole pattern, marks kept' % '/'.join(K)); continue
        need = collections.Counter(want[id(r)][0] for r in rs)
        if need != pool: log('  nc_refine %s: NC hole patterns give %s, the part list %s; marks kept' % ('/'.join(K), dict(need), dict(pool))); continue
        for r in rs:
            k = want[id(r)][0]
            if k != r['pos']: r['posWhy'] += '; NC holes: %s -> %s' % (r['pos'], k); r['pos'] = k
            else: r['posWhy'] += '; NC holes agree'

def mark_key(m):
    """Tekla's report order: numbers first by value, then marks with a prefix (F1, F3501)"""
    return (0, int(m), '') if m.isdigit() else (1, 0, m)

def pkg2_read(z, name):
    n = next(x for x in z.namelist() if x.endswith('/' + name)); return z.read(n)

def single_list(pkg=None):
    """Package_2 SingleList.xsr (Tekla's part list): mark -> profile, count, grade, length, weight of one part (grout not listed)"""
    import zipfile, re
    with zipfile.ZipFile(pkg or MODELS['R13']['pkg']) as z: t = pkg2_read(z, 'SingleList.xsr').decode('latin-1')
    out = {}
    for line in t.splitlines():
        m = re.match(r'\s{4}(\S+)\s+(\S+)\s+(\d+)\s+(\S+)\s+(\d+)\s+([\d.]+)\s+([\d.]+)\s*$', line)
        if m and m.group(1) != 'PartPos': out[m.group(1)] = dict(prof=m.group(2), n=int(m.group(3)), mat=m.group(4), len=float(m.group(5)), w=float(m.group(7)))
    return out

def asm_drawing_lists(pkg=None):
    """assembly mark (the drawing's file name) -> {part mark: No. in one assembly} of its 'MATERIAL LIST FOR ASSEMBLY' table, from each Package_2 assembly
    drawing (Assembley.PDF). Words are read with their boxes (pdftotext -bbox): the table is the words right of and below the header
    word 'Mark' (with 'Profile' beside it); one row = the words of one top line and the header's text height: mark, profile, material, No., length, area, weight."""
    import zipfile, re, tempfile, html
    res = collections.defaultdict(dict)
    wre = re.compile(r'<word xMin="([-\d.]+)" yMin="([-\d.]+)" xMax="([-\d.]+)" yMax="([-\d.]+)">(.*?)</word>')
    with zipfile.ZipFile(pkg or MODELS['R13']['pkg']) as z, tempfile.TemporaryDirectory() as td:
        for n in sorted(x for x in z.namelist() if '/Assembley.PDF/' in x and x.lower().endswith('.pdf')):
            mark = os.path.basename(n).split(' - ')[0]
            fp = os.path.join(td, 'a.pdf'); open(fp, 'wb').write(z.read(n))
            t = subprocess.run(['pdftotext', '-bbox', fp, '-'], check=True, capture_output=True).stdout.decode('utf-8', 'replace')
            for page in t.split('<page ')[1:]:
                W = [(float(x0), float(y0), float(x1), float(y1), html.unescape(w)) for x0, y0, x1, y1, w in wre.findall(page)]
                hd = [w for w in W if w[4] == 'Mark' and any(v[4] == 'Profile' and abs(v[1] - w[1]) < 2 and 0 < v[0] - w[2] < 120 for v in W)]
                for h in hd:
                    hh = h[3] - h[1]
                    rows = collections.defaultdict(list)
                    for w in W:
                        # table text: the header's text height, every cell of a row on the same top line (drawing labels are bigger)
                        if w[0] >= h[0] - hh and w[1] > h[3] - 1 and abs((w[3] - w[1]) - hh) < 0.5: rows[round(w[1], 0)].append(w)
                    for k in sorted(rows):
                        ws = [w[4] for w in sorted(rows[k])]
                        if ws and ws[0] == 'Total': break
                        if len(ws) == 7 and re.fullmatch(r'\d+', ws[3]) and re.fullmatch(r'[\d.]+', ws[6]): res[mark][ws[0]] = res[mark].get(ws[0], 0) + int(ws[3])
    return res

def setup(name):
    global MODEL, IFC, R11, R12, OUT, TEMPLATE, PKG
    c = MODELS[name]; MODEL = name; IFC, R11, R12, OUT, TEMPLATE, PKG = c['ifc'], c['prior'], c['out'], c['work'], c['template'], c['pkg']

if __name__ == '__main__':
    a = sys.argv[1:]; setup(a[0] if a else 'R12')
    if len(a) > 1: R12 = os.path.abspath(a[1])
    main()
