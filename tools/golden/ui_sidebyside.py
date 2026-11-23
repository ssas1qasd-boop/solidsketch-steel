#!/usr/bin/env python3
"""UI golden: v69 (left) and the app (right) side by side, the same state, in tests/steel/ui/compare/sbs_<name>.png.
On both halves the v69 numbers are drawn: grid labels / handles / cones as rings (green labels, yellow handles, red cones)
and v69's button / sheet boxes as cyan outlines, so a difference is visible at a glance on the app half.
  python3 tools/golden/ui_sidebyside.py   (after node tools/golden/ui_golden.js and the app's UiGoldenTest)"""
import json, os
from PIL import Image, ImageDraw
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
G = os.path.join(R, 'tests/steel/ui/golden'); C = os.path.join(R, 'tests/steel/ui/compare')
gold = json.load(open(os.path.join(G, 'ui_golden.json')))
PAIRS = [(s, 'v69/%s.png' % s, 'app_%s.png' % s, s) for s in gold['states']] + [
    ('panel', 'v69/cam1_overview.png', 'app_panel.png', None), ('grid_sheet', 'v69/sheet_grid.png', 'app_grid_sheet.png', None),
    ('catalog', 'v69/sheet_catalog.png', 'app_catalog.png', None), ('dialog_t144', 'v69/sheet_dialog_t144.png', 'app_dialog_t144.png', None)]

def marks(d, name, st):
    if st:
        s = gold['states'][st]
        for key, col in (('labels', (60, 220, 90)), ('handles', (255, 212, 0)), ('cones', (255, 60, 60))):
            for x, y, front in s[key]:
                if front: d.ellipse([x - 14, y - 14, x + 14, y + 14], outline=col, width=4)
    # camera shots: only the points (v69's panel boxes are taken with nothing selected, the shots have the beam selected)
    groups = [] if st else [gold['panel']] if name == 'panel' else list(gold['boxes'].get(name, {}).values())
    for grp in groups:
        for b in grp: d.rectangle([b['x'], b['y'], b['x'] + b['w'], b['y'] + b['h']], outline=(0, 230, 255), width=3)

for name, v, a, st in PAIRS:
    pv, pa = os.path.join(G, v), os.path.join(C, a)
    if not (os.path.exists(pv) and os.path.exists(pa)): print('skip', name); continue
    iv, ia = Image.open(pv).convert('RGB'), Image.open(pa).convert('RGB')
    h = max(iv.height, ia.height); out = Image.new('RGB', (iv.width + ia.width + 24, h + 70), (40, 40, 40))
    out.paste(iv, (0, 70)); out.paste(ia, (iv.width + 24, 70)); d = ImageDraw.Draw(out)
    d.text((20, 20), 'web v69: ' + name, fill=(255, 255, 255)); d.text((iv.width + 44, 20), 'app: ' + name + '   (rings / boxes: v69 numbers)', fill=(255, 255, 255))
    for ox, im in ((0, iv), (iv.width + 24, ia)):   # each half gets its own layer, clipped to that half
        sub = Image.new('RGBA', im.size, (0, 0, 0, 0)); marks(ImageDraw.Draw(sub), name, st)
        sh = Image.new('RGBA', out.size, (0, 0, 0, 0)); sh.paste(sub, (ox, 70)); out = Image.alpha_composite(out.convert('RGBA'), sh).convert('RGB')
    out = out.resize((out.width // 2, out.height // 2))
    out.save(os.path.join(C, 'sbs_%s.png' % name)); print('wrote', 'sbs_%s.png' % name)
