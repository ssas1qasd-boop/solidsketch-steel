#!/usr/bin/env python3
"""Rule B on the app's R13 rebuild against the itemised baseline (owner 2026-11-28, PARITY.md integration rule).
Every finding must match a line of tools/ci/ruleB_r13_baseline.txt (same check, same parts, place within 2 mm); a finding not on
the list fails, even if the total goes down. A line whose finding has disappeared also fails, so it is removed (the list only shrinks).
    python3 tools/ci/ruleB_baseline_check.py build/suite/ruleB_r13_app.json [tools/ci/ruleB_r13_baseline.txt]"""
import json, math, sys
F = json.load(open(sys.argv[1])); P = sys.argv[2] if len(sys.argv) > 2 else 'tools/ci/ruleB_r13_baseline.txt'
lines = [l for l in open(P).read().splitlines() if l.strip() and not l.startswith('#')]
def key_f(x): return (x['check'], 'Ttg grip' if x['check'] == 'BOLTS' and x.get('part') == 'grip' else ' / '.join(sorted([x.get('part') or '', x.get('other') or ''])))
entries = []
for l in lines:
    c = [s.strip() for s in l.split('|')]; parts = c[2] if c[1] != 'OVERLAP' else ' / '.join(sorted(s.strip() for s in c[2].split(' / ')))
    entries.append(dict(line=l, check=c[1], parts=parts, at=[float(v) for v in c[3].split()], used=False))
new = []
for x in F:
    k = key_f(x); at = x.get('at')
    m = next((e for e in entries if not e['used'] and e['check'] == k[0] and e['parts'] == k[1] and at and math.dist(e['at'], at) <= 2.0), None)
    if m: m['used'] = True
    else: new.append(x)
stale = [e for e in entries if not e['used']]
for x in new: print('NOT ON THE LIST:', json.dumps(x))
for e in stale: print('GONE (remove from the list):', e['line'])
print(f'rule B on R13_app: {len(F)} findings, {len(F) - len(new)} on the baseline, {len(new)} not on it, {len(stale)} baseline lines gone')
sys.exit(1 if new or stale else 0)
