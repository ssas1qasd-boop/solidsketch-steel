# Shared brief for the connection agents (SolidSketch Steel, owner rules 2026-11-28)

Repo: ssas1qasd-boop/solidsketch-steel. Main checkout /home/user/solidsketch-steel belongs to the MAIN SESSION: never edit, build, checkout or reset in it. Read the project rules first: /home/user/solidsketch-steel/CLAUDE.md and docs/steel/PARITY.md (sections "Reference model: R13", "Open", "Reference order", "Reverse-engineering method", "Standing rule", and the sections of your connections), docs/steel/R13.md, docs/steel/WEIGHT-ANALYSIS.md.

## Setup
- `cd /home/user/solidsketch-steel && git fetch origin && git worktree add /home/user/wt-<agent> -b <branch> origin/main`; work only there.
- Copy the untracked files the tools need: `cp /home/user/solidsketch-steel/reference/solidsketch_v69.html <wt>/reference/` and `cp /home/user/solidsketch-steel/local.properties <wt>/`.
- Host JNI tests need `-PlocalM2=/home/user/m2local -PhostJni`.

## Reference and pipelines (R13 is the reference; SS_MODEL defaults to R13)
- Web build: `python3 tools/web/build_v70.py` -> reference/solidsketch_v70.html. The parity chapter is web/v70_tekla_parity.js.
- R13 rebuild: `python3 tools/golden/r10_inputs.py && python3 tools/golden/r10_c04.py && node tools/golden/r10_rebuild.js && python3 tools/golden/r10_compare.py` -> tests/steel/golden/r13/{rebuild_inputs,rebuild_v69,compare_v69}.json and docs/steel/R13-REPORT.md (generated: change r10_compare.py, never hand-edit). Model: tests/steel/models/Frame_28x53m_R13.ss.
- Golden cases: `node tools/golden/run_cases.js [filter]`; rule-B dumps `node tools/golden/c09_cases.js` -> build/c09/*.json; rule B: `python3 tools/golden/geom_checks.py <dump or .ss>` must show 0 failures on every case you touch and on the R13 rebuild of your connections.
- App goldens: `node tools/golden/app_cases.js tools/golden/cases/s10_conns.js tests/steel/golden/app/s10_conns.json` (and s10_hw / s10_stairs / s10_portal). Data/dialog goldens after catalog/dialog changes: `node tools/golden/data_export.js && node tools/golden/s7_catalog.js && node tools/golden/s7_dialog.js && node tools/golden/conn_defs.js`.
- Host tests: `./gradlew --no-daemon --no-parallel -q -PlocalM2=/home/user/m2local -PhostJni :steel:testDebugUnitTest :app:testDebugUnitTest --rerun --continue`. All must pass before you merge (except a test another agent owns, if main already has it failing: say so).
- App R13 tests: app/src/test/java/com/solidsketchapp/steel/R13*Test.kt (one per connection; R13RebuildTest compares app vs web body by body and writes build/R13_app.ss).
- Parity images: tools/golden/parity_png.py (add a composer for your code, like c17 / x02): R13 | Tekla drawing (Package_2 PDFs; the old Package_1 zip helpers are in parity_png.py) | video frame | app emulator still, orthographic, labelled, with dimensions.
- Tekla package 2 (reference only, never in the APK): tests/steel/tekla/Frame_28mx53m_Package_2.zip; unzip to /tmp/<agent>-pkg (outside the repo; never commit extracted files). Use `python3 -I` on extracted files.

## Method per connection (PARITY.md): step 0 Tekla's own help page (support.tekla.com), the video toolchain (tools/video/find.py, tools/video/gemini_read.py; note in VIDEO.txt when the lighter Gemini model was used), R13 as target, reverse engineering to 0.1 mm, rule B 0 failures, the fix in BOTH web v70 and the app (Kotlin, steel/ module), tests (golden case + R13 app test).
SECURITY: APIFY_TOKEN and GEMINI_API_KEY are set in the environment. Never print them, never write them to a file, a log or a commit.

## CI on your branch
- The workflow (.github/workflows/android-emulator.yml) runs on every branch; concurrency is per branch, so runs never cancel each other's. Emulator 4 cores / 8192M.
- Your recording test (app/src/androidTest/.../<CODE>RecordingTest.kt; copy the pattern of C17RecordingTest / X02RecordingTest: catalog search by the exact catalog label, dialog fields via tags tk_<key>, hideKeyboard before dropdown picks, Pick parts with taps and the catHit / nearMember fallback, orbit front/end/top/3D, orthographic stills in landscape, save the .ss, then the dialog again with one option changed + Modify) must be added to the notClass list in tools/ci/emulator_run.sh and to the adb pull loop (lowercase code), and set REC_CLASSES on your branch to your recording classes only.
- Set your own APK version in app/build.gradle.kts (versionName given below; versionCode as given). Watch the run with `gh run list --repo ssas1qasd-boop/solidsketch-steel --branch <branch>`; download artifacts with `gh run download <id> --repo ssas1qasd-boop/solidsketch-steel -D <dir>`. Check <CODE>RecordingTest_rc.txt = 0 and look at the recording frames before you deliver. Push with retries. Don't wait with `sleep` chains longer than a few minutes in one command; poll.

## Shared files
PARITY.md, PROGRESS.md: add or update ONLY your own section (one per connection). R13-REPORT.md is generated (regenerate after rebasing). WEIGHT-ANALYSIS.md: main session owns it; report your weight change in your reply instead. Touch only your own components, tests, cases and recording tests; do not edit other connections' code.

## Commits
End every commit message with:
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WuKK1kogVMvr56g7SHa75X
Never put a model name in commits or files.

## Delivery (per connection or group, from your own final green CI run)
Put these in /tmp/claude-0/-home-user-solidsketch-steel/6611724d-ad87-5673-92d6-fdf1e5de3b4b/scratchpad/deliver/<CODE>/ : the APK (apk-phone artifact, unzipped, your version name), the v70 HTML built from the SAME commit as the APK (git worktree of that commit + build_v70.py), R13_app.ss (from R13RebuildTest at that commit), <CODE>.png, <CODE>_recording.mp4, VIDEO.txt, BEFORE_AFTER.txt (matched before -> after, weight change vs Tekla 64,023.4 kg list / 64,017.49 kg st.tkW), the .ss files the emulator saved. The main session sends them to the owner. Nothing is marked done (✓) until the owner's phone OK.

## Finish
When done and green: `git fetch origin && git rebase origin/main`, regenerate the goldens/reports your connections own, rerun the host tests and rule B, push the branch (force-with-lease after rebase), then merge into main: `git checkout main`-free way: `git push origin <branch>:main` only if it is a fast-forward of origin/main (otherwise rebase again). Then reply with: what you did, a table (connection, matched before -> after at 0.1 mm, weight change, CI run id + status), the delivery folder path, and anything not verified. If you get stuck on the same failure after several attempts, stop and report it.
