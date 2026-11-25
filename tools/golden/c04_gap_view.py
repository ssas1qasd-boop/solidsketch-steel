#!/usr/bin/env python3
"""Orthographic end view of the C04 joint of cleat 3185 (rafter R3022, purlins 3183 / 3184), looking along the purlins, with the
gap "purlin underside - rafter top" (perpendicular to the rafter) written on it:
  app cases from build/c04-gap/<case>.json (C04MemberGapTest, -PhostJni) and R11 from tests/steel/models/Frame_28x53m_R11.ss.
    python3 tools/golden/c04_gap_view.py  -> docs/steel/parity/C04_gap.png
"""
import gzip, json, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16); FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 18)
J = np.array([6507.808, 22355.0, 9057.605]); P1 = np.array([237.306, 22355, 7416.591]); P2 = np.array([6705.5, 22355, 8887.192])
A = (P2 - P1) / np.linalg.norm(P2 - P1); N = np.array([0, 0, 1.0]) - A * A[2]; N /= np.linalg.norm(N)
COL = {'rafter': (60, 150, 80), 'purlin': (60, 110, 200), 'cleat': (190, 190, 200), 'stiff': (150, 150, 165), 'bolts': (40, 40, 40)}

def role(name, kind):
    n = (name or '').upper()
    if 'CLEAT' in n and 'STIFF' not in n or n == 'CLEAT ANGLE': return 'cleat'
    if 'STIFF' in n: return 'stiff'
    if kind == 'bolts' or 'BOLT' in n: return 'bolts'
    if 'PURLIN' in n: return 'purlin'
    return 'rafter'

def panel(bodies, gap, title, W=520, H=520, scale=1.6):
    im = Image.new('RGB', (W, H), (255, 255, 255)); d = ImageDraw.Draw(im)
    def to2(P): q = P - J; return np.c_[W / 2 + (q @ A) * scale, H * 0.55 - (q @ N) * scale]
    order = ['rafter', 'purlin', 'cleat', 'stiff', 'bolts']
    for r in order:
        for name, kind, V, T in bodies:
            if role(name, kind) != r: continue
            q = V - J
            keep = (np.abs(q @ A) < 400) & (np.abs(q[:, 1]) < 3000) & (np.abs(q @ N) < 400)
            if not keep.any(): continue
            P = to2(V)
            for t in T:
                if not keep[t].any(): continue
                d.polygon([tuple(P[i]) for i in t], fill=COL[r])
    # the rafter top line and the purlin underside, and the dimension
    xs = W / 2 + 150
    top_y = H * 0.55 - 0 * scale
    d.line([(20, top_y), (W - 20, top_y)], fill=(200, 0, 0), width=1)
    bot_y = H * 0.55 - gap * scale
    d.line([(20, bot_y), (W - 20, bot_y)], fill=(200, 120, 0), width=1)
    d.line([(xs, top_y), (xs, bot_y)], fill=(0, 0, 0), width=2)
    d.text((xs + 6, (top_y + bot_y) / 2 - 10), f'{gap:.2f} mm', font=FB, fill=(200, 0, 0))
    d.text((10, 8), title, font=FB, fill=(0, 0, 0))
    d.text((10, H - 48), 'red: rafter top flange · orange: purlin underside', font=F, fill=(90, 90, 90))
    d.text((10, H - 26), 'gap measured perpendicular to the rafter at the joint', font=F, fill=(90, 90, 90))
    return im

def app(case):
    j = json.load(open(os.path.join(REPO, 'build', 'c04-gap', case + '.json')))
    return [(b['name'], b['kind'], np.asarray(b['vp'], float).reshape(-1, 3), np.asarray(b['tv'], int).reshape(-1, 3)) for b in j['bodies']], j['gap']

def r11():
    d = json.load(gzip.open(os.path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R11.ss')))['doc']
    out = []
    for b in d['bodies']:
        st = b.get('st') or {}; s = b.get('shape') or {}
        if 'vp' not in s or not s.get('tv'): continue
        V = np.asarray(s['vp'], float).reshape(-1, 3)
        q = V - J
        if not ((np.abs(q @ A) < 400) & (np.abs(q[:, 1]) < 3000) & (np.abs(q @ N) < 400)).any(): continue
        name = st.get('name'); kind = st.get('kind')
        if st.get('amark') == 'R3022' and name in ('Top flange', 'Web', 'Bottom flange'): name = 'RAFTER'
        elif name == 'Purlin': name = 'PURLIN'
        elif name == 'Cleat Angle': name = 'CLEAT'
        elif name == 'Stiff.': name = 'CLEAT STIFFENER'
        elif kind == 'bolts': pass
        else: continue
        out.append((name, kind, V, np.asarray(s['tv'], int).reshape(-1, 3)))
    vs = lambda r: [b for b in out if role(b[0], b[1]) == r]
    top = max((b[2] - J) @ N for b in vs('rafter') for _ in [0]).max() if False else max(float(((b[2][(np.abs((b[2] - J) @ A) < 300) & (np.abs(b[2][:, 1] - J[1]) < 400)] - J) @ N).max()) for b in vs('rafter') if ((np.abs((b[2] - J) @ A) < 300) & (np.abs(b[2][:, 1] - J[1]) < 400)).any())
    bot = min(float(((b[2][(np.abs((b[2] - J) @ A) < 300) & (np.abs(b[2][:, 1] - J[1]) < 400)] - J) @ N).min()) for b in vs('purlin') if ((np.abs((b[2] - J) @ A) < 300) & (np.abs(b[2][:, 1] - J[1]) < 400)).any())
    # shift so the rafter top is at the panel's red line (as the app panels: J is the purlin axis point)
    return out, bot - top, top

def main():
    o, g11, top11 = r11()
    J0 = J.copy()
    panels = []
    for case, title in (('recording+C04', 'App, recording case (before): rafter depth Behind'), ('R11+C04', 'App, R11 inputs (fixed case)')):
        bodies, g = app(case)
        top = max(float(((V[(np.abs((V - J0) @ A) < 300) & (np.abs(V[:, 1] - J0[1]) < 400)] - J0) @ N).max()) for n, k, V, T in bodies if role(n, k) == 'rafter' and ((np.abs((V - J0) @ A) < 300) & (np.abs(V[:, 1] - J0[1]) < 400)).any())
        globals()['J'] = J0 + N * top; panels.append(panel(bodies, g, title))
    globals()['J'] = J0 + N * top11; panels.append(panel(o, g11, 'R11 (Tekla meshes)'))
    W = sum(p.width for p in panels) + 20 * (len(panels) + 1); H = panels[0].height + 70
    im = Image.new('RGB', (W, H), (255, 255, 255)); d = ImageDraw.Draw(im)
    d.text((20, 12), 'C04 joint, cleat 3185 / rafter R3022: orthographic end view along the purlins', font=FB, fill=(0, 0, 0))
    x = 20
    for p in panels: im.paste(p, (x, 50)); d.rectangle((x, 50, x + p.width - 1, 50 + p.height - 1), outline=(180, 180, 180)); x += p.width + 20
    out = os.path.join(REPO, 'docs', 'steel', 'parity', 'C04_gap.png'); im.save(out); print(out, im.size, 'gaps', [round(g11, 2)])

if __name__ == '__main__': main()
