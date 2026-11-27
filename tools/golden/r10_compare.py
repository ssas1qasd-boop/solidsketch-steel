#!/usr/bin/env python3
"""S10 acceptance, v69 side, step 3: compare the v69 rebuild of R10 with R10 itself.

    python3 tools/golden/r10_inputs.py && node tools/golden/r10_rebuild.js && python3 tools/golden/r10_compare.py

Inputs (all in tests/steel/golden/r10/ except the model):
  tests/steel/models/Frame_28x53m_R10.ss   R10's own part fields (st) and bolt groups
  rebuild_inputs.json                      what was placed / connected (tools/golden/r10_inputs.py)
  rebuild_v69.json                         the rebuild as v69 made it (tools/golden/r10_rebuild.js)
  r10_parts_v69.json                       R10 opened in v69: each body's solid (volume, box) and v66 Tekla weight
Outputs:
  tests/steel/golden/r10/compare_v69.json  the raw comparison (every R10 part with its match and differences)
  docs/steel/R10-REPORT.md                 the report (generated; edit this script, not the file)

Matching (a part counts as MATCHED only when it really matches within the tolerances):
  members      R10 members placed as given and the built-up equivalents: the rebuilt member with that input (member
               index). Compared: profile, p1 / p2 (0.1 mm), rot / rAng, plane / depth, pOff / dOff / dx1 / dx2, class,
               name (the built-up equivalents: against the derived input; their solid is compared with the three R10
               plates: box and volume). A member is MATCHED when every field is equal (points within 0.1 mm) AND its
               solid box is within 0.1 mm of R10's (for built-ups: of the three plates together).
  detail parts R10 plates that are not built-up webs / flanges, and the R10 members that v69 components make (cleat
               angles, anchor rods, nuts, washers, tread brackets): the rebuild part (kind conn, or a bolt group for
               anchor rods) of the same component output with the closest box (greedy, closest first, each used once).
               MATCHED: box within 0.1 mm on all six numbers and volume within 2 % (hole counts may differ);
               NEAR: box within 5 mm; ROUGH: box within 50 mm (same place, other size or position); else MISSING.
  bolts        bolt groups: same diameter and every bolt position within 0.1 mm (as a set) = MATCHED; per size
               (M d, grade, L) and per diameter the counts on both sides.
  totals       bodies per kind, parts per type (name x profile), bolts, weight: sum of v69 partWeight (v66 Tekla
               weights, bolts excluded) over the non-bolt bodies on both sides (R10 opened in v69: 63,722 kg).
Deterministic: sorted keys and lists, rounding to 0.001 mm / 0.01 kg.
"""
import gzip
import json
import math
import os
import re
from collections import defaultdict, OrderedDict, Counter

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MODEL = os.environ.get('SS_MODEL', 'R13')   # R11 = the reference (docs/steel/PARITY.md); SS_MODEL=R10 for the old one
G = os.path.join(REPO, 'tests', 'steel', 'golden', MODEL.lower())
R10 = os.path.join(REPO, 'tests', 'steel', 'models', f'Frame_28x53m_{MODEL}.ss')
REPORT = os.path.join(REPO, 'docs', 'steel', f'{MODEL}-REPORT.md')
TOL = 0.1          # mm, positions and boxes
NEAR, ROUGH = 5.0, 50.0
VOL_TOL = 0.02
ANG_TOL = 0.01     # degrees (the dump rounds to 0.001)

def load(p): return json.load(open(p))
def dist(a, b): return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))
def boxdiff(a, b): return max(abs(x - y) for x, y in zip(a, b))
def r2(v): return round(v + 0.0, 2)
def r3(v): return round(v + 0.0, 3)
def pre(m): return re.match(r'^[A-Z]*', m or '').group(0) or (m or '')
def thk(prof):
    m = re.match(r'^(?:PL|FLT)(\d+(?:\.\d+)?)', prof or '')
    return float(m.group(1)) if m else None


# R10 detail part groups: (amark prefix, name, tprof) -> (checklist row, what v69 makes for it, reason if it is not reproduced, option needed)
ROW_TITLES = OrderedDict([
    ('1', 'Base plate 1042: columns (frame BI220 / BI320, gable, mezzanine)'), ('2', 'End plate 144: eave (column -> rafter)'),
    ('3', 'Apex haunch 106 (left rafter -> right rafter)'), ('4', 'Purlin cleat (rafter -> purlin 1 -> purlin 2)'),
    ('5', 'Tube gusset 20: tongue (main -> brace)'), ('6', 'Node gusset (several braces on one gusset)'),
    ('7', 'Tube crossing (continuous -> half 1 -> half 2)'), ('8', 'Sag rod (purlin -> rod)'),
    ('9', '144: monitor post on rafter'), ('10', '144: monitor rafter to post'), ('11', 'Joining plates 14: monitor apex'),
    ('12', 'Shear plate 146: column -> beam'), ('13', '144: mezzanine girder to column'), ('14', '146: joist to girder'),
    ('15', 'Cap 39: mezzanine column -> girder'), ('16', 'Base plate 1004: stair landing post'),
    ('17', '146 hanger: gable column top'), ('18', 'Stairs: grating treads'), ('19', 'Railing welds'),
    ('20', 'Column with stiffeners 186: mezzanine column web -> beam'),
])

def classify(st, amark):
    """(row, label) of an R10 detail part"""
    n, tp, g = st.get('name', ''), st.get('tprof') or st.get('prof'), pre(amark)
    if n in ('Base Plate',) and g == 'STR': return '16', 'stair post base plate'
    if n in ('Base Plate', 'GROUT', 'CASTPLATE', 'Anchor Bolt', 'NUT', 'WASHER'):
        if n == 'GROUT' and tp == 'PL30*230' or n == 'Anchor Bolt' and st.get('prof') == 'ROD16': return '16', n
        return '1', n
    if n == 'PLATE' and tp == 'FLT20*150': return '2', 'eave end plate'
    if n == 'End Plate' and tp == 'FLT20*400': return '3', 'apex end plate'
    if n == 'Web' and tp in ('FLT8*130',) or n == 'Bottom flange' and g == 'R': return '3', 'haunch (web / flange)'
    if n == 'Web' and tp == 'FLT8*80': return '2', 'eave haunch web'
    if n == 'Stiff.' and tp == 'FLT10*60': return '3', 'apex stiffener'
    if n in ('Cleat Angle',) or n == 'Stiff.' and tp == 'PL6*44': return '4', n
    if n == 'Connection Plate': return '5', 'tongue plate'
    if n == 'Gusset Plate' and tp in ('FLT5*100',): return '5', 'tongue end cap'
    if n == 'Gusset Plate' and g in ('HZ', 'VL') and re.match(r'^PL12\*3', tp or ''): return '7', 'crossing gusset'
    if n == 'Gusset Plate' and re.match(r'^PL5\*', tp or ''): return '7', 'crossing cap'
    if n == 'Gusset Plate' and tp == 'FLT12*140': return '6', 'single-brace gusset (fin)'
    if n == 'Gusset Plate': return '6', 'node gusset'
    if n.startswith('Tie Rod'): return '8', n
    if n == 'End plate Post' or n == 'HAUNCH_PLATE' or (n == 'Stiff.' and g == 'PC'): return '9', n
    if n == 'PLATE' and tp == 'FLT12*120': return '10', 'monitor rafter end plate'
    if n == 'PLATE' and tp == 'PL12*155': return '11', 'monitor apex joining plate'
    if n == 'Shear Plate' and tp == 'FLT10*130' and g in ('EB',): return '14', 'joist shear plate'
    if n == 'Shear Plate': return '12', 'shear plate'
    if n in ('Top FS', 'Bot FS', 'Top NS', 'Bot Ns'): return '20', n
    if n == 'PLATE' and tp == 'PL16*120' or n in ('Top Stiff.', 'Bot Stiff.'): return '13', n
    if n == 'End Plate ': return '15', 'cap plate'
    if n in ('PLATE', 'D') and tp == 'FLT10*120': return '17', 'gable hanger'
    if n in ('THREAD', 'BRACKET', 'Stair Plate', 'STRINGER') or g in ('STR', 'STB') and n in ('ENDPLATE', 'PLATE'): return '18', n
    if n in ('Hand Rail', 'Stanchion', 'RAILING'): return '19', n
    if n == 'Cap Plate': return '-', 'column top cap plate'
    return '-', n


# Why a group is not reproduced and which application / component option would be needed (first matching pattern wins;
# the key is "<row> | <label> | <assembly prefix> <name> <profile>"). Distances quoted are the "same-size part at" medians
# of this run (compare_v69.json, groups[].sameSize).
REASONS = [
    (r'^builtup', 'Tekla built-up section (three plates) placed as one v69 BI member: one solid with square ends against three Tekla plates with their own end cuts (sloped column tops, plumb rafter ends, notches); the inputs equal the derived equivalent',
     'built-up members as plates (Web / Top flange / Bottom flange as parts of one assembly) and end fitting to the connected part'),
    (r'given member \| (HZ|VL|ST) ', 'placed with R10\'s inputs; the solid differs where v69\'s Ttg / Ttgx cut the tube (tongue slots, cut-backs) instead of R10\'s TKCUT cuts', 'tongue slot / cut-back as Tekla 20'),
    (r'given member \| SR STRINGER', 'placed with R10\'s inputs; v69 stair treads cut / fit the stringer ends differently from R10\'s TKCUT cuts', ''),
    (r'^- \| (Top|Bottom) flange \| EB', 'three mezzanine girders exist in R10 as flanges only (no web in the Tekla import), so no member was placed', 'none (R10 data)'),
    (r'^- \| (Web|Bottom flange) \| STB', 'stair beam STB9002 whose top flange is a notched PLATE: not recognised as a built-up section, not placed', 'none (built-up detection)'),
    (r'column top cap plate', 'loose cap plates FLT8*150 on the column tops under the rafters (marks A1 to A4, and in C2 assemblies): no v69 component in the checklist makes them', 'a column cap plate option in End plate 144 (or Tekla 1002-style cap)'),
    (r'^(1|16) \|', 'C01 / C16 (v70: Base plate with = Anchor rods: hooked ROD20 / ROD16 parts (a 2 d, b), washer and nut PD40*10, grout with slopes under the plate, cast plate under the grout): every base part matches', 'done (v70 C01 / C16)'),
    (r'^2 \| eave haunch|^2 \| haunch', 'C02h Eaves haunch (102) haunch plates: length 2,000 from the column axis, depth 82.91 at the end plate (exploded in Tekla)', 'done (v70 C02h)'),
    (r'^3 \|', 'C03 (v70: Apex haunch as Tekla 106: length horizontal from the apex, depth plumb at the end plate, flange ends plumb; end plate stiffeners FLT10*60 above the rafter): every apex part matches', 'done (v70 C03)'),
    (r'^4 \|', 'Cleat location Back (v69, and models saved before v70) puts the cleat and its stiffener behind the purlin web: a same-size cleat is about 119 mm away, mirrored about the purlin', 'Cleat location Front (v70, the Tekla default)'),
    (r'^5 \| tongue plate|^5 \| tongue end cap', 'C05 (v70: tube end at the brace end, plate on the axis / against the node gusset, Tekla slot, end plates, bolts): every Ttg end matches; the rest are the crossing halves (their plates and end plates are row 7, C07) and the plates at the 6 nodes whose gusset is its own assembly (C06)', 'C07 / C06'),
    (r'^6 \| node gusset \| (11|12|13) |^6 \| node gusset \| (C|ST) .*(FLT12\*400|PL12\*349|PL12\*222|FLT5\*45)', 'C06: the gable / end-frame nodes (gussets that are their own assembly, marks 11 / 12 / 13): the braces are not in the gusset\'s plane (the vertical brace is skewed 4 to 60 mm out of it); Tekla 20\'s outline and the gusset centred on the braces put them within 0.2 to 4.3 mm, not 0.1', 'open (C06): the plate position of a brace skewed out of the gusset plane'),
    (r'^6 \|', 'C06 (v70: Tekla 20\'s outline: edges square to each brace 20 before its end plate, the convex hull with their projections onto the main part\'s face, square and along the brace): every node gusset, single-brace gusset and fin matches except the gable nodes above', 'done (v70 C06)'),
    (r'^7 \| crossing gusset', 'C07 (v70: Tekla 20 crossing: the halves keep their own ends, tongue plates on their axes, the gusset through the continuous brace with edges square to the halves 20 before their end plates, projected onto the continuous brace\'s far face): every crossing gusset matches', 'done (v70 C07)'),
    (r'^7 \| crossing cap', 'PL5 plates that are the 100 x 44 end plates at the halves\' upper ends (wall crossings VL6011 to VL6014 on the frame lines, at the column / rafter), trimmed by Tekla to 32 to 93 mm; ours have the same trimmed size (within 0.2 mm) but Tekla turned each about 0.3 degrees about the brace axis (X axis (-1, 0.003, -0.004) in R12), 0.1 to 1.7 mm at the far corner', 'open (C07): a 0.3 degree turn of Tekla\'s end plate (IFC data)'),
    (r'^9 \| HAUNCH_PLATE', 'Tekla 144 haunch plates under the monitor post end plate: v70 144 Haunch tab (t 8, b 60, h 60, chamfers 15)', 'done (v70 C09)'),
    (r'^10 \| .* 14 PLATE', 'a monitor rafter end plate that is its own assembly (mark 14) and not bolted to anything: no component added', 'none (R10 data)'),
    (r'^12 \| shear plate \| C ', 'six column shear plates (at EB4006 / EB4008, beams with flanges only in R10) are not bolted to any beam (no secondary part to pick): no component added; the other 4 (146) match, and the 18 at 186 joints are row 20', 'none (R10 data)'),
    (r'^12 \| shear plate \| STR', 'two stair-post shear plates are not bolted to anything in R10 (or bolted to a part with no member): no component added', 'none (R10 data)'),
    (r'^20 \| (Top|Bot) (FS|NS|Ns) \| C ', 'Column with stiffeners (186) at the beam on the column web (v70 C20): 12 of 15 columns match; at C2019 and the two C2023 joints the beam on the web is EB4006 / EB4008, which has flanges only and no web in R12 (no member placed, so no secondary to pick)', 'none (R12 data)'),
    (r'^20 \| shear tab', 'Column with stiffeners (186) shear tab (v70 C20)', 'done (v70 C20)'),
    (r'^13 \| (Top|Bot) Stiff', 'girder-side Stiff. plates FLT10*50: End plate 144 Haunch tab (C13: 12 / 12 at the 6 girder joints); the 4 at the MBC2001 column end plates are row 15', 'done (v70 C13) / row 15'),
    (r'^14 \|', 'same plate size and plan position; v69 centres the tab on the joist web depth, R10\'s tab is 7.8 mm higher', 'vertical position of the tab (Tekla 146 plate position)'),
    (r'^17 \|', 'C17 (v70: Shear plate 146 as a hanger: Main part face = the secondary web\'s uphill face, the tab cut on the sloped underside, R12\'s 190 / 49.056 / Middle 35, Tekla bolt length): every gable hanger matches', 'done (v70 C17)'),
    (r'^18 \| Stair Plate \| EB', 'the counter plates FLT12*150 on mezzanine girder EB4002 (the top stair plates bolt to them): a mezzanine girder part, left until R13 (owner 2026-11-27); Stair end plate option Counter plate on the main part makes them', 'R13'),
    (r'^18 \|', 'C18 (v70: Stair treads = Tekla: U-pan grating treads on L60*6 brackets bolted to the webs, the stringers mitred to their pieces; Stair end plates): every stair part matches', 'done (v70 C18)'),
    (r'^-', 'no v69 component in the checklist makes this part', ''),
]
def reason_for(key):
    for pat, why, opt in REASONS:
        if re.search(pat, key): return why, opt
    return '', ''


def main():
    inp = load(os.path.join(G, 'rebuild_inputs.json'))
    reb = load(os.path.join(G, 'rebuild_v69.json'))
    r10v = load(os.path.join(G, 'r10_parts_v69.json'))
    doc = json.load(gzip.open(R10))['doc']
    doc['bodies'] = [b for b in doc['bodies'] if b.get('st')]   # R12: Tekla's concrete bodies have no st (not steel)
    rb = {b['id']: b for b in doc['bodies']}
    rv = {b['id']: b for b in r10v['bodies']}
    asm_of = {b['id']: b['st'].get('asm', b['id']) for b in doc['bodies'] if b['st']['kind'] != 'bolts'}
    amark = lambda i: rb[i]['st'].get('amark', '')
    nb = {b['id']: b for b in reb['bodies']}
    by_mi = {b['mi']: b for b in reb['bodies'] if 'mi' in b}

    out = OrderedDict()
    # ------------------------------------------------ (a) members
    FIELDS = ['prof', 'rot', 'rAng', 'plane', 'depth', 'pOff', 'dOff', 'dx1', 'dx2', 'cls', 'name']
    mem_rows = []
    in_builtup = set()
    for m in inp['members']:
        b = by_mi.get(m['i'])
        row = OrderedDict(i=m['i'], kind=m['kind'], group=m['group'], amark=m['amark'], r10=m['r10'])
        if m['kind'] == 'builtup': in_builtup.update(m['r10'])
        if b is None: row['status'] = 'NOT BUILT'; mem_rows.append(row); continue
        ref = m['st'] if m['kind'] == 'builtup' else rb[m['r10'][0]]['st']
        s = b['st']; diffs = OrderedDict()
        for f in FIELDS:
            a, c = ref.get(f), s.get(f)
            if isinstance(a, (int, float)) and isinstance(c, (int, float)):
                if abs(a - c) > (ANG_TOL if f == 'rAng' else TOL if f in ('pOff', 'dOff', 'dx1', 'dx2') else 1e-9): diffs[f] = [a, c]
            elif (a if a is not None else '') != (c if c is not None else ''): diffs[f] = [a, c]
        for f in ('p1', 'p2'):
            dd = dist(ref[f], s[f])
            if dd > TOL: diffs[f] = [ref[f], s[f], r3(dd)]
        # solids: R10 (one member, or the three plates of a built-up) vs the rebuilt member
        rbox = None; rvol = 0.0; rkg = 0.0
        for rid in m['r10']:
            v = rv[rid]; bx = v['box']; rvol += v['volume']; rkg += (v['w'] or {}).get('weight', 0) or 0
            rbox = bx if rbox is None else [min(rbox[k], bx[k]) for k in range(3)] + [max(rbox[k + 3], bx[k + 3]) for k in range(3)]
        bd = boxdiff(rbox, b['box'])
        row.update(boxDiff=r3(bd), volR10=r3(rvol), volV69=b['volume'], volRatio=r3(b['volume'] / rvol) if rvol else None,
                   kgR10=r2(rkg), kgV69=r2((b['w'] or {}).get('weight', 0)), diffs=diffs)
        row['status'] = 'MATCHED' if not diffs and bd <= TOL else ('INPUT EQUAL, SOLID DIFFERS' if not diffs else 'DIFFERENT')
        mem_rows.append(row)
    out['members'] = mem_rows

    # ------------------------------------------------ detail parts: R10 plates (not built-up) + component-made members
    placed = {rid for m in inp['members'] for rid in m['r10']}
    details = [b for b in doc['bodies'] if b['st']['kind'] != 'bolts' and b['id'] not in placed]
    cands = [b for b in reb['bodies'] if b['st']['kind'] == 'conn']
    bolt_cands = [b for b in reb['bodies'] if b['st']['kind'] == 'bolts']
    # candidate pairs within ROUGH, by box distance; greedy closest first
    grid = defaultdict(list); C = 500.0
    cen = lambda bx: [(bx[k] + bx[k + 3]) / 2 for k in range(3)]
    for b in cands:
        c = cen(b['box']); grid[tuple(int(math.floor(x / C)) for x in c)].append(b)
    pairs = []
    anchors = []
    for d in details:
        if d['st']['name'] == 'Anchor Bolt': anchors.append(d)   # v70 C01: also matched by box (rods made as parts); the bolt-group check below is the fallback
        bx = rv[d['id']]['box']; c = cen(bx); k0 = tuple(int(math.floor(x / C)) for x in c)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for b in grid.get((k0[0] + dx, k0[1] + dy, k0[2] + dz), []):
                        bd = boxdiff(bx, b['box'])
                        if bd <= ROUGH: pairs.append((r3(bd), d['id'], b['id']))
    pairs.sort()
    used_r, used_v, match = set(), set(), {}
    for bd, rid, vid in pairs:
        if rid in used_r or vid in used_v: continue
        used_r.add(rid); used_v.add(vid); match[rid] = (vid, bd)
    # anchor rods vs anchor bolt groups: each rod axis on one bolt point (plan within 0.1 mm), length = bolt L
    bolt_pts = []
    for b in bolt_cands:
        if not re.search(r'ANCHOR', b['st'].get('name', '')): continue
        for p in b['st']['pts']: bolt_pts.append((b['id'], p, b['st']['L'], b['st']['d']))
    ext = lambda bx: sorted(bx[k + 3] - bx[k] for k in range(3))
    sgrid = defaultdict(list); C3 = 1000.0
    for b in cands: sgrid[tuple(int(math.floor(x / C3)) for x in cen(b['box']))].append((cen(b['box']), ext(b['box']), b['id']))
    def same_size(bx):
        """the nearest v69 component part of the same size (box extents within 3 mm): centre distance in mm, or None"""
        c, e = cen(bx), ext(bx); k0 = tuple(int(math.floor(x / C3)) for x in c); best = None
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for cc, ee, vid in sgrid.get((k0[0] + dx, k0[1] + dy, k0[2] + dz), []):
                        if max(abs(x - y) for x, y in zip(e, ee)) > 3: continue
                        dd = dist(c, cc)
                        if best is None or dd < best: best = dd
        return r3(best) if best is not None else None
    # R10's haunch bottom flanges: eave (next to a FLT8*80 web) or apex (next to a FLT8*130 web)
    hwebs = [(b['id'], b['st']['tprof'], cen(rv[b['id']]['box'])) for b in doc['bodies'] if b['st']['kind'] == 'plate' and b['st']['name'] == 'Web' and b['st']['tprof'] in ('FLT8*80', 'FLT8*130')]
    def haunch_row(i):
        c = cen(rv[i]['box']); w = min(hwebs, key=lambda x: dist(x[2], c))
        return ('2', 'eave haunch flange') if w[1] == 'FLT8*80' else ('3', 'haunch (web / flange)')
    c20_ids = {i for st_ in inp['steps'] if st_['op'] == 'connectMany' for e in st_['list'] if e[3] == '20' for i in (e[4] if len(e) > 4 else [])}
    det_rows = []
    for d in sorted(details, key=lambda x: x['id']):
        s = d['st']; row_, label = classify(s, s.get('amark', ''))
        if s['name'] == 'Bottom flange' and pre(s.get('amark', '')) == 'R': row_, label = haunch_row(d['id'])
        if d['id'] in c20_ids and s['name'] == 'Shear Plate': row_, label = '20', 'shear tab'
        r = OrderedDict(id=d['id'], kind=s['kind'], name=s['name'], tprof=s.get('tprof') or s.get('prof'), amark=s.get('amark', ''),
                        row=row_, label=label, kg=r2((rv[d['id']]['w'] or {}).get('weight', 0) or 0))
        if s['name'] == 'Anchor Bolt' and d['id'] not in match:   # v70 C01: rods made as parts are matched by box like any part
            L = dist(s['p1'], s['p2']); best = None
            for vid, p, bl, bd_ in bolt_pts:
                dd = math.hypot(p[0] - s['p1'][0], p[1] - s['p1'][1])
                if best is None or dd < best[0]: best = (dd, vid, bl, bd_)
            if best:
                r.update(v69=best[1], v69name='ANCHOR BOLTS (bolt group)', planDiff=r3(best[0]), lenR10=r3(L), lenV69=best[2], dV69=best[3])
                r['status'] = 'AS BOLT' if best[0] <= TOL else 'AS BOLT, MOVED'
            else: r['status'] = 'MISSING'
        elif d['id'] in match:
            vid, bd = match[d['id']]; vb = nb[vid]
            vr = rv[d['id']]['volume']; vv = vb['volume']
            r.update(v69=vid, v69name=vb['st']['name'], v69prof=vb['st'].get('prof'), boxDiff=bd, volRatio=r3(vv / vr) if vr else None,
                     kgV69=r2((vb['w'] or {}).get('weight', 0) or 0))
            r['status'] = 'MATCHED' if bd <= TOL and vr and abs(vv / vr - 1) <= VOL_TOL else 'NEAR' if bd <= NEAR else 'ROUGH'
        else: r['status'] = 'MISSING'
        if r['status'] in ('NEAR', 'ROUGH', 'MISSING') and s['name'] != 'Anchor Bolt':
            ss = same_size(rv[d['id']]['box'])
            if ss is not None: r['sameSizeAt'] = ss
        det_rows.append(r)
    out['details'] = det_rows
    extra = [b for b in cands if b['id'] not in used_v]
    out['v69Extra'] = OrderedDict(sorted(Counter(f"{b['st']['name']} {b['st'].get('prof', '')}" for b in extra).items()))

    # ------------------------------------------------ bolts
    r10_bolts = [b for b in doc['bodies'] if b['st']['kind'] == 'bolts']
    def size(s): return f"M{int(s['d'])} {s.get('grade', '')} L{int(round(s['L']))}"
    bs = OrderedDict()
    for side, lst in (('r10', [b['st'] for b in r10_bolts]), ('v69', [b['st'] for b in bolt_cands])):
        sz, dia = Counter(), Counter()
        for s in lst: sz[size(s)] += int(s.get('n') or 0); dia[f"M{int(s['d'])}"] += int(s.get('n') or 0)
        bs[side] = OrderedDict(groups=len(lst), bolts=sum(sz.values()), byDiameter=OrderedDict(sorted(dia.items())), bySize=OrderedDict(sorted(sz.items())))
    # bolt match: same diameter, the bolt on the same line (axis parallel, the R10 point within 0.1 mm of the v69 bolt's
    # axis line, at most 100 mm apart along it: R10 and v69 put the group's points at different places along the bolt)
    C2 = 100.0
    vidx = defaultdict(list)
    for b in bolt_cands:
        for p in b['st']['pts']: vidx[(int(b['st']['d']), int(math.floor(p[0] / C2)), int(math.floor(p[1] / C2)), int(math.floor(p[2] / C2)))].append((b['id'], p, b['st']['ax'], b['st']['name'], b['st'].get('L'), b['st'].get('grade')))
    gm = Counter(); by_size_match = Counter()
    for b in r10_bolts:
        s = b['st']; ok_pts = 0; same_L = 0; ax = s['ax']; na = math.sqrt(sum(x * x for x in ax)) or 1; ax = [x / na for x in ax]
        for p in s['pts']:
            hit = None; k = (int(s['d']), int(math.floor(p[0] / C2)), int(math.floor(p[1] / C2)), int(math.floor(p[2] / C2)))
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        for vid, q, qa, qn, qL, qg in vidx.get((k[0], k[1] + dx, k[2] + dy, k[3] + dz), []):
                            nq = math.sqrt(sum(x * x for x in qa)) or 1
                            if abs(sum(ax[i] * qa[i] for i in range(3)) / nq) < 0.9999: continue
                            dd = [q[i] - p[i] for i in range(3)]; al = sum(dd[i] * ax[i] for i in range(3))
                            perp = math.sqrt(max(0.0, sum(x * x for x in dd) - al * al))
                            if perp <= TOL and abs(al) <= 100 and (hit is None or perp < hit[0]): hit = (perp, qL, qg)
            if hit:
                ok_pts += 1
                if hit[1] is not None and abs(hit[1] - s['L']) < 0.5 and str(hit[2]) == str(s.get('grade')): same_L += 1
        n = len(s['pts']); gm['groups'] += 1; gm['bolts'] += n
        if ok_pts == n: gm['groupsMatched'] += 1
        gm['boltsMatched'] += ok_pts; gm['boltsMatchedSameLengthGrade'] += same_L
        by_size_match[f"M{int(s['d'])}"] += ok_pts
    bs['matchedPerDiameter'] = OrderedDict(sorted(by_size_match.items()))
    bs['positions'] = OrderedDict(sorted(gm.items()))
    out['bolts'] = bs

    # ------------------------------------------------ totals
    def kinds(bodies, f): return OrderedDict(sorted(Counter(f(b) for b in bodies).items()))
    tot = OrderedDict()
    tot['bodies'] = OrderedDict(r10=len(doc['bodies']), v69=len(reb['bodies']))
    tot['kinds'] = OrderedDict(r10=kinds(doc['bodies'], lambda b: b['st']['kind']), v69=kinds(reb['bodies'], lambda b: b['st']['kind']))
    tot['connections'] = OrderedDict(r10=len(doc['stConns']), v69=len(reb['conns']), r10Types=kinds(doc['stConns'], lambda c: c['type']), v69Types=kinds(reb['conns'], lambda c: c['type']))
    tot['partsPerType'] = OrderedDict(
        r10=OrderedDict(sorted(Counter(f"{b['st']['name']} | {b['st'].get('tprof') or b['st'].get('prof')}" for b in doc['bodies'] if b['st']['kind'] != 'bolts').items())),
        v69=OrderedDict(sorted(Counter(f"{b['st']['name']} | {b['st'].get('prof')}" for b in reb['bodies'] if b['st']['kind'] != 'bolts').items())))
    kg_r10 = sum(((rv[i]['w'] or {}).get('weight', 0) or 0) for i in rv)
    kg_v69 = sum(((b['w'] or {}).get('weight', 0) or 0) for b in reb['bodies'])
    tot['weightKg'] = OrderedDict(r10=r2(kg_r10), v69=r2(kg_v69), diff=r2(kg_v69 - kg_r10), r10Weights=r10v['totals']['weights'], v69Weights=reb['totals']['weights'])
    # weight by category
    cat = OrderedDict()
    bu = [m for m in mem_rows if m['kind'] == 'builtup']; gv = [m for m in mem_rows if m['kind'] == 'given']
    cat['builtup members'] = [r2(sum(m.get('kgR10', 0) for m in bu)), r2(sum(m.get('kgV69', 0) for m in bu))]
    cat['given members'] = [r2(sum(m.get('kgR10', 0) for m in gv)), r2(sum(m.get('kgV69', 0) for m in gv))]
    cat['detail parts (R10) / component parts (v69)'] = [r2(sum(r['kg'] for r in det_rows)), r2(sum(((b['w'] or {}).get('weight', 0) or 0) for b in cands))]
    tot['weightByCategory'] = cat
    out['totals'] = tot

    # ------------------------------------------------ groups and checklist rows
    groups = defaultdict(lambda: Counter())
    gkg = defaultdict(lambda: [0.0, 0.0]); gss = defaultdict(list)
    for r in det_rows:
        key = f"{r['row']} | {r['label']} | {pre(r['amark'])} {r['name']} {r['tprof']}"
        groups[key][r['status']] += 1; groups[key]['total'] += 1
        gkg[key][0] += r['kg']; gkg[key][1] += r.get('kgV69', 0) or 0
        if 'sameSizeAt' in r: gss[key].append(r['sameSizeAt'])
    for m in mem_rows:
        key = f"builtup | {m['group']} {inp['members'][m['i']]['st']['prof']}" if m['kind'] == 'builtup' else f"{classify(rb[m['r10'][0]]['st'], m['amark'])[0]} | given member | {m['group']} {rb[m['r10'][0]]['st']['name']} {rb[m['r10'][0]]['st']['prof']}"
        groups[key][m['status']] += 1; groups[key]['total'] += 1
        gkg[key][0] += m.get('kgR10', 0); gkg[key][1] += m.get('kgV69', 0)
    grows = []
    for key in sorted(groups, key=lambda k: (len(k.split(' | ')[0]), k)):
        c = groups[key]; why, opt = reason_for(key) if (c['total'] - c['MATCHED']) else ('', '')
        ss = sorted(gss[key])
        grows.append(OrderedDict(group=key, counts=OrderedDict(sorted(c.items())), kgR10=r2(gkg[key][0]), kgV69=r2(gkg[key][1]),
                                 sameSize=OrderedDict(n=len(ss), median=r2(ss[len(ss) // 2])) if ss else None, reason=why, option=opt))
    out['groups'] = grows

    rows = OrderedDict()
    conn_by_row = Counter(c[3] for c in inp['steps'][2]['list'])
    cid_row = {}
    for i, c in enumerate(inp['steps'][2]['list']):
        cr = reb['connResults'][i]
        if cr.get('id') is not None: cid_row[cr['id']] = c[3]
    made = defaultdict(Counter)
    for b in reb['bodies']:
        cid = b['st'].get('conn')
        if cid is None: continue
        rk = cid_row.get(cid, '18' if any(c['id'] == cid and c['type'] in ('stairtreads', 'T144') for c in reb['conns']) else '?')
        made[rk]['bolts' if b['st']['kind'] == 'bolts' else 'parts'] += 1
        if b['st']['kind'] == 'bolts': made[rk]['boltCount'] += int(b['st'].get('n') or 0)
    out['v69MadePerRow'] = OrderedDict(sorted((k, OrderedDict(sorted(v.items()))) for k, v in made.items()))
    for rk, title in ROW_TITLES.items():
        rr = [r for r in det_rows if r['row'] == rk]
        st = Counter(r['status'] for r in rr)
        mm = [m for m in mem_rows if m['kind'] == 'given' and classify(rb[m['r10'][0]]['st'], m['amark'])[0] == rk]
        n = len(rr); ok = st['MATCHED']
        verdict = 'reproduced' if n and ok == n else 'partly' if ok or st['NEAR'] or st['AS BOLT'] else 'not' if n else ('placed as given' if mm else 'no R10 parts')
        rows[rk] = OrderedDict(title=title, components=conn_by_row.get(rk, 0), v69Parts=made[rk]['parts'], v69Bolts=made[rk]['boltCount'], r10Parts=n, status=OrderedDict(sorted(st.items())),
                               givenMembers=len(mm), givenMatched=sum(1 for m in mm if m['status'] == 'MATCHED'), verdict=verdict)
    out['checklist'] = rows
    out['notPlaced'] = inp['notPlaced']; out['unhandled'] = inp['unhandled']; out['leftover'] = inp['leftover']
    out['replay'] = OrderedDict(errors=reb['errors'], connectionsListed=len(inp['steps'][2]['list']), connectionsInV69=len(reb['conns']),
                                notOk=sum(1 for c in reb['conns'] if not c['ok']))

    with open(os.path.join(G, 'compare_v69.json'), 'w') as f: json.dump(out, f, indent=1); f.write('\n')
    write_report(out, inp)
    print(json.dumps(OrderedDict((k, out['totals'][k]) for k in ('bodies', 'weightKg', 'weightByCategory')), indent=1))
    print('members', Counter(m['status'] for m in mem_rows))
    print('details', Counter(r['status'] for r in det_rows))
    print('bolts', out['bolts']['positions'])


def write_report(o, inp):
    L = []
    t = o['totals']; m = o['members']; d = o['details']
    ms = Counter(x['status'] for x in m); ds = Counter(x['status'] for x in d)
    L.append('# R10 rebuilt with v69\'s applications: comparison with Frame_28x53m_R10.ss')
    L.append('')
    L.append('Generated by `tools/golden/r10_compare.py` (do not edit by hand). Pipeline: `python3 tools/golden/r10_inputs.py` (inputs) → '
             '`node tools/golden/r10_rebuild.js` (replay in web v69 headless, and R10 opened in v69) → `python3 tools/golden/r10_compare.py`. '
             'Raw data: `tests/steel/golden/r10/rebuild_inputs.json`, `rebuild_v69.json`, `r10_parts_v69.json`, `compare_v69.json`. '
             'This is the v69 side of the S10 acceptance; the app replays the same `rebuild_inputs.json` through its ports: '
             '**the app\'s rebuild equals v69\'s body by body** (`R10RebuildTest`, -PhostJni: 6,327 bodies and 1,272 connections, 0 differences in kind, name, volume, box, part fields, owners, bolt groups, connection settings and welds), so every number below holds for the app too.')
    L.append('')
    L.append('Web build = v69 + the chapter **v70 · Tekla parity** (`web/v70_tekla_parity.js`, docs/steel/PARITY.md; the harness adds it to the '
             'reference copy, `SS_ORACLE=v69` runs plain v69); "v69" below means that web build. v70 so far: C04 purlin cleat, Cleat location Back '
             '(Tekla, the default for new cleats; R11 fix 1): 528 / 528 parts and 1,056 / 1,056 bolts (`R11C04Test`, app vs R11, 0.1 mm).')
    L.append('')
    L.append('## Headline')
    L.append('')
    L.append('| | R10 | v69 rebuild |')
    L.append('|---|---|---|')
    L.append(f"| Bodies | {t['bodies']['r10']:,} | {t['bodies']['v69']:,} |")
    for k in sorted(set(t['kinds']['r10']) | set(t['kinds']['v69'])):
        L.append(f"| … kind `{k}` | {t['kinds']['r10'].get(k, 0):,} | {t['kinds']['v69'].get(k, 0):,} |")
    L.append(f"| Connections | {t['connections']['r10']:,} (TKBOLT / TKCUT) | {t['connections']['v69']:,} (components) |")
    L.append(f"| Bolts | {o['bolts']['r10']['bolts']:,} in {o['bolts']['r10']['groups']:,} groups | {o['bolts']['v69']['bolts']:,} in {o['bolts']['v69']['groups']:,} groups |")
    L.append(f"| Weight (v66 Tekla weights, bolts excluded) | {t['weightKg']['r10']:,.2f} kg | {t['weightKg']['v69']:,.2f} kg ({t['weightKg']['diff']:+,.2f}) |")
    for k, (a, b) in t['weightByCategory'].items(): L.append(f"| … {k} | {a:,.2f} kg | {b:,.2f} kg |")
    L.append('')
    L.append(f"Members: {len(m)} placed ({sum(1 for x in m if x['kind'] == 'builtup')} built-up equivalents, {sum(1 for x in m if x['kind'] == 'given')} R10 members as given): "
             + ', '.join(f'{v} {k}' for k, v in sorted(ms.items())) + '.')
    for kind, lab in (('builtup', 'built-up equivalents'), ('given', 'R10 members as given')):
        xs = [x for x in m if x['kind'] == kind]
        L.append(f"- {lab}: {sum(1 for x in xs if x['status'] == 'MATCHED')} matched of {len(xs)} (inputs equal R10 for all {sum(1 for x in xs if not x.get('diffs'))})")
    L.append('')
    L.append(f"R10 detail parts (plates that are not built-up webs / flanges, and the members v69 components make): {len(d)}: "
             + ', '.join(f'{v} {k}' for k, v in sorted(ds.items())) + '.')
    p = o['bolts']['positions']
    L.append(f"Bolts on the same line (same diameter, R10 bolt within 0.1 mm of a v69 bolt axis): {p.get('boltsMatched', 0):,} of {p.get('bolts', 0):,} R10 bolts "
             f"({p.get('boltsMatchedSameLengthGrade', 0):,} of them also with the same length and grade); whole groups: {p.get('groupsMatched', 0)} of {p.get('groups', 0)}. "
             "Per diameter: " + ', '.join(f"{k} {v:,}" for k, v in o['bolts']['matchedPerDiameter'].items()) + '.')
    L.append('')
    L.append('Status words: MATCHED = equal within the tolerances (0.1 mm points and boxes, volume within 2 % for plates); '
             'INPUT EQUAL, SOLID DIFFERS = the member\'s inputs equal R10 but its solid does not (built-ups: one BI solid against three Tekla plates with their end cuts; given members: v69\'s component cuts against R10\'s TKCUT cuts); '
             'NEAR = box within 5 mm; ROUGH = box within 50 mm; MISSING = no v69 part within 50 mm; AS BOLT = an R10 anchor rod member that v69 makes as a bolt of a bolt group.')
    L.append('')
    L.append('## How R10 was rebuilt in v69 (the inputs)')
    L.append('')
    L.append(f"- Grid: `x {inp['grid']['x']}`, `y {inp['grid']['y']}`, `z {inp['grid']['z']}` (R10's own, Tekla syntax).")
    L.append('- **Portal frame application: not used.** v69\'s portal frame (v33, with v42 girts / gables and v43 crane) makes one span at x = 0 (two columns, two rafters per frame) and replaces the parts of an earlier run, so it cannot make R10\'s four spans on five column lines, '
             'its built-up BI220 / BI320 columns, the ridge monitor or the mezzanine. Option needed: number of spans (or a list of spans), an x origin, and built-up / different column sections per line. Everything is placed member by member, as in the owner\'s video (Column / Beam tools on the grid).')
    c = inp['counts']
    L.append(f"- Members: {c['builtup']} built-up equivalents (each R10 web with its two flanges → one `BI<H>-<tw>-<b>-<tf>` member on the section centre line, ends from the three plates) and {c['given']} R10 members as given "
             '(purlins, bracing, struts, sag rods, stair stringers, stanchions, hand rails, railings). Not placed because v69 components make them: '
             + ', '.join(f"{k} ({len(v)})" for k, v in inp['notPlaced'].items()) + '.')
    L.append(f"- Components: {c['connections']} in one `connectMany` step: " + ', '.join(f"{k} {v}" for k, v in c['byKey'].items())
             + f". v69 built {o['replay']['connectionsInV69']} connections, {o['replay']['notOk']} with a red symbol, {len(o['replay']['errors'])} replay errors.")
    L.append('- Which parts each component joins comes from R10: the detail plate it would make (base plate, end plate, shear plate, tongue, cleat …) and the TKBOLT bolt groups that join that plate to the other assembly; '
             'settings (plate thickness and size, bolt diameter, rows / gauge / pitches / edge distance, anchors, grout, cast plate) come from that plate and its bolts where v69 has the option.')
    if inp['unhandled']:
        L.append('- R10 detail plates for which no component was added (not bolted to another assembly, or no rule): '
                 + '; '.join(f"{k}: {len(v)}" for k, v in inp['unhandled'].items()) + '.')
    lw = inp['leftover']['webs']
    L.append(f"- Webs left as plates (no two flanges found): {len(lw)} (96 are the eave and apex haunch webs, which have one flange; the apex haunch is made by the v54 apex).")
    L.append('')
    L.append('## R04 checklist, row by row')
    L.append('')
    L.append('Columns: the components added for the row; the parts and bolts v69 made with them; R10\'s detail parts of that row and how many of them v69 reproduced. '
             'Row 6 (node gussets) is made by the Ttg connections of row 5 (node = 1).')
    L.append('')
    L.append('| # | Connection | v69 components | v69 parts / bolts made | R10 detail parts | matched | near / rough | missing | verdict |')
    L.append('|---|---|---|---|---|---|---|---|---|')
    for rk, r in o['checklist'].items():
        s = r['status']
        L.append(f"| {rk} | {r['title']} | {r['components'] or ('(row 5)' if rk == '6' else 0)} | {r['v69Parts']} / {r['v69Bolts']} | {r['r10Parts']}" + (f" (+{r['givenMembers']} members as given, {r['givenMatched']} matched)" if r['givenMembers'] else '')
                 + f" | {s.get('MATCHED', 0)} | {s.get('NEAR', 0)} / {s.get('ROUGH', 0)}" + (f" (+{s.get('AS BOLT', 0) + s.get('AS BOLT, MOVED', 0)} as bolts)" if s.get('AS BOLT', 0) + s.get('AS BOLT, MOVED', 0) else '')
                 + f" | {s.get('MISSING', 0)} | {r['verdict']} |")
    L.append('')
    L.append('## Members, group by group')
    L.append('')
    L.append('| Group | count | status | box diff (max, mm) | volume v69 / R10 | kg R10 → v69 |')
    L.append('|---|---|---|---|---|---|')
    mg = defaultdict(list)
    for x in m: mg[(x['kind'], x['group'], inp['members'][x['i']]['st']['prof'], inp['members'][x['i']]['st']['name'])].append(x)
    for k in sorted(mg):
        xs = mg[k]; sc = Counter(x['status'] for x in xs)
        L.append(f"| {k[0]} {k[1]} {k[3]} {k[2]} | {len(xs)} | " + ', '.join(f'{v} {s}' for s, v in sorted(sc.items()))
                 + f" | {max(x.get('boxDiff', 0) for x in xs):.1f} | {min(x.get('volRatio') or 0 for x in xs):.3f}–{max(x.get('volRatio') or 0 for x in xs):.3f} | {sum(x.get('kgR10', 0) for x in xs):,.1f} → {sum(x.get('kgV69', 0) for x in xs):,.1f} |")
    dfl = Counter(f for x in m for f in x.get('diffs', {}))
    L.append('')
    L.append('Field differences on members (count of members): ' + (', '.join(f'{k} {v}' for k, v in sorted(dfl.items())) if dfl else 'none (every placed member has R10\'s inputs: profile, points, rotation, plane / depth, offsets, class, name)') + '.')
    L.append('')
    L.append('## Part groups not reproduced (or not exactly), with the reason and the option needed')
    L.append('')
    L.append('"Same-size part at" = for the parts not matched, how many have a v69 component part of the same size (box extents within 3 mm) within about a metre, and the median centre distance: a small distance means the right plate in a slightly different place.')
    L.append('')
    L.append('| Row | Group (R10 name, profile) | R10 count | status | same-size part at | kg R10 → v69 | Why | Option needed |')
    L.append('|---|---|---|---|---|---|---|---|')
    prev = None
    for g in o['groups']:
        c = g['counts']
        if c.get('MATCHED', 0) == c['total']: continue
        why, opt = (g['reason'], g['option']) if (g['reason'], g['option']) != prev else ('as above', 'as above')
        prev = (g['reason'], g['option'])
        parts = g['group'].split(' | ')
        st = ', '.join(f'{v} {k}' for k, v in c.items() if k != 'total')
        ssz = f"{g['sameSize']['n']} at {g['sameSize']['median']:,.1f} mm" if g['sameSize'] else '—'
        L.append(f"| {parts[0]} | {' / '.join(parts[1:])} | {c['total']} | {st} | {ssz} | {g['kgR10']:,.1f} → {g['kgV69']:,.1f} | {why} | {opt} |")
    L.append('')
    L.append('## v69 parts with no R10 counterpart')
    L.append('')
    L.append(', '.join(f'{k}: {v}' for k, v in o['v69Extra'].items()) + '.')
    L.append('')
    L.append('## Bolts per size')
    L.append('')
    L.append('| Size | R10 | v69 |')
    L.append('|---|---|---|')
    b = o['bolts']
    for k in sorted(set(b['r10']['bySize']) | set(b['v69']['bySize']), key=lambda s: (int(re.match(r'M(\d+)', s).group(1)), s)):
        L.append(f"| {k} | {b['r10']['bySize'].get(k, 0)} | {b['v69']['bySize'].get(k, 0)} |")
    for k in sorted(set(b['r10']['byDiameter']) | set(b['v69']['byDiameter']), key=lambda s: int(s[1:])):
        L.append(f"| **{k} total** | {b['r10']['byDiameter'].get(k, 0)} | {b['v69']['byDiameter'].get(k, 0)} |")
    L.append('')
    L.append('R10\'s anchor rods (ROD20 192, ROD16 16) are members, not bolts; v69 makes them as anchor bolt groups (M20 L677 4.6, M16 L284 4.6).')
    L.append('')
    L.append('## Parts per type (name × profile)')
    L.append('')
    L.append('Names differ between Tekla and v69 (e.g. `Base Plate × FLT20*200` against `BASE PLATE × PL20`), so the two lists are given side by side; the matching above pairs them by geometry.')
    L.append('')
    L.append('| R10 | count | v69 | count |')
    L.append('|---|---|---|---|')
    a = list(t['partsPerType']['r10'].items()); c2 = list(t['partsPerType']['v69'].items())
    for i in range(max(len(a), len(c2))):
        x = a[i] if i < len(a) else ('', ''); y = c2[i] if i < len(c2) else ('', '')
        L.append(f"| {x[0].replace(' | ', ' × ')} | {x[1]} | {y[0].replace(' | ', ' × ')} | {y[1]} |")
    L.append('')
    L.append('## Not verified / unsure')
    L.append('')
    L.append('- The built-up equivalents are derived (section centre line, square ends at the outermost plate extent on each plate\'s centre line); Tekla\'s own member reference line and end cuts are not in R10, so they are compared as solids, not as Tekla inputs.')
    L.append('- R10 data oddities kept as they are: the 16 gable-column webs sit 28.05 mm off the flange centre line, one stair-post flange 12 mm off the web, grout plates up to 130 mm off the base plate centre, and 3 mezzanine girder assemblies have flanges but no web.')
    L.append('- Component settings are taken from R10 where v69 has an option; positions along members (cleats, tongue plates, shear tabs) follow v69\'s rules, so many detail parts land NEAR or ROUGH rather than MATCHED.')
    L.append('- The app side is not part of this report: it replays `rebuild_inputs.json` through the ported applications later.')
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    text = '\n'.join(L) + '\n'
    if MODEL != 'R10':   # the report speaks of the model it compares with (R11 = R10 + Tekla-proven fixes, docs/steel/PARITY.md)
        text = text.replace('R10', MODEL).replace(f'{MODEL}RebuildTest', 'R11RebuildTest')
        text = text.replace('# ' + MODEL + ' rebuilt', '# ' + MODEL + ' rebuilt', 1)
    with open(REPORT, 'w') as f: f.write(text)


if __name__ == '__main__':
    main()
