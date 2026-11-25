#!/usr/bin/env python3
"""Orthographic end view of the C04 workflow case (tools/golden/c04_workflow.js / C04WorkflowTest): rafter A (default RAFTER set,
depth Behind, 12.8°), the purlin with the cleat, looking along the purlins; the purlin underside vs the rafter top, perpendicular to
the rafter (lowest and other bottom corner), written on each panel.
    python3 tools/golden/c04_workflow_view.py build/c04-workflow-v69.json build/c04-workflow-web.json build/c04-workflow-app.json
      -> docs/steel/parity/C04_workflow.png"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16); FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 18)
COL = {'RAFTER': (60, 150, 80), 'PURLIN': (60, 110, 200), 'CLEAT': (190, 190, 200), 'CLEAT STIFFENER': (150, 150, 165), 'BOLTS': (40, 40, 40)}
TITLES = {'v69': 'Before (web v69): purlins upright', 'web': 'Web v70', 'app': 'App (0.10.3-c04)'}

def panel(path, W=560, H=560, scale=1.3):
    d = json.load(open(path)); B = {str(b['id']): b for b in d['bodies']}; A = B[str(d['A'])]
    p1, p2 = np.array(A['st']['p1'], float), np.array(A['st']['p2'], float); a = (p2 - p1) / np.linalg.norm(p2 - p1)
    n = np.array([0, 0, 1.0]) - a * a[2]; n /= np.linalg.norm(n)
    tcl = next(c for c in d['conns'] if c['type'] == 'Tcl'); pid = str(tcl['parts'][1]); P = B[pid]
    q1 = np.array(P['st']['p1'], float); ax = np.array(P['st']['p2'], float) - q1; ax /= np.linalg.norm(ax)
    VA = np.asarray(A['m']['v'], float); top = float((VA @ n).max())
    VP = np.asarray(P['m']['v'], float); s = VP @ n; lo = float(s.min()); acr = VP @ a; far = VP[np.abs(acr - acr[np.argmin(s)]) > 20]; hi = float((far @ n).min())
    # the joint point: on rafter A's top, under the purlin's centre
    J = q1 + ax * float((p1 - q1) @ ax); J = J + n * (top - float(J @ n))
    im = Image.new('RGB', (W, H), (255, 255, 255)); dr = ImageDraw.Draw(im)
    cx, cy = W / 2, H * 0.6
    to2 = lambda V: np.c_[cx + ((V - J) @ a) * scale, cy - ((V - J) @ n) * scale]
    conn_of = {str(b['id']): str(b['st'].get('conn')) for b in d['bodies'] if b['st'].get('conn') is not None}
    order = ['RAFTER', 'PURLIN', 'CLEAT', 'CLEAT STIFFENER', 'BOLTS']
    for r in order:
        for b in d['bodies']:
            nm = (b.get('name') or '').upper(); bid = str(b['id'])
            role = 'RAFTER' if bid == str(d['A']) else 'PURLIN' if bid == pid else nm if nm in COL else None
            if role != r: continue
            if role in ('CLEAT', 'CLEAT STIFFENER', 'BOLTS') and conn_of.get(bid) not in (str(tcl['id']),): continue
            V = np.asarray(b['m']['v'], float); T = np.asarray(b['m']['t'], int).reshape(-1, 3); Q = to2(V)
            for t in T: dr.polygon([tuple(Q[i]) for i in t], fill=COL[r])
    ty = cy; by = cy - (lo - top) * scale
    dr.line([(10, ty), (W - 10, ty)], fill=(200, 0, 0)); dr.line([(10, by), (W - 10, by)], fill=(220, 130, 0))
    xs = cx + 170; dr.line([(xs, ty - 40), (xs, ty + 40)], fill=(0, 0, 0))
    dr.text((10, 10), TITLES.get(os.path.basename(path).split('-')[-1].split('.')[0], path), font=FB, fill=(0, 0, 0))
    dr.text((10, 36), f"purlin {P['st']['prof']}, rAng {P['st'].get('rAng')}", font=F, fill=(60, 60, 60))
    dr.text((10, H - 74), f'purlin underside - rafter top (perpendicular):', font=F, fill=(0, 0, 0))
    dr.text((10, H - 50), f'  lowest corner {lo - top:+.2f} mm, other corner {hi - top:+.2f} mm', font=FB, fill=(200, 0, 0))
    dr.text((10, H - 24), 'red: rafter top · orange: purlin underside (lowest)', font=F, fill=(90, 90, 90))
    return im

def main():
    ps = [panel(p) for p in sys.argv[1:]]
    W = sum(p.width for p in ps) + 20 * (len(ps) + 1); im = Image.new('RGB', (W, ps[0].height + 60), (255, 255, 255)); d = ImageDraw.Draw(im)
    d.text((20, 14), 'C04 workflow: RAFTER set (IPE360, depth Behind, 12.8°) + Generation of purlins (CC200-2-20-60) + Tcl, orthographic end view along the purlins', font=FB, fill=(0, 0, 0))
    x = 20
    for p in ps: im.paste(p, (x, 48)); d.rectangle((x, 48, x + p.width - 1, 48 + p.height - 1), outline=(170, 170, 170)); x += p.width + 20
    out = os.path.join(REPO, 'docs', 'steel', 'parity', 'C04_workflow.png'); im.save(out); print(out, im.size)

if __name__ == '__main__': main()
