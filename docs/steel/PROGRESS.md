# Steel progress

History: S0–S4 in `docs/steel/PROGRESS-S0-S4.md` (incl. the postponed license gate rules); S5–S6 in `docs/steel/PROGRESS-S5-S6.md` (incl. the S5 and S6 phone check lists).

## Current milestone
**S7 in progress** (owner plan 2026-11-23: S7, S8 and S9 without waiting for phone checks, push after every step, stop before S10; Claude does all of S8 and S9). Done: S7 step 1 (component engine, End plate 144 equals golden), S7 step 2 (catalog sheet, pick order, component dialog, presets). S5 and S6 APKs: `dist/solidsketch-steel-s5-debug.apk`, `dist/solidsketch-steel-s6-debug.apk` (phone checks in `PROGRESS-S5-S6.md`).

## S7 plan (component framework)
1. Component engine: v69 `famGeom` context, the v58 geometry wrapper (bolt options reach `stBolts`, bolt info d0 / std / thr / sw, Welds tab), the live EP family (v48), component plates and bolts in regen (`tkKidSt` part fields, class colours, assembly owner), welds kept on the connection. **Done.**
2. Catalog sheet (18 groups / 252 items, search by name or number, recently used), pick order (TK_PICK prompts), component dialog with Modify / Get / on-off / Apply, saved presets. **Done.**
3. Symbols (cones green / yellow / red, lines to the joined parts) and the rule checks per component (`tkStatus`).
Then the S7 APK and phone check list.

## S7 step 1 (2026-11-23): component engine, End plate 144
- `steel/conn/Fam.kt`: `FamCtx` (v69 `famGeom`: q = CONN def + c.q; cut / plate / bolts / weld collectors), `Fam.DEF` (v69 FAM_DEF with the v58 EP additions), helpers `tkRows`, `v49Pitches`, `v58Span`, `stStiffPair`, `stLAngle`, and **EP** (live v48 definition: plate flush on the main part's face, rows / pitches / posV / refV, cols 2 or 4, gauge2, posH / refH, cope limits, shim, front / stub plate, compensating flange plates, seat angle, column stiffeners, two-sided with shared grip), then the v58 wrapper (bolt info `d0`, `std`, `thr`, `sw`, `cmt`, `blen`; Welds tab `q.W`). The component's settings reach `stBolts` here (v58 TKB_Q), unlike TKBOLT.
- `steel/conn/Joint.kt`: `J.u`, `J.Pc`, `tPrim`, `yHi` / `yLo`, `P`; `stPoly3`, `stFrame3`.
- `StRegen`: catalog components (`T<n>`) go to their family with v69's CONN def (FAM_DEF + the component's TK options); component plates are made as bodies with v69's `tkKidSt` fields (name / role / prof / mat / cls / asm / pp / ps / fin), colour 0x9aa0a6 or the General tab class colour (`TK_CLS`), assembly owner by v69's family rule (`stOwner`, v40); holes subtracted with Manifold; the connection keeps its welds (`len` rounded) as v69's `stRegen` does.
- **Acceptance:** `S7T144Test` (Manifold on the build machine): **End plate 144 equals the S1 golden cases 3/3** (standard; HEM300 turned with an IPE450, 3 rows, 12 mm stiffeners; sloped R10 rafter): every body's volume (1e-3) and box (0.1 mm), the bolt group (points in v69's order, n, L, d), the welds (name, a, len), the plates' name / prof / mat / cls / asm. Unit tests: 74, all pass.

## S7 step 2 (2026-11-23): catalog sheet, pick order, component dialog, presets
- **Data from the live v69 page** (`tools/golden/s7_catalog.js`, `--check` compares): `steel/src/main/assets/steel/tk_ui.json` = ST_CATALOG after every chapter (18 groups, 252 items: the 246 Tekla components, the 4 dialogs v49 adds — purlin cleat `Tcl`, tube gusset `Ttg`, tube crossing `Ttgx`, sag rod `Tsag` — and Numbering / Checks), the live CONN of each component (label, Tekla name, pick prompts, family, group, def), the live dialog spec `tkSpec(k)` (tabs, groups, fields; the 3 field accessors v69 uses — `neg`, `gaugeList`, `pitchList` — are matched by their source text and ported), the catalog icons (SS_ICONS), SVG_PIC, TKPG; and `thumbs/*.webp` (v58 SS_THUMBS: 536 pictures).
- **Found in v69:** three tube components change family after the catalog is built (`T102t`, `T103t` → GU; `Ttc` → WF with a new def, L15458/L15461). Regen now uses the live CONN (family and def) from tk_ui.json, not the raw TK table.
- `steel/comp/Components.kt`: v69 `stCatalog` groups with "Recently used" (5, newest first), search (`label tekla key` contains the text, sheet order), `stCatPrompt`, `stCQ` (CONN def + saved defaults), presets (`ss.connSets`), `stConnAdd` + regen as one undo step (label = the component name; `stNextConn`; `pt` = first picked point), dialog Modify (one or all of this type, locked ones skipped), Delete (plates, bolts, cuts), `stConnOK`. Storage = v69's localStorage keys and JSON (`ss.connDef`, `ss.connSets`, `ss.connRecent`) in the app's SharedPreferences, so presets and defaults survive a restart.
- `CompDialog`: the live v58 dialog's inputs in v69's order (picture boxes, parts table, field rows with Tekla on/off, welds table), `tkGet` / `tkSet` (incl. `P.<role>.<x>`, `W.<weld>.<x>`), `tkInput` rules (number with comma, `auto` fields), read / fill / Load / Save / Save as / Apply / Get / on-off / Modify exactly as v69 (Modify applies each ticked field over CONN def + the component's own q, `_` keys dropped).
- `steel/comp/CompUi.kt` + `SvgLite.kt` + `BoltPic.kt`: catalog sheet (v38 look: search box, groups, icon or thumbnail, name, Tekla name, Properties), the v58 dialog (header with status, Save / Load / preset / Save as / ignore other types / Welds… / Help, Tekla tabs, picture with red input boxes placed as v69, Bolts tab picture = `tkBoltPic` with the real bolt pattern, numbered, dimension chains), OK / Apply / Modify / Get / on/off / all of this type / Pick parts / Cancel / Delete. A small SVG renderer draws v69's icons, pictures and pictograms (all of them parse in the test).
- Steel panel: **Components** (v69 `stConnSheet`: a selected component plate opens its dialog, else the catalog); while picking: `<name> · Properties · Catalog · Done`; the hint line shows `End plate (144): pick the main part` …; taps on a member are picked (`Already picked`, `Pick a steel part`); toasts `End plate created` / `could not be built for these parts`. Generators (family GEN) and the Numbering / Checks entries say which later milestone brings them.
- **Golden** `tests/steel/golden/s7/dialog.json` (`tools/golden/s7_dialog.js`): v69's live dialog driven through its own DOM: 4 cases on End plate 144 (Modify with nothing changed; Modify with t, the negated boxes 1/2, 4 bolt columns with the gauge list `60 100 60`, edge distance, tolerance, an unticked row, the Welds table size, class; Modify with 4 rows and pitches `80 70 90`, the Parts table material / name, Holes; Apply then a new component), plus 10 catalog searches.
- **Acceptance:** `S7CatalogTest` (5): 18 groups, 252 items, every one of the 246 TK components with v69's label / family / group / pick prompts, search equals v69 for all 10 filters, recently used, presets, every dialog spec / accessor / icon / picture / pictogram / thumbnail loads. `S7DialogTest` (Manifold): **the app's dialog gives v69's settings (c.q, compared key by key), welds, plates and bolt group in all 4 cases** (mutation check: with the `neg` accessor broken the test fails). Unit tests: 160 runs (debug and release variants), all pass.

## Large generated files (owner bundle)
Owner rule: big generated files do not go through the GitHub tool; they come in a bundle for the owner to push. Until they are on GitHub, the tests that read them fail there.
- From S4 (bundle `steel-s4-data.bundle`, sent 2026-11-22): `steel/src/main/assets/steel/profiles.json` (f822a133…76e5), `steel/src/main/assets/steel/tk_catalog.json` (dd1ec06b…ffe0), `tests/steel/golden/data/profile_cases.json` (94e2dba7…9205).
- From S7 (bundle `steel-s7-data.bundle`, 841 KB, branch `s7-data`; fetch it and copy the files to main): `steel/src/main/assets/steel/tk_ui.json` (5d88a4c5…3b9b3f), `steel/src/main/assets/steel/thumbs/` (536 webp, tree aac3ba8e…b7f067). The app's catalog sheet and dialogs need them; `S7CatalogTest` / `S7DialogTest` read them.
- From S5 and S6 (bundle `steel-s5-s6-data.bundle`): `tests/steel/golden/s5/members.json` (c0f8bf65…44e9), `tests/steel/golden/s6/r10_regen.json` (9c2ef222…9e1c), `tests/steel/golden/s6/bolts.json` (4a223bcc…88d4), `tests/steel/golden/s6/fittings.json` (42d9dc7d…e227).

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
S7 data and golden: `node tools/golden/s7_catalog.js` (tk_ui.json + thumbs; `--check`), `node tools/golden/s7_dialog.js` → `tests/steel/golden/s7/dialog.json`.
S6 golden: `node tools/golden/s6_cuts_bolts.js` (bolt / cut cases and R10 regenerated by v69, about 1 min) and `node tools/golden/s6_fittings.js` → `tests/steel/golden/s6/`. R10 acceptance: `./gradlew :app:testDebugUnitTest --tests '*S6R10RegenTest*' -PhostJni`.
Android modules: run one test class with `./gradlew :steel:testDebugUnitTest --tests '*StGridTest*'` (`:steel:test --tests` is refused by Gradle).
Note: Maven Central answered 429 (Too Many Requests) to parallel downloads in the cloud session; `--no-parallel` on the first build helped.

## NOT VERIFIED
- Steel mode screen on a phone (built and unit-tested, not yet tried by the owner): tap targets, the sheets' scrolling, the labels' positions.
- Two-finger pan on the phone (not shown in the recordings; Latin digits and LTR were confirmed in the S3 check).
- S5 look on a phone: edge lines and v69's lights are written to v69's numbers and checked with one pixel in WebGL2, not compared side by side with v69 on the owner's phone. Edge cost on R10 on a phone (time until they appear, GPU memory) not measured.
- S6 on a phone: Modify on R10 parts with their cuts and bolt groups (built and tested on the build machine with Manifold, not tried on a phone).
- Welds: no S6 connection type makes welds (TKCUT, TKBOLT and fitting have none in v69); weld data, the weld list and `weldonly` come with the components (S7–S9) and are not verified yet.
- The v58 Tekla bolt options (staggering, deleted bolts, slotted / oversized / tapped holes, bolt direction, washers / nuts off) are ported but have no v69 golden yet: v69's TKBOLT ignores them (see S6, found), and the components that use them are S7–S9. Checked then.
- Fittings with copes (a beam into a main beam's web) are ported (`stJoint` cope) but none of the 3 golden cases has a cope.
- Edges in v69 also go through `smoothEdgeLines` (drops seams of smooth surfaces); not ported (the 12° rule already keeps smooth round tubes and bolts without side lines).

## Decisions for the owner to review
Chosen while the owner was away (owner rule 2026-11-23: pick what matches v69, note it here, continue).
- Saved property sets ("Save as column", …) last for the session only; v69 keeps them in the browser's localStorage. S7 component presets and defaults are kept in the app's SharedPreferences with v69's keys and JSON (`ss.connDef`, `ss.connSets`, `ss.connRecent`); the part property sets can move there too.
- Component picking uses the app's normal body pick: a component plate in front of a member is hit first and answered "Pick a steel part" (v69 casts its ray against members only). Rarely matters; noted.
- Grid level lines are solid (v69 dashes them); our overlay lines have no dash pattern yet.
- **Sloped fitting:** v69 builds the right cut box (equal to ours), but its Manifold leaves the sloped rafter uncut (the 120 m cut box loses precision in v69's older Manifold; the volume is unchanged to 12 digits). The native app makes the intended cut (9.2 mm). Matching v69 here would mean copying a precision failure, so the app cuts; the straight cases equal v69.
- After Modify, connection parts are rebuilt only for the types ported in S6 (TKCUT, TKBOLT, fitting); other connection types keep their bodies as the file has them until S7–S9.
- `clean()` on load (S3 question): not done. v69 cleans every body it loads; the app keeps the file's geometry, and Modify rebuilds a part fresh from its st (as v69's regen does), so the result equals v69 either way (R10 acceptance above).
- The edges of a big model are computed in the background and appear a few seconds after the parts (v69 computes them when the body is made). Chosen so that opening R10 stays fast.

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
