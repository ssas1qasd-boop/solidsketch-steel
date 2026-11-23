# Steel progress

History of S0–S4 (milestone notes, phone checks, the postponed license gate rules): `docs/steel/PROGRESS-S0-S4.md`.

## Current milestone
S5 in progress (owner plan 2026-11-23: finish S5 and S6 without waiting for phone checks, push after every step, stop before S7). Done: step 1 (profile name rules, top bar), step 2 (grid). Next: step 3, member solids.

## S5 plan (steps; each one is tested, committed and pushed)
1. stProf name rules for the 97 names S4 left; top bar with its own solid background. **Done.**
2. Grid and levels in Tekla syntax (`stGrid`), drawn as overlays. **Done.**
3. Profile sections and member / plate solids with the Tekla position rules (plane, rotation, depth, offsets, dx1/dx2; live `stSection` and `stBuild` chains), class colours; golden: member solids within 0.1 mm of v69.
4. Steel mode screen: Grid sheet, work levels, Column / Beam / Plate tools picking grid points, property sets (`stPropSheet`), handles to move part ends, Exit Steel.
5. Look: edge lines `0x262b34` (v69 feature edges, 12°), v69's lights (hemisphere + three directional) and Phong material.
Then the S5 APK `dist/solidsketch-steel-s5-debug.apk` and the S5 phone check list.

## S5 step 2 (2026-11-23): grid and levels
- `steel/model/StGrid.kt`: exact port of v69 `stParse` (first value = start, then spacings; `n*d` repeats; as the first token `n*d` places n lines d apart starting at d; tokens read with JavaScript number rules, so `1e3` = 1000 and a bad token turns the rest into NaN, which v69 drops), `stLetters` (A…Z, AA…, ZZ, AAA), line labels (`lx`/`ly` or 1, 2, 3… / A, B, C…), level labels (`+7500`), JSON (`stGrid` = {x, y, z, lx?, ly?}), and the overlay lines of `stGridDraw` (every level, plus the work level; 1500 mm beyond the outer lines; v69 colours 0x4a5a70 at 0.55 and 0x2f6fb3 at 0.85; v69 dashes the level lines, here they are solid).
- Golden `tests/steel/golden/s5/grid.json` from the live v69 `stParse` / `stLetters` (harness patch line now also exposes `parse`, `letters`, `section`, `mf`): 16 grid strings incl. the edge cases above, 9 letter cases. `StGridTest` (4): all equal v69.
- **Acceptance:** X `0 4*13195` → 0, 13195, 26390, 39585, 52780; Y `0 5*5570` → 0, 5570, 11140, 16710, 22280, 27850; Z `0 7500 1500` → 0, 7500, 9000 (labels 1–5, A–F, +0 / +7500 / +9000), equal to v69.
- `tools/golden/s5_geometry.js` also writes `members.json` (40 member and plate solids from the live `stBuild`); used from step 3.

## S5 step 1 (2026-11-23)
- **Proof for S4 first** (owner): `data_export.js --check` → "all data files match v69"; `SteelDataTest` 5/5 pass; the HEA300, IPE360 and SHS100x100x5 rows equal v69 line 13557 (`["I",290,300,8.5,14,27,88.3,1.717]`, `["I",360,170,8,12.7,18,57.1,1.353]`, `["R",100,100,5,7.5,14.7,0.387]`; fields h, b, tw/t, tf, r, kg/m, paint m²/m; v69 keeps no area: area = kg/m ÷ 7850 kg/m³, e.g. HEA300 11,248 mm²). Note: `./gradlew :steel:test --tests …` is refused by Gradle for Android modules; use `:steel:testDebugUnitTest --tests …`.
- **stProf name rules ported** (`steel/data/StProf.kt`, exact port of the live chain: v32 base L13577 + v41 wrapper L15203): PL (`PLt`, `PLa*b`), D/RD/ROD, L `a*b*t` and `a*t`, FLT, parametric SHS/RHS (r = 2t) and CHS/PD/PIPE, UPN/UPE/U, C/CC/Z/ZZ, BI (welded I, optional bottom flange), PHI (tapered welded I). Same operation order as v69, so the doubles are bit-identical (e.g. FLT20*150 = 23.549999999999997 kg/m, as in v69). Keys use JavaScript number text: `JsNumber` moved from `:core-io` to `:core-api` (`:steel` may only use `:core-api`; noted in MERGE.md).
- **Test now strict:** all 636 golden names: **629 equal v69** (key, kind, and exactly the same set of fields with the same values) and 7 are null in both (`pl12x150`, `M10`–`M20` sizes R10 keeps in `prof`, `NOTAPROFILE`, empty). Mutation check: with the SHS corner radius changed from 2t to t, 3 names differ and the test fails.
- **Top bar:** the buttons and status lines now sit in their own white bar above the viewport (v69's `--panel` white, fully opaque), so the model can no longer draw over them at any zoom.
- App 0.5.0-s5a; the data line also shows a tapered profile from the new rules (`PHI300-600-8-200-12 = 64.4 kg/m`; v69: 64.4328). Unit tests: 52 JVM, all pass (host JNI tests not rerun: no native change). APK: `dist/solidsketch-steel-s5a-debug.apk`.

## How to build
Golden data: `node tools/golden/r10_totals.js` and `node tools/golden/run_cases.js` (needs the reference HTML and Playwright; see `tools/golden/README.md`).

Android SDK with platform 35, build-tools 35.0.0, NDK 30.0.16248370, CMake 4.1.2; `local.properties` with `sdk.dir`.
`./gradlew test assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
Manifold source: `./gradlew fetchManifold` (runs automatically before native builds; needs git and network once).
Unit tests with the real native code on the host: `./gradlew test -PhostJni` (needs cmake, ninja, a C++ compiler and a JDK).
Host native test: `tools/native/fetch_manifold.sh && cmake -S native/tests -B build/native-tests -G Ninja && cmake --build build/native-tests && ctest --test-dir build/native-tests`.
Shader check: `node tools/native/check_shaders.js`.
.ss both ways with v69: three steps in `tools/golden/README.md` (".ss both ways").
Data assets (S4): `node tools/golden/data_export.js` (write) and `--check` (compare with v69).
S5 golden (grid, member solids): `node tools/golden/s5_geometry.js` → `tests/steel/golden/s5/`.
Android modules: run one test class with `./gradlew :steel:testDebugUnitTest --tests '*StGridTest*'` (`:steel:test --tests` is refused by Gradle).
Note: Maven Central answered 429 (Too Many Requests) to parallel downloads in the cloud session; `--no-parallel` on the first build helped.

## NOT VERIFIED
- UI look of the S2/S3 check screen: not the web build's UI (Steel mode UI is S5 step 4).
- Two-finger pan on the phone (not shown in the recordings; Latin digits and LTR were confirmed in the S3 check).
- Shading is a simple headlight, not three.js's lighting; edge lines (`0x262b34`) are not drawn yet (S5 step 5).

## Decisions for the owner to review
Chosen while the owner was away (owner rule 2026-11-23: pick what matches v69, note it here, continue).
- (none yet)

## Decisions
- Separate app until the merge (owner, 2026-11-21).
- Own DWG writer, clean-room, R2000 (owner, 2026-11-21).
- Repo: `ssas1qasd-boop/solidsketch-steel` (owner, 2026-11-21).
- License gate postponed; always-allow `LicenseGate` for now (owner, 2026-11-21).
- `sscore-lite` and `sssteel` are static libraries; each JNI .so links its own copy, so modules never clash on shared-library names (S0).
- Manifold 3.5.4 (pinned, fetched at build time, not vendored) rather than the web's ~2.3–2.4 (S2).

## Open (owner)
- applicationId (proposed `com.solidsketchapp.steel`).
- License gate: when to bring it back, and which variants unlock Steel (TODO(owner)).
- Tekla `.db1` import: in or out of this plan.
- The workflow video (R10 and the R04 comparison arrived 2026-11-22).
