#!/usr/bin/env python3
"""Writes reference/solidsketch_v70.html = the owner's v69 DEV copy + the chapter web/v70_tekla_parity.js, inserted just before the
'v28 · BIM layers' chapter (inside the steel scope). The same insertion the golden harness makes in memory (tools/golden/lib/v69.js).
reference/ stays out of git (DEV copy: license check skipped); never put either file in an APK.

    python3 tools/web/build_v70.py
"""
import os, sys
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(REPO, 'reference', 'solidsketch_v69.html')
OUT = os.path.join(REPO, 'reference', 'solidsketch_v70.html')
CH = os.path.join(REPO, 'web', 'v70_tekla_parity.js')
ANCHOR = '  // ===================== v28 · BIM layers: Types and History Folders (schedules) ====================='
if os.path.getsize(SRC) != 20495043: sys.exit(f'{SRC}: not the expected v69 copy')
lines = open(SRC, encoding='utf-8', newline='').read().split('\n')
i = lines.index(ANCHOR)
import glob   # integration rule (PARITY.md): an agent's fix may be its own file web/v70/<code>.js, appended after the chapter in name order
EXTRA = sorted(glob.glob(os.path.join(REPO, 'web', 'v70', '*.js')))
chapter = open(CH, encoding='utf-8').read().rstrip('\n').split('\n')
for f in EXTRA: chapter += [f'  // ===== {os.path.relpath(f, REPO)} ====='] + open(f, encoding='utf-8').read().rstrip('\n').split('\n')
lines[i:i] = chapter + ['']
open(OUT, 'w', encoding='utf-8', newline='').write('\n'.join(lines))
print(f'{OUT}: {os.path.getsize(OUT)} bytes, v70 chapter at line {i + 1}' + (f' (+ {len(EXTRA)} file(s) from web/v70/)' if EXTRA else ''))
