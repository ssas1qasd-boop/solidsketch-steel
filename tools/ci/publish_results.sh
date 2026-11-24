#!/usr/bin/env bash
# Writes ci-out/summary.md (what ran, how long, every test step) and pushes it with the step results to the
# ci-results branch (runs/<run id>/ and latest/), so the results can be read without the Actions API.
set -u
OUT=ci-out; mkdir -p "$OUT"
NOW=$(date +%s); START=${RUN_START:-$NOW}; SECS=$((NOW - START))
STEPS=$(find app/build/outputs/connected_android_test_additional_output -name steps.json 2>/dev/null | head -1)
[ -n "$STEPS" ] && cp "$STEPS" "$OUT/steps.json"
python3 - "$OUT" "$SECS" <<'PY'
import json, os, sys, glob, xml.etree.ElementTree as ET
out, secs = sys.argv[1], int(sys.argv[2])
env = os.environ.get
L = [f"# Emulator UI run {env('GITHUB_RUN_ID','?')} ({env('GITHUB_SHA','?')[:7]})", ""]
L += [f"- data files on main: {env('DATA_OK','?')}", f"- KVM: {env('KVM','?')} · runner cores: {os.cpu_count()}",
      f"- unit tests incl. UI golden (gate before the APK): {env('UNIT','skipped')}",
      f"- build APK + test APK: {env('BUILD','skipped')}", f"- emulator tests step: {env('EMU','skipped')}"]
rc = open(f"{out}/connected_rc.txt").read().strip() if os.path.exists(f"{out}/connected_rc.txt") else "-"
cs = open(f"{out}/connected_seconds.txt").read().strip() if os.path.exists(f"{out}/connected_seconds.txt") else "-"
L += [f"- connectedDebugAndroidTest exit code: {rc} ({cs} s)", f"- job time until this step: {secs} s = {secs/60:.1f} min (billed: whole minutes, rounded up per job)"]
if os.path.exists(f"{out}/device.txt"): L += ["", "Device:", "```", open(f"{out}/device.txt").read().strip(), "```"]
t = f = 0; fails = []
for x in glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True) + glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    try: r = ET.parse(x).getroot()
    except Exception: continue
    for tc in r.iter("testcase"):
        t += 1
        for k in ("failure", "error"):
            e = tc.find(k)
            if e is not None: f += 1; fails.append(f"{tc.get('classname')}.{tc.get('name')}: {(e.get('message') or e.text or '')[:1500]}")
L += ["", f"JUnit (unit + instrumented): {t} tests, {f} failed"]
for x in fails[:20]: L += ["", "```", x, "```"]
if os.path.exists(f"{out}/steps.json"):
    L += ["", "| step | ok | ms | message |", "|---|---|---|---|"]
    for s in json.load(open(f"{out}/steps.json")): L.append(f"| {s['step']} | {'✓' if s['ok'] else '✗'} | {s['ms']} | {s['message'][:300].replace('|','/').replace(chr(10),' ')} |")
if os.path.exists(f"{out}/connected.log"):
    L += ["", "connected.log (end):", "```", "".join(open(f"{out}/connected.log", errors="replace").readlines()[-40:]), "```"]
open(f"{out}/summary.md", "w").write("\n".join(L) + "\n")
PY
cat "$OUT/summary.md"
cat "$OUT/summary.md" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
# publish to the ci-results branch
R=$(mktemp -d); git -C "$R" init -q; git -C "$R" config user.name "ci-results"; git -C "$R" config user.email "ci-results@users.noreply.github.com"
URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
if git -C "$R" fetch -q --depth 1 "$URL" ci-results 2>/dev/null; then git -C "$R" checkout -q -b ci-results FETCH_HEAD; else git -C "$R" checkout -q --orphan ci-results; fi
D="runs/${GITHUB_RUN_ID:-local}"; mkdir -p "$R/$D" "$R/latest"; rm -f "$R"/latest/*
for f in summary.md steps.json device.txt connected_rc.txt connected_seconds.txt; do [ -f "$OUT/$f" ] && cp "$OUT/$f" "$R/$D/" && cp "$OUT/$f" "$R/latest/"; done
[ -f "$OUT/connected.log" ] && tail -n 400 "$OUT/connected.log" > "$R/$D/connected_tail.log" && cp "$R/$D/connected_tail.log" "$R/latest/"
[ -f "$OUT/unit.log" ] && tail -n 300 "$OUT/unit.log" > "$R/$D/unit_tail.log" && cp "$R/$D/unit_tail.log" "$R/latest/"
[ -f "$OUT/build.log" ] && tail -n 300 "$OUT/build.log" > "$R/$D/build_tail.log" && cp "$R/$D/build_tail.log" "$R/latest/"
[ -f "$OUT/logcat.txt" ] && grep -E " (E|F) |AndroidRuntime|FATAL" "$OUT/logcat.txt" | tail -n 300 > "$R/latest/logcat_errors.txt" || true
git -C "$R" add -A && git -C "$R" commit -q -m "results of run ${GITHUB_RUN_ID:-local} (${GITHUB_SHA:-?})" && git -C "$R" push -q "$URL" ci-results || echo "could not publish to ci-results"
