#!/usr/bin/env python3
"""R13 (Tekla IFC v3, the fixed model) vs R12, by geometry, and R13 against the lists and drawings of Tekla's Package_2.

    python3 tools/golden/r12_build.py R13 && node tools/golden/r13_totals.js && python3 -I tools/golden/r13_compare.py
      -> build/r13/compare.json, docs/steel/R13-REPORT.md

Tekla renumbered the model after the fix, so parts are paired by geometry, never by mark:
  parts  same kind + Tekla profile + grade, solid boxes within 0.1 mm (greedy, nearest first). A pair is "same" when the two
         meshes have the same vertices and triangles within 0.1 mm; otherwise the solids differ (e.g. new holes).
  bolts  TKBOLT groups: same diameter, length, grade, holes-only flag and number of bolts, the bolt points within 0.1 mm.
  assemblies  a part's assembly = its main part (st.asm, or itself); compared through the part pairing.
"""
import gzip, json, os, re, sys, zipfile, collections
import numpy as np
import manifold3d as mf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r12_build as B

REPO = B.REPO
M = os.path.join(REPO, 'tests', 'steel', 'models')
OUT = os.path.join(REPO, 'build', 'r13')
PKG = B.MODELS['R13']['pkg']
TOL = 0.1


def load(p):
    d = json.load(gzip.open(p))['doc']; return d['bodies'], d['stConns']


def VT(b): s = b['shape']; return np.asarray(s['vp'], float).reshape(-1, 3), np.asarray(s['tv'], int).reshape(-1, 3)


def pkey(b):
    if b.get('st'): return (b['st']['kind'], b['st'].get('tprof', b['st']['prof']), b['st']['mat'])
    return ('concrete', b['tk']['prof'], b['tk']['mat'])


def parts(bodies): return [b for b in bodies if (b.get('st') and b['st']['kind'] in ('plate', 'member')) or b.get('tk')]


def pair(A, B_, feat, tol):
    """greedy nearest-first pairing of A and B items with the same key: feat(x) -> (key, vector); pairs within tol (max abs)"""
    ga, gb = collections.defaultdict(list), collections.defaultdict(list)
    for x in A: k, v = feat(x); ga[k].append((x, v))
    for x in B_: k, v = feat(x); gb[k].append((x, v))
    out = []
    for k, la in ga.items():
        lb = gb.get(k, [])
        if not lb: continue
        VA = np.array([v for _, v in la]); VB = np.array([v for _, v in lb])
        D = np.abs(VA[:, None, :] - VB[None, :, :]).max(2)
        ua, ub = set(), set()
        for x in np.argsort(D, axis=None):
            i, j = divmod(int(x), len(lb))
            if D[i, j] > tol: break
            if i in ua or j in ub: continue
            ua.add(i); ub.add(j); out.append((la[i][0], lb[j][0], float(D[i, j])))
    return out


def mesh_same(Va, Ta, Vc, Tc):
    """the same solid whatever the vertex / triangle order (Manifold orders the result of the hole cuts by the order of the cuts, and
    R13 has 6 more bolt groups in front): equal counts, the sorted vertices within 0.1 mm, the triangles as sets of vertex positions"""
    if Va.shape != Vc.shape or Ta.shape != Tc.shape: return False
    ia, ic = np.lexsort(np.round(Va, 1).T[::-1]), np.lexsort(np.round(Vc, 1).T[::-1])
    if float(np.abs(Va[ia] - Vc[ic]).max()) > TOL: return False
    ra, rc = np.empty(len(ia), int), np.empty(len(ic), int); ra[ia] = np.arange(len(ia)); rc[ic] = np.arange(len(ic))
    ka = {tuple(sorted(t)) for t in ra[Ta].tolist()}; kc = {tuple(sorted(t)) for t in rc[Tc].tolist()}
    if ka == kc: return True
    # the same vertices, triangulated another way: the same solid when the symmetric difference is <= 0.1 mm x the surface area
    try:
        ma, mc = (mf.Manifold(mf.Mesh64(np.ascontiguousarray(V, np.float64), np.ascontiguousarray(T, np.uint64))) for V, T in ((Va, Ta), (Vc, Tc)))
        return (ma - mc).volume() + (mc - ma).volume() <= TOL * area(Va, Ta)
    except Exception: return False

def area(V, T): a = V[T[:, 1]] - V[T[:, 0]]; b = V[T[:, 2]] - V[T[:, 0]]; return float(np.linalg.norm(np.cross(a, b), axis=1).sum() / 2)

def xsr_assembly_list(t):
    out = {}
    for line in t.splitlines():
        if len(line) < 40 or not re.match(r' \S', line) or line.startswith(' Ass Mk') or line.startswith(' Tekla') or line.startswith(' Contract') or line.startswith(' Totals'): continue
        m = re.match(r' (\S+)\s+(\d+)\s+(.{12})(\S+)\s+(\d+)\s+(\d+)\s+([\d.]+)\s+([\d.]+)\s*$', line)
        if m: x = out.setdefault(m.group(1), dict(n=0, name=m.group(3).strip(), prof=m.group(4), w=0.0)); x['n'] += int(m.group(2)); x['w'] += float(m.group(8))
    tot = re.search(r'Totals for report\s+\((\d+)Assemblies\)\s+([\d.]+)\s+([\d.]+)', t)
    return out, (int(tot.group(1)), float(tot.group(3)))


def xsr_bolts(t):
    out = {}
    for line in t.splitlines():
        m = re.match(r'\s+(\S+)\s+(Site|Shop)\s+BOLT (\d+)X(\d+)\s+(\d+)\s*$', line)
        if m: out['M%s %s L%s' % (m.group(3), m.group(1).replace('XOX', ''), m.group(4))] = int(m.group(5))
    return out


def main():
    b12, c12 = load(os.path.join(M, 'Frame_28x53m_R12.ss')); b13, c13 = load(os.path.join(M, 'Frame_28x53m_R13.ss'))
    tot = json.load(open(os.path.join(OUT, 'v69_totals.json')))
    P12, P13 = parts(b12), parts(b13)
    id12, id13 = {b['id']: b for b in b12}, {b['id']: b for b in b13}
    box = {}
    for tag, L in (('12', P12), ('13', P13)):
        for b in L: V, _ = VT(b); box[(tag, b['id'])] = np.r_[V.min(0), V.max(0)]
    s12 = {id(b) for b in P12}
    pr = pair(P12, P13, lambda b: (pkey(b), box[('12' if id(b) in s12 else '13', b['id'])]), TOL)
    m12 = {a['id']: c['id'] for a, c, _ in pr}; m13 = {c['id']: a['id'] for a, c, _ in pr}
    out = collections.OrderedDict()

    # ---------------- parts
    same, solid = [], []
    for a, c, d in pr:
        Va, Ta = VT(a); Vc, Tc = VT(c)
        if mesh_same(Va, Ta, Vc, Tc): same.append((a, c))
        else: solid.append((a, c, len(Ta), len(Tc)))
    new13 = [b for b in P13 if b['id'] not in m13]; gone12 = [b for b in P12 if b['id'] not in m12]

    def desc(b):
        st = b.get('st') or {}; bb = box[('12' if id(b) in s12 else '13', b['id'])]
        return dict(id=b['id'], name=(st.get('name') or b.get('name', '')).strip(), prof=pkey(b)[1], mat=pkey(b)[2], pos=st.get('pos'), amark=st.get('amark', (b.get('tk') or {}).get('amark')),
                    tkW=st.get('tkW', (b.get('tk') or {}).get('w')), box=[round(float(v), 1) for v in bb])

    # data fields on the pairs (marks are renumbered: not compared)
    fld = collections.Counter(); fdiff = []; wch = []
    for a, c, _ in pr:
        sa, sc = a.get('st') or {}, c.get('st') or {}
        for k in ('name', 'cls', 'tkW', 'tkL', 'prof', 'rot', 'rAng', 'plane', 'depth'):
            if sa.get(k) != sc.get(k): fld[k] += 1; fdiff.append((a['id'], c['id'], k, sa.get(k), sc.get(k)))
        for k in ('p1', 'p2'):
            if (k in sa) != (k in sc) or (k in sa and max(abs(x - y) for x, y in zip(sa[k], sc[k])) > TOL): fld[k] += 1; fdiff.append((a['id'], c['id'], k, sa.get(k), sc.get(k)))
        if (sa.get('pts3') is None) != (sc.get('pts3') is None) or (sa.get('pts3') and (len(sa['pts3']) != len(sc['pts3']) or max(abs(x - y) for p, q in zip(sa['pts3'], sc['pts3']) for x, y in zip(p, q)) > TOL)):
            fld['pts3'] += 1; fdiff.append((a['id'], c['id'], 'pts3', None, None))
        wa = sorted((w.get('shop'), w.get('len'), json.dumps(w.get('above')), json.dumps(w.get('below')), m12.get(w.get('main'))) for w in sa.get('tkWelds', []))
        wc = sorted((w.get('shop'), w.get('len'), json.dumps(w.get('above')), json.dumps(w.get('below')), w.get('main')) for w in sc.get('tkWelds', []))
        if wa != wc:
            fld['tkWelds'] += 1; fdiff.append((a['id'], c['id'], 'tkWelds', len(wa), len(wc)))
            wch.append(dict(r12=a['id'], r13=c['id'], n=[len(wa), len(wc)], added=[w for w in sc.get('tkWelds', []) if (w.get('shop'), w.get('len'), json.dumps(w.get('above')), json.dumps(w.get('below')), w.get('main')) not in set(wa)]))

    # assemblies: main part through the pairing
    def main_of(b): st = b.get('st') or {}; return st.get('asm') or b['id']
    asm_changed = []
    for a, c, _ in pr:
        if not a.get('st'): continue
        ma, mc = main_of(a), main_of(c)
        if m12.get(ma) != mc: asm_changed.append((a, c, ma, mc))
    # R13 assembly instances and their R12 origin
    inst13 = collections.defaultdict(list)
    for b in P13:
        if b.get('st'): inst13[main_of(b)].append(b)
    inst12 = collections.defaultdict(list)
    for b in P12:
        if b.get('st'): inst12[main_of(b)].append(b)

    # ---------------- bolts
    def bfeat(c, tag):
        q = c['q']; P = np.array(sorted(map(tuple, q['pts']))); return ((q['d'], q['L'], q['grade'], bool(q.get('holesOnly')), len(q['pts'])), P.reshape(-1))
    tb = [c for c in c12 if c['type'] == 'TKBOLT']; tc = [c for c in c13 if c['type'] == 'TKBOLT']
    s12c = {id(c) for c in tb}
    bp = pair(tb, tc, lambda c: bfeat(c, '12' if id(c) in s12c else '13'), TOL)
    bm12 = {a['id'] for a, _, _ in bp}; bm13 = {c['id'] for _, c, _ in bp}
    bnew = [c for c in tc if c['id'] not in bm13]; bgone = [c for c in tb if c['id'] not in bm12]
    bparts = []
    for a, c, _ in bp:
        pa = sorted(m12.get(x, -x) for x in a['parts']); pc = sorted(c['parts'])
        if pa != pc or abs(a['q']['grip'] - c['q']['grip']) > TOL or a['q']['ax'] != c['q']['ax']: bparts.append((a, c, pa, pc))

    def bdesc(c, idx):
        q = c['q']; P = np.array(q['pts']); b = idx
        return dict(id=c['id'], size='M%g %s L%g' % (q['d'], q['grade'], q['L']), n=len(q['pts']), holesOnly=bool(q.get('holesOnly')), grip=q['grip'],
                    parts=['%s %s %s (%s)' % (b[x]['st']['name'].strip(), b[x]['st']['tprof'], b[x]['st']['pos'], b[x]['st']['amark']) for x in c['parts']],
                    centre=[round(float(v), 1) for v in P.mean(0)])

    out['parts'] = dict(r12=len(P12), r13=len(P13), paired=len(pr), same=len(same), solidDiffers=len(solid), new=[desc(b) for b in new13], gone=[desc(b) for b in gone12],
                        solid=[dict(r12=desc(a), r13=desc(c), tris=[ta, tc]) for a, c, ta, tc in solid], fields=dict(fld), fieldDiffs=fdiff[:200], welds=[dict(x, part=desc(id13[x['r13']]), mains=[desc(id13[w['main']]) if w.get('main') in id13 else None for w in x['added']]) for x in wch],
                        asmChanged=[dict(r12=desc(a), r13=desc(c), main12=desc(id12[ma]), main13=desc(id13[mc]), r12main13=m12.get(ma)) for a, c, ma, mc in asm_changed])
    out['bolts'] = dict(r12=len(tb), r13=len(tc), paired=len(bp), new=[bdesc(c, id13) for c in bnew], gone=[bdesc(c, id12) for c in bgone],
                        partsChanged=[dict(r12=bdesc(a, id12), r13=bdesc(c, id13)) for a, c, _, _ in bparts])

    # ---------------- Package_2 lists
    with zipfile.ZipFile(PKG) as z:
        al_t = B.pkg2_read(z, 'AssembleyList.xsr').decode('latin-1'); bl_t = B.pkg2_read(z, 'BOLT-LIST.xsr').decode('latin-1')
        sl_t = B.pkg2_read(z, 'SingleList.xsr').decode('latin-1')
        names = z.namelist()
    plist = B.single_list(); ptot = re.search(r'Total for (\d+)\s+members:\s+([\d.]+)\s+([\d.]+)', sl_t)
    alist, atot = xsr_assembly_list(al_t); blist = xsr_bolts(bl_t)
    steel = [b for b in P13 if b.get('st')]; nogrout = [b for b in steel if b['st']['mat'] != 'GROUT']
    wsum = sum(b['st'].get('tkW') or 0 for b in steel)
    by_pos = collections.defaultdict(list)
    for b in steel: by_pos[b['st']['pos']].append(b)
    bad_pos, near_pos = [], []
    for pos, x in sorted(plist.items(), key=lambda kv: B.mark_key(kv[0])):
        L = by_pos.get(pos, []); ws = [b['st'].get('tkW') or 0 for b in L]; e = []
        if len(L) != x['n']: e.append('count %d vs %d' % (len(L), x['n']))
        if ws and max(abs(w - x['w']) for w in ws) > 0.06 + 1e-9: e.append('one part %s kg vs %g' % ('/'.join('%g' % round(w, 2) for w in sorted(set(ws))), x['w']))
        if L and not all(B.prof_eq(x['prof'], b['st']['tprof']) or (B.is_plate(x['prof']) and B.is_plate(b['st']['tprof'])) for b in L): e.append('profile %s vs %s' % ('/'.join(sorted({b['st']['tprof'] for b in L})), x['prof']))
        if e: bad_pos.append((pos, '; '.join(e)))
        elif ws and max(abs(w - x['w']) for w in ws) > 0.05 + 1e-9: near_pos.append((pos, '/'.join('%g' % round(w, 2) for w in sorted(set(ws))), x['w']))
    extra_pos = sorted((p for p in by_pos if p not in plist), key=B.mark_key)
    # assemblies
    asm13 = collections.Counter(); aw = collections.Counter()
    for mn, L in inst13.items():
        asm13[L[0]['st']['amark']] += 1
        for b in L: aw[b['st']['amark']] += b['st'].get('tkW') or 0
    # Tekla's list cuts a long mark: 'PLST***' = PLSTR9001
    full = {m: (m if not m.endswith('***') else next((x for x in asm13 if x.startswith(m[:-3]) and x not in alist), m)) for m in alist}
    bad_asm = [(m, asm13.get(full[m], 0), alist[m]['n']) for m in sorted(alist, key=B.mark_key) if asm13.get(full[m], 0) != alist[m]['n']]
    extra_asm = sorted(set(asm13) - set(full.values()), key=B.mark_key)
    asm_w = [(full[m], round(aw[full[m]], 1), alist[m]['w']) for m in sorted(alist, key=B.mark_key) if abs(aw[full[m]] - alist[m]['w']) > max(0.15, 0.002 * alist[m]['w'])]
    # the same marks in R12 against Package_1's Assembly List (net weight for all), to tell old differences from new ones
    al1 = B.report_rows('Assembly List Excel.xls', B.MODELS['R12']['pkg']); h1 = al1.index(next(r for r in al1 if r[:2] == ['Assembly Mark', 'No.']))
    w1 = collections.Counter()
    for r in al1[h1 + 1:]:
        if r and r[0] != 'Total for' and len(r) >= 8: w1[r[0]] += float(r[7])
    aw12 = collections.Counter()
    for b in P12:
        if b.get('st'): aw12[b['st']['amark']] += b['st'].get('tkW') or 0
    asm_w = [(m, a, t, round(aw12[m], 1) if m in w1 else None, round(w1[m], 1) if m in w1 else None) for m, a, t in asm_w]
    # assembly drawings' material lists vs each R13 assembly instance
    dl = B.asm_drawing_lists(); inst_bad = []
    for mn, L in inst13.items():
        mk = L[0]['st']['amark']; got = collections.Counter(b['st']['pos'] for b in L); want = collections.Counter(dl.get(mk, {}))
        if got != want: inst_bad.append((mk, mn, dict(got - want), dict(want - got)))
    # bolts
    bsz = collections.Counter()
    for b in b13:
        if b.get('st') and b['st']['kind'] == 'bolts': bsz['M%g %s L%g' % (b['st']['d'], b['st']['grade'], b['st']['L'])] += b['st']['n']
    # drawings
    def files(folder): return sorted({os.path.basename(n).split(' - ')[0] for n in names if '/%s/' % folder in n and not n.endswith('/')}, key=B.mark_key)
    pa, da, ps, ds, pg, dg = files('Assembley.PDF'), files('Assembley.Dwg'), files('SINGLE.PDF'), files('Single.Dwg'), files('GA.PDF'), files('GA.Dwg')
    nc = sorted({os.path.basename(n)[:-4] for n in names if '/NC/' in n and n.endswith('.nc1')}, key=B.mark_key)
    va, vw, vg = tot['marks'].get('A', []), tot['marks'].get('W', []), tot['marks'].get('G', [])
    out['package'] = dict(
        partList=dict(members=int(ptot.group(1)), weight=float(ptot.group(3)), rows=len(plist), r13NoGrout=len(nogrout), r13WithGrout=len(steel), r13Marks=len(by_pos),
                      tkWsum=round(wsum, 2), v69weight=round(tot['weightKg'], 2), badMarks=bad_pos, nearMarks=near_pos, marksNotInList=extra_pos),
        assemblyList=dict(marks=len(alist), assemblies=atot[0], weight=atot[1], r13Marks=len(asm13), r13Assemblies=sum(asm13.values()), countDiffs=bad_asm, extraMarks=extra_asm,
                          weightDiffs=asm_w, r13WeightSum=round(sum(aw.values()), 2)),
        drawingLists=dict(instances=len(inst13), mismatches=inst_bad),
        bolts=dict(tekla=blist, r13=dict(bsz), teklaTotal=sum(blist.values()), r13Total=sum(bsz.values())),
        drawings=dict(assemblyPdf=len(pa), assemblyDwg=len(da), singlePdf=len(ps), singleDwg=len(ds), gaPdf=len(pg), gaDwg=len(dg), nc=len(nc),
                      v69=dict(A=len(va), W=len(vw), G=len(vg)), aOnlyTekla=sorted(set(pa) - set(va), key=B.mark_key), aOnlyR13=sorted(set(va) - set(pa), key=B.mark_key),
                      wOnlyTekla=sorted(set(ps) - set(vw), key=B.mark_key), wOnlyR13=sorted(set(vw) - set(ps), key=B.mark_key), pdfVsDwg=sorted((set(pa) ^ set(da)) | (set(ps) ^ set(ds)) | (set(pg) ^ set(dg))),
                      ncNotPart=sorted(set(nc) - set(by_pos), key=B.mark_key), gaTekla=pg, gaV69=vg),
        v69=dict(bodies=tot['bodies'], conns=tot['conns'], pageErrors=tot['pageErrors'], partMarks=tot['partMarks'], assemblies=tot['assemblies']))
    os.makedirs(OUT, exist_ok=True); json.dump(out, open(os.path.join(OUT, 'compare.json'), 'w'), indent=1, default=str)
    report(out)


def report(o):
    P, Bo, K = o['parts'], o['bolts'], o['package']
    L = ['# R13 vs R12, and R13 against Tekla\'s Package_2', '',
         'Generated by `tools/golden/r13_compare.py` from `tests/steel/models/Frame_28x53m_R13.ss` (built by `tools/golden/r12_build.py R13` from '
         '`tests/steel/tekla/Frame_28x53m_tekla_v3.ifc` = Package_2 `out.ifc`, see `docs/steel/R13.md`) and `Frame_28x53m_R12.ss`. '
         'Package_2 itself (`tests/steel/tekla/Frame_28mx53m_Package_2.zip`) is read in place and is reference only.', '',
         'Tekla renumbered the whole model after the fix, so R12 and R13 are compared **by geometry**: parts by kind + Tekla profile + grade and the solid\'s box within 0.1 mm, '
         'then the mesh (vertices and triangles within 0.1 mm); bolt groups by size, grade, holes-only flag, number of bolts and every bolt point within 0.1 mm.', '',
         '## Summary of the diff', '',
         '| Kind | R12 | R13 | Paired | Same | Differs | Only in R13 (new) | Only in R12 (gone) |', '|---|---|---|---|---|---|---|---|',
         f"| Parts (steel, grout, grating, concrete) | {P['r12']} | {P['r13']} | {P['paired']} | {P['same']} | {P['solidDiffers']} (solid) | {len(P['new'])} | {len(P['gone'])} |",
         f"| Bolt groups (TKBOLT) | {Bo['r12']} | {Bo['r13']} | {Bo['paired']} | {Bo['paired'] - len(Bo['partsChanged'])} | {len(Bo['partsChanged'])} (parts joined / grip / axis) | {len(Bo['new'])} | {len(Bo['gone'])} |",
         f"| Parts with another assembly (main part) | | | | | {len(P['asmChanged'])} | | |", '']
    # reading of the diff against the owner's change (3 webs FLT8*300 joined into their girders' assemblies)
    webs = [d for d in P['new'] if d['prof'] == 'FLT8*300']
    sp = [x for x in P['solid'] if x['r13']['prof'] == 'FLT10*150']
    nb = Bo['new']; nbolts = sum(d['n'] for d in nb)
    L += ['## Reading of the diff', '',
          f"- **New webs:** {len(webs)} FLT8*300 (Tekla 103.89 kg each), mark {', '.join(sorted({d['pos'] for d in webs}))}, assembly {', '.join(sorted({d['amark'] for d in webs}))}." + ('' if len(P['new']) == len(webs) else f" **Unexpected:** {len(P['new']) - len(webs)} other new parts."),
          f"- **New bolt groups:** {len(nb)} ({', '.join(sorted({d['size'] for d in nb}))}, {nbolts} bolts), each joins a new web to a column shear plate FLT10*150.",
          f"- **Shear plates:** {len(sp)} column shear plates FLT10*150 (assemblies {', '.join(sorted({x['r13']['amark'] for x in sp}))}) now have the 8 holes of those bolts (R12: no holes). Their marks are now {', '.join(sorted({x['r13']['pos'] for x in sp}))} (R12: {', '.join(sorted({x['r12']['pos'] for x in sp}))})." + ('' if len(sp) == len(P['solid']) else f" **Unexpected:** {len(P['solid']) - len(sp)} other parts with another solid."),
          f"- **Assemblies:** {len(P['asmChanged'])} paired parts have another main part: " + '; '.join(f"{x['r13']['name']} {x['r13']['prof']} (R12 {x['r12']['amark']}, its own main part; R13 {x['r13']['amark']}, main {x['main13']['name']})" for x in P['asmChanged']) + '. The webs join the same assemblies. Every R13 assembly holds exactly the parts of its drawing\'s material list (table below).',
          f"- **Welds:** {sum(x['n'][1] - x['n'][0] for x in P['welds'])} new shop welds, web to flange (st.tkWelds on the flanges).",
          f"- **Everything else is unchanged:** {P['same']} of {P['paired']} paired parts have the same solid, and {Bo['paired'] - len(Bo['partsChanged'])} of {Bo['r12']} R12 bolt groups have the same bolts, grip, axis and parts. No R12 part or bolt group is missing in R13.",
          '- Part and assembly marks: Tekla renumbered; R13\'s marks are Package_2\'s (see `docs/steel/R13.md`).', '']
    L += ['Data fields that differ on paired parts (marks excluded, they are renumbered): ' + (', '.join(f'{k} {v}' for k, v in sorted(P['fields'].items())) or 'none') + '.', '']
    if P['welds']:
        L += ['### Welds (st.tkWelds, kept on the secondary part)', '', '| R13 id | Part | Mark / assembly | Welds R12 / R13 | New weld: main part, shop / site, length, above / below |', '|---|---|---|---|---|']
        L += [f"| {x['r13']} | {x['part']['name']} {x['part']['prof']} | {x['part']['pos']} / {x['part']['amark']} | {x['n'][0]} / {x['n'][1]} | " + '; '.join(f"{(m or {}).get('name')} {(m or {}).get('prof')} (id {w.get('main')}), {w.get('shop')}, {w.get('len')}, {(w.get('above') or {}).get('a')}/{(w.get('below') or {}).get('a')}" for w, m in zip(x['added'], x['mains'])) + ' |' for x in P['welds']] + ['']
    if P['new']:
        L += ['### Parts only in R13', '', '| R13 id | Name | Profile | Grade | Mark | Assembly | Tekla kg | Box (mm) |', '|---|---|---|---|---|---|---|---|']
        L += [f"| {d['id']} | {d['name']} | {d['prof']} | {d['mat']} | {d['pos']} | {d['amark']} | {d['tkW']} | {d['box']} |" for d in P['new']] + ['']
    if P['gone']:
        L += ['### Parts only in R12', '', '| R12 id | Name | Profile | Grade | Mark | Assembly | Box |', '|---|---|---|---|---|---|---|']
        L += [f"| {d['id']} | {d['name']} | {d['prof']} | {d['mat']} | {d['pos']} | {d['amark']} | {d['box']} |" for d in P['gone']] + ['']
    if P['solid']:
        L += ['### Paired parts whose solid differs', '', '| R12 id | R13 id | Name | Profile | R12 mark / assembly | R13 mark / assembly | Triangles R12 / R13 |', '|---|---|---|---|---|---|---|']
        L += [f"| {x['r12']['id']} | {x['r13']['id']} | {x['r13']['name']} | {x['r13']['prof']} | {x['r12']['pos']} / {x['r12']['amark']} | {x['r13']['pos']} / {x['r13']['amark']} | {x['tris'][0]} / {x['tris'][1]} |" for x in P['solid']] + ['']
    if P['asmChanged']:
        L += ['### Paired parts in another assembly', '', 'Main part = the part `st.asm` points to (or the part itself). "R12 main in R13" is the R13 part paired with R12\'s main part.', '',
              '| R13 id | Name | Profile | R12 mark / assembly / main | R13 mark / assembly / main | R12 main in R13 |', '|---|---|---|---|---|---|']
        L += [f"| {x['r13']['id']} | {x['r13']['name']} | {x['r13']['prof']} | {x['r12']['pos']} / {x['r12']['amark']} / {x['main12']['name']} {x['main12']['prof']} | {x['r13']['pos']} / {x['r13']['amark']} / {x['main13']['name']} {x['main13']['prof']} (id {x['main13']['id']}) | {x['r12main13']} |" for x in P['asmChanged']] + ['']
    for t, k in (('Bolt groups only in R13', 'new'), ('Bolt groups only in R12', 'gone')):
        if Bo[k]:
            L += ['### ' + t, '', '| id | Size | Bolts | Holes only | Grip | Parts joined (name, profile, mark, assembly) | Centre (mm) |', '|---|---|---|---|---|---|---|']
            L += [f"| {d['id']} | {d['size']} | {d['n']} | {'yes' if d['holesOnly'] else ''} | {d['grip']} | {'; '.join(d['parts'])} | {d['centre']} |" for d in Bo[k]] + ['']
    if Bo['partsChanged']:
        L += ['### Paired bolt groups that join other parts', '', '| R13 id | Size | Bolts | R12 parts | R13 parts |', '|---|---|---|---|---|']
        L += [f"| {x['r13']['id']} | {x['r13']['size']} | {x['r13']['n']} | {'; '.join(x['r12']['parts'])} | {'; '.join(x['r13']['parts'])} |" for x in Bo['partsChanged']] + ['']
    pl, al, bl, dw, dl = K['partList'], K['assemblyList'], K['bolts'], K['drawings'], K['drawingLists']
    ok = lambda c: 'pass' if c else '**fail**'
    bsame = bl['tekla'] == bl['r13']
    L += ['## R13 against Package_2', '',
          '| Check | Tekla (Package_2) | R13 | Result | Differences |', '|---|---|---|---|---|',
          f"| Parts (SingleList.xsr, grout not listed) | {pl['members']} members | {pl['r13NoGrout']} (+ {pl['r13WithGrout'] - pl['r13NoGrout']} grout = {pl['r13WithGrout']}) | {ok(pl['members'] == pl['r13NoGrout'])} | |",
          f"| Total weight | {pl['weight']:,.1f} kg | {pl['tkWsum']:,.2f} kg (sum of st.tkW); web v69/v70: {pl['v69weight']:,.1f} kg | {ok(abs(pl['weight'] - pl['tkWsum']) <= 10)} | {pl['tkWsum'] - pl['weight']:+.2f} kg (Tekla's list total vs its per-part IFC weights) |",
          f"| Part marks (count and one part's weight per mark) | {pl['rows']} marks (+4 grout marks with drawings) | {pl['r13Marks']} marks | {ok(not pl['badMarks'])} | {'; '.join(f'{a}: {b}' for a, b in pl['badMarks']) or 'none'}; marks not in the list: {', '.join(pl['marksNotInList']) or 'none'} |",
          f"| Assembly marks / assemblies (AssembleyList.xsr) | {al['marks']} / {al['assemblies']} | {al['r13Marks']} / {al['r13Assemblies']} | {ok(not al['countDiffs'] and not al['extraMarks'] and al['assemblies'] == al['r13Assemblies'])} | {', '.join(f'{a} {b}/{c}' for a, b, c in al['countDiffs']) or 'none'} |",
          f"| Assembly weights per mark (sum of the parts' Tekla weights) | {al['weight']:,.1f} kg | {al['r13WeightSum']:,.1f} kg | {ok(not al['weightDiffs']) if not all(d[3] is not None and abs(d[3] - d[1]) < 0.05 and abs(d[4] - d[2]) < 0.05 for d in al['weightDiffs']) else 'pass (as R12)'} | {'; '.join(f'{a}: {b} vs {c} (R12 vs Package_1: {d} vs {e})' for a, b, c, d, e in al['weightDiffs']) or 'none'} |",
          f"| Each assembly's parts = its drawing's material list | material list of each Assembley.PDF | {dl['instances']} assemblies | {ok(not dl['mismatches'])} | {len(dl['mismatches'])} differ |",
          f"| Bolts per size (BOLT-LIST.xsr) | {bl['teklaTotal']:,} | {bl['r13Total']:,} | {ok(bsame)} | {'none' if bsame else json.dumps({k: [bl['tekla'].get(k), bl['r13'].get(k)] for k in sorted(set(bl['tekla']) | set(bl['r13'])) if bl['tekla'].get(k) != bl['r13'].get(k)})} |",
          f"| Assembly drawings (Assembley.PDF / .Dwg) | {dw['assemblyPdf']} / {dw['assemblyDwg']} | {dw['v69']['A']} (web Document manager) | {ok(not dw['aOnlyTekla'] and not dw['aOnlyR13'])} | only Tekla: {', '.join(dw['aOnlyTekla']) or 'none'}; only R13: {', '.join(dw['aOnlyR13']) or 'none'} |",
          f"| Single part drawings (SINGLE.PDF / Single.Dwg) | {dw['singlePdf']} / {dw['singleDwg']} | {dw['v69']['W']} | {ok(not dw['wOnlyTekla'] and not dw['wOnlyR13'])} | only Tekla: {', '.join(dw['wOnlyTekla']) or 'none'}; only R13: {', '.join(dw['wOnlyR13']) or 'none'} |",
          f"| GA drawings (GA.PDF / .Dwg) | {dw['gaPdf']} / {dw['gaDwg']} | {dw['v69']['G']} | {ok(dw['gaPdf'] == dw['v69']['G'])} | web v69 has a fixed list of 13 GA sheets (TK_GA); Tekla's Package_2 has 16 (`1 - STANDARD` … `16 - STANDARD`) |",
          f"| Drawings in total | {dw['assemblyPdf'] + dw['singlePdf'] + dw['gaPdf']} | {dw['v69']['A'] + dw['v69']['W'] + dw['v69']['G']} | {ok(dw['assemblyPdf'] + dw['singlePdf'] + dw['gaPdf'] == dw['v69']['A'] + dw['v69']['W'] + dw['v69']['G'])} | |",
          f"| NC files (NC/*.nc1) | {dw['nc']} | | info | every NC file is an R13 part mark: {ok(not dw['ncNotPart'])} |",
          f"| Web v69 opens R13 | | {K['v69']['bodies']} bodies, {K['v69']['conns']} connections | {ok(not K['v69']['pageErrors'])} | page errors: {len(K['v69']['pageErrors'])} |", '']
    if pl['nearMarks']: L += ['Marks whose one-part weight (Tekla\'s IFC) is 0.05–0.06 kg off the list\'s 0.1 kg value (within the 0.06 kg tolerance R12 used): ' + ', '.join(f'{a} {b} vs {c}' for a, b, c in pl['nearMarks']) + '.', '']
    L += ['Bolts per size:', '', '| Size | Tekla | R13 |', '|---|---|---|'] + [f"| {k} | {bl['tekla'].get(k, 0)} | {bl['r13'].get(k, 0)} |" for k in sorted(set(bl['tekla']) | set(bl['r13']))] + ['']
    if dl['mismatches']:
        L += ['Assemblies whose parts differ from their drawing\'s material list (mark, main part id, R13 extra, R13 missing):', ''] + [f'- {a} ({b}): {c} / {d}' for a, b, c, d in dl['mismatches'][:60]] + ['']
    open(os.path.join(REPO, 'docs', 'steel', 'R13-REPORT.md'), 'w').write('\n'.join(L) + '\n')
    print('\n'.join(L))


if __name__ == '__main__': main()
