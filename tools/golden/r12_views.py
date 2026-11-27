#!/usr/bin/env python3
"""Rule-B failure groups of R12 -> orthographic emulator images next to Tekla's drawing (owner, step 1 of 2026-11-25).

    python3 tools/golden/r12_views.py prepare     build/r12/geom_r12.json -> tests/steel/golden/r12/views/*.ss + views.json
        one small .ss per failure group: the first instance's two parts (red, blue) and every R12 part within 400 mm (grey),
        cut out of R12 unchanged; the camera: target = the overlap's centre, direction = along the contact face (the face is a
        line on screen), the distance frames 500 mm
    (CI) app/src/androidTest/.../R12ViewsTest opens each .ss in the app, orthographic camera, screenshot -> r12/<group>.png
    python3 tools/golden/r12_views.py compose <dir with the screenshots>
        -> docs/steel/parity/R12_<group>.png: emulator image (labels and the measured values written on it) | Tekla drawing page
"""
import gzip, json, math, os, re, subprocess, sys, zipfile, collections
import numpy as np
import manifold3d as mf

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MODEL = os.environ.get('SS_MODEL', 'R13'); ML = MODEL.lower()   # the reference model (R13 since 2026-11-28)
R12 = os.path.join(REPO, 'tests', 'steel', 'models', f'Frame_28x53m_{MODEL}.ss')
VDIR = os.path.join(REPO, 'tests', 'steel', 'golden', ML, 'views')
PKG = os.path.join(REPO, 'tests', 'steel', 'tekla', 'Frame_28mx53m_Package_1.zip')

GROUPS = [   # (key, title, test on a failure) - first match wins
    ('anchor_nut', 'Nut / washer on the anchor rod', lambda f: f['check'] == 'OVERLAP' and 'Anchor Bolt' in f.get('other', '') + f['part'] and ('NUT' in f['part'] or 'WASHER' in f['part'])),
    ('anchor_grout', 'Anchor rod through the grout (grout not drilled)', lambda f: f['check'] == 'OVERLAP' and 'Anchor Bolt' in f['part'] + f.get('other', '') and 'GROUT' in f['part'] + f.get('other', '')),
    ('rail_stanchion', 'Hand rail / railing into the stanchion', lambda f: f['check'] == 'OVERLAP' and 'Stanchion' in f['part'] + f.get('other', '')),
    ('purlin_rafter', 'Purlin / cleat on the rafter top flange, cleat on the purlin', lambda f: f['check'] == 'OVERLAP' and ('Purlin' in f['part'] + f.get('other', '') or 'Cleat' in f['part'] + f.get('other', ''))),
    ('builtup', 'Built-up member: web against flange', lambda f: f['check'] == 'OVERLAP' and {f['part'].split()[0], f.get('other', '').split()[0]} <= {'Web', 'Top', 'Bottom'}),
    ('gusset', 'Node: connection plate / gusset plate / web', lambda f: f['check'] == 'OVERLAP' and ('Gusset' in f['part'] + f.get('other', '') or 'Connection' in f['part'] + f.get('other', ''))),
    ('endplate', 'End plate (post) / plate on a flange', lambda f: f['check'] == 'OVERLAP'),
    ('bolts', 'Bolts at node gussets', lambda f: f['check'] == 'BOLTS'),
    ('floating', 'Floating part', lambda f: f['check'] == 'FLOATING'),
    ('contact', 'Contact', lambda f: f['check'] == 'CONTACT'),
]

def doc(): return json.load(gzip.open(R12))

def VT(b): s = b['shape']; return np.asarray(s['vp'], float).reshape(-1, 3), np.asarray(s['tv'], np.uint64).reshape(-1, 3)
def man(b): V, T = VT(b); return mf.Manifold(mf.Mesh64(np.ascontiguousarray(V), np.ascontiguousarray(T)))

def group_of(f):
    for k, t, test in GROUPS:
        if test(f): return k, t
    return 'other', 'Other'

def prepare(fails_path):
    D = doc(); B = {b['id']: b for b in D['doc']['bodies']}; C = {c['id']: c for c in D['doc']['stConns']}
    F = json.load(open(fails_path)); groups = collections.OrderedDict()
    for f in F:
        if f['check'] == 'OVERLAP' and f['type'] != 'model': continue          # the model-wide pass lists every overlap once
        k, t = group_of(f); g = groups.setdefault(k, dict(key=k, title=t, fails=[]))
        g['fails'].append(f)
    os.makedirs(VDIR, exist_ok=True); views = []
    boxes = {i: np.r_[VT(b)[0].min(0), VT(b)[0].max(0)] for i, b in B.items() if b.get('shape')}
    for k, g in groups.items():
        f = g['fails'][0]
        if f.get('ids'): ids = f['ids']
        else: c = C[int(f['conn'])]; ids = c['parts'][:2]
        a, b = B[ids[0]], B[ids[1]] if len(ids) > 1 else None
        def axis_of(x):                                   # a part's long direction
            V = VT(x)[0]; U, s_, Vt = np.linalg.svd(V - V.mean(0), full_matrices=False); return Vt[0]
        if b is not None and f['check'] == 'OVERLAP':
            I = man(a) ^ man(b); m = I.to_mesh64(); P = np.asarray(m.vert_properties)[:, :3]
            ctr = (P.min(0) + P.max(0)) / 2; U, s, Vt = np.linalg.svd(P - P.mean(0), full_matrices=False); n = Vt[2]
            a1, a2 = axis_of(a), axis_of(b); rod = any(x['st'].get('kind') == 'member' and x['st']['tprof'].upper().startswith(('ROD', 'PD', 'CHS')) for x in (a, b))
            if rod and abs(float(a1 @ a2)) < 0.8: view = np.cross(a1, a2)              # two crossing tubes / rods: square to both
            elif rod:                                                                 # rod in a nut / grout: side-on
                ax = a1 if a['st'].get('kind') == 'member' else a2; view = np.cross(ax, [0, 0, 1.0]) if abs(ax[2]) < 0.9 else np.array([1.0, 0, 0])
            else:                                                                     # faces in contact: edge-on, the longer part across
                th = lambda x: np.linalg.svd(VT(x)[0] - VT(x)[0].mean(0), full_matrices=False)
                ua, sa, va = th(a); ub, sb, vb = th(b); n = va[2] if sa[2] / sa[0] <= sb[2] / sb[0] else vb[2]   # the thinner plate's normal
                big = a1 if np.ptp(VT(a)[0], 0).max() >= np.ptp(VT(b)[0], 0).max() else a2; view = np.cross(n, big)
                if np.linalg.norm(view) < 0.3: view = np.cross(n, a2 if big is a1 else a1)
            view = view / np.linalg.norm(view)
        else:
            c = C[int(f['conn'])]; q = c['q']; ctr = np.mean(np.array(q['pts'], float), 0); ax = np.array(q['ax'], float)
            pl = B[c['parts'][1]] if len(c['parts']) > 1 else a; view = np.cross(ax, axis_of(pl)); view = view / (np.linalg.norm(view) or 1); n = ax
        keep = [i for i, bx in boxes.items() if (bx[:3] < ctr + 400).all() and (bx[3:] > ctr - 400).all()]
        bodies = []
        for i in keep:
            x = json.loads(json.dumps(B[i])); x['color'] = 0xd03030 if i == ids[0] else 0x3060d0 if (len(ids) > 1 and i == ids[1]) else 0xb0b0b8
            bodies.append(x)
        out = {k2: v for k2, v in D.items() if k2 != 'doc'}; d2 = json.loads(json.dumps({k2: v for k2, v in D['doc'].items() if k2 not in ('bodies', 'stConns')}))
        d2['bodies'] = bodies; d2['stConns'] = [c for c in D['doc']['stConns'] if set(c['parts']) <= set(keep)]; out['doc'] = d2; out['name'] = MODEL + '_' + k
        fn = MODEL + '_%s.ss' % k
        with open(os.path.join(VDIR, fn), 'wb') as fh:
            with gzip.GzipFile(fileobj=fh, mode='wb', mtime=0, filename='') as gz: gz.write(json.dumps(out, separators=(',', ':')).encode())
        vals = [x['value'] for x in g['fails'] if isinstance(x['value'], (int, float))]; deps = [x['depth'] for x in g['fails'] if x.get('depth') is not None]
        ext = float(np.ptp(P, 0).max()) if (b is not None and f['check'] == 'OVERLAP') else 300.0
        views.append(dict(key=k, title=g['title'], file=fn, target=[round(float(v), 2) for v in ctr], dir=[round(float(v), 5) for v in view], dist=float(min(700.0, max(220.0, 2.2 * ext))),
                          count=len(g['fails']), ids=ids, parts=[a['st']['name'] + ' ' + a['st']['tprof'], (b['st']['name'] + ' ' + b['st']['tprof']) if b else ''],
                          amark=[a['st'].get('amark'), b['st'].get('amark') if b else None], pos=[a['st'].get('pos'), b['st'].get('pos') if b else None],
                          value=f['value'], depth=f.get('depth'), vmin=min(vals) if vals else None, vmax=max(vals) if vals else None,
                          dmin=min(deps) if deps else None, dmax=max(deps) if deps else None,
                          kinds=dict(collections.Counter('%s / %s' % (x['part'], x.get('other', '')) for x in g['fails']).most_common(8))))
    json.dump(views, open(os.path.join(VDIR, 'views.json'), 'w'), indent=1)
    for v in views: print(v['key'], v['count'], v['parts'], v['amark'], v['value'], v['depth'])

def tekla_page(amark, out_png):
    with zipfile.ZipFile(PKG) as z:
        n = next((x for x in z.namelist() if x.endswith('/Assembly Drawings/%s - Assembly Drawings.pdf' % amark)), None)
        if n is None: return False
        tmp = out_png + '.pdf'; open(tmp, 'wb').write(z.read(n))
    subprocess.run(['pdftoppm', '-png', '-r', '110', '-singlefile', tmp, out_png[:-4]], check=True); os.remove(tmp); return True

def compose(shots):
    from PIL import Image, ImageDraw, ImageFont
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 22); FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26)
    views = json.load(open(os.path.join(VDIR, 'views.json'))); outd = os.path.join(REPO, 'docs', 'steel', 'parity', ML); os.makedirs(outd, exist_ok=True)
    for v in views:
        p = next((os.path.join(shots, x) for x in sorted(os.listdir(shots)) if x.endswith('_%s.png' % v['key'])), None)
        if p is None: print('no screenshot for', v['key']); continue
        em = Image.open(p).convert('RGB'); de = ImageDraw.Draw(em)
        tx = p[:-4] + '.txt'
        if os.path.exists(tx):                    # the measured spot: the view's place on screen + the spot's pixel in the view
            vx, vy, px, py = [float(x) if x != 'null' else 0.0 for x in open(tx).read().split()]
            X, Y = vx + px, vy + py
            for rr in (34, 36, 38): de.ellipse((X - rr, Y - rr, X + rr, Y + rr), outline=(255, 210, 0))
            lab = ('%.4f mm deep, %.1f mm³' % (v['depth'], v['value'])) if isinstance(v['value'], (int, float)) else str(v['value'])
            FL = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 40)
            bx = de.textbbox((0, 0), lab, font=FL); tx0 = max(10, min(em.width - (bx[2] - bx[0]) - 10, X - (bx[2] - bx[0]) / 2)); ty0 = min(em.height - 300, Y + 60)
            de.rectangle((tx0 - 8, ty0 - 6, tx0 + bx[2] - bx[0] + 8, ty0 + bx[3] - bx[1] + 14), fill=(0, 0, 0)); de.text((tx0, ty0), lab, font=FL, fill=(255, 210, 0))
            de.line((X, Y + 38, X, ty0 - 6), fill=(255, 210, 0), width=3)
            de.text((30, em.height - 260), 'orthographic · view %s' % ' '.join('%.2f' % x for x in v['dir']), font=FL, fill=(255, 255, 255))
        H = 1100; em = em.resize((int(em.width * H / em.height), H))
        tk = os.path.join(outd, '_tekla_%s.png' % v['key']); ok = tekla_page(v['amark'][0], tk)
        tim = Image.open(tk).convert('RGB') if ok else Image.new('RGB', (800, H), (240, 240, 240))
        tim = tim.resize((int(tim.width * H / tim.height), H)) if ok else tim
        W = em.width + tim.width + 60; im = Image.new('RGB', (W, H + 260), (255, 255, 255)); d = ImageDraw.Draw(im)
        d.text((20, 14), MODEL + ' rule-B failure group: %s (%d)' % (v['title'], v['count']), font=FB, fill=(0, 0, 0))
        d.text((20, 50), 'left: the app on the emulator, %s opened, orthographic camera; red = %s, blue = %s (marks %s / %s, assembly %s)' % (MODEL, v['parts'][0], v['parts'][1], v['pos'][0], v['pos'][1], v['amark'][0]), font=F, fill=(40, 40, 40))
        d.text((20, 80), 'right: Tekla assembly drawing %s (package)' % v['amark'][0], font=F, fill=(40, 40, 40))
        im.paste(em, (20, 120)); im.paste(tim, (40 + em.width, 120))
        y = 130 + H
        meas = ('overlap %.1f mm³ (all in group %.1f..%.1f), mean depth %.4f mm (group %.4f..%.4f)' % (v['value'], v['vmin'], v['vmax'], v['depth'] or 0, v['dmin'] or 0, v['dmax'] or 0)) if isinstance(v['value'], (int, float)) else 'measured: %s' % v['value']
        d.text((20, y), meas, font=FB, fill=(200, 0, 0)); d.text((20, y + 40), 'in this group: ' + '; '.join('%s x%d' % (a, b) for a, b in list(v['kinds'].items())[:4]), font=F, fill=(60, 60, 60))
        out = os.path.join(outd, MODEL + '_%s.png' % v['key']); im.save(out); os.remove(tk) if ok else None; print(out, im.size)

def preview():
    from PIL import Image, ImageDraw
    views = json.load(open(os.path.join(VDIR, 'views.json'))); out = os.path.join(REPO, 'build', ML, 'preview'); os.makedirs(out, exist_ok=True)
    for v in views:
        D = json.load(gzip.open(os.path.join(VDIR, v['file'])))['doc']; t = np.array(v['target']); d = np.array(v['dir']); d /= np.linalg.norm(d)
        r = np.cross([0, 0, 1.0], d); r = r / np.linalg.norm(r) if np.linalg.norm(r) > 1e-6 else np.array([1.0, 0, 0]); u = np.cross(d, r)
        W, H = 600, 600; k = H / (2 * v['dist'] * math.tan(math.radians(22.5))); im = Image.new('RGB', (W, H), (30, 30, 34)); dr = ImageDraw.Draw(im); tris = []
        for b in D['bodies']:
            V, T = VT(b); X = (V - t) @ r * k + W / 2; Y = H / 2 - (V - t) @ u * k; Z = (V - t) @ d
            col = b.get('color', 0xb0b0b8); c = ((col >> 16) & 255, (col >> 8) & 255, col & 255)
            for tr in T: tris.append((Z[tr].mean(), [(X[i], Y[i]) for i in tr], c))
        for z, pts, c in sorted(tris, key=lambda x: x[0]): dr.polygon(pts, fill=c, outline=tuple(max(0, x - 40) for x in c))
        im.save(os.path.join(out, v['key'] + '.png'))

if __name__ == '__main__':
    if sys.argv[1] == 'preview': preview(); sys.exit()
    if sys.argv[1] == 'prepare': prepare(sys.argv[2] if len(sys.argv) > 2 else os.path.join(REPO, 'build', ML, 'geom_%s.json' % ML))
    else: compose(sys.argv[2])
