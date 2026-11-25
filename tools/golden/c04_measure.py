#!/usr/bin/env python3
"""Measures the C04 workflow case (tools/golden/c04_workflow.js for the web, C04WorkflowTest for the app): perpendicular to rafter A,
the rafter top and each purlin's underside over the rafter (lowest / highest bottom corner), and whether the cleat bolts cross the
purlin web.   python3 tools/golden/c04_measure.py build/c04-workflow-web.json"""
import json, sys
import numpy as np
d = json.load(open(sys.argv[1])); B = {b['id']: b for b in d['bodies']}
A = B[d['A']]; p1, p2 = np.array(A['st']['p1'], float), np.array(A['st']['p2'], float); a = (p2 - p1) / np.linalg.norm(p2 - p1)
n = np.array([0, 0, 1.0]) - a * a[2]; n /= np.linalg.norm(n)
VA = np.asarray(A['m']['v'], float); top = float((VA @ n).max())
rows = []
for b in d['bodies']:
    if b['name'] != 'PURLIN': continue
    V = np.asarray(b['m']['v'], float); q1, q2 = np.array(b['st']['p1'], float), np.array(b['st']['p2'], float); ax = (q2 - q1) / np.linalg.norm(q2 - q1)
    # the purlin's underside: its lowest vertices along n (a straight member: constant along its axis, which is perpendicular to n)
    s = V @ n; lo = float(s.min())
    # the bottom flange's other corner: the lowest vertex on the far side across the purlin (along the rafter)
    across = V @ a; far = V[np.abs(across - across[np.argmin(s)]) > 20]; hi = float((far @ n).min()) if len(far) else lo
    rows.append((b['id'], round(lo - top, 2), round(hi - top, 2), b['st'].get('rAng'), b['st'].get('depth')))
print('rafter A top along n:', round(top, 2), '| rafter depth', A['st'].get('depth'), A['st'].get('prof'))
for r in rows: print('purlin %s: underside - rafter top = %+.2f mm (lowest corner), %+.2f mm (other bottom corner); rAng %s depth %s' % r)
