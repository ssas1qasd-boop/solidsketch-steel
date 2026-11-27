#!/usr/bin/env bash
# The full suite after each merge (PARITY.md "Integration rule for parallel agents", step 3). Regenerates every web golden from
# v69 + v70 (+ web/v70/*.js), lists which golden files changed (each change must be explained by the merged work), runs rule B on
# every golden case dump and on the app's R13 rebuild, the R13 checks (rebuild, weights, assemblies, drawings) and all host tests.
#   bash tools/ci/full_suite.sh  -> build/suite/summary.txt (exit 1 if anything failed)
set -u; cd "$(dirname "$0")/../.."; OUT=build/suite; rm -rf "$OUT"; mkdir -p "$OUT"; FAIL=0
step() { local name=$1; shift; echo "== $name"; if "$@" > "$OUT/$name.log" 2>&1; then echo "ok   $name" >> "$OUT/summary.txt"; else echo "FAIL $name (see $OUT/$name.log)" >> "$OUT/summary.txt"; FAIL=1; fi; }
step build_v70 python3 tools/web/build_v70.py
step data bash -c 'node tools/golden/data_export.js && node tools/golden/s7_catalog.js && node tools/golden/s7_dialog.js && node tools/golden/s7_status.js && node tools/golden/conn_defs.js'
step s5_s6 bash -c 'node tools/golden/s5_geometry.js && node tools/golden/s6_fittings.js && node tools/golden/s6_cuts_bolts.js'
step cases bash -c 'node tools/golden/run_cases.js > build/suite_cases.log 2>&1; cat build/suite_cases.log; ! grep -q "^FAIL" build/suite_cases.log'
step comp bash -c 'for g in tests/steel/golden/comp/*.json; do c=$(python3 -c "import json,sys,re; s=json.load(open(sys.argv[1])).get(\"source\",\"\"); m=re.search(r\"(tools/golden/cases/\S+\.js)\", s); print(m.group(1) if m else \"\")" "$g"); [ -n "$c" ] || continue; node tools/golden/comp_cases.js "$c" "$g" || exit 1; done'
step gen node tools/golden/gen_cases.js tools/golden/cases/s9_gen.js tests/steel/golden/gen/s9_gen.json
step apps bash -c 'for f in s10_conns s10_hw s10_stairs s10_portal; do node tools/golden/app_cases.js tools/golden/cases/$f.js tests/steel/golden/app/$f.json || exit 1; done'
step video_workflow node tools/golden/c04_workflow.js
step r13 bash -c 'python3 tools/golden/r10_inputs.py && python3 tools/golden/r10_c04.py && node tools/golden/r10_rebuild.js && python3 tools/golden/r10_compare.py && node tools/golden/r12_weights.js && node tools/golden/r13_totals.js'
step r11 bash -c 'SS_MODEL=R11 node tools/golden/r10_rebuild.js && SS_MODEL=R11 python3 tools/golden/r10_compare.py'
step ruleB_cases bash -c 'node tools/golden/c09_cases.js > /dev/null && n=0; for f in build/c09/*.json; do r=$(python3 tools/golden/geom_checks.py "$f" 2>&1 | grep -o "[0-9]* failures" | head -1); k=$(grep "^$(basename "$f")|" tools/ci/ruleB_known.txt | cut -d"|" -f2); [ "$r" = "0 failures" ] || [ -n "$k" -a "$r" = "$k failures" ] || { echo "$f $r"; n=$((n+1)); }; [ -n "$k" ] && echo "known Tekla geometry: $(basename "$f") $r (expected $k)"; done; echo "rule B case failures: $n"; [ $n = 0 ]'
step video_status node tools/golden/video_status.js
step host ./gradlew --no-daemon --no-parallel -q -PlocalM2=/home/user/m2local -PhostJni :core-io:testDebugUnitTest :core-view:testDebugUnitTest :steel:testDebugUnitTest :app:testDebugUnitTest --rerun --continue
# rule B on the app's whole R13 rebuild: the failure count may not rise above the baseline (tools/ci/ruleB_r13_baseline.txt); groups left
# are owned by open connections (PARITY.md "Open") and listed in the summary
step ruleB_r13_app bash -c 'python3 tools/golden/geom_checks.py build/R13_app.ss --out build/suite/ruleB_r13_app.json --md build/suite/ruleB_r13_app.md | head -1; python3 tools/ci/ruleB_baseline_check.py build/suite/ruleB_r13_app.json'
git status --short tests docs > "$OUT/golden_changes.txt"
python3 - "$OUT" <<'PY' >> "$OUT/summary.txt"
import json, sys
c = json.load(open('tests/steel/golden/r13/compare_v69.json')); t = c['totals']['weightKg']; import collections
print('R13 rebuild: members', dict(collections.Counter(m['status'] for m in c['members'])), '| details', dict(collections.Counter(d['status'] for d in c['details'])), '| web kg', t['v69'])
try:
    v = json.load(open('build/r13/v69_totals.json')); print('R13 totals (web opens R13):', {k: v[k] for k in v if k in ('parts', 'assemblies', 'kg', 'drawings', 'errors', 'bolts')})
except Exception as e: print('R13 totals: not read', e)
PY
grep -h 'rule B on R13_app' $OUT/ruleB_r13_app.log >> "$OUT/summary.txt"; sed -n '3,12p' $OUT/ruleB_r13_app.md >> "$OUT/summary.txt"
echo "golden files changed: $(wc -l < "$OUT/golden_changes.txt")" >> "$OUT/summary.txt"; cat "$OUT/summary.txt"; exit $FAIL
