#!/usr/bin/env python3
"""R12 (Tekla IFC) vs R11, part by part, plus R12 against Tekla's own reports (Part List, Assembly List, bolt list).

    python3 tools/golden/r12_compare.py      (after tools/golden/r12_build.py and tools/golden/r12_totals.js)
      -> build/r12/compare.json, docs/steel/R12-COMPARE.md

Part pairs: r12_build.py's match (same name + material, nearest box; then same profile + material). "Same" = the two solids' boxes
agree within 0.1 mm AND the volume of their symmetric difference is <= 0.1 mm x the part's surface area (a face moved 0.1 mm), so a
mirrored or turned part with the same box counts as different.
"""
import gzip, json, os, re, html, sys, zipfile, collections
import numpy as np
import manifold3d as mf

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
M = os.path.join(REPO, 'tests', 'steel', 'models')
PKG = os.path.join(REPO, 'tests', 'steel', 'tekla', 'Frame_28mx53m_Package_1.zip')
OUT = os.path.join(REPO, 'build', 'r12')

def load(p):
    d = json.load(gzip.open(p))['doc']; return {b['id']: b for b in d['bodies']}, d['stConns']

def VT(b): s = b['shape']; return np.asarray(s['vp'], float).reshape(-1, 3), np.asarray(s['tv'], int).reshape(-1, 3)

_m = {}
def man(tag, b):
    k = (tag, b['id'])
    if k not in _m:
        V, T = VT(b)
        try:
            m = mf.Manifold(mf.Mesh64(np.ascontiguousarray(V, np.float64), np.ascontiguousarray(T, np.uint64)))
            _m[k] = m if m.status() == mf.Error.NoError and not m.is_empty() else None
        except Exception: _m[k] = None
    return _m[k]

def area(V, T): a = V[T[:, 1]] - V[T[:, 0]]; b = V[T[:, 2]] - V[T[:, 0]]; return float(np.linalg.norm(np.cross(a, b), axis=1).sum() / 2)

def report_rows(name):
    with zipfile.ZipFile(PKG) as z:
        n = next(x for x in z.namelist() if x.endswith('Reports/' + name)); t = z.read(n).decode('latin-1')
    R = []
    for r in re.findall(r'<tr[^>]*>(.*?)</tr>', t, re.S | re.I):
        c = [html.unescape(re.sub(r'<[^>]+>', '', x)).replace('\xa0', ' ').strip() for x in re.findall(r'<td[^>]*>(.*?)</td>', r, re.S | re.I)]
        if any(c): R.append(c[1:] if c and c[0] == '' else c)
    return R

def main():
    B12, C12 = load(os.path.join(M, 'Frame_28x53m_R12.ss')); B11, C11 = load(os.path.join(M, 'Frame_28x53m_R11.ss'))
    P = json.load(open(os.path.join(OUT, 'parts.json'))); tot = json.load(open(os.path.join(OUT, 'v69_totals.json')))
    pinfo = {p['bid']: p for p in P['parts']}
    steel12 = {k: b for k, b in B12.items() if b.get('st') and b['st']['kind'] in ('plate', 'member')}
    out = collections.OrderedDict(); L = []

    # ---------------- Tekla reports
    pl = report_rows('Part List Excel.xls'); hdr = pl.index(next(r for r in pl if r[:2] == ['PartPos', 'Profile']))
    plist = {r[0]: dict(prof=r[1], n=int(r[2]), mat=r[3], len=float(r[4]), w=float(r[6])) for r in pl[hdr + 1:] if r and r[0] != 'Total for' and len(r) >= 7}
    ptotal = next(r for r in pl if r and r[0] == 'Total for')
    al = report_rows('Assembly List Excel.xls'); ah = al.index(next(r for r in al if r[:2] == ['Assembly Mark', 'No.']))
    alist = {}
    for r in al[ah + 1:]:                       # a mark can have several rows (Tekla splits it by weight / area): add their counts
        if r and r[0] != 'Total for' and len(r) >= 8: x = alist.setdefault(r[0], dict(n=0, name=r[2], prof=r[3], w=float(r[6]))); x['n'] += int(r[1])
    atotal = next(r for r in al if r and r[0] == 'Total for')

    # R12 per part mark vs the Part List
    by_pos = collections.defaultdict(list)
    for b in steel12.values(): by_pos[b['st']['pos']].append(b)
    bad_pos = []
    for pos, lst in sorted(by_pos.items()):
        t = plist.get(pos)
        if t is None: bad_pos.append((pos, 'not in the Part List', len(lst))); continue
        ws = [b['st'].get('tkW') or 0 for b in lst]; th = {b['st']['tprof'] for b in lst}
        errs = []
        if len(lst) != t['n']: errs.append('count %d vs %d' % (len(lst), t['n']))
        if max(abs(x - t['w']) for x in ws) > 0.06: errs.append('weight %s vs %g' % ('/'.join('%g' % x for x in sorted(set(ws))), t['w']))
        if errs: errs.append('profile %s, Part List %s' % ('/'.join(sorted(th)), t['prof']))
        if errs: bad_pos.append((pos, '; '.join(errs), len(lst)))
    missing_pos = sorted(set(plist) - set(by_pos))
    asm12 = collections.Counter()
    seen = set()
    for b in steel12.values():
        st = b['st']; key = (st['amark'], st.get('asm') or b['id'])
        if key not in seen: seen.add(key); asm12[st['amark']] += 1
    bad_asm = [(m, asm12.get(m, 0), alist[m]['n']) for m in sorted(alist) if asm12.get(m, 0) != alist[m]['n']]
    wsum = sum(b['st'].get('tkW') or 0 for b in steel12.values())
    out['tekla'] = dict(partList=dict(marks=len(plist), parts=int(ptotal[2]), weight=float(ptotal[-1])), assemblyList=dict(marks=len(alist), assemblies=int(atotal[1]), weight=float(atotal[-1])))
    out['r12'] = dict(parts=len(steel12), partMarks=len(by_pos), partMarkErrors=bad_pos, marksMissing=missing_pos, assemblyMarks=len(asm12), assemblies=sum(asm12.values()),
                      assemblyErrors=bad_asm, tklaWeightSum=round(wsum, 2), v69=tot)

    # ---------------- part by part, R11 vs R12
    rows = collections.defaultdict(lambda: collections.Counter()); worst = {}; per = []
    for bid, b in steel12.items():
        r11 = pinfo[bid].get('r11'); a = B11.get(r11) if r11 is not None else None
        nm = b['st']['name'].strip()
        if a is None: rows[nm]['no R11 part'] += 1; continue
        V2, T2 = VT(b); V1, T1 = VT(a)
        dbox = float(np.abs(np.r_[V2.min(0), V2.max(0)] - np.r_[V1.min(0), V1.max(0)]).max())
        m1, m2 = man('11', a), man('12', b); xv = None
        if m1 is not None and m2 is not None: xv = float((m1 - m2).volume() + (m2 - m1).volume())
        tol = 0.1 * area(V2, T2)
        rnd = b['st']['tprof'].upper().startswith(('CHS', 'PD', 'ROD', 'D'))     # round: R11 and R12 facet the circle differently
        if rnd and xv is not None: xv = abs(m1.volume() - m2.volume()); tol = max(tol, 0.01 * m2.volume())
        same = dbox <= 0.1 and (xv is None or xv <= tol)
        k = 'same' if same else ('turned / mirrored (box same)' if dbox <= 0.1 else 'moved / resized')
        rows[nm][k] += 1; rows[nm]['n'] += 1
        if not same: worst[nm] = max(worst.get(nm, 0), dbox)
        per.append(dict(r12=bid, r11=r11, name=nm, prof=b['st']['tprof'], pos=b['st']['pos'], dbox=round(dbox, 2), xor=None if xv is None else round(xv, 1), same=same))
    out['parts'] = per
    allsame = sum(1 for p in per if p['same'])

    # ---------------- topics
    T = collections.OrderedDict()
    def grp(names): return [p for p in per if p['name'] in names]
    for title, names in [('C04 purlin cleats (Cleat Angle L140*60*6, Stiff. PL6*44)', {'Cleat Angle', 'Stiff.'}), ('Purlins (CC200-2-20-60)', {'Purlin'}),
                         ('Stair brackets and stringers (BRACKET L60*6, STRINGER UPN240)', {'BRACKET', 'STRINGER'}), ('Node gussets (Gusset Plate, Connection Plate)', {'Gusset Plate', 'Connection Plate'})]:
        g = grp(names); T[title] = dict(n=len(g), same=sum(p['same'] for p in g), maxBox=max([p['dbox'] for p in g] or [0]))
    # purlin web side: R11 vs R12 (the C's web normal, from the solid: the side of the box with the most material)
    def web_side(b):
        st = b['st']; V, _ = VT(b); p1, p2 = np.array(st['p1'], float), np.array(st['p2'], float); a = (p2 - p1) / np.linalg.norm(p2 - p1)
        c = (V.min(0) + V.max(0)) / 2; W = V - c; W = W - np.outer(W @ a, a)
        # the web is the 2 mm wall: its vertices are the ones at the extreme of the narrow direction that have the full height
        U, s, Vt = np.linalg.svd(W - W.mean(0), full_matrices=False); n = Vt[1]          # narrow direction (60 vs 200)
        t = W @ n; hi = W[t > t.max() - 2.5]; lo = W[t < t.min() + 2.5]
        h = lambda X: (X @ Vt[0]).max() - (X @ Vt[0]).min() if len(X) else 0
        # the lips are short (20): the side whose points cover the full 200 height with few gaps = web; use the count of points
        return n if len(hi) < len(lo) else -n
    flips = 0; np_ = 0
    for p in grp({'Purlin'}):
        b2, b1 = B12[p['r12']], B11[p['r11']]
        try:
            n2, n1 = web_side(b2), web_side(b1); np_ += 1
            if float(n2 @ n1) < 0: flips += 1
        except Exception: pass
    T['Purlins (CC200-2-20-60)']['webSideDiffers'] = flips; T['Purlins (CC200-2-20-60)']['checked'] = np_

    # ---------------- bolts
    def bolts(C, B):
        g = collections.Counter(); n = collections.Counter(); pts = []
        for c in C:
            if c['type'] != 'TKBOLT': continue
            q = c['q']; body = next((b for b in B.values() if b.get('st') and b['st'].get('kind') == 'bolts' and b['st'].get('conn') == c['id']), None)
            key = 'M%g %s L%g' % (q['d'], (body['st'].get('grade') if body else q.get('grade', '')) or '', (body['st'].get('L') if body else q.get('L', 0)) or 0) + (' holes only' if q.get('holesOnly') or not body else '')
            g[key] += 1; n[key] += len(q['pts'])
            for P in q['pts']: pts.append((np.array(P, float), np.array(q['ax'], float), key, c['id']))
        return g, n, pts
    g11, n11, p11 = bolts(C11, B11); g12, n12, p12 = bolts(C12, B12)
    out['bolts'] = dict(r11={k: [g11[k], n11[k]] for k in sorted(g11)}, r12={k: [g12[k], n12[k]] for k in sorted(g12)})
    # each R12 bolt: the nearest R11 bolt line (distance across the axis) and the shift along it
    A11 = np.array([p[0] for p in p11]); X11 = np.array([p[1] for p in p11]); res = collections.defaultdict(list)
    for P, ax, key, cid in p12:
        d = A11 - P; along = d @ ax; perp = np.linalg.norm(d - np.outer(along, ax), axis=1); j = int(perp.argmin())
        res[key].append((perp[j], along[j], p11[j][2]))
    bl = {}
    for k, v in sorted(res.items()):
        pe = np.array([x[0] for x in v]); al = np.array([x[1] for x in v])
        bl[k] = dict(bolts=len(v), onR11line=int((pe <= 0.1).sum()), maxAcross=round(float(pe.max()), 1), alongR11=dict(collections.Counter(round(float(x), 1) for x, y in zip(al, pe) if y <= 0.1).most_common(4)),
                     r11sizes=dict(collections.Counter(x[2] for x in v).most_common(3)))
    out['boltLines'] = bl
    # the 280 M10 groups: which parts they hole in R12
    m10 = [c for c in C12 if c['type'] == 'TKBOLT' and c['q']['d'] == 10]
    out['m10'] = dict(groups=len(m10), holesOnly=sum(1 for c in m10 if c['q'].get('holesOnly')), parts=dict(collections.Counter('/'.join(sorted({B12[p]['st']['name'] for p in c['parts']})) for c in m10)),
                      partsPerGroup=dict(collections.Counter(len(c['parts']) for c in m10)))
    m10r11 = [c for c in C11 if c['type'] == 'TKBOLT' and c['q'].get('d') == 10]
    out['m10']['r11'] = dict(groups=len(m10r11), withBoltBody=sum(1 for c in m10r11 if any(b.get('st', {}).get('conn') == c['id'] and b['st'].get('kind') == 'bolts' for b in B11.values())))
    # assemblies / marks: R11 vs R12 on the matched parts
    am = collections.Counter(); mainm = collections.Counter()
    for p in per:
        a, b = B11[p['r11']]['st'], B12[p['r12']]['st']
        am['same amark' if a.get('amark') == b.get('amark') else 'amark differs'] += 1
        ra = a.get('asm'); rb = b.get('asm')
        ra_ = None if ra is None else next((x['r12'] for x in per if x['r11'] == ra), 'unmatched')
        mainm['same main part' if ra_ == rb else 'main part differs'] += 1
    out['marks'] = dict(amark=dict(am), main=dict(mainm))
    out['topics'] = T
    os.makedirs(OUT, exist_ok=True); json.dump(out, open(os.path.join(OUT, 'compare.json'), 'w'), indent=1, default=float)

    # ---------------- markdown
    t = out['tekla']; r = out['r12']; v = tot
    L += ['# R12 (Tekla IFC) vs R11 and vs Tekla\'s reports', '',
          'Generated by `tools/golden/r12_compare.py` from `tests/steel/models/Frame_28x53m_R12.ss` (built by `tools/golden/r12_build.py` from '
          '`tests/steel/tekla/Frame_28x53m_tekla_v2.ifc`), `Frame_28x53m_R11.ss` and the Tekla package reports. R10 and R11 are unchanged.', '',
          '## Totals', '', '| | Tekla (reports) | R12 | R11 |', '|---|---|---|---|',
          f"| Parts (steel, grout, grating) | {t['partList']['parts']} | {r['parts']} | 4945 |",
          f"| Part marks | {t['partList']['marks']} | {r['partMarks']} ({len(r['partMarkErrors'])} disagree with the Part List) | 243 |",
          f"| Assembly marks (incl. single-part marks) | {t['assemblyList']['marks']} | {r['assemblyMarks']} ({len(r['assemblyErrors'])} counts differ) | |",
          f"| Assemblies (instances) | {t['assemblyList']['assemblies']} | {r['assemblies']} | |",
          f"| Assembly drawings / single-part drawings / GA (v69 Document manager) | 162 / 243 / 13 | {v['byType'].get('A')} / {v['byType'].get('W')} / {v['byType'].get('G')} | 162 / 243 / 13 |",
          f"| Weight, Tekla's own per-part weights | 63,711.7 kg (Part List total) | {r['tklaWeightSum']:,.2f} kg (sum of the IFC part weights, 2 decimals each) | |",
          f"| Weight, v69 rules (v66, bolts excluded) | | {v['weightKg']:,.1f} kg | 63,722.07 kg |",
          f"| Bolts (v69 bolt bodies) | 4,548 (bolt list) | {v['bolts']} | |", '']
    L += ['Part marks are checked against the Part List by count and weight of one part (0.06 kg; the list has 0.1 kg). Profile names '
          'are not compared: Tekla\'s reports round a contour plate\'s width (PL12*222.6 -> PL12*223) and may list a contour plate as a '
          'flat of the other orientation (PL5*44.1 -> FLT5*100, length 44).', '']
    if r['partMarkErrors']: L += ['Part marks that disagree with Tekla\'s Part List (mark, problem, R12 count):', ''] + [f'- {a}: {b} ({c})' for a, b, c in r['partMarkErrors']] + ['']
    if r['marksMissing']: L += ['Part marks in the Part List with no R12 part: ' + ', '.join(r['marksMissing']), '']
    if r['assemblyErrors']: L += ['Assembly marks whose count differs from the Assembly List (mark, R12, Tekla): ' + ', '.join(f'{a} {b}/{c}' for a, b, c in r['assemblyErrors'][:40]), '']
    L += ['## Part by part, R11 vs R12', '', f'{allsame} of {len(per)} matched parts are the same (box within 0.1 mm and the same solid).', '',
          '| Part name | n | same | turned / mirrored (same box) | moved / resized | worst box difference (mm) |', '|---|---|---|---|---|---|']
    for nm, c in sorted(rows.items(), key=lambda x: -x[1]['n']):
        L.append(f"| {nm} | {c['n']} | {c['same']} | {c['turned / mirrored (box same)']} | {c['moved / resized']} | {worst.get(nm, 0):.1f} |")
    L += ['', '## Topics', '']
    for k, x in T.items(): L.append(f'- **{k}**: {x["same"]} / {x["n"]} the same; worst box difference {x["maxBox"]:.1f} mm' + (f'; web on the other side in R11: {x["webSideDiffers"]} / {x["checked"]}' if 'webSideDiffers' in x else ''))
    m = out['m10']; L += [f"- **M10 groups**: R12 {m['groups']} groups (Tekla 'BOLTM10', standard 6.8, length 2, no bolt solid = holes only), parts: {m['parts']}; R11 {m['r11']['groups']} groups, {m['r11']['withBoltBody']} with a bolt body",
                          f"- **Assembly marks** on matched parts: {out['marks']['amark']}; main part: {out['marks']['main']}", '',
                          '## Bolts', '', '| Size | R11 groups / bolts | R12 groups / bolts |', '|---|---|---|']
    for k in sorted(set(out['bolts']['r11']) | set(out['bolts']['r12'])):
        a = out['bolts']['r11'].get(k, [0, 0]); b = out['bolts']['r12'].get(k, [0, 0]); L.append(f'| {k} | {a[0]} / {a[1]} | {b[0]} / {b[1]} |')
    L += ['', 'R12 bolts on an R11 bolt line (within 0.1 mm across the axis) and their shift along it:', '', '| R12 size | bolts | on an R11 line | worst across (mm) | shift along the line (mm: count) |', '|---|---|---|---|---|']
    for k, x in bl.items(): L.append(f"| {k} | {x['bolts']} | {x['onR11line']} | {x['maxAcross']} | {x['alongR11']} |")
    open(os.path.join(REPO, 'docs', 'steel', 'R12-COMPARE.md'), 'w').write('\n'.join(L) + '\n')
    print('\n'.join(L))

if __name__ == '__main__': main()
