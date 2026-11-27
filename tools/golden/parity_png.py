#!/usr/bin/env python3
"""Comparison image for a parity connection (standing rule B5: orthographic, labelled, dimensions written on it):
    R12 (Tekla's IFC, rendered orthographic from the app's view) | Tekla's drawing | video frame | app on the emulator (orthographic)
and the measured values from the model the emulator itself built and saved.

    python3 tools/golden/parity_png.py C04 <shots dir with NN_ortho_end.png/.txt and c04_workflow.ss>
    python3 tools/golden/parity_png.py C14 <shots dir with NN_ortho_end.png/.txt and c14_case.ss>
    python3 tools/golden/parity_png.py C02 | C09 <shots dir with NN_ortho_front.png/.txt and c02_case.ss / c09_case.ss>
      -> docs/steel/parity/<code>.png and build/<code>_measure.txt
"""
import gzip, json, math, os, subprocess, sys, zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PKG = os.path.join(REPO, 'tests', 'steel', 'tekla', 'Frame_28mx53m_Package_1.zip')
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 24); FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 30)
FL = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 40)

def VT(b): s = b['shape']; return np.asarray(s['vp'], float).reshape(-1, 3), np.asarray(s['tv'], int).reshape(-1, 3)

def render(bodies, t, d, dist, colour, W=900, H=1100):
    """orthographic painter's render, the same camera as the app (target t, from direction d, visible height 2 dist tan 22.5°)"""
    d = np.asarray(d, float); d /= np.linalg.norm(d); r = np.cross([0, 0, 1.0], d); r = r / np.linalg.norm(r) if np.linalg.norm(r) > 1e-6 else np.array([1.0, 0, 0]); u = np.cross(d, r)
    k = H / (2 * dist * math.tan(math.radians(22.5))); im = Image.new('RGB', (W, H), (30, 30, 34)); dr = ImageDraw.Draw(im); tris = []
    for b in bodies:
        V, T = VT(b); X = (V - t) @ r * k + W / 2; Y = H / 2 - (V - t) @ u * k; Z = (V - t) @ d
        c = colour(b)
        for tr in T:
            if Z[tr].max() > 0: continue                 # in front of the target plane: cut away, the part shows in section (like the app's near plane)
            tris.append((Z[tr].mean(), [(X[i], Y[i]) for i in tr], c))
    for z, pts, c in sorted(tris, key=lambda x: x[0]): dr.polygon(pts, fill=c, outline=tuple(max(0, x - 50) for x in c))
    return im

def tekla_page(name, sub='Assembly Drawings'):
    with zipfile.ZipFile(PKG) as z:
        n = next(x for x in z.namelist() if x.endswith(f'/{sub}/{name} - {sub}.pdf'))
        tmp = os.path.join(REPO, 'build', f'_{name}.pdf'); open(tmp, 'wb').write(z.read(n))
    out = tmp[:-4]; subprocess.run(['pdftoppm', '-png', '-r', '120', '-singlefile', tmp, out], check=True); os.remove(tmp); return Image.open(out + '.png').convert('RGB')

def shot(shots, name):
    p = next(os.path.join(shots, x) for x in sorted(os.listdir(shots)) if x.endswith(f'_{name}.png'))
    txt = p[:-4] + '.txt'; meta = open(txt).read().split() if os.path.exists(txt) else None
    return Image.open(p).convert('RGB'), meta

def fit(im, H): return im.resize((max(1, int(im.width * H / im.height)), H))

def label(im, xy, text, colour=(255, 210, 0)):
    d = ImageDraw.Draw(im); bx = d.textbbox((0, 0), text, font=FL); x, y = xy
    d.rectangle((x - 8, y - 6, x + bx[2] - bx[0] + 8, y + bx[3] - bx[1] + 14), fill=(0, 0, 0)); d.text((x, y), text, font=FL, fill=colour)

def compose(code, panels, title, lines):
    H = 1100; ps = [(t, fit(im, H)) for t, im in panels]; W = sum(im.width for _, im in ps) + 30 * (len(ps) + 1)
    out = Image.new('RGB', (W, H + 140 + 40 * len(lines)), 'white'); d = ImageDraw.Draw(out); d.text((30, 16), title, font=FB, fill=(0, 0, 0)); x = 30
    for t, im in ps: out.paste(im, (x, 100)); d.rectangle((x, 100, x + im.width - 1, 100 + H - 1), outline=(160, 160, 160)); d.text((x, 62), t, font=F, fill=(40, 40, 40)); x += im.width + 30
    y = 120 + H
    for ln in lines: d.text((30, y), ln, font=F if not ln.startswith('!') else FB, fill=(0, 0, 0) if not ln.startswith('!') else (190, 0, 0)); y += 40
    p = os.path.join(REPO, 'docs', 'steel', 'parity', f'{code}.png'); out.save(p); print(p, out.size); return p

def c04(shots):
    D = json.load(gzip.open(os.path.join(shots, 'c04_workflow.ss')))['doc']; B = {b['id']: b for b in D['bodies']}
    st = lambda b: b.get('st') or {}; conns = D['stConns']; tcl = next(c for c in conns if c['type'] == 'Tcl')
    raf, purs = B[int(tcl['parts'][0])], [B[int(p)] for p in tcl['parts'][1:]]
    p1, p2 = np.array(st(raf)['p1'], float), np.array(st(raf)['p2'], float); a = (p2 - p1) / np.linalg.norm(p2 - p1); n = np.array([0, 0, 1.0]) - a * a[2]; n /= np.linalg.norm(n)
    top = (VT(raf)[0] @ n).max(); under = [(VT(p)[0] @ n).min() for p in purs]
    cl = next(b for b in D['bodies'] if st(b).get('conn') == tcl['id'] and st(b).get('name') == 'CLEAT'); clb = (VT(cl)[0] @ n).min()
    tpc = sum(1 for c in conns if c['type'] == 'Tpc'); tpc_here = sum(1 for c in conns if c['type'] == 'Tpc' and c['parts'][0] == tcl['parts'][0] and c['parts'][1] in tcl['parts'][1:])
    rb = subprocess.run([sys.executable, os.path.join(REPO, 'tools', 'golden', 'geom_checks.py'), os.path.join(shots, 'c04_workflow.ss'), '--no-all-pairs'], capture_output=True, text=True).stdout.splitlines()[0]
    meas = [f'purlin underside - rafter top: ' + ', '.join(f'{u - top:+.2f} mm' for u in under), f'cleat underside - purlin underside: {clb - min(under):+.2f} mm',
            f'plate cleats (Tpc) left at this joint: {tpc_here} (of {tpc} in the model)', 'rule B on the emulator model: ' + rb.split(': ', 1)[1]]
    app, meta = shot(shots, 'ortho_end'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, int(float(meta[1]) + float(meta[3])) + 150), f'purlin underside - rafter top {under[0] - top:+.2f} mm')
    # R12 at a C04 joint: the same view relative to that joint's cleat
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = R['bodies']
    rc = next(b for b in RB if st(b).get('name') == 'Cleat Angle' and st(b).get('amark') == 'R3022'); c = (VT(rc)[0].min(0) + VT(rc)[0].max(0)) / 2
    near = [b for b in RB if b.get('shape') and (VT(b)[0].min(0) < c + 700).all() and (VT(b)[0].max(0) > c - 700).all()]
    col = lambda b: (200, 60, 60) if st(b).get('name') == 'Cleat Angle' else (70, 110, 210) if st(b).get('name') == 'Purlin' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 150, 80)
    r12 = render(near, c, dv, dist, col)
    vid = Image.open(os.path.join(REPO, 'tests', 'steel', 'videos', 'C04', 'a5N_x5zrTYk_~50pct_cleat_zoom4x.png')).convert('RGB')
    return compose('C04', [('R12 (Tekla IFC), cleat at R3022, orthographic', r12), ('Tekla drawing R3022 (package)', tekla_page('R3022')), ('video a5N_x5zrTYk (cleat)', vid), ('app on the emulator, orthographic end view', app)],
                   'C04 purlin cleat L: R12 | Tekla drawing | video | app (default RAFTER set + Generation of purlins CC200-2-20-60 + Purlin cleat L)', ['Measured on the model the emulator built and saved (c04_workflow.ss):'] + ['! ' + m for m in meas])

def c14(shots):
    D = json.load(gzip.open(os.path.join(shots, 'c14_case.ss')))['doc']; B = {b['id']: b for b in D['bodies']}
    st = lambda b: b.get('st') or {}; c = next(c for c in D['stConns'] if c['type'] == 'T146')
    G, J = B[int(c['parts'][0])], B[int(c['parts'][1])]; jv = VT(J)[0]; gv = VT(G)[0]
    tab = next(b for b in D['bodies'] if st(b).get('conn') == c['id'] and st(b).get('name') == 'SHEAR TAB'); tv = VT(tab)[0]
    bo = next(b for b in D['bodies'] if st(b).get('conn') == c['id'] and st(b).get('kind') == 'bolts'); zs = sorted({round(p[2], 2) for p in st(bo)['pts']})
    jt = jv[:, 2].max()
    rb = subprocess.run([sys.executable, os.path.join(REPO, 'tools', 'golden', 'geom_checks.py'), os.path.join(shots, 'c14_case.ss'), '--no-all-pairs'], capture_output=True, text=True).stdout.splitlines()[0]
    meas = [f'joist top - top bolt row {jt - zs[-1]:.1f} mm (R12 77.2), rows {zs[-1] - zs[0]:.1f} apart (R12 70)', f'tab: {jt - tv[:, 2].max():.1f} .. {jt - tv[:, 2].min():.1f} mm below the joist top (R12 47.2 .. 177.2)',
            f'bolts M{st(bo)["d"]:g} {st(bo)["grade"]} L{st(bo)["L"]:g} (R12 M16 8.8 L45)', 'rule B on the emulator model: ' + rb.split(': ', 1)[1]]
    app, meta = shot(shots, 'ortho_end'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, int(float(meta[1]) + float(meta[3])) + 260), f'joist top - top bolt {jt - zs[-1]:.1f} mm')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = R['bodies']
    rt = next(b for b in RB if st(b).get('name') == 'Shear Plate' and st(b).get('tprof') == 'FLT10*130' and st(b).get('amark') == 'EB4002'); cc = (VT(rt)[0].min(0) + VT(rt)[0].max(0)) / 2
    near = [b for b in RB if b.get('shape') and (VT(b)[0].min(0) < cc + 700).all() and (VT(b)[0].max(0) > cc - 700).all()]
    col = lambda b: (200, 60, 60) if st(b).get('name') == 'Shear Plate' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200) if st(b).get('amark', '').startswith('MB') else (60, 150, 80)
    r12 = render(near, cc, dv, dist, col)
    vid = Image.open(os.path.join(REPO, 'tests', 'steel', 'videos', 'C14', 'MrhM4X9opLo_14m50s_bolts_tab_zoom.png')).convert('RGB')
    return compose('C14', [('R12 (Tekla IFC), tab at EB4002, orthographic', r12), ('Tekla drawing EB4002 (package)', tekla_page('EB4002')), ('video MrhM4X9opLo 14:50: Bolts tab, Top', vid), ('app on the emulator, orthographic end view', app)],
                   'C14 Shear plate (146), joist <- girder: R12 | Tekla drawing | video | app (the 146 dialog: Vertical measurement Top 77.2)', ['Measured on the model the emulator built and saved (c14_case.ss):'] + ['! ' + m for m in meas])

def help_pic(*guids):
    """Tekla help pictures (downloaded to build/help/ from help.trimble.com; not kept in the repo), side by side"""
    ims = [Image.open(os.path.join(REPO, 'build', 'help', f'{g}.png')).convert('RGB') for g in guids]; H = max(i.height for i in ims)
    out = Image.new('RGB', (sum(i.width for i in ims) + 20 * (len(ims) - 1), H), 'white'); x = 0
    for i in ims: out.paste(i, (x, 0)); x += i.width + 20
    return out

def rule_b(path): return subprocess.run([sys.executable, os.path.join(REPO, 'tools', 'golden', 'geom_checks.py'), path, '--no-all-pairs'], capture_output=True, text=True).stdout.splitlines()[0].split(': ', 1)[1]

def c02(shots):
    D = json.load(gzip.open(os.path.join(shots, 'c02_case.ss')))['doc']; st = lambda b: b.get('st') or {}
    eh = next(c for c in D['stConns'] if c['type'] == 'Teh'); ep = next(c for c in D['stConns'] if c['type'] == 'T144'); B = {b['id']: b for b in D['bodies']}
    col = B[int(eh['parts'][0])]; cx = np.array(st(col)['p1'][:2], float)
    web = next(b for b in D['bodies'] if st(b).get('conn') == eh['id'] and st(b).get('name') == 'Web'); fl = next(b for b in D['bodies'] if st(b).get('conn') == eh['id'] and st(b).get('name') == 'Bottom flange')
    wv = VT(web)[0]; raf = B[int(eh['parts'][1])]; rd = np.array(st(raf)['p2'][:2], float) - np.array(st(raf)['p1'][:2], float); rd /= np.linalg.norm(rd)
    hx = (wv[:, :2] - cx) @ rd; sgn = 1 if np.abs(hx.max()) > np.abs(hx.min()) else -1; hx = hx * sgn
    L = hx.max(); xn = hx.min(); near = wv[np.abs(hx - xn) < 0.5]; Dp = near[:, 2].max() - near[:, 2].min()
    fv = VT(fl)[0]; bo = next(b for b in D['bodies'] if st(b).get('conn') == ep['id'] and st(b).get('kind') == 'bolts')
    meas = [f'haunch length from the column axis {L:.2f} mm (R12 2,000.00), depth at the column {Dp:.2f} mm (R12 82.93 / 82.90), starts {xn:.1f} from the axis = end plate back face (R12 180.0)',
            f'flange {np.ptp(fv[:, 1]):.1f} wide (R12 150), bolts M{st(bo)["d"]:g} {st(bo)["grade"]} L{st(bo)["L"]:g} (R12 M20 8.8 L60)', 'rule B on the emulator model: ' + rule_b(os.path.join(shots, 'c02_case.ss')),
            'R12 rebuild (web v70 and app): before 48 end plates MATCHED, 96 haunch parts MISSING  ->  after 144 / 144 MATCHED within 0.1 mm (worst 0.016), 384 / 384 bolts']
    app, meta = shot(shots, 'ortho_front'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), f'L {L:.1f} from the column axis,  D {Dp:.2f} at the column')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = R['bodies']
    rw = next(b for b in RB if str(b['id']) == '48'); cc = (VT(rw)[0].min(0) + VT(rw)[0].max(0)) / 2; off = cc - t
    near_ = [b for b in RB if b.get('shape') and (VT(b)[0].min(0) < cc + 2600).all() and (VT(b)[0].max(0) > cc - 2600).all()]
    colr = lambda b: (200, 60, 60) if str(b['id']) in ('47', '48') else (220, 160, 40) if str(b['id']) == '2239' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200) if st(b).get('amark', '').startswith('C') else (60, 150, 80)
    r12 = render(near_, t + off, dv, dist, colr)
    return compose('C02', [('R12 (Tekla IFC), eave R3023 / C2017, orthographic', r12), ('Tekla drawing R3023 (package)', tekla_page('R3023')), ('Tekla 102 help, Picture tab (haunch length as Tekla 102; no eave video readable)', help_pic('707DEAAA-1114-4B38-829F-02812A94E415')), ('app on the emulator, orthographic front view', app)],
                   'C02 End plate (144) + C02h Eave haunch at the eave: R12 | Tekla drawing | Tekla help | app (dialogs: 144 Picture + Bolts; Eave haunch L 2000, D 82.91)', ['Measured on the model the emulator built and saved (c02_case.ss):'] + ['! ' + m for m in meas])

def c09(shots):
    D = json.load(gzip.open(os.path.join(shots, 'c09_case.ss')))['doc']; st = lambda b: b.get('st') or {}
    ep = next(c for c in D['stConns'] if c['type'] == 'T144'); hp = [b for b in D['bodies'] if st(b).get('conn') == ep['id'] and st(b).get('name') == 'HAUNCH_PLATE']
    epb = next(b for b in D['bodies'] if st(b).get('conn') == ep['id'] and st(b).get('name') == 'END PLATE'); bo = next(b for b in D['bodies'] if st(b).get('conn') == ep['id'] and st(b).get('kind') == 'bolts')
    bx = [(np.ptp(VT(h)[0][:, 0]), np.ptp(VT(h)[0][:, 2])) for h in hp]
    meas = [f'{len(hp)} haunch plates, box {", ".join(f"{a:.1f} x {b:.1f}" for a, b in bx)} mm (R12 58.5 x 60.0 and 58.5 x 56.7: b 60 along the end plate, h 60, chamfer 15)',
            f'end plate {np.ptp(VT(epb)[0][:, 0]):.1f} x {np.ptp(VT(epb)[0][:, 2]):.1f} (R12 265.9 x 71.6), bolts M{st(bo)["d"]:g} {st(bo)["grade"]} L{st(bo)["L"]:g} (R12 M16 8.8 L45, heads under the rafter flange)',
            'rule B on the emulator model: ' + rule_b(os.path.join(shots, 'c09_case.ss')),
            'R12 rebuild (web v70 and app): before 24 end plates NEAR 0.8 mm, 48 haunch plates MISSING  ->  after 72 / 72 MATCHED within 0.044 mm, 192 / 192 bolts']
    app, meta = shot(shots, 'ortho_front'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'haunch plates b 60, h 60, chamfer 15')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = R['bodies']
    re_ = next(b for b in RB if str(b['id']) == '1872'); cc = (VT(re_)[0].min(0) + VT(re_)[0].max(0)) / 2
    near_ = [b for b in RB if b.get('shape') and (VT(b)[0].min(0) < cc + 900).all() and (VT(b)[0].max(0) > cc - 900).all()]
    colr = lambda b: (200, 60, 60) if st(b).get('name') == 'HAUNCH_PLATE' else (220, 160, 40) if st(b).get('name') == 'End plate Post' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200) if st(b).get('amark', '').startswith('PC') else (60, 150, 80)
    r12 = render(near_, cc, dv, dist, colr)
    return compose('C09', [('R12 (Tekla IFC), monitor post PC7001, orthographic', r12), ('Tekla drawing PC7001 (package)', tekla_page('PC7001')), ('Tekla 144 help: haunch plates, chamfers 1-4 (video frames blocked)', help_pic('EDF9C3E4-BCA0-4F18-B080-8B9C6A92AFA8', '6BFC0874-1B37-4A14-A4E9-212C4534F173')), ('app on the emulator, orthographic front view', app)],
                   'C09 End plate (144) Haunch tab at the monitor post: R12 | Tekla drawing | Tekla help | app (Haunch tab: Top / Bottom plate 8 x 60 x 60, chamfers 15)', ['Measured on the model the emulator built and saved (c09_case.ss):'] + ['! ' + m for m in meas])

def c13(shots, video=None):
    """C13: c13_case.ss (R12's joint through the dialog) and c13_stiff.ss (+ 144's Stiffeners tab: created, square, 10 x 70)"""
    D = json.load(gzip.open(os.path.join(shots, 'c13_case.ss')))['doc']; st = lambda b: b.get('st') or {}
    ep = next(c for c in D['stConns'] if c['type'] == 'T144'); kids = [b for b in D['bodies'] if st(b).get('conn') == ep['id']]
    epb = next(b for b in kids if st(b).get('name') == 'END PLATE'); hp = [b for b in kids if st(b).get('name') == 'HAUNCH_PLATE']; bo = next(b for b in kids if st(b).get('kind') == 'bolts')
    ev = VT(epb)[0]; hb = [np.ptp(VT(h)[0], axis=0) for h in hp]
    D2 = json.load(gzip.open(os.path.join(shots, 'c13_stiff.ss')))['doc']; ep2 = next(c for c in D2['stConns'] if c['type'] == 'T144')
    sf = [b for b in D2['bodies'] if st(b).get('conn') == ep2['id'] and st(b).get('name') == 'STIFFENER']; sb = [np.ptp(VT(b)[0], axis=0) for b in sf]
    meas = [f'end plate {np.ptp(ev[:, 2]):.1f} x {np.ptp(ev[:, 1]):.1f} x {np.ptp(ev[:, 0]):.1f} (R12 430 x 120 x 16), bolts M{st(bo)["d"]:g} {st(bo)["grade"]} L{st(bo)["L"]:g} x {st(bo).get("n", len(st(bo)["pts"]))} (R12 M16 8.8 L50 x 8)',
            f'girder-side Stiff. plates (144 Haunch tab): {", ".join(f"{a[0]:.1f} x {a[2]:.1f} x {a[1]:.1f}" for a in hb)} (R12 50 x 55 x 10, clip 15, chamfer 12.5)',
            f'144 Stiffeners tab (created, square, t 10, b 70): {len(sf)} stiffeners {", ".join(f"{a[0]:.0f} x {a[1]:.0f} x {a[2]:.0f}" for a in sb[:1])} (R12\'s own FS / NS there: 296 x 70 x 10, by Column with stiffeners (186))',
            'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c13_case.ss')) + ' | with stiffeners: ' + rule_b(os.path.join(shots, 'c13_stiff.ss')),
            'R12 rebuild (web v70 and app): before 0 / 6 end plates as 144 (read as Cap 39), 12 Stiff. MISSING  ->  after 6 / 6 end plates + 12 / 12 Stiff. at box 0.0, 48 / 48 bolts']
    app, meta = shot(shots, 'ortho_front'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'end plate 430, Stiff. 55 x 50, M16 L50')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = R['bodies']
    re_ = next(b for b in RB if str(b['id']) == '3001'); cc = (VT(re_)[0].min(0) + VT(re_)[0].max(0)) / 2
    near_ = [b for b in RB if b.get('shape') and (VT(b)[0].min(0) < cc + 1300).all() and (VT(b)[0].max(0) > cc - 1300).all()]
    colr = lambda b: (200, 60, 60) if st(b).get('name', '').strip() in ('Top Stiff.', 'Bot Stiff.') else (220, 160, 40) if st(b).get('name', '').strip() == 'End Plate' else (230, 230, 230) if st(b).get('name') in ('Top FS', 'Top NS', 'Bot FS', 'Bot Ns') else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200) if st(b).get('amark', '').startswith('C') else (60, 150, 80)
    r12 = render(near_, cc + np.array([-400.0, 0, 0]), dv, dist, colr, W=1400, H=900)
    vid = Image.open(video).convert('RGB') if video and os.path.exists(video) else help_pic('6BFC0874-1B37-4A14-A4E9-212C4534F173')
    return compose('C13', [('R12 (Tekla IFC), C2015 / EB4002, orthographic', r12), ('Tekla drawing EB4002 (package)', tekla_page('EB4002')), ('video / Tekla help (see VIDEO.txt)', vid), ('app on the emulator, orthographic front view (landscape)', app)],
                   'C13 End plate (144) at the mezzanine girder -> column flange: R12 | Tekla drawing | video | app (144 Picture + Haunch + Bolts; Stiffeners tab)', ['Measured on the models the emulator built and saved (c13_case.ss, c13_stiff.ss):'] + ['! ' + m for m in meas])

def c20(shots, video=None):
    """C20: c20_case.ss (R12's joint C2020 / EB4007 through the 186 dialog) and c20_b50.ss (Top NS b 50, Modify)"""
    st = lambda b: b.get('st') or {}
    def meas(f):
        D = json.load(gzip.open(os.path.join(shots, f)))['doc']; c = next(c for c in D['stConns'] if c['type'] == 'T186'); kids = [b for b in D['bodies'] if st(b).get('conn') == c['id']]
        sf = sorted([b for b in kids if st(b).get('name') == 'STIFFENER'], key=lambda b: (-VT(b)[0][:, 2].mean(), VT(b)[0][:, 1].mean()))
        tab = next(b for b in kids if st(b).get('name') == 'SHEAR TAB'); bo = next(b for b in kids if st(b).get('kind') == 'bolts')
        beam = next(b for b in D['bodies'] if st(b).get('kind') == 'member' and st(b).get('prof', '').startswith('BI324'))
        bv = VT(beam)[0]; fl = bv[np.abs(bv[:, 2] - 3838) > 151]; strip = fl[:, 1].min() - 16789.0
        sz = lambda b: np.ptp(VT(b)[0], axis=0)
        return dict(stiff=[f'{sz(b)[2]:.1f} x {sz(b)[1]:.1f} x {sz(b)[0]:.1f}' for b in sf], tab=sz(tab), bolt=st(bo), strip=strip)
    a = meas('c20_case.ss'); b = meas('c20_b50.ss')
    lines = [f'stiffeners (t x b x length along the column depth): {", ".join(a["stiff"])} (R12 296 x 70 x 10, i.e. 10 x 70 x 296; chamfer 5.6, K prep)',
             f'shear tab {a["tab"][0]:.1f} x {a["tab"][1]:.1f} x {a["tab"][2]:.1f} (R12 10 x 150 x 210), bolts M{a["bolt"]["d"]:g} {a["bolt"]["grade"]} L{a["bolt"]["L"]:g} x {a["bolt"].get("n", len(a["bolt"]["pts"]))} (R12 M16 8.8 L45 x 8)',
             f'beam flanges stripped to {a["strip"]:.1f} from the column web face (R12 90.0 = stiffener 70 + Picture 2 = 20)',
             f'after Top NS b 50 + Modify: {", ".join(b["stiff"])}; flange strip {b["strip"]:.1f} (50 + 20 = 70)',
             'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c20_case.ss')).split(';')[0] + ' | after b 50: ' + rule_b(os.path.join(shots, 'c20_b50.ss')).split(';')[0],
             'R12 rebuild (web v70 and app): row 20 stiffeners 0 / 68 -> 56 / 68 (12 at beams with no web in R12), tabs 18 / 18, bolts 128 / 128, member bodies 36 / 36 (app vs R12, 0.1 mm)']
    app, meta = shot(shots, 'ortho_top'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'stiffeners 10 x 70, gap 2, tab 150, strip 90')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = [b for b in R['bodies'] if b.get('shape')]
    near_ = [b for b in RB if (VT(b)[0].min(0) < t + 1300).all() and (VT(b)[0].max(0) > t - 1300).all()]
    colr = lambda b: (230, 230, 230) if st(b).get('name') in ('Top FS', 'Top NS', 'Bot FS', 'Bot Ns') else (220, 160, 40) if st(b).get('name') == 'Shear Plate' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200) if st(b).get('amark', '').startswith('C') else (60, 150, 80)
    r12 = render(near_, t, dv, dist, colr, W=1400, H=900)
    vid = Image.open(video or os.path.join(REPO, 'tests', 'steel', 'videos', 'C20', 'frames_lYG_CPzBR40', 'f_134.jpg')).convert('RGB'); vid = vid.resize((vid.width * 2, vid.height * 2))
    return compose('C20', [('R12 (Tekla IFC), C2020 / EB4007, plan, cut at +4300', r12), ('Tekla drawing C2020 (package)', tekla_page('C2020')), ('video lYG_CPzBR40 02:14: the 186 Stiffeners tab', vid), ('app on the emulator, orthographic plan (landscape)', app)],
                   'C20 Column with stiffeners (186) at the mezzanine column web: R12 | Tekla drawing | video | app (Picture + Plates + Stiffeners + Bolts tabs)', ['Measured on the models the emulator built and saved (c20_case.ss, c20_b50.ss):'] + ['! ' + m for m in lines])

def c05(shots, video=None):
    """C05: c05_case.ss (R12's node R3019 through the 20 dialog: HZ6014 + strut ST5008) and c05_e40.ss (HZ brace e 30 -> 40, Modify)"""
    st = lambda b: b.get('st') or {}
    def meas(f):
        D = json.load(gzip.open(os.path.join(shots, f)))['doc']; cs = [c for c in D['stConns'] if c['type'] == 'Ttg']; out = []
        for c in cs:
            kids = [b for b in D['bodies'] if st(b).get('conn') == c['id']]
            tg = next(b for b in kids if st(b).get('name') == 'TONGUE PLATE'); caps = [b for b in kids if st(b).get('name') == 'END CAP']; bo = next(b for b in kids if st(b).get('kind') == 'bolts')
            def sz(b):   # the plate's own extents (principal axes), not the world box of a rotated plate
                v = VT(b)[0]; c0 = v.mean(0); w_, vv = np.linalg.eigh((v - c0).T @ (v - c0)); n_ = vv[:, 0]; u_, w2 = vv[:, 1], vv[:, 2]; best = None
                for a_ in np.radians(np.arange(0, 180, 0.01)):   # the minimum-area box in the plate's plane (holes tilt the principal axes)
                    x_ = np.cos(a_) * u_ + np.sin(a_) * w2; y_ = np.cross(n_, x_); e_ = (np.ptp((v - c0) @ x_), np.ptp((v - c0) @ y_))
                    if best is None or e_[0] * e_[1] < best[0] * best[1]: best = e_
                return sorted(np.round([np.ptp((v - c0) @ n_), best[0], best[1]], 1))
            out.append(f"tube end {c['id']}: plate {' x '.join(str(x) for x in sz(tg))}, {len(caps)} end plates, bolts M{st(bo)['d']:g} L{st(bo)['L']:g} x {st(bo).get('n', len(st(bo)['pts']))}")
        return out
    a = meas('c05_case.ss'); b = meas('c05_e40.ss')
    lines = ['before the e change: ' + '; '.join(a), 'after HZ e 30 -> 40 + Modify: ' + '; '.join(b),
             'R12 (NC 6508 / 6512 / 1): plate 315 x 140 x 12 (285 x 140 x 12), holes d18 at 40 / 100 x 40 / 100 (30 / 80 x 45 / 95), end plates 44 x 100 x 5; M16 8.8 L50, grip 24',
             'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c05_case.ss')).split(';')[0] + ' | after e 40: ' + rule_b(os.path.join(shots, 'c05_e40.ss')).split(';')[0],
             'R12 rebuild (web v70 and app): row 5 parts 0 -> 943 / 1,294 (the rest: crossing halves C07, own-assembly gusset nodes C06); app vs R12 plates 916 / 916, bolts 1,264 / 1,264, member bodies 632 / 632']
    app, meta = shot(shots, 'ortho_top'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'plates 12 x 140, 150 in the tube, end plates 5')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = [b for b in R['bodies'] if b.get('shape')]
    near_ = [b for b in RB if (VT(b)[0].min(0) < t + 1300).all() and (VT(b)[0].max(0) > t - 1300).all()]
    colr = lambda b: (230, 170, 40) if st(b).get('name', '').strip() == 'Connection Plate' else (235, 235, 235) if st(b).get('tprof') == 'FLT5*100' else (150, 90, 200) if st(b).get('name', '').strip() == 'Gusset Plate' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200) if st(b).get('amark', '').startswith('R') else (60, 150, 80)
    r12 = render(near_, t + np.array([0, 0, 400.0]), dv, dist, colr, W=1400, H=900)
    vid = Image.open(video or os.path.join(REPO, 'tests', 'steel', 'videos', 'C05', 'frames_Z5SVpvwDdv8', 'f_660.jpg')).convert('RGB'); vid = vid.resize((vid.width * 2, vid.height * 2))
    return compose('C05', [('R12 (Tekla IFC), node on R3019, plan', r12), ('Tekla single-part drawing 6508 (package)', tekla_sp('6508')), ('video Z5SVpvwDdv8 11:00: Tube gusset (20), Brace conn tab', vid), ('app on the emulator, orthographic plan (landscape)', app)],
                   'C05 Tube gusset (20), the tube end: R12 | Tekla drawing | video | app (Picture + Gusset + Bolts tabs)', ['Measured on the models the emulator built and saved (c05_case.ss, c05_e40.ss):'] + ['! ' + m for m in lines])

def tekla_sp(mark):
    with zipfile.ZipFile(PKG) as z:
        n = next(x for x in z.namelist() if x.endswith(f'/Single Part Drawings/{mark} - Standard.pdf'))
        tmp = os.path.join(REPO, 'build', f'_{mark}_sp.pdf'); open(tmp, 'wb').write(z.read(n))
    out = tmp[:-4]; subprocess.run(['pdftoppm', '-png', '-r', '120', '-singlefile', tmp, out], check=True); os.remove(tmp); return Image.open(out + '.png').convert('RGB')

def c06(shots, video=None):
    """C06: c06_case.ss (R12's node R3019 with Gusset shape = Tekla) and c06_gd30.ss (HZ brace Picture 5 = 30, Modify)"""
    st = lambda b: b.get('st') or {}
    def outline(f):
        D = json.load(gzip.open(os.path.join(shots, f)))['doc']; g = next(b for b in D['bodies'] if st(b).get('name') == 'GUSSET PLATE'); v = VT(g)[0]
        c0 = v.mean(0); w_, vv = np.linalg.eigh((v - c0).T @ (v - c0)); n_ = vv[:, 0]; best = None
        for a_ in np.radians(np.arange(0, 180, 0.01)):
            x_ = np.cos(a_) * vv[:, 1] + np.sin(a_) * vv[:, 2]; y_ = np.cross(n_, x_); e_ = (np.ptp((v - c0) @ x_), np.ptp((v - c0) @ y_))
            if best is None or e_[0] * e_[1] < best[0] * best[1]: best = e_
        return sorted(np.round(best, 1), reverse=True), st(g).get('conn')
    a, _ = outline('c06_case.ss'); b, _ = outline('c06_gd30.ss')
    lines = [f'recording case = 2 of the node\'s 4 braces (HZ6014, ST5008): gusset {a[0]} x {a[1]} x 12; the full node (+ HZ6004, ST5004) in R12_app.ss: 549.4 x 280.5 (R12 IFC 549.4 x 280.4 / NC 6532: PL12 549.34 x 280.39; outline (0,0) (549.34,0) (549.34,190.21) (426.53,257.42) (339.32,280.39) (199.32,280.39) ...)',
             f'after the HZ brace Picture 5 = 30 + Modify: gusset {b[0]} x {b[1]} x 12 (its edge 10 mm nearer the gusset\'s centre)',
             'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c06_case.ss')).split(';')[0] + ' | after 5 = 30: ' + rule_b(os.path.join(shots, 'c06_gd30.ss')).split(';')[0],
             'R12 rebuild (web v70 and app): row 6 gussets 82 -> 233 / 250 at 0.1 mm (17 near: the gable / end-frame nodes, braces skewed out of the gusset plane); app vs R12 230 / 230']
    app, meta = shot(shots, 'ortho_top'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'gusset: edges 20 before the end plates')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = [b for b in R['bodies'] if b.get('shape')]
    near_ = [b for b in RB if (VT(b)[0].min(0) < t + 1300).all() and (VT(b)[0].max(0) > t - 1300).all()]
    colr = lambda b: (150, 90, 200) if st(b).get('name', '').strip() == 'Gusset Plate' else (230, 170, 40) if st(b).get('name', '').strip() == 'Connection Plate' else (235, 235, 235) if st(b).get('tprof') == 'FLT5*100' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200) if st(b).get('amark', '').startswith('R') else (60, 150, 80)
    r12 = render(near_, t + np.array([0, 0, 400.0]), dv, dist, colr, W=1400, H=900)
    pic = Image.open(video or os.path.join(REPO, 'build', 'help', 'c20', '07.png')).convert('RGB'); pic = pic.resize((pic.width * 3, pic.height * 3))
    return compose('C06', [('R12 (Tekla IFC), node on R3019 (gusset 6532), plan', r12), ('Tekla single-part drawing 6532 (package)', tekla_sp('6532')), ('Tekla help: Tube gusset (20) Picture tab (dims 1-10; 5 = gusset to end plate)', pic), ('app on the emulator, orthographic plan (landscape)', app)],
                   'C06 Tube gusset (20), the gusset: R12 | Tekla drawing | Tekla help picture | app (Gusset tab: Gusset shape = Tekla)', ['Measured on the models the emulator built and saved (c06_case.ss, c06_gd30.ss):'] + ['! ' + m for m in lines])

def c07(shots, video=None):
    """C07: c07_case.ss (R12's roof crossing HZ6004 / HZ6002 / HZ6006 through the Tube crossing dialog) and c07_gd30.ss (Picture 5 = 30, Modify)"""
    st = lambda b: b.get('st') or {}
    def box(v):
        c0 = v.mean(0); w_, vv = np.linalg.eigh((v - c0).T @ (v - c0)); n_ = vv[:, 0]; best = None
        for a_ in np.radians(np.arange(0, 180, 0.01)):
            x_ = np.cos(a_) * vv[:, 1] + np.sin(a_) * vv[:, 2]; y_ = np.cross(n_, x_); e_ = (np.ptp((v - c0) @ x_), np.ptp((v - c0) @ y_))
            if best is None or e_[0] * e_[1] < best[0] * best[1]: best = e_
        return sorted(np.round(best, 1), reverse=True)
    def meas(f):
        D = json.load(gzip.open(os.path.join(shots, f)))['doc']; c = next(c for c in D['stConns'] if c['type'] == 'Ttgx'); kids = [b for b in D['bodies'] if st(b).get('conn') == c['id']]
        g = next(b for b in kids if st(b).get('name') == 'CROSSING GUSSET'); tg = [b for b in kids if st(b).get('name') == 'TONGUE PLATE']; caps = [b for b in kids if st(b).get('name') == 'END CAP']
        bo = [b for b in kids if st(b).get('kind') == 'bolts']; gb = box(VT(g)[0])
        return gb, f"gusset {gb[0]} x {gb[1]} x 12, {len(tg)} connection plates {' / '.join('x'.join(str(x) for x in box(VT(b)[0])) for b in tg)}, {len(caps)} end plates, bolts " + ', '.join(f"M{st(b)['d']:g} L{st(b)['L']:g} x {st(b).get('n', len(st(b)['pts']))}" for b in bo)
    (ga, a), (gb, b) = meas('c07_case.ss'), meas('c07_gd30.ss')
    lines = ['R12 / NC 6534: gusset PL12 572.73 x 330.06 (hexagon (0,130.82) (49.85,0) (425.18,0) (572.74,199.23) (522.89,330.06) (147.55,330.06)), 8 holes d18; plates 315 x 140 x 12; end plates 100 x 44 x 5; M16 8.8',
             'app: ' + a, f'after Picture 5 = 30 + Modify: gusset {gb[0]} x {gb[1]} x 12 (each half\'s edge 10 mm nearer the crossing)',
             'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c07_case.ss')).split(';')[0] + ' | after 5 = 30: ' + rule_b(os.path.join(shots, 'c07_gd30.ss')).split(';')[0],
             'R12 rebuild (web v70 and app): row 7 crossing gussets 0 -> 52 / 52 at 0.1 mm, caps 48 / 66 (18 near, 0.1-1.7 mm, Tekla turned them 0.3 deg); app vs R12 parts 364 / 364, bolts 416 / 416']
    app, meta = shot(shots, 'ortho_top'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), f'crossing gusset {ga[0]} x {ga[1]}')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = [b for b in R['bodies'] if b.get('shape')]
    near_ = [b for b in RB if (VT(b)[0].min(0) < t + 1500).all() and (VT(b)[0].max(0) > t - 1500).all()]
    colr = lambda b: (150, 90, 200) if st(b).get('name', '').strip() == 'Gusset Plate' and str(st(b).get('tprof', '')).startswith('PL12') else (230, 170, 40) if st(b).get('name', '').strip() == 'Connection Plate' else (235, 235, 235) if str(st(b).get('tprof', '')).startswith(('FLT5', 'PL5')) else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 150, 80)
    r12 = render(near_, t + np.array([0, 0, 400.0]), dv, dist, colr, W=1400, H=900)
    pic = Image.open(video or os.path.join(REPO, 'build', 'help', 'c20', '07.png')).convert('RGB'); pic = pic.resize((pic.width * 3, pic.height * 3))
    return compose('C07', [('R12 (Tekla IFC), crossing HZ6004 (gusset 6534), plan', r12), ('Tekla single-part drawing 6534 (package)', tekla_sp('6534')), ('Tekla help: Tube gusset (20) Picture tab (5 = gusset to end plate)', pic), ('app on the emulator, orthographic plan (landscape)', app)],
                   'C07 Tube crossing (Tekla 20): R12 | Tekla drawing | Tekla help picture | app (Tube end = at the brace end)', ['Measured on the models the emulator built and saved (c07_case.ss, c07_gd30.ss):'] + ['! ' + m for m in lines])

def c01(shots, video=None):
    """C01 / C16: c01_case.ss (R12's C2020 base through the 1042 dialog: anchor rods as parts) and c01_hooky.ss (hook direction Y, Modify)"""
    st = lambda b: b.get('st') or {}
    def meas(f):
        D = json.load(gzip.open(os.path.join(shots, f)))['doc']; c = next(c for c in D['stConns'] if c['type'] == 'T1042'); kids = [b for b in D['bodies'] if st(b).get('conn') == c['id']]
        by = lambda n: [b for b in kids if st(b).get('name') == n]; bp = by('BASE PLATE')[0]; top = VT(bp)[0][:, 2].max(); bot = VT(bp)[0][:, 2].min()
        r = by('ANCHOR ROD')[0]; rv = VT(r)[0]; ext = np.ptp(rv[:, :2], 0)
        g = by('GROUT')[0]; gv = VT(g)[0]; gz0, gz1 = gv[:, 2].min(), gv[:, 2].max(); gb = np.ptp(gv[np.isclose(gv[:, 2], gz0)][:, :2], 0); gt = np.ptp(gv[np.isclose(gv[:, 2], gz1)][:, :2], 0)
        cp = by('CAST PLATE')[0]; cz = VT(cp)[0][:, 2]
        nz = ', '.join(f"{st(b).get('name').lower()} {VT(b)[0][:, 2].min() - top:.0f}..{VT(b)[0][:, 2].max() - top:.0f}" for b in sorted(by('WASHER')[:1] + by('NUT')[:1], key=lambda b: VT(b)[0][:, 2].min()))
        return (f"{len(by('ANCHOR ROD'))} rods {st(r).get('prof')}: top +{rv[:, 2].max() - top:.1f}, bottom {rv[:, 2].min() - top:.1f} from the plate top, hook {max(ext):.1f} x {min(ext):.1f} in plan",
                f"{nz} above the plate; grout {gz1 - gz0:.0f} thick, bottom {gb[0]:.0f} x {gb[1]:.0f}, top {gt[0]:.0f} x {gt[1]:.0f} (plate {np.ptp(VT(bp)[0][:, 0]):.0f} x {np.ptp(VT(bp)[0][:, 1]):.0f}); cast plate {cz.min() - bot:.0f} .. {cz.max() - bot:.0f} below the plate", ext)
    a1, a2, ea = meas('c01_case.ss'); b1, b2, eb = meas('c01_hooky.ss')
    lines = ['app (c01_case.ss): ' + a1, '   ' + a2, f"after Hook direction = Y + Modify (c01_hooky.ss): hook {eb[0]:.1f} x {eb[1]:.1f} in plan (X x Y)",
             'R12 / Tekla 1501: ROD20 600 overall, +50 above the plate, a 40, b 120, 1.53 kg; washer PD40*10 0..10, nut 10..30; grout PL30 with slopes (margin 30), cast plate PL4 30 below the plate',
             'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c01_case.ss')).split(';')[0] + ' | after hook Y: ' + rule_b(os.path.join(shots, 'c01_hooky.ss')).split(';')[0],
             'R12 rebuild (web v70 and app): row 1 48 -> 720 / 720, row 16 4 -> 24 / 24 at 0.1 mm; app vs R12 744 / 744 parts, 923.41 kg = Tekla 923.41']
    app, meta = shot(shots, 'ortho_front'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'hooked ROD20, washer + nut, grout with slopes, cast plate')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = [b for b in R['bodies'] if b.get('shape')]
    near_ = [b for b in RB if (VT(b)[0].min(0) < t + 900).all() and (VT(b)[0].max(0) > t - 900).all()]
    colr = lambda b: (200, 60, 60) if st(b).get('name') == 'Anchor Bolt' else (230, 170, 40) if st(b).get('name') in ('NUT', 'WASHER') else (150, 150, 150) if st(b).get('name') == 'GROUT' else (150, 90, 200) if st(b).get('name') == 'CASTPLATE' else (60, 110, 200) if st(b).get('name') == 'Base Plate' else (60, 150, 80)
    r12 = render(near_, t, dv, dist, colr, W=1100, H=1100)   # cut at the rods' plane, as the app's section view
    pic = Image.open(video).convert('RGB') if video else tekla_page('1001')
    return compose('C01', [('R12 (Tekla IFC), base of C2020, front section', r12), ('Tekla single-part drawing 1501 (package)', tekla_sp('1501')), ('video / Tekla assembly drawing 1001', pic), ('app on the emulator, orthographic front (landscape)', app)],
                   'C01 / C16 Base plate 1042 / 1004: R12 | Tekla drawing | video | app (Anchor rods tab: Base plate with = Anchor rods)', ['Measured on the models the emulator built and saved (c01_case.ss, c01_hooky.ss):'] + ['! ' + m for m in lines])

def c18(shots, video=None):
    """C18: c18_case.ss (R12's lower flight: Stair treads in Tekla mode + a Stair end plate at the landing) and c18_ntr10.ss (10 treads, Modify)"""
    st = lambda b: b.get('st') or {}
    def meas(f):
        D = json.load(gzip.open(os.path.join(shots, f)))['doc']; tr = [b for b in D['bodies'] if st(b).get('name') == 'TREAD']; br = [b for b in D['bodies'] if st(b).get('name') == 'BRACKET']
        sp = [b for b in D['bodies'] if st(b).get('name') == 'STAIR PLATE']; bo = [b for b in D['bodies'] if st(b).get('kind') == 'bolts']
        t0 = sorted(tr, key=lambda b: VT(b)[0][:, 2].max())[:2]; z = [VT(b)[0][:, 2].max() for b in t0]; y = [VT(b)[0][:, 1].min() for b in t0]
        tb = np.ptp(VT(t0[0])[0], 0); bb = np.ptp(VT(br[0])[0], 0) if br else [0, 0, 0]
        return (f"{len(tr)} treads {st(t0[0]).get('prof')} {st(t0[0]).get('mat')} {tb[0]:.0f} x {tb[1]:.0f} x {tb[2]:.0f}, rise {z[1] - z[0]:.3f}, going {y[1] - y[0]:.2f}; {len(br)} brackets {st(br[0]).get('prof') if br else ''} {bb[1]:.0f} long",
                f"{len(sp)} stair plate(s) {', '.join(st(b).get('prof', '') for b in sp)}; bolts " + ', '.join(sorted({f"M{st(b)['d']:g} {st(b).get('grade')} L{st(b)['L']:g} x {st(b).get('n', len(st(b)['pts']))}" for b in bo})))
    a1, a2 = meas('c18_case.ss'); b1, _ = meas('c18_ntr10.ss')
    lines = ['app (c18_case.ss): ' + a1, '   ' + a2, 'after 10 treads + Modify (c18_ntr10.ss): ' + b1,
             'R12: 12 treads PL10*335.7 Grating 990 x 300 x 40, rise 156.667, going 309.92; L60*6 x 140 brackets, 2 M12 8.8 L35 each; landing plate FLT12*150 320 x 150, 2 x 4 M16 L45 through the post',
             'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c18_case.ss')).split(';')[0] + ' | after 10 treads: ' + rule_b(os.path.join(shots, 'c18_ntr10.ss')).split(';')[0],
             'R12 rebuild (web v70 and app): row 18 4 -> 81 / 83 parts (the 2 counter plates on EB4002 left for R13), 12 / 12 stringer pieces, 148 / 148 bolts; treads + brackets 141.22 kg = Tekla']
    app, meta = shot(shots, 'ortho_tread'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'U-pan grating tread on L60*6 brackets, 2 M12 each')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = [b for b in R['bodies'] if b.get('shape')]
    near_ = [b for b in RB if (VT(b)[0].min(0) < t + 900).all() and (VT(b)[0].max(0) > t - 900).all()]
    colr = lambda b: (150, 150, 150) if st(b).get('name') == 'THREAD' else (230, 170, 40) if st(b).get('name') == 'BRACKET' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200)
    r12 = render(near_, t + np.array([0, -500.0, 0]), dv, dist, colr, W=1100, H=1100)
    def tekla_file(rel):
        with zipfile.ZipFile(PKG) as z:
            n = next(x for x in z.namelist() if x.endswith('/' + rel)); tmp = os.path.join(REPO, 'build', '_c18.pdf'); open(tmp, 'wb').write(z.read(n))
        out = tmp[:-4]; subprocess.run(['pdftoppm', '-png', '-r', '120', '-singlefile', tmp, out], check=True); os.remove(tmp); return Image.open(out + '.png').convert('RGB')
    pic = Image.open(video).convert('RGB') if video else tekla_file('GA Drawings/9 - Stairs View.pdf')
    return compose('C18', [('R12 (Tekla IFC), first tread of the lower flight, section', r12), ('Tekla assembly drawing SGR9001: treads + brackets (package)', tekla_page('SGR9001')), ('Tekla GA 9 - Stairs View (package)', pic), ('app on the emulator, orthographic tread section (landscape)', app)],
                   'C18 Stairs as Tekla: R12 | Tekla drawing | Tekla GA | app (Stair treads: Treads = Tekla; Stair end plate)', ['Measured on the models the emulator built and saved (c18_case.ss, c18_ntr10.ss):'] + ['! ' + m for m in lines])

def c03(shots, video=None):
    """C03: c03_case.ss (R12's apex at R3008 through Apex haunch (106)) and c03_len1500.ss (haunch length 1500, Modify)"""
    st = lambda b: b.get('st') or {}
    def meas(f):
        D = json.load(gzip.open(os.path.join(shots, f)))['doc']; by = lambda n: [b for b in D['bodies'] if st(b).get('name') == n]
        ep = by('END PLATE'); web = by('APEX HAUNCH WEB'); fl = by('APEX HAUNCH FLANGE'); sf = by('APEX STIFFENER')
        ap = max(VT(b)[0][:, 0].max() for b in ep if VT(b)[0][:, 0].min() < 46280)   # the joint: the left plate's outer face
        w = [b for b in web if VT(b)[0][:, 0].min() < ap][0]; wv = VT(w)[0]; face = wv[:, 0].max(); atp = wv[np.isclose(wv[:, 0], face)][:, 2]
        out = f"haunch {ap - wv[:, 0].min():.1f} horizontal from the apex, {atp.max() - atp.min():.3f} plumb at the plate"
        if sf: s0 = [b for b in sf if VT(b)[0][:, 0].min() < ap][0]; sv = VT(s0)[0]; out += f"; stiffener {np.ptp(sv[:, 0]):.1f} long, {sv[:, 2].max() - sv[np.isclose(sv[:, 0], sv[:, 0].min())][:, 2].min():.2f} high at its far end, {np.ptp(sv[:, 1]):.0f} thick"
        return out, len(sf)
    (a, na), (b, nb) = meas('c03_case.ss'), meas('c03_len1500.ss')
    lines = ['app (c03_case.ss): ' + a, 'after Haunch length 1500 + Modify (c03_len1500.ss): ' + b,
             'R12 / Tekla: haunch 2000 horizontal, 83.373 plumb, flange ends plumb; 3515 FLT8*130 L2029, 3516 FLT10*150 L2016; 2504 Stiff. FLT10*60 length 88.0 (48 pcs)',
             'rule B on the emulator models: ' + rule_b(os.path.join(shots, 'c03_case.ss')).split(';')[0] + ' | after 1500: ' + rule_b(os.path.join(shots, 'c03_len1500.ss')).split(';')[0],
             'R12 rebuild (web v70 and app): row 3 48 -> 192 / 192 at 0.1 mm; app vs R12 192 / 192, 1,885.92 kg = Tekla 1,885.92']
    app, meta = shot(shots, 'ortho_apex'); t = np.array([float(x) for x in meta[4:7]]); dv = [float(x) for x in meta[7:10]]; dist = float(meta[10])
    label(app, (40, 140), 'end plate stiffeners FLT10*60 above the rafter')
    R = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R12.ss')))['doc']; RB = [b for b in R['bodies'] if b.get('shape')]
    near_ = [b for b in RB if (VT(b)[0].min(0) < t + 700).all() and (VT(b)[0].max(0) > t - 700).all()]
    colr = lambda b: (230, 170, 40) if st(b).get('name') == 'Stiff.' else (150, 90, 200) if st(b).get('name') == 'End Plate' else (90, 90, 90) if st(b).get('kind') == 'bolts' else (60, 110, 200)
    r12 = render(near_, t, dv, dist, colr, W=1100, H=1100)
    pic = Image.open(video).convert('RGB') if video else tekla_page('R3008')
    return compose('C03', [('R12 (Tekla IFC), apex at R3008, section', r12), ('Tekla single-part drawing 2504: Stiff. FLT10*60 (package)', tekla_sp('2504')), ('Tekla assembly drawing R3008 (package)', pic), ('app on the emulator, orthographic apex (landscape)', app)],
                   'C03 Apex haunch as Tekla 106: R12 | Tekla drawing | Tekla drawing | app (Apex haunch (106): Haunch = Tekla, Stiffeners)', ['Measured on the models the emulator built and saved (c03_case.ss, c03_len1500.ss):'] + ['! ' + m for m in lines])

if __name__ == '__main__':
    os.makedirs(os.path.join(REPO, 'build'), exist_ok=True)
    {'C04': c04, 'C14': c14, 'C02': c02, 'C09': c09, 'C13': c13, 'C20': c20, 'C05': c05, 'C06': c06, 'C07': c07, 'C01': c01, 'C18': c18, 'C03': c03}[sys.argv[1]](*sys.argv[2:])
