#!/usr/bin/env python3
"""Comparison image for a parity connection (standing rule B5: orthographic, labelled, dimensions written on it):
    R12 (Tekla's IFC, rendered orthographic from the app's view) | Tekla's drawing | video frame | app on the emulator (orthographic)
and the measured values from the model the emulator itself built and saved.

    python3 tools/golden/parity_png.py C04 <shots dir with NN_ortho_end.png/.txt and c04_workflow.ss>
    python3 tools/golden/parity_png.py C14 <shots dir with NN_ortho_end.png/.txt and c14_case.ss>
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

if __name__ == '__main__':
    os.makedirs(os.path.join(REPO, 'build'), exist_ok=True)
    {'C04': c04, 'C14': c14}[sys.argv[1]](sys.argv[2])
